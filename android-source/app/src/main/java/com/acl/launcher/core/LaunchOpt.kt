package com.acl.launcher.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 启动参数 AI 优化（对应桌面版 AclLaunchOpt.vb）。
 *
 * 桌面版是在点「启动游戏」后、真正拉起 Java 之前调用 AI 分析并写入配置。
 * 手机端同样遵循这个顺序：点击启动 → AI 优化参数 → 保存 → 继续。
 */
object LaunchOpt {

    /** 优化结果。 */
    data class Params(
        val jvm: String = "",
        val gc: String = "",
        val ramGb: Int = 0,
        val note: String = ""
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 读取本地版本信息摘要，作为 AI 的上下文。 */
    fun buildContext(
        mcVersion: String,
        loader: String,
        modCount: Int,
        ramTotalGb: Int,
        cpuCores: Int
    ): String = """
设备与整合包信息：
- MC 版本：$mcVersion
- 加载器：$loader
- 模组数量：$modCount
- 设备物理内存：${ramTotalGb} GB
- CPU 核心数：$cpuCores
""".trimIndent()

    /**
     * 让 AI 给出合适的 JVM 参数、GC 与内存分配。
     * 失败时回落到本地启发式规则。
     */
    suspend fun optimize(
        settings: Settings,
        mcVersion: String,
        loader: String,
        modCount: Int,
        ramTotalGb: Int,
        cpuCores: Int
    ): Params {
        if (!Ai.isConfigured(settings)) {
            val local = heuristic(mcVersion, modCount, ramTotalGb, cpuCores)
            return local.copy(note = "AI 未配置，使用本地推荐")
        }
        val sys = """
你是 Minecraft 启动器的 JVM 参数优化助手。
根据设备与整合包信息，给出最合适的启动参数。
严格要求：
1. 只输出 JSON，不要任何解释文字。
2. jvm：完整的 JVM 参数字符串（不含 -Xmx），例如 "-XX:+UseG1GC -XX:+ParallelRefProcEnabled"。
3. gc：GC 名称，G1GC / ZGC / ShenandoahGC 之一，ZGC 需 17+ 且内存 >8G。
4. ramGb：建议分配内存（GB），整数，不要超过设备物理内存的 60%。
5. note：一句话说明依据。
格式：{"jvm":"...","gc":"G1GC","ramGb":4,"note":"..."}
""".trimIndent()
        val ctx = buildContext(mcVersion, loader, modCount, ramTotalGb, cpuCores)
        val r = Ai.chat(settings, sys, ctx, 2000, 0.2)
        if (!r.ok) {
            val local = heuristic(mcVersion, modCount, ramTotalGb, cpuCores)
            return local.copy(note = "AI 调用失败（${r.error}），使用本地推荐")
        }
        val js = Pack.extractJson(r.content) ?: run {
            val local = heuristic(mcVersion, modCount, ramTotalGb, cpuCores)
            return local.copy(note = "AI 返回格式不对，使用本地推荐")
        }
        val ram = js["ramGb"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: heuristic(mcVersion, modCount, ramTotalGb, cpuCores).ramGb
        // AI 可能回 "G1" / "G1GC" / "UseG1GC"，统一成 G1GC 形式
        val rawGc = js["gc"]?.jsonPrimitive?.content ?: "G1"
        val gc = when {
            rawGc.contains("ZGC", true) -> "ZGC"
            rawGc.contains("Shenandoah", true) -> "ShenandoahGC"
            else -> "G1GC"
        }
        // AI 返回的 jvm 里可能已带 -Xmx，去掉避免与后面重复
        val rawJvm = js["jvm"]?.jsonPrimitive?.content ?: ""
        val jvm = rawJvm.replace(Regex("-Xmx\\d+[gGmM]"), "").trim()
        return Params(
            jvm = jvm,
            gc = gc,
            ramGb = ram.coerceIn(1, maxOf(1, (ramTotalGb * 0.6).toInt())),
            note = js["note"]?.jsonPrimitive?.content ?: "AI 优化"
        )
    }

    /**
     * 本地启发式（与桌面版一致的经验规则）。
     */
    fun heuristic(mcVersion: String, modCount: Int, ramTotalGb: Int, cpuCores: Int): Params {
        val maxRam = maxOf(1, (ramTotalGb * 0.6).toInt())
        val base = when {
            modCount >= 60 -> 6
            modCount >= 30 -> 4
            modCount >= 12 -> 3
            else -> 2
        }
        val ram = base.coerceAtMost(maxRam).coerceAtLeast(1)
        // 1.20.5+ 用 Java 21，可用 ZGC；内存够大时 ZGC 更平滑
        val gc = if (isModern(mcVersion) && ram >= 8) "ZGC" else "G1"
        val jvm = buildString {
            append("-XX:+Use${gc}GC")
            append(" -XX:+ParallelRefProcEnabled")
            if (gc == "ZGC") append(" -XX:+ZGenerational")
            append(" -Dsun.stdout.encoding=UTF-8")
            append(" -Dfile.encoding=UTF-8")
        }
        return Params(jvm = jvm, gc = gc + "GC", ramGb = ram, note = "本地推荐（$modCount 个模组 / $ramTotalGb GB 内存）")
    }

    private fun isModern(mcVersion: String): Boolean {
        val parts = mcVersion.split('.')
        if (parts.size < 2) return false
        val major = parts[0].toIntOrNull() ?: return false
        val minor = parts[1].toIntOrNull() ?: return false
        return major > 1 || (major == 1 && minor >= 20)
    }

    /** 把参数渲染成人类可读的一行。 */
    fun describe(p: Params): String {
        val sb = StringBuilder()
        if (p.jvm.isNotEmpty()) sb.append(p.jvm).append(' ')
        sb.append("-Xmx${p.ramGb}G")
        return sb.toString()
    }}
