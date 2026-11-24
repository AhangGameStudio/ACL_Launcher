package com.acl.launcher.core

/** 一条诊断结论（对应 AclDiagnosis）。 */
data class Diagnosis(
    val id: String,
    val reason: String,
    val advice: String,
    val severity: String = "mid"
)

/**
 * 本地规则诊断（对应桌面版 AclDiagnose）。
 * 思路一致：确定性优先，命中即给人话；规则没命中时才交给 AI。
 */
object Diagnose {

    private class Rule(
        val id: String,
        val pattern: Regex,
        val reason: String,
        val advice: String,
        val severity: String
    )

    private val rules = listOf(
        Rule("METASPACE", Regex("OutOfMemoryError:\\s*Metaspace"), "元空间不足",
            "大量 Mod 会挤爆类元数据。可减少 Mod 数量，或在 Java 参数里加 -XX:MaxMetaspaceSize=512m，并确认用的是 64 位 Java。", "high"),
        Rule("OOM", Regex("java\\.lang\\.OutOfMemoryError"), "游戏内存不足",
            "在「版本设置 → 高级 → 内存」里调大分配（建议 4G~8G，不超过物理内存 60%），并关闭后台程序。给足仍崩多为某个 Mod 泄漏。", "high"),
        Rule("CLASSVERSION", Regex("UnsupportedClassVersionError|compiled by a more recent version"), "Java 版本过低",
            "该 Mod 要求更高版本 Java。1.16.5 及以下用 Java 8，1.17+ 用 17，1.20.5+ 用 21。到「版本设置 → Java」切换。", "high"),
        Rule("JVMBOOT", Regex("Could not create the Java Virtual Machine|Invalid maximum heap size"), "JVM 参数非法",
            "检查 Java 参数里的 -Xmx 是否拼错或超范围，把内存分配改回默认再试。", "high"),
        Rule("MAINCLASS", Regex("Could not find or load main class"), "启动参数或版本文件损坏",
            "多为版本 JSON 损坏。在版本设置里执行「补全文件」，或重新安装该版本。", "high"),
        Rule("MISSINGDEP", Regex("Missing or unsupported mandatory dependencies|missing dependency"), "缺少前置 Mod",
            "按日志点名的 Mod 补前置（Fabric API / Architectury / Cloth Config 等），并确认前置版本与游戏版本一致。", "high"),
        Rule("NOCLASS", Regex("NoClassDefFoundError|ClassNotFoundException"), "缺少类：前置缺失或 Mod 未加载",
            "确认前置装全，且 Mod 放在 mods 目录而不是核心库目录。", "mid"),
        Rule("NOSUCHMETHOD", Regex("NoSuchMethodError|NoSuchFieldError|AbstractMethodError"), "Mod 版本不匹配",
            "某个 Mod 调用了不存在的接口，通常是同一 Mod 装了两个版本或前置版本不对。禁用最近新加的 Mod 试试。", "mid"),
        Rule("MIXIN", Regex("MixinApplyError|Mixin apply failed|MixinTransformerError"), "Mixin 注入失败（Mod 冲突）",
            "典型多 Mod 冲突。按日志里的 mixin 目标类定位 Mod，二分禁用；也可能是 Mod 与该 MC 版本不兼容。", "high"),
        Rule("PIXELFORMAT", Regex("Pixel format not accelerated|Failed to create GL context"), "显卡/OpenGL 不支持",
            "更新显卡驱动；双显卡笔记本请强制独显运行 Java。", "high"),
        Rule("ACCESSVIOLATION", Regex("EXCEPTION_ACCESS_VIOLATION"), "显卡驱动或本地库崩溃",
            "崩溃发生在 JVM 之外，多为显卡驱动。更新驱动、关闭光影、移除 OpenGL 相关 Mod 后重试。", "high"),
        Rule("DUPLICATE", Regex("duplicate mods|duplicate mod"), "Mod 重复安装",
            "同一 Mod 装了两份（常见 .jar 与 .jar.disabled 并存，或版本文件夹嵌套）。删掉多余副本。", "mid"),
        Rule("TICKING", Regex("Exception ticking world|Ticking entity|Ticking block entity"), "特定方块/实体导致崩溃",
            "看崩溃日志里点名的坐标，用编辑器移除该方块，或禁用相关 Mod。", "mid"),
        Rule("MODZIP", Regex("zip END header not found|not a valid mod file"), "Mod 文件损坏或被解压",
            "Mod 必须是 .jar 原文件，不能解压成文件夹。删掉被解压的目录，重新下载。", "mid"),
        Rule("NATIVELIB", Regex("UnsatisfiedLinkError|Can't load library"), "本地库/位数不匹配",
            "32 位与 64 位混用，或 lwjgl 本地库解压失败。删除版本目录下的 natives 后重新启动。", "mid"),
        Rule("NETWORK", Regex("Failed to download|Connection timed out|SSLHandshakeException|UnknownHostException"), "网络请求失败",
            "检查网络或代理；下载源不可用时在设置里换源。SSL 报错多为系统时间不对或代理证书拦截。", "low"),
        Rule("JAVA32", Regex("Could not reserve enough space for object heap"), "32 位 Java 内存不够",
            "改用 64 位 Java（32 位进程最多约 1.5G 堆）。", "high"),
        Rule("CRASHED", Regex("The game crashed whilst|A fatal error has been detected by the Java Runtime Environment"), "游戏崩溃（通用）",
            "这是崩溃报告的通用标记，具体原因见下方条目；没有条目时用 AI 深度分析。", "low")
    )

    /** 分析日志，返回命中的诊断结论。 */
    fun analyze(text: String): List<Diagnosis> {
        val out = mutableListOf<Diagnosis>()
        for (r in rules) {
            if (r.pattern.containsMatchIn(text)) {
                out.add(Diagnosis(r.id, r.reason, r.advice, r.severity))
            }
        }
        return out
    }

    /** 构造给 AI 的上下文（对应 BuildContext）。 */
    fun buildContext(logText: String): String {
        val tail = if (logText.length > 4000) logText.takeLast(4000) else logText
        return "崩溃日志尾部：\n$tail"
    }
}
