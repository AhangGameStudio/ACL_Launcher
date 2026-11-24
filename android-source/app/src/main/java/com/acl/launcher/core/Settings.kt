package com.acl.launcher.core

import android.content.Context
import android.content.SharedPreferences

/**
 * ACL 设置存储（对应桌面版 PCL 的 Settings / Registry）。
 * 键名与桌面版保持一致，方便两边互导配置。
 */
class Settings private constructor(private val prefs: SharedPreferences) {

    // ── AI 相关（对应 AclAi.vb 的常量）──
    var aiEnabled: Boolean
        get() = prefs.getBoolean(KEY_AI_ENABLED, false)
        set(v) { prefs.edit().putBoolean(KEY_AI_ENABLED, v).apply() }

    var aiUrl: String
        get() = prefs.getString(KEY_AI_URL, DEFAULT_AI_URL) ?: DEFAULT_AI_URL
        set(v) { prefs.edit().putString(KEY_AI_URL, v).apply() }

    var aiKey: String
        get() = prefs.getString(KEY_AI_KEY, "") ?: ""
        set(v) { prefs.edit().putString(KEY_AI_KEY, v).apply() }

    var aiModel: String
        get() = prefs.getString(KEY_AI_MODEL, DEFAULT_AI_MODEL) ?: DEFAULT_AI_MODEL
        set(v) { prefs.edit().putString(KEY_AI_MODEL, v).apply() }

    // ── 启动优化（对应 AclLaunchOpt.vb）──
    var launchAiOptimize: Boolean
        get() = prefs.getBoolean(KEY_AI_OPTIMIZE, true)
        set(v) { prefs.edit().putBoolean(KEY_AI_OPTIMIZE, v).apply() }

    // ── 整合包（对应 AclPack.vb）──
    var packLoader: String
        get() = prefs.getString(KEY_PACK_LOADER, "fabric") ?: "fabric"
        set(v) { prefs.edit().putString(KEY_PACK_LOADER, v).apply() }

    var packMcVersion: String
        get() = prefs.getString(KEY_PACK_MC, "1.20.1") ?: "1.20.1"
        set(v) { prefs.edit().putString(KEY_PACK_MC, v).apply() }

    var packTargetCount: Int
        get() = prefs.getInt(KEY_PACK_COUNT, 20)
        set(v) { prefs.edit().putInt(KEY_PACK_COUNT, v.coerceIn(1, 60)).apply() }

    // ── 外观 ──
    var glassEnabled: Boolean
        get() = prefs.getBoolean(KEY_GLASS, true)
        set(v) { prefs.edit().putBoolean(KEY_GLASS, v).apply() }

    // ── 更新（对应 AclUpdate.vb）──
    var updateSource: String
        get() = prefs.getString(KEY_UPDATE_SRC, DEFAULT_UPDATE_SOURCE) ?: DEFAULT_UPDATE_SOURCE
        set(v) { prefs.edit().putString(KEY_UPDATE_SRC, v).apply() }

    var autoCheckUpdate: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UPDATE, true)
        set(v) { prefs.edit().putBoolean(KEY_AUTO_UPDATE, v).apply() }

    // ── 联机（对应 AclLink.vb）──
    var linkCode: String
        get() = prefs.getString(KEY_LINK_CODE, "") ?: ""
        set(v) { prefs.edit().putString(KEY_LINK_CODE, v).apply() }

    fun all(): Map<String, Any?> = mapOf(
        KEY_AI_ENABLED to aiEnabled,
        KEY_AI_URL to aiUrl,
        KEY_AI_MODEL to aiModel,
        KEY_AI_OPTIMIZE to launchAiOptimize,
        KEY_PACK_LOADER to packLoader,
        KEY_PACK_MC to packMcVersion,
        KEY_PACK_COUNT to packTargetCount,
        KEY_GLASS to glassEnabled,
        KEY_UPDATE_SRC to updateSource,
        KEY_AUTO_UPDATE to autoCheckUpdate,
    )

    companion object {
        const val KEY_AI_ENABLED = "AclAiEnabled"
        const val KEY_AI_URL = "AclAiUrl"
        const val KEY_AI_KEY = "AclAiKey"
        const val KEY_AI_MODEL = "AclAiModel"
        const val KEY_AI_OPTIMIZE = "LaunchAiOptimize"

        const val KEY_PACK_LOADER = "AclPackLoader"
        const val KEY_PACK_MC = "AclPackMcVersion"
        const val KEY_PACK_COUNT = "AclPackTargetCount"

        const val KEY_GLASS = "AclGlassEnabled"
        const val KEY_UPDATE_SRC = "AclUpdateSource"
        const val KEY_AUTO_UPDATE = "AclAutoUpdate"
        const val KEY_LINK_CODE = "AclLinkCode"

        const val DEFAULT_AI_URL = "https://apihub.agnes-ai.com/v1/chat/completions"
        const val DEFAULT_AI_MODEL = "agnes-2.5-flash"
        const val DEFAULT_UPDATE_SOURCE =
            "https://ahanggamestudio.github.io/ACL_Launcher/version.json"

        @Volatile
        private var instance: Settings? = null

        fun get(context: Context): Settings =
            instance ?: synchronized(this) {
                instance ?: Settings(
                    context.applicationContext.getSharedPreferences("ACL", Context.MODE_PRIVATE)
                ).also { instance = it }
            }
    }
}
