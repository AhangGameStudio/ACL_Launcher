package com.acl.launcher.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * AI 客户端（对应桌面版 AclAi.vb）。
 * OpenAI 兼容端点，默认走 agnes。
 *
 * 关键：推理模型会先输出 reasoning_content 再给 content，
 * max_tokens 给小了会拿回空 content（桌面版踩过这个坑），
 * 所以这里给了较宽松的默认预算，并对空 content 做一次精简重试。
 */
object Ai {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class Result(
        val ok: Boolean = false,
        val content: String = "",
        val error: String = "",
        val elapsedMs: Long = 0
    )

    fun isConfigured(settings: Settings): Boolean =
        settings.aiEnabled &&
            settings.aiKey.isNotBlank() &&
            settings.aiUrl.isNotBlank()

    /**
     * 发一次对话请求。失败不抛异常，返回 ok=false。
     */
    suspend fun chat(
        settings: Settings,
        systemPrompt: String,
        userContent: String,
        maxTokens: Int = 3200,
        temperature: Double = 0.2
    ): Result {
        val start = System.currentTimeMillis()
        if (!settings.aiEnabled) return Result(error = "AI 助手未开启", elapsedMs = 0)
        if (settings.aiKey.isBlank()) return Result(error = "未填写 API 密钥")

        return try {
            val payload = buildJsonObject {
                put("model", settings.aiModel)
                put("temperature", temperature)
                put("max_tokens", maxTokens)
                put("messages", kotlinx.serialization.json.buildJsonArray {
                    if (systemPrompt.isNotBlank()) {
                        add(buildJsonObject {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                    }
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", userContent)
                    })
                })
            }
            val body = Http.postJson(
                settings.aiUrl.trim(),
                payload.toString(),
                headers = mapOf("Authorization" to "Bearer ${settings.aiKey.trim()}")
            )
            val content = extractContent(body).trim()
            if (content.isEmpty()) {
                Result(error = "响应中没有 content 字段", elapsedMs = System.currentTimeMillis() - start)
            } else {
                Result(ok = true, content = content, elapsedMs = System.currentTimeMillis() - start)
            }
        } catch (e: Http.HttpException) {
            Result(error = e.message ?: "HTTP ${e.code}", elapsedMs = System.currentTimeMillis() - start)
        } catch (e: Exception) {
            Result(error = e.message ?: e.javaClass.simpleName, elapsedMs = System.currentTimeMillis() - start)
        }
    }

    /** 连通性测试。 */
    suspend fun test(settings: Settings): Result =
        chat(settings, "你是连通性测试助手，请只回复 OK。", "ping", 400, 0.0)

    private fun extractContent(body: String): String {
        return try {
            val root = json.parseToJsonElement(body) as? JsonObject ?: return body
            val choices = root["choices"]?.jsonArray
            if (!choices.isNullOrEmpty()) {
                val msg = choices!![0].jsonObject["message"]?.jsonObject
                if (msg != null) {
                    val c = msg["content"] as? JsonPrimitive
                    if (c != null && c.content.isNotBlank()) return c.content
                }
                val text = choices!![0].jsonObject["text"] as? JsonPrimitive
                if (text != null) return text.content
            }
            if (root["error"] != null) return ""
            body
        } catch (e: Exception) {
            body
        }
    }

    /** 崩溃诊断的系统提示词（对应 AclAi.CrashPrompt）。 */
    fun crashPrompt(): String = """
你是 Minecraft 启动器内置的崩溃诊断助手。
用户会给你启动器日志或崩溃报告。
要求：
1. 用简体中文，不客套，不复述日志。
2. 严格按此格式输出，不要额外标题：
结论：<一句话根因>
置信度：<高/中/低>
证据：<1~2 行关键日志原文，用反引号包裹>
步骤：<可直接执行的步骤>
可疑对象：<可疑 Mod / 库 / 配置，没有写 无>
3. 日志不足时在结论里说明缺什么，不要编造。
4. 不要建议关闭杀毒软件或下载不明工具。
""".trimIndent()

    /** 截断字符串（对应 AclStr.Cut）。 */
    fun cut(s: String, n: Int): String =
        if (s.length <= n) s else s.take(n) + "…"
}
