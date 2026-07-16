package com.vela.app.data.ai

import com.vela.app.data.model.UserPreferences
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class AiServiceConnectionReport(
    val isReachable: Boolean,
    val message: String,
    val textStatus: String,
    val visionStatus: String,
    val voiceStatus: String,
)

object AiServiceConnectionTester {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    suspend fun test(preferences: UserPreferences): AiServiceConnectionReport {
        val endpoint = preferences.aiEndpoint.trim()
        val capabilityStatus = preferences.capabilityStatus()
        if (endpoint.isBlank()) {
            return capabilityStatus.copy(
                isReachable = false,
                message = "Base URL 未配置，当前不会发送 AI 请求。",
            )
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                probe(
                    endpoint = endpoint,
                    apiKey = preferences.aiApiKey,
                )
            }.fold(
                onSuccess = { probe ->
                    capabilityStatus.copy(
                        isReachable = probe.isReachable,
                        message = probe.message,
                    )
                },
                onFailure = { error ->
                    val failure = describeAiNetworkFailure(error, operation = "AI 服务")
                    capabilityStatus.copy(
                        isReachable = false,
                        message = failure.message,
                    )
                },
            )
        }
    }

    internal fun probeUrl(endpoint: String): String {
        val url = URL(endpoint.trim().trimEnd('/'))
        val isLocalProxy = url.host in setOf("127.0.0.1", "localhost") &&
            (url.port == 8787 || url.defaultPort == 8787)
        return if (isLocalProxy) {
            URL(url.protocol, url.host, url.port, "/health").toString()
        } else {
            url.toString()
        }
    }

    private fun probe(
        endpoint: String,
        apiKey: String,
    ): ProbeResult {
        val url = probeUrl(endpoint)
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = false
            if (apiKey.isNotBlank() && !url.endsWith("/health")) {
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
        }
        return try {
            val code = connection.responseCode
            val responseBody = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            if (url.endsWith("/health") && code in 200..299) {
                val health = runCatching {
                    json.decodeFromString<LocalProxyHealth>(responseBody)
                }.getOrNull()
                when {
                    health == null -> ProbeResult(
                        isReachable = true,
                        message = "本机代理可达；健康响应格式未识别，请通过实际导入验证模型。",
                    )

                    !health.configured -> ProbeResult(
                        isReachable = false,
                        message = "本机代理已启动，但缺少上游配置：${health.missing.joinToString()}。",
                    )

                    else -> ProbeResult(
                        isReachable = true,
                        message = "本机代理连接正常，上游 AI 已配置。",
                    )
                }
            } else {
                when (code) {
                    in 200..399 -> ProbeResult(
                        isReachable = true,
                        message = "AI 服务地址可达；模型和鉴权将在实际导入时验证。",
                    )

                    401, 403 -> ProbeResult(
                        isReachable = false,
                        message = "AI 服务可达，但当前鉴权未通过，请检查 API Key。",
                    )

                    404 -> ProbeResult(
                        isReachable = true,
                        message = "AI 服务主机可达；当前路径返回 404，请通过实际导入验证接口路径。",
                    )

                    429 -> ProbeResult(
                        isReachable = false,
                        message = "AI 服务可达，但当前被限流或额度不足。",
                    )

                    else -> ProbeResult(
                        isReachable = false,
                        message = "AI 服务返回 $code，当前不可用。",
                    )
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun UserPreferences.capabilityStatus(): AiServiceConnectionReport =
        AiServiceConnectionReport(
            isReachable = false,
            message = "尚未检查连接。",
            textStatus = modelStatus(aiTextModel, "文本模型"),
            visionStatus = modelStatus(aiVisionModel, "图片模型"),
            voiceStatus = modelStatus(aiVoiceModel, "语音模型"),
        )

    private fun modelStatus(model: String, label: String): String =
        if (model.isBlank()) {
            "$label：未配置"
        } else {
            "$label：已配置（$model）"
        }

    private data class ProbeResult(
        val isReachable: Boolean,
        val message: String,
    )

    @Serializable
    private data class LocalProxyHealth(
        val configured: Boolean = false,
        val missing: List<String> = emptyList(),
    )
}
