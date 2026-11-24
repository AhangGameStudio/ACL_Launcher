package com.acl.launcher.core

import kotlinx.coroutines.runBlocking

/**
 * 自检（对应桌面版 --acl-selftest / --acl-packnet 探针）。
 * 在无 Android 依赖的纯 JVM 上跑，验证移植过来的核心逻辑。
 */
object SelfTest {

    fun run(): String = runBlocking {
        val sb = StringBuilder()
        sb.appendLine("=== ACL Android 自检 ===")

        // 1. 版本校验
        try {
            val versions = Pack.fetchMcVersions()
            sb.appendLine("MC_VERSIONS=${versions.size}")
            sb.appendLine("HAS_1_20_1=${versions.contains("1.20.1")}")
            sb.appendLine("HAS_1_21_1=${versions.contains("1.21.1")}")
            val bad = Pack.validateMcVersion("1.20.8")
            sb.appendLine("VALIDATE_1_20_8=${if (bad.isEmpty()) "OK" else bad}")
            val good = Pack.validateMcVersion("1.20.6")
            sb.appendLine("VALIDATE_1_20_6=${if (good.isEmpty()) "OK" else good}")
        } catch (e: Exception) {
            sb.appendLine("VERSION_ERR=${e.message}")
        }

        // 2. 加载器解析
        try {
            sb.appendLine("FABRIC_1_21_1=${Pack.resolveFabricLoader("1.21.1")}")
        } catch (e: Exception) {
            sb.appendLine("FABRIC_ERR=${e.message}")
        }

        // 3. 搜索 + 版本匹配（计时，验证并行效果）
        try {
            val o = PackOptions()
            o.mcVersion = "1.21.1"
            o.loader = "fabric"
            o.targetCount = 8
            val job = PackJob(o)
            val sw = System.currentTimeMillis()
            val hits = Pack.searchProjects(job, 40)
            val searchMs = System.currentTimeMillis() - sw
            sb.appendLine("SEARCH_MS=$searchMs HITS=${hits.size}")

            val sw2 = System.currentTimeMillis()
            // 并行版本匹配（App 实际走的路径），验证并发收益
            val probe = hits.take(16).map { it["project_id"]!!.jsonPrimitiveContent() }
            val found = arrayOfNulls<kotlinx.serialization.json.JsonObject>(probe.size)
            val ths = probe.indices.map { i ->
                Thread {
                    found[i] = Pack.resolveVersionBlocking(probe[i], "1.21.1", "fabric")
                }.apply { isDaemon = true }
            }
            ths.forEach { it.start() }
            ths.forEach { it.join() }
            val ok = found.count { it != null }
            sb.appendLine("RESOLVE_PARALLEL_MS=${System.currentTimeMillis() - sw2} OK=$ok/${probe.size}")
        } catch (e: Exception) {
            sb.appendLine("SEARCH_ERR=${e.message}")
        }

        // 4. 本地需求解析
        try {
            val local = Pack.demandToMods("生存+战争+科技+冒险", "fabric")
            sb.appendLine("LOCAL_TERMS=${local.joinToString(",")}")
        } catch (e: Exception) {
            sb.appendLine("LOCAL_ERR=${e.message}")
        }

        // 5. 启动参数启发式
        try {
            val p = LaunchOpt.heuristic("1.21.1", 40, 16, 8)
            sb.appendLine("LAUNCH_OPT=${LaunchOpt.describe(p)}")
        } catch (e: Exception) {
            sb.appendLine("OPT_ERR=${e.message}")
        }

        // 6. 诊断规则
        try {
            val d = Diagnose.analyze("java.lang.OutOfMemoryError: Java heap space")
            sb.appendLine("DIAGNOSE=${d.size} first=${d.firstOrNull()?.id}")
        } catch (e: Exception) {
            sb.appendLine("DIAG_ERR=${e.message}")
        }

        // 7. 版本比较
        sb.appendLine("VER_CMP=${Update.compareVer("0.8.1", "0.8.0")}")

        sb.toString()
    }
}

/** JsonPrimitive 的内容取值小工具（避免在自检里引入更多 import）。 */
private fun kotlinx.serialization.json.JsonElement.jsonPrimitiveContent(): String =
    (this as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
