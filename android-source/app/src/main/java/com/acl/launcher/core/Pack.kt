package com.acl.launcher.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.File

/** 单个模组（对应桌面版 AclPackMod）。 */
data class PackMod(
    var projectId: String = "",
    var slug: String = "",
    var title: String = "",
    var description: String = "",
    var versionId: String = "",
    var versionName: String = "",
    var fileName: String = "",
    var url: String = "",
    var size: Long = 0,
    var downloads: Long = 0,
    val requiredBy: MutableList<String> = mutableListOf(),
    val missingDeps: MutableList<String> = mutableListOf(),
    val conflicts: MutableList<String> = mutableListOf(),
    var removed: Boolean = false,
    var removeReason: String = "",
    var installed: Boolean = false
)

/** 流程步骤状态（对应 AclPackStep）。 */
class PackStep(val title: String) {
    var state: Int = 0   // 0 待开始 / 1 进行中 / 2 完成 / 3 失败 / 4 跳过
    var detail: String = ""
}

/** 任务选项（对应 AclPackOptions）。 */
class PackOptions {
    var instanceName: String = ""
    var mcVersion: String = "1.20.1"
    var loader: String = "fabric"
    var loaderVersion: String = ""
    var demand: String = ""
    var keywords: String = ""
    var targetCount: Int = 20
    var testRounds: Int = 3
    var allowAi: Boolean = true
    var autoInstall: Boolean = true
    val parsedTerms = mutableListOf<String>()
}

/**
 * 整合包任务（对应 AclPackJob）。
 * 日志用 synchronized 保护，UI 通过 listener 接收更新。
 */
class PackJob(val options: PackOptions) {
    val steps = listOf(
        PackStep("基础环境（原版 + 加载器）"),
        PackStep("AI 分析需求 → 模组名 → 加载器适配 → 搜索"),
        PackStep("匹配版本与加载器"),
        PackStep("AI 结合版本 + 模组适配选配"),
        PackStep("AI 自主运行测试"),
        PackStep("产出整合包"),
    )
    val mods = mutableListOf<PackMod>()
    val lines = mutableListOf<String>()
    val testRounds = mutableListOf<String>()

    @Volatile
    var canceled: Boolean = false
    var succeeded: Boolean = false
    var reportPath: String = ""
    var instancePath: String = ""
    var baseName: String = ""

    var onChanged: (() -> Unit)? = null

    private val logLock = Any()

    fun setStep(index: Int, state: Int, detail: String = "") {
        if (index < 0 || index >= steps.size) return
        steps[index].state = state
        if (detail.isNotEmpty()) steps[index].detail = detail
        onChanged?.invoke()
    }

    fun log(text: String) {
        synchronized(logLock) {
            lines.add("[${Time.now()}] $text")
            if (lines.size > 800) lines.subList(0, 200).clear()
        }
        onChanged?.invoke()
    }

    fun checkCancel() {
        if (canceled) throw PackCanceledException()
    }

    fun snapshotLines(): List<String> = synchronized(logLock) { lines.toList() }
}

class PackCanceledException : Exception("已取消")

/**
 * 整合包引擎（对应桌面版 AclPack）。
 *
 * 与桌面版保持一致的提速策略：
 * 1. 多关键词并行搜索
 * 2. 候选版本并行匹配
 * 3. 依赖分层并行补齐
 * 4. 模组文件并发下载（默认 6 并发）
 * 5. AI 需求解析与基础环境准备并行
 */
object Pack {

    const val API_BASE = "https://api.modrinth.com/v2"

    /** 并发网络请求数（桌面版 PackParallelIo = 8）。 */
    const val PARALLEL_IO = 8
    /** 模组并发下载数（桌面版 PackParallelDownloads = 6）。 */
    const val PARALLEL_DOWNLOADS = 6

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ── HTTP ────────────────────────────────────────────────────────────

    suspend fun httpGet(url: String, timeoutMs: Int = 25_000): String =
        Http.getText(url, timeoutMs)

    // ── 版本清单 ────────────────────────────────────────────────────────

    /** 从官方 manifest 取全部 MC 版本（对应 ValidateMcVersion 的数据源）。 */
    suspend fun fetchMcVersions(): List<String> {
        return try {
            val txt = httpGet("https://launchermeta.mojang.com/mc/game/version_manifest.json")
            val root = json.parseToJsonElement(txt) as JsonObject
            root["versions"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
        } catch (e: Exception) {
            try {
                val txt = httpGet("https://bmclapi2.bangbang93.com/mc/game/version_manifest_v2.json")
                val root = json.parseToJsonElement(txt) as JsonObject
                root["versions"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
            } catch (e2: Exception) {
                FALLBACK_MC_VERSIONS
            }
        }
    }

    /**
     * 校验 MC 版本是否真实存在（对应 ValidateMcVersion）。
     * 返回空串表示合法，否则返回可读错误。
     */
    suspend fun validateMcVersion(mc: String): String {
        if (!Regex("^\\d+\\.\\d+(\\.\\d+)?$").matches(mc)) {
            // 快照版（如 24w14a）也要接受，只挡明显非法字符
            if (!Regex("^[0-9a-zA-Z.]+$").matches(mc)) {
                return "MC 版本格式不对，例：1.20.1"
            }
        }
        val versions = try {
            val txt = httpGet("https://launchermeta.mojang.com/mc/game/version_manifest.json")
            val root = json.parseToJsonElement(txt) as JsonObject
            root["versions"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
        } catch (e: Exception) {
            return ""   // 拿不到清单时不误判
        }
        if (versions.contains(mc)) return ""
        val near = versions.filter { it.startsWith(mc.substringBeforeLast('.')) }
            .take(6).joinToString("、")
        return if (near.isEmpty()) {
            "Minecraft 官方没有 $mc 这个版本。"
        } else {
            "Minecraft 官方没有 $mc 这个版本。可改用：$near"
        }
    }

    /** 加载器 facet（对应 LoaderFacet）。 */
    fun loaderFacet(loader: String): String = when (loader) {
        "fabric" -> "fabric"
        "forge" -> "forge"
        "neoforge" -> "neoforge"
        "quilt" -> "quilt"
        else -> ""
    }

    /** 取该 MC 版本可用的最新 Fabric Loader（对应 ResolveFabricLoader）。 */
    suspend fun resolveFabricLoader(mc: String): String = try {
        val arr = json.parseToJsonElement(
            httpGet("https://meta.fabricmc.net/v2/versions/loader/$mc")
        ).jsonArray
        val stable = arr.firstOrNull { it.jsonObject["loader"]?.jsonObject?.get("stable")?.jsonPrimitive?.boolean == true }
        stable?.jsonObject?.get("loader")?.jsonObject?.get("version")?.jsonPrimitive?.content
            ?: arr.firstOrNull()?.jsonObject?.get("loader")?.jsonObject?.get("version")?.jsonPrimitive?.content
            ?: ""
    } catch (e: Exception) {
        ""
    }

    /** 自动解析 Forge / NeoForge 版本（对应 ResolveLoaderVersion）。 */
    suspend fun resolveLoaderVersion(mc: String, loader: String): String = try {
        when (loader) {
            "forge" -> {
                val arr = json.parseToJsonElement(
                    httpGet("https://bmclapi2.bangbang93.com/forge/minecraft/$mc")
                ).jsonArray
                arr.firstOrNull()?.jsonObject?.get("version")?.jsonPrimitive?.content ?: ""
            }
            "neoforge" -> {
                val txt = httpGet("https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge")
                val root = json.parseToJsonElement(txt) as JsonObject
                val versions = root["versions"]!!.jsonArray.map { it.jsonPrimitive.content }
                // 取适配该 MC 版本的最大版本号
                versions.filter { it.startsWith("$mc-") }
                    .maxByOrNull { it.substringAfter("$mc-").substringBefore("-") }
                    ?.substringAfter("$mc-")?.substringBefore("-") ?: ""
            }
            "fabric" -> resolveFabricLoader(mc)
            else -> ""
        }
    } catch (e: Exception) {
        ""
    }

    // ── AI 需求解析 ────────────────────────────────────────────────────

    /**
     * 需求描述 → 模组名（对应 AiParseDemand）。
     * AI 失败时回落到本地同义词表。
     */
    suspend fun aiParseDemand(job: PackJob, settings: Settings): List<String> {
        val o = job.options
        val demand = o.demand.trim()
        if (demand.isEmpty()) return emptyList()

        val lf = loaderFacet(o.loader).ifEmpty { "不限" }
        val isSnap = !Regex("^\\d+\\.\\d+(\\.\\d+)?$").matches(o.mcVersion)
        val example = buildJsonObject {
            put("terms", kotlinx.serialization.json.buildJsonArray {
                add(JsonPrimitive("xaeros_minimap"))
                add(JsonPrimitive("journeymap"))
            })
            put("note", "一句话说明")
        }
        val sys = """
你是 Minecraft 模组顾问。
把玩家的需求描述转换成 Modrinth 上真实存在的模组英文名（slug 或常用名），用于搜索。
严格要求：
1. 输出的所有模组必须兼容给定的 MC 版本和加载器；不确定的不要输出。
2. 只输出模组名，不要中文解释、不要版本号、不要分类词。
3. terms 里给出 3~${Math.max(6, Math.min(12, o.targetCount))} 个具体模组名。
4. 若给定版本是快照版/远古版，优先输出跨版本通用或该版本确有发布的模组，不要凭空假设。
5. 只输出 JSON，不要任何额外文字，格式：${example}
""".trimIndent()
        val content = buildString {
            append("MC 版本：${o.mcVersion}${if (isSnap) "（快照/特殊版本，模组支持较少）" else "（正式版）"}\n")
            append("加载器：$lf\n")
            append("玩家需求：$demand")
        }

        if (Ai.isConfigured(settings) && o.allowAi) {
            var r = Ai.chat(settings, sys, content, 3200, 0.3)
            if (!r.ok && r.error.contains("content")) {
                // 精简输入再试一次（只给需求和版本，模板更短）
                r = Ai.chat(
                    settings,
                    sys,
                    "MC ${o.mcVersion} / $lf\n需求：$demand\n只输出 JSON：{\"terms\":[\"mod1\"],\"note\":\"\"}",
                    4000, 0.2
                )
                if (r.ok) job.log("AI 首次响应为空，已用精简输入重试成功")
            }
            if (r.ok) {
                val js = extractJson(r.content)
                val arr = js?.get("terms") as? JsonArray
                if (arr != null) {
                    val terms = arr.mapNotNull { it.jsonPrimitive.content.trim() }
                        .filter { it.isNotEmpty() }
                    if (terms.isNotEmpty()) {
                        job.log("AI 需求→模组名：" + terms.joinToString(", "))
                        js["note"]?.let {
                            job.log("AI 说明：" + Ai.cut(it.jsonPrimitive.content, 120))
                        }
                        return terms
                    }
                }
                job.log("AI 返回的 JSON 里没有 terms，改用本地解析")
            } else {
                job.log("AI 需求解析失败：" + r.error + "（改用本地解析）")
            }
        }
        val local = demandToMods(demand, o.loader)
        if (local.isNotEmpty()) job.log("本地解析得到模组名：" + local.joinToString(", "))
        return local
    }

    /**
     * 本地需求 → 模组名同义词表（对应 DemandToMods）。
     * AI 不可用或解析失败时的兜底，保证流程仍能跑。
     */
    fun demandToMods(demand: String, loader: String): List<String> {
        val d = demand.lowercase()
        val out = LinkedHashSet<String>()
        fun add(vararg terms: String) { terms.forEach { out.add(it) } }

        // 小地图
        if (listOfAny(d, "小地图", "地图", "minimap", "map")) add("xaeros_minimap", "journeymap")
        // 任务
        if (listOfAny(d, "任务", "quest", "任务册")) add("ftb-quests-for-fabric")
        // 光影
        if (listOfAny(d, "光影", "shader", "影")) add("shadersodium", "iris")
        // 科技
        if (listOfAny(d, "科技", "tech", "工业", "机器")) add("create", "mekanism")
        // 战争
        if (listOfAny(d, "战争", "战斗", "武器", "gun", "war")) add("epicfight", "tacz")
        // 冒险
        if (listOfAny(d, "冒险", "adventure", "探索", "地牢")) add("dungeon-mobs", "terralith")
        // 生存
        if (listOfAny(d, "生存", "survival", "生存向")) add("waystones", "traveler's-backpack")
        // 背包
        if (listOfAny(d, "背包", "inventory", "背包整理")) add("traveler's-backpack", "jei")
        // 矿物
        if (listOfAny(d, "矿物", "mining", "挖矿")) add("create", "exchangers")
        // 生物
        if (listOfAny(d, "生物", "mob", "动物")) add("more-mobs", "better-combat")
        // 家具
        if (listOfAny(d, "家具", "装饰", "furniture", "decor")) add("furniture", "decorated-comms")
        // 性能
        if (listOfAny(d, "性能", "fps", "优化", "卡顿")) add("lithium", "modmenu", "sodium")
        // 天气
        if (listOfAny(d, "天气", "weather")) add("weather", "skyblock-additions")
        // 家园
        if (listOfAny(d, "家园", "家园向", "建造", "build")) add("chunky", "tectonic")

        // 加载器白名单：明显只适配某加载器的模组要剔除
        val filtered = out.filter { termFitsLoader(it, loader) }
        return filtered.toList()
    }

    private fun listOfAny(text: String, vararg keys: String): Boolean =
        keys.any { text.contains(it) }

    /** 模组是否适配该加载器（对应 TermFitsLoader）。 */
    fun termFitsLoader(term: String, loader: String): Boolean {
        val t = term.lowercase()
        return when (loader) {
            "forge" -> !t.contains("fabric")
            "neoforge" -> !t.contains("fabric")
            "fabric" -> !t.contains("-forge") && !t.contains("neoforge")
            else -> true
        }
    }

    // ── 搜索与版本匹配 ─────────────────────────────────────────────────

    /**
     * 多关键词并行搜索并合并去重（对应 SearchProjects）。
     */
    suspend fun searchProjects(job: PackJob, limit: Int): List<JsonObject> {
        val o = job.options
        val terms = o.parsedTerms
        if (terms.isEmpty()) return searchOne(job, o.keywords.trim(), limit)

        val perTerm = Math.max(3, Math.min(10, Math.ceil(limit.toDouble() / terms.size).toInt() + 2))
        val results = arrayOfNulls<List<JsonObject>>(terms.size)
        val threads = (0 until terms.size).map { i ->
            Thread {
                try {
                    results[i] = searchOneBlocking(job, terms[i], perTerm)
                } catch (e: Exception) {
                    results[i] = emptyList()
                }
            }.apply { name = "ACL 搜索"; isDaemon = true }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        val res = mutableListOf<JsonObject>()
        val seen = HashSet<String>()
        for (part in results) {
            if (part == null) continue
            for (h in part) {
                val pid = h["project_id"]?.jsonPrimitive?.content ?: continue
                if (pid.isNotEmpty() && seen.add(pid)) res.add(h)
            }
            if (res.size >= limit) break
        }
        return res
    }

    /** 单关键词搜索（含 MC 版本 + 加载器 facet）。 */
    private suspend fun searchOne(job: PackJob, q: String, limit: Int): List<JsonObject> =
        withContext(Dispatchers.IO) { searchOneBlocking(job, q, limit) }

    private fun searchOneBlocking(job: PackJob, q: String, limit: Int): List<JsonObject> {
        val o = job.options
        var facet = """[["project_type:mod"],["versions:${o.mcVersion}"]]"""
        val lf = loaderFacet(o.loader)
        if (lf.isNotEmpty()) {
            facet = facet.dropLast(1) + """,["categories:$lf"]]"""
        }
        val url = "$API_BASE/search?query=${enc(q)}&facets=${enc(facet)}" +
                "&limit=${limit.coerceIn(1, 100)}&index=downloads"
        job.log("搜索：${if (q.isEmpty()) "(全部热门)" else q} · MC ${o.mcVersion} · ${if (lf.isEmpty()) "不限加载器" else lf}")
        val txt = Http.getTextBlocking(url)
        val root = json.parseToJsonElement(txt) as JsonObject
        val hits = root["hits"]?.jsonArray ?: return emptyList()
        return hits.map { it.jsonObject }
    }

    /**
     * 取项目在当前 MC/加载器下最新的可用文件（对应 ResolveVersion）。
     * 阻塞式版本，供后台线程直接调用。
     */
    fun resolveVersionBlocking(projectId: String, mc: String, loader: String): JsonObject? {
        var url = "$API_BASE/project/$projectId/version?game_versions=${enc("[\"$mc\"]")}"
        val lf = loaderFacet(loader)
        if (lf.isNotEmpty()) url += "&loaders=${enc("[\"$lf\"]")}"
        val arr = json.parseToJsonElement(Http.getTextBlocking(url)).jsonArray
        if (arr.isEmpty()) return null
        // 优先 release，其次最新
        arr.firstOrNull { it.jsonObject["version_type"]?.jsonPrimitive?.content == "release" }
            ?.let { return it.jsonObject }
        return arr.first().jsonObject
    }

    suspend fun resolveVersion(projectId: String, mc: String, loader: String): JsonObject? =
        withContext(Dispatchers.IO) { resolveVersionBlocking(projectId, mc, loader) }

    /** 阻塞式取项目信息。 */
    fun getProjectBlocking(projectId: String): JsonObject =
        json.parseToJsonElement(Http.getTextBlocking("$API_BASE/project/$projectId")) as JsonObject

    suspend fun getProject(projectId: String): JsonObject =
        withContext(Dispatchers.IO) { getProjectBlocking(projectId) }

    /** 从 version 对象填充模组信息（对应 FillFromVersion）。 */
    fun fillFromVersion(m: PackMod, v: JsonObject) {
        m.versionId = v["id"]?.jsonPrimitive?.content ?: ""
        m.versionName = v["version_number"]?.jsonPrimitive?.content ?: ""
        val files = v["files"]?.jsonArray ?: return
        if (files.isEmpty()) return
        var primary = files[0].jsonObject
        for (f in files) {
            val fo = f.jsonObject
            if (fo["primary"]?.jsonPrimitive?.boolean == true) {
                primary = fo
                break
            }
        }
        m.fileName = primary["filename"]?.jsonPrimitive?.content ?: ""
        m.url = primary["url"]?.jsonPrimitive?.content ?: ""
        m.size = primary["size"]?.jsonPrimitive?.long ?: 0
    }

    // ── 主流程 ──────────────────────────────────────────────────────────

    /**
     * 完整流程（对应桌面版 Run）。
     *
     * Android 端与桌面版的关键差异：
     * 手机不能直接跑 Java 版 Minecraft，所以"基础环境"与"自主测试"改为
     * 在本机准备 mods 目录并产出可导入的整合包；版本匹配、依赖补齐、
     * AI 适配选配、并发下载等逻辑完全一致。
     */
    suspend fun run(
        job: PackJob,
        settings: Settings,
        targetDir: File,
        onProgress: (PackJob) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val o = job.options
        try {
            // ── 1. 基础环境：准备 mods 目录 ──
            job.setStep(0, 1, "正在准备整合包目录")
            targetDir.mkdirs()
            val modsDir = File(targetDir, "mods").apply { mkdirs() }
            File(targetDir, "resourcepacks").mkdirs()
            File(targetDir, "config").mkdirs()
            job.instancePath = targetDir.absolutePath
            job.baseName = o.instanceName.ifEmpty { "ACL Pack ${o.mcVersion} ${o.loader}" }
            job.setStep(0, 2, "${o.mcVersion} · ${o.loader} · ${job.baseName}")

            // ── 2. AI 需求解析（需求→模组名→加载器适配）+ 搜索 ──
            job.checkCancel()
            job.setStep(1, 1, "AI 正在分析需求并转成模组名")
            val terms = aiParseDemand(job, settings)
            o.parsedTerms.clear()
            o.parsedTerms.addAll(terms)
            job.setStep(1, 1, if (terms.isNotEmpty()) "按 ${terms.size} 个模组名搜索" else "按热门搜索")

            val hits = searchProjects(job, Math.max(o.targetCount * 3, 30))
            if (hits.isEmpty()) throw Exception("没有搜到符合条件的模组（换个需求描述或降低 MC 版本要求试试）")
            job.setStep(1, 2, "候选 ${hits.size} 个")

            // ── 3. 并行匹配版本 ──
            job.checkCancel()
            job.setStep(2, 1, "正在匹配每个模组的文件")
            val probeCount = Math.min(hits.size, Math.max(o.targetCount * 2, o.targetCount + 20))
            val found = arrayOfNulls<PackMod>(probeCount)
            val threads = (0 until probeCount).map { i ->
                Thread {
                    try {
                        val h = hits[i]
                        val pid = h["project_id"]?.jsonPrimitive?.content ?: return@Thread
                        val v = resolveVersionBlocking(pid, o.mcVersion, o.loader) ?: return@Thread
                        val m = PackMod(
                            projectId = pid,
                            slug = h["slug"]?.jsonPrimitive?.content ?: "",
                            title = h["title"]?.jsonPrimitive?.content ?: pid,
                            description = h["description"]?.jsonPrimitive?.content ?: "",
                            downloads = h["downloads"]?.jsonPrimitive?.long ?: 0
                        )
                        m.requiredBy.add("用户选择")
                        fillFromVersion(m, v)
                        if (m.url.isEmpty()) return@Thread
                        val deps = v["dependencies"]?.jsonArray
                        deps?.forEach { d ->
                            val dobj = d.jsonObject
                            if (dobj["dependency_type"]?.jsonPrimitive?.content != "required") return@forEach
                            val depId = dobj["project_id"]?.jsonPrimitive?.content ?: return@forEach
                            if (depId.isNotEmpty() && !m.missingDeps.contains(depId)) m.missingDeps.add(depId)
                        }
                        found[i] = m
                    } catch (e: Exception) {
                        job.log("跳过 " + (hits[i]["title"]?.jsonPrimitive?.content ?: "") + "：" + (e.message ?: ""))
                    }
                }.apply { name = "ACL 版本匹配"; isDaemon = true }
            }
            threads.forEach { it.start() }
            threads.forEach { it.join() }

            val picked = mutableListOf<PackMod>()
            for (i in 0 until probeCount) {
                val m = found[i] ?: continue
                picked.add(m)
                job.log("匹配 ${m.title} → ${m.versionName}")
                if (picked.size >= o.targetCount) break
            }
            if (picked.isEmpty()) throw Exception("没有模组能在当前版本下取到文件")
            job.mods.addAll(picked)
            job.setStep(2, 2, "已选 ${picked.size} 个模组")

            // ── 4. 依赖补齐 + AI 适配选配 ──
            job.checkCancel()
            job.setStep(3, 1, "正在补齐依赖")
            resolveDependencies(job)
            job.checkCancel()
            job.setStep(3, 1, "正在检查本地适配情况")
            val issues = inspectCompatibility(job)
            job.setStep(3, 1, "AI 正在结合版本 + 模组做适配选配")
            if (o.allowAi && Ai.isConfigured(settings)) {
                aiReview(job, settings)
            } else {
                job.log("AI 未开启或未配置，跳过 AI 适配选配（本地规则检查已执行）")
            }
            job.setStep(3, 2, "本地问题 $issues 个，最终选配 ${job.mods.count { !it.removed }} 个")

            // ── 5. 并发下载 ──
            job.checkCancel()
            downloadAll(job, modsDir)

            // ── 6. 产出 ──
            job.setStep(4, 4, "手机端不执行实机测试（需在电脑端启动器验证）")
            job.log("已跳过实机测试：手机端无法直接运行 Java 版 Minecraft")
            job.testRounds.add("SKIPPED：Android 端无法直接运行 Java 版 Minecraft")

            writeArtifacts(job, targetDir)
            job.succeeded = true
            job.setStep(5, 2, "完成")
        } catch (ce: PackCanceledException) {
            job.log("已取消")
            for (i in job.steps.indices) {
                if (job.steps[i].state == 1) job.setStep(i, 4, "已取消")
            }
        } catch (ex: Exception) {
            job.log("失败：" + (ex.message ?: ex.javaClass.simpleName))
            for (i in job.steps.indices) {
                if (job.steps[i].state == 1) job.setStep(i, 3, ex.message ?: "失败")
            }
        }
        onProgress(job)
    }

    // ── 依赖补齐 ────────────────────────────────────────────────────────

    /** 分层并行补齐依赖（对应 ResolveDependencies）。 */
    private suspend fun resolveDependencies(job: PackJob) {
        val o = job.options
        val known = HashSet(job.mods.map { it.projectId })
        val queue = ArrayDeque<String>()
        for (m in job.mods) for (d in m.missingDeps) if (!known.contains(d)) queue.add(d)

        var guard = 0
        val maxTotal = o.targetCount + 12
        val batchSize = PARALLEL_IO * 2
        while (queue.isNotEmpty() && guard < 40 && job.mods.size < maxTotal) {
            job.checkCancel()
            val batch = mutableListOf<String>()
            while (queue.isNotEmpty() && batch.size < batchSize && job.mods.size + batch.size < maxTotal) {
                val p = queue.removeFirst()
                if (known.add(p)) batch.add(p)
            }
            if (batch.isEmpty()) break
            guard++

            val resolved = arrayOfNulls<Pair<PackMod, List<String>>>(batch.size)
            val threads = batch.indices.map { i ->
                Thread {
                    try {
                        val pid = batch[i]
                        val proj = getProjectBlocking(pid)
                        val v = resolveVersionBlocking(pid, o.mcVersion, o.loader)
                        if (v == null) {
                            job.log("依赖 $pid 没有适配当前版本的文件，已跳过")
                            return@Thread
                        }
                        val dm = PackMod(
                            projectId = pid,
                            slug = proj["slug"]?.jsonPrimitive?.content ?: "",
                            title = proj["title"]?.jsonPrimitive?.content ?: pid,
                            description = proj["description"]?.jsonPrimitive?.content ?: ""
                        )
                        dm.requiredBy.add("依赖补齐")
                        fillFromVersion(dm, v)
                        if (dm.url.isEmpty()) return@Thread
                        val nested = mutableListOf<String>()
                        v["dependencies"]?.jsonArray?.forEach { d ->
                            val dobj = d.jsonObject
                            if (dobj["dependency_type"]?.jsonPrimitive?.content != "required") return@forEach
                            val d2 = dobj["project_id"]?.jsonPrimitive?.content ?: return@forEach
                            if (d2.isNotEmpty()) nested.add(d2)
                        }
                        resolved[i] = dm to nested
                    } catch (ex: Exception) {
                        job.log("依赖 ${batch[i]} 解析失败：" + (ex.message ?: ""))
                    }
                }.apply { name = "ACL 依赖解析"; isDaemon = true }
            }
            threads.forEach { it.start() }
            threads.forEach { it.join() }

            for (i in batch.indices) {
                val r = resolved[i] ?: continue
                job.mods.add(r.first)
                job.log("补齐依赖：" + r.first.title)
                for (d2 in r.second) if (!known.contains(d2)) queue.add(d2)
            }
        }
    }

    /** 本地规则检查（对应 InspectCompatibility）。 */
    private fun inspectCompatibility(job: PackJob): Int {
        var issues = 0
        val byFile = job.mods.groupBy { it.fileName.lowercase() }.filter { it.key.isNotEmpty() && it.value.size > 1 }
        for (g in byFile) {
            issues++
            for (m in g.value) m.conflicts.add("文件名重复：${g.key}")
            job.log("冲突：多个模组共用文件名 ${g.key}")
        }
        val ids = HashSet(job.mods.map { it.projectId })
        for (m in job.mods) {
            val miss = m.missingDeps.filter { !ids.contains(it) }
            for (d in miss) {
                m.missingDeps.remove(d)
                m.conflicts.add("缺依赖：$d")
                issues++
                job.log("警告：${m.title} 缺少依赖 $d（该依赖没有适配版本）")
            }
        }
        return issues
    }

    // ── AI 适配选配 ─────────────────────────────────────────────────────

    /**
     * AI 结合版本 + 模组清单做最终适配选配（对应 AiReview）。
     * 与桌面版一致：token 预算给足，空 content 时用精简清单重试一次。
     */
    private suspend fun aiReview(job: PackJob, settings: Settings) {
        try {
            val o = job.options
            val sb = StringBuilder()
            sb.appendLine("目标环境：")
            sb.appendLine("- MC 版本：${o.mcVersion}${if (Regex("^\\d+\\.\\d+(\\.\\d+)?$").matches(o.mcVersion)) "（正式版）" else "（快照/特殊版本）"}")
            sb.appendLine("- 加载器：${o.loader}")
            sb.appendLine("- 玩家需求：${o.demand.ifEmpty { "（未指定，按通用实用向）" }}")
            sb.appendLine("候选模组（共 ${job.mods.size} 个）：")
            for (m in job.mods) {
                val dep = if (m.missingDeps.isNotEmpty()) "｜依赖:" + m.missingDeps.joinToString(",") else ""
                val cf = if (m.conflicts.isNotEmpty()) "｜问题:" + m.conflicts.joinToString(",") else ""
                sb.appendLine("- id=${m.projectId}｜${m.title} (${m.versionName})｜${Ai.cut(m.description, 90)}$dep$cf")
            }
            val example = buildJsonObject {
                put("keep", kotlinx.serialization.json.buildJsonArray {
                    add(JsonPrimitive("projectId1"))
                    add(JsonPrimitive("projectId2"))
                })
                put("remove", kotlinx.serialization.json.buildJsonArray {
                    add(buildJsonObject {
                        put("id", "projectId")
                        put("reason", "版本不适配/需求无关/功能重复等原因")
                    })
                })
                put("note", "一句话说明这次选配如何贴合版本与需求")
            }
            val sys = """
你是 Minecraft 整合包适配选配顾问。
根据给定的「MC 版本 + 加载器」和「玩家需求」，对候选模组做最终选配。
判断依据（按优先级）：
1. 版本/加载器适配：该模组在此 MC 版本和加载器下是否真的能用；明显不适配的移除。
2. 需求匹配：与玩家需求无关、或功能与已保留模组重复的，移除。
3. 已知冲突：互斥组合、缺关键依赖的，移除。
保守处理：拿不准能否用的模组保留，只在理由充分时移除。
keep 列出最终保留的 projectId；remove 列出被移除的 projectId 及原因。
只输出 JSON，不要任何解释文字，格式示例：${example}
""".trimIndent()

            var r = Ai.chat(settings, sys, sb.toString(), 3200, 0.15)
            if (!r.ok && r.error.contains("content")) {
                // 精简清单重试：只给 id+标题+版本，省输入 token
                val lean = StringBuilder()
                lean.appendLine("MC ${o.mcVersion} / ${o.loader} / 需求：${o.demand.ifEmpty { "通用" }}")
                lean.appendLine("候选 ${job.mods.size} 个（id｜标题｜版本）：")
                for (m in job.mods) lean.appendLine("- ${m.projectId}｜${m.title}｜${m.versionName}")
                lean.appendLine("只输出 JSON：{\"keep\":[\"id\"],\"remove\":[{\"id\":\"id\",\"reason\":\"原因\"}],\"note\":\"一句话\"}")
                r = Ai.chat(settings, sys, lean.toString(), 4000, 0.1)
                if (r.ok) job.log("AI 首次响应为空，已用精简清单重试成功")
            }
            if (!r.ok) {
                job.log("AI 适配选配失败：" + r.error + "（保留全部候选）")
                return
            }
            job.log("AI 适配选配：" + Ai.cut(r.content.replace("\r\n", " "), 170))

            val js = extractJson(r.content) ?: return

            // keep 白名单剔除
            val keepArr = js["keep"] as? JsonArray
            if (keepArr != null && keepArr.isNotEmpty()) {
                val keepIds = keepArr.mapNotNull { it.jsonPrimitive.content.trim() }.toSet()
                if (keepIds.size >= Math.max(1, (job.mods.size * 0.4).toInt())) {
                    for (m in job.mods) {
                        if (!m.removed && !keepIds.contains(m.projectId)) {
                            m.removed = true
                            m.removeReason = "AI 适配选配：未选中"
                            job.log("AI 未选中 ${m.title}（版本/需求匹配度低）")
                        }
                    }
                }
            }

            // remove 明确剔除
            val rm = js["remove"] as? JsonArray
            rm?.forEach { item ->
                val key: String
                val reason: String
                if (item is JsonObject) {
                    key = item["id"]?.jsonPrimitive?.content?.trim() ?: ""
                    reason = item["reason"]?.jsonPrimitive?.content ?: ""
                } else {
                    key = item.jsonPrimitive.content.trim()
                    reason = "AI 判定不适配"
                }
                if (key.isEmpty()) return@forEach
                val target = job.mods.firstOrNull {
                    !it.removed && (it.projectId.equals(key, true) ||
                            it.title.equals(key, true) || it.slug.equals(key, true))
                }
                if (target != null) {
                    target.removed = true
                    target.removeReason = reason
                    job.log("AI 剔除 ${target.title}：$reason")
                }
            }

            js["note"]?.let { job.log("AI 选配结论：" + Ai.cut(it.jsonPrimitive.content, 140)) }

            // 保险：AI 若全删，回滚一半
            if (job.mods.isNotEmpty() && job.mods.all { it.removed }) {
                var revived = 0
                for (m in job.mods) {
                    m.removed = false
                    m.removeReason = ""
                    revived++
                    if (revived >= Math.max(1, job.mods.size / 2)) break
                }
                job.log("AI 选配过于激进，已恢复前 $revived 个模组")
            }
        } catch (ex: Exception) {
            job.log("AI 适配选配异常：" + (ex.message ?: "") + "（保留全部候选）")
        }
    }

    // ── 下载 ────────────────────────────────────────────────────────────

    /**
     * 并发下载模组（对应 DownloadAll）。
     * 与桌面版一致：6 并发、每个文件分片下载、已存在则跳过。
     */
    private suspend fun downloadAll(job: PackJob, modsDir: File) {
        val list = job.mods.filter { !it.removed && it.url.isNotEmpty() }
        job.log("开始下载 ${list.size} 个模组到 mods（并发 $PARALLEL_DOWNLOADS）")

        val doneCount = java.util.concurrent.atomic.AtomicInteger(0)
        val index = java.util.concurrent.atomic.AtomicInteger(-1)
        val pieces = if (PARALLEL_DOWNLOADS >= 6) 4 else 6

        val threads = list.map { m ->
            Thread {
                try {
                    job.checkCancel()
                    val target = File(modsDir, m.fileName)
                    if (target.exists() && (m.size <= 0 || target.length() == m.size)) {
                        m.installed = true
                        job.log("[${doneCount.incrementAndGet()}/${list.size}] 已存在，跳过 ${m.fileName}")
                        return@Thread
                    }
                    var state = ""
                    val ok = Http.downloadBlocking(m.url, target, pieces) { _, _, s ->
                        state = s
                        job.setStep(3, 1, "下载 ${doneCount.get()}/${list.size}：${m.title}")
                    }
                    if (ok && target.exists() && target.length() > 0) {
                        m.installed = true
                        job.log("[${doneCount.incrementAndGet()}/${list.size}] ${m.fileName} (${formatSize(target.length())})")
                    } else {
                        m.conflicts.add("下载失败")
                        job.log("[${doneCount.incrementAndGet()}/${list.size}] 下载失败：${m.fileName}（$state）")
                    }
                } catch (ce: PackCanceledException) {
                    throw ce
                } catch (ex: Exception) {
                    m.conflicts.add("下载失败")
                    job.log("[${doneCount.incrementAndGet()}/${list.size}] 下载异常：${m.fileName}（${ex.message ?: ""}）")
                }
            }.apply { name = "ACL 下载"; isDaemon = true }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        job.setStep(3, 1, "下载完成 ${doneCount.get()}/${list.size}")
    }

    // ── 产出 ────────────────────────────────────────────────────────────

    /** 写报告与清单（对应 WriteArtifacts）。 */
    private fun writeArtifacts(job: PackJob, targetDir: File) {
        try {
            val report = File(targetDir, "整合包报告.txt")
            report.writeText(buildString {
                appendLine("ACL 启动器 · AI 一键整合包报告")
                appendLine("生成时间：${Time.nowFull()}")
                appendLine("MC 版本：${job.options.mcVersion}")
                appendLine("加载器：${job.options.loader}")
                appendLine("玩家需求：${job.options.demand.ifEmpty { "（未指定）" }}")
                appendLine("最终模组数：${job.mods.count { !it.removed }}")
                appendLine()
                appendLine("── 模组清单 ──")
                for (m in job.mods) {
                    val flag = if (m.removed) "[已移除] " else ""
                    appendLine("$flag${m.title} (${m.versionName})  ${m.fileName}")
                    if (m.removeReason.isNotEmpty()) appendLine("    移除原因：${m.removeReason}")
                }
                appendLine()
                appendLine("── 流程日志 ──")
                job.snapshotLines().forEach { appendLine(it) }
            }, Charsets.UTF_8)
            job.reportPath = report.absolutePath

            val manifest = File(targetDir, "acl-manifest.json")
            manifest.writeText(buildJsonObject {
                put("generator", "ACL 启动器 Android 版")
                put("version", "0.8.0")
                put("mcVersion", job.options.mcVersion)
                put("loader", job.options.loader)
                put("demand", job.options.demand)
                put("mods", kotlinx.serialization.json.buildJsonArray {
                    job.mods.filter { !it.removed }.forEach { m ->
                        add(buildJsonObject {
                            put("projectId", m.projectId)
                            put("slug", m.slug)
                            put("title", m.title)
                            put("version", m.versionName)
                            put("fileName", m.fileName)
                            put("url", m.url)
                            put("size", m.size)
                        })
                    }
                })
            }.toString(), Charsets.UTF_8)
        } catch (ex: Exception) {
            job.log("写报告失败：" + (ex.message ?: ""))
        }
    }

    // ── 工具 ────────────────────────────────────────────────────────────

    /** 从 AI 返回文本里抠出 JSON（对应 ExtractJson）。 */
    fun extractJson(s: String): JsonObject? {
        return try {
            val i = s.indexOf('{')
            val j = s.lastIndexOf('}')
            if (i < 0 || j <= i) null
            else json.parseToJsonElement(s.substring(i, j - i + 1)) as? JsonObject
        } catch (e: Exception) {
            null
        }
    }

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

    fun formatSize(bytes: Long): String = when {
        bytes <= 0 -> "0 B"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${Math.round(bytes / 1024.0 * 10) / 10.0} KB"
        bytes < 1024L * 1024 * 1024 -> "${Math.round(bytes / 1024.0 / 1024 * 100) / 100.0} MB"
        else -> "${Math.round(bytes / 1024.0 / 1024 / 1024 * 100) / 100.0} GB"
    }

    /** 离线兜底版本清单。 */
    val FALLBACK_MC_VERSIONS = listOf(
        "1.21.4", "1.21.3", "1.21.1", "1.21", "1.20.6", "1.20.4", "1.20.2",
        "1.20.1", "1.20", "1.19.4", "1.19.3", "1.19.2", "1.18.2", "1.17.1",
        "1.16.5", "1.15.2", "1.14.4", "1.13.2", "1.12.2", "1.11.2", "1.10.2",
        "1.9.4", "1.8.9", "1.7.10", "1.6.4", "1.5.2", "1.4.7", "1.3.2",
        "1.2.5", "1.1", "b1.7.3", "a1.2.6", "0.30c", "1.RV-Pre1", "1.16.5- forge"
    )
}
