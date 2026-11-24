package com.acl.launcher.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 版本检查（对应桌面版 AclUpdate.vb）。
 * 更新源是站点上的 version.json，格式：{"version":"0.8.0","url":"...","notes":"..."}。
 */
object Update {

    const val CURRENT_VERSION = "0.8.0"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class Info(
        val version: String = "",
        val url: String = "",
        val notes: String = "",
        val ok: Boolean = false,
        val error: String = ""
    )

    suspend fun check(settings: Settings): Info {
        return try {
            val txt = Http.getText(settings.updateSource, 20_000)
            val root = json.parseToJsonElement(txt) as? JsonObject
                ?: return Info(error = "更新源格式不对")
            Info(
                version = root["version"]?.jsonPrimitive?.content ?: "",
                url = root["url"]?.jsonPrimitive?.content ?: "",
                notes = root["notes"]?.jsonPrimitive?.content ?: "",
                ok = true
            )
        } catch (e: Exception) {
            Info(error = e.message ?: "检查失败")
        }
    }

    /** 比较版本号（对应 CompareVer）。 */
    fun compareVer(a: String, b: String): Int {
        val pa = a.split('.', '-').mapNotNull { it.toIntOrNull() }
        val pb = b.split('.', '-').mapNotNull { it.toIntOrNull() }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    /** 是否有新版本。 */
    fun hasUpdate(info: Info): Boolean =
        info.ok && info.version.isNotEmpty() && compareVer(info.version, CURRENT_VERSION) > 0
}
