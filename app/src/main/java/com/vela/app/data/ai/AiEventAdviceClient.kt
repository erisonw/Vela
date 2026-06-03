package com.vela.app.data.ai

import com.vela.app.data.model.Event
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class AiEventAdviceRequest(
    val event: Event,
    val weatherHint: String,
    val timezone: String = "Asia/Shanghai",
    val locale: String = "zh-CN",
)

sealed interface AiEventAdviceResult {
    data class Success(val adviceText: String) : AiEventAdviceResult

    data class Failure(
        val code: String,
        val message: String,
        val retryable: Boolean,
    ) : AiEventAdviceResult
}

interface AiEventAdviceClient {
    fun generate(request: AiEventAdviceRequest): AiEventAdviceResult
}

class HttpAiEventAdviceClient(
    private val endpoint: String,
    private val apiKey: String,
    private val model: String,
) : AiEventAdviceClient {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    override fun generate(request: AiEventAdviceRequest): AiEventAdviceResult {
        val serviceUrl = endpoint.toChatCompletionsUrl()
        val cleanModel = model.trim()
        if (serviceUrl.isBlank() || cleanModel.isBlank()) {
            return AiEventAdviceResult.Failure(
                code = "SERVICE_NOT_CONFIGURED",
                message = "AI 建议服务未配置。",
                retryable = true,
            )
        }
        return runCatching {
            executeRequest(serviceUrl, cleanModel, request)
        }.getOrElse {
            AiEventAdviceResult.Failure(
                code = "NETWORK_ERROR",
                message = "AI 建议生成失败。",
                retryable = true,
            )
        }
    }

    private fun executeRequest(
        serviceUrl: String,
        cleanModel: String,
        request: AiEventAdviceRequest,
    ): AiEventAdviceResult {
        val connection = (URL(serviceUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
        }

        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(openAiAdvicePayload(cleanModel, request).toString())
        }

        val responseCode = connection.responseCode
        val responseText = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        connection.disconnect()

        if (responseCode !in 200..299) {
            return AiEventAdviceResult.Failure(
                code = "HTTP_$responseCode",
                message = "AI 建议服务返回 $responseCode。",
                retryable = true,
            )
        }

        val content = json.parseToJsonElement(responseText)
            .jsonObject["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?.get("content")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            .orEmpty()
        val advice = content.extractAdviceText()
        return if (advice.isBlank()) {
            AiEventAdviceResult.Failure(
                code = "EMPTY_ADVICE",
                message = "AI 没有返回可用建议。",
                retryable = true,
            )
        } else {
            AiEventAdviceResult.Success(advice)
        }
    }

    private fun openAiAdvicePayload(
        cleanModel: String,
        request: AiEventAdviceRequest,
    ): JsonObject =
        buildJsonObject {
            put("model", cleanModel)
            put("temperature", 0.4)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "system")
                            put("content", eventAdviceSystemPrompt())
                        },
                    )
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", request.toPromptText())
                        },
                    )
                },
            )
        }

    private fun AiEventAdviceRequest.toPromptText(): String =
        buildString {
            appendLine("请为下面这个日程生成一条提醒建议。")
            appendLine("标题：${event.title}")
            appendLine("开始：${event.startAt}")
            appendLine("结束：${event.endAt.orEmpty()}")
            appendLine("地点：${event.location?.name.orEmpty()}")
            appendLine("备注：${event.description.orEmpty()}")
            appendLine("天气：$weatherHint")
            appendLine("时区：$timezone")
            appendLine("语言：$locale")
        }

    private fun String.toChatCompletionsUrl(): String {
        val trimmed = trim().trimEnd('/')
        return when {
            trimmed.isBlank() -> ""
            trimmed.endsWith("/chat/completions") -> trimmed
            trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
            else -> "$trimmed/v1/chat/completions"
        }
    }
}

object UnavailableAiEventAdviceClient : AiEventAdviceClient {
    override fun generate(request: AiEventAdviceRequest): AiEventAdviceResult =
        AiEventAdviceResult.Failure(
            code = "SERVICE_UNAVAILABLE",
            message = "AI 建议服务未配置。",
            retryable = true,
        )
}

private fun eventAdviceSystemPrompt(): String =
    """
        你是 Vela 的日程提醒助手。根据日程标题、时间、地点、备注和天气生成一条中文行动建议。
        要求：
        - 只输出一句话，不要输出 JSON 或 Markdown。
        - 不要复述所有字段，要给出用户到点前真正有用的准备提醒。
        - 没有足够上下文时也要保持克制，不要编造具体物品或地点。
        - 24 个中文字以内。
    """.trimIndent()

private fun String.extractAdviceText(): String =
    trim()
        .removePrefix("\"")
        .removeSuffix("\"")
        .replace("\\n", " ")
        .replace(Regex("\\s+"), " ")
        .take(80)
