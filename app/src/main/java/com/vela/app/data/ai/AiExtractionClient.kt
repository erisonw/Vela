package com.vela.app.data.ai

import com.vela.app.data.model.EventCandidate
import com.vela.app.data.model.EventCandidateReviewStatus
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

const val EventCandidatesExtractPath = "/v1/event-candidates:extract"

@Serializable
data class AiExtractionRequest(
    val sessionId: String,
    val type: AiInputType,
    val text: String? = null,
    val attachments: List<AiInputAttachment> = emptyList(),
    val attachmentIds: List<String> = emptyList(),
    val timezone: String = "Asia/Shanghai",
    val locale: String = "zh-CN",
)

@Serializable
data class AiInputAttachment(
    val fileName: String,
    val mimeType: String,
    val base64Data: String? = null,
    val textContent: String? = null,
)

enum class AiInputType {
    Text,
    Image,
    File,
    Voice,
}

sealed interface AiExtractionResult {
    data class Success(
        val summary: String,
        val candidates: List<EventCandidate>,
    ) : AiExtractionResult

    data class Failure(
        val code: String,
        val message: String,
        val retryable: Boolean,
    ) : AiExtractionResult
}

interface AiExtractionClient {
    fun extract(request: AiExtractionRequest): AiExtractionResult
}

class HttpAiExtractionClient(
    private val endpoint: String,
    private val apiKey: String,
    private val textModel: String,
    private val visionModel: String,
    private val documentModel: String,
    private val voiceModel: String,
) : AiExtractionClient {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    override fun extract(request: AiExtractionRequest): AiExtractionResult {
        val serviceUrl = endpoint.toServiceUrl()
        if (serviceUrl.isBlank()) {
            return missingConfigFailure()
        }
        if (request.type == AiInputType.Voice) {
            return unsupportedVoiceFailure()
        }
        val model = modelFor(request)
        if (model.isBlank()) {
            return missingModelFailure(request.type)
        }
        val attachmentFailure = request.validateAttachments()
        if (attachmentFailure != null) {
            return attachmentFailure
        }

        var result: AiExtractionResult? = null
        var error: Throwable? = null
        val worker = thread(name = "vela-ai-extract") {
            runCatching {
                executeRequest(serviceUrl, request)
            }.onSuccess {
                result = it
            }.onFailure {
                error = it
            }
        }
        worker.join()
        error?.let {
            return networkFailure()
        }
        return result ?: networkFailure()
    }

    private fun executeRequest(
        serviceUrl: String,
        request: AiExtractionRequest,
    ): AiExtractionResult {
        if (serviceUrl.endsWith(EventCandidatesExtractPath)) {
            return executeVelaExtractionRequest(serviceUrl, request)
        }
        return executeOpenAiCompatibleRequest(serviceUrl, request)
    }

    private fun executeVelaExtractionRequest(
        serviceUrl: String,
        request: AiExtractionRequest,
    ): AiExtractionResult =
        runCatching {
            val connection = (URL(serviceUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                if (apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
            }
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(json.encodeToString(request))
            }

            val responseCode = connection.responseCode
            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            connection.disconnect()

            if (responseCode !in 200..299) {
                AiExtractionResult.Failure(
                    code = "HTTP_$responseCode",
                    message = "连接失败请重试。AI 服务返回 $responseCode，未生成候选日程。",
                    retryable = true,
                )
            } else {
                val response = json.decodeFromString<AiExtractionResponse>(responseText)
                AiExtractionResult.Success(
                    summary = response.summary.ifBlank { "已解析出 ${response.candidates.size} 条候选日程。" },
                    candidates = response.candidates,
                )
            }
        }.getOrElse {
            networkFailure()
        }

    private fun executeOpenAiCompatibleRequest(
        serviceUrl: String,
        request: AiExtractionRequest,
    ): AiExtractionResult {
        val firstResult = executeOpenAiCompatibleRequestOnce(
            serviceUrl = serviceUrl,
            request = request,
            includeResponseFormat = true,
        )
        if (
            firstResult is AiExtractionResult.Failure &&
            firstResult.code in setOf("HTTP_400", "HTTP_404", "HTTP_422")
        ) {
            return executeOpenAiCompatibleRequestOnce(
                serviceUrl = serviceUrl,
                request = request,
                includeResponseFormat = false,
            )
        }
        return firstResult
    }

    private fun executeOpenAiCompatibleRequestOnce(
        serviceUrl: String,
        request: AiExtractionRequest,
        includeResponseFormat: Boolean,
    ): AiExtractionResult =
        runCatching {
            val connection = (URL(serviceUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 90_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                if (apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
            }
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(json.encodeToString(openAiCompatiblePayload(request, includeResponseFormat)))
            }

            val responseCode = connection.responseCode
            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            connection.disconnect()

            if (responseCode !in 200..299) {
                AiExtractionResult.Failure(
                    code = "HTTP_$responseCode",
                    message = "连接失败请重试。AI 服务返回 $responseCode，未生成候选日程。",
                    retryable = true,
                )
            } else {
                parseOpenAiCompatibleResponse(responseText)
            }
        }.getOrElse {
            networkFailure()
        }

    private fun openAiCompatiblePayload(
        request: AiExtractionRequest,
        includeResponseFormat: Boolean,
    ): JsonObject =
        buildJsonObject {
            put("model", modelFor(request))
            put("temperature", 0.1)
            if (includeResponseFormat) {
                put("response_format", buildJsonObject { put("type", "json_object") })
            }
            put(
                "messages",
                buildJsonArray {
                    addTextChatMessage(
                        role = "system",
                        content = extractionSystemPrompt(),
                    )
                    addChatMessage(
                        role = "user",
                        content = request.toOpenAiUserContent(),
                    )
                },
            )
        }

    private fun parseOpenAiCompatibleResponse(responseText: String): AiExtractionResult {
        val root = json.parseToJsonElement(responseText).jsonObject
        val content = root["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?.get("content")
            ?.jsonPrimitive
            ?.contentOrNull
            .orEmpty()
            .trim()
        if (content.isBlank()) {
            return AiExtractionResult.Failure(
                code = "EMPTY_RESPONSE",
                message = "连接失败请重试。AI 服务没有返回可解析内容。",
                retryable = true,
            )
        }

        val jsonContent = content.extractJsonObjectText()
        val extraction = runCatching {
            json.decodeFromString<OpenAiExtractionContent>(jsonContent)
        }.getOrElse {
            return AiExtractionResult.Failure(
                code = "PARSE_ERROR",
                message = "连接失败请重试。AI 返回内容无法解析为日程候选。",
                retryable = true,
            )
        }
        val candidates = extraction.candidates.mapIndexedNotNull { index, candidate ->
            candidate.toEventCandidate(index)
        }
        return AiExtractionResult.Success(
            summary = extraction.summary.ifBlank { "已解析出 ${candidates.size} 条候选日程。" },
            candidates = candidates,
        )
    }

    private fun String.toServiceUrl(): String {
        val trimmed = trim().trimEnd('/')
        return when {
            trimmed.isBlank() -> ""
            trimmed.endsWith(EventCandidatesExtractPath) -> trimmed
            trimmed.endsWith("/chat/completions") -> trimmed
            trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
            else -> "$trimmed$EventCandidatesExtractPath"
        }
    }

    private fun missingConfigFailure(): AiExtractionResult.Failure =
        AiExtractionResult.Failure(
            code = "SERVICE_NOT_CONFIGURED",
            message = "连接失败请重试。当前未配置真实 AI 服务，可以到「日程」里本地新建。",
            retryable = true,
        )

    private fun missingModelFailure(type: AiInputType): AiExtractionResult.Failure =
        AiExtractionResult.Failure(
            code = "MODEL_NOT_CONFIGURED",
            message = when (type) {
                AiInputType.Text -> "文本解析模型未配置，请先在设置中填写文本模型。"
                AiInputType.Image -> "图片识别模型未配置，请先在设置中填写图片模型。"
                AiInputType.File -> "文档解析模型未配置，请先在设置中填写文档模型。"
                AiInputType.Voice -> "语音转文字模型未配置，请先在设置中填写语音模型。"
            },
            retryable = true,
        )

    private fun unsupportedVoiceFailure(): AiExtractionResult.Failure =
        AiExtractionResult.Failure(
            code = "VOICE_NOT_READY",
            message = if (voiceModel.isBlank()) {
                "语音转文字模型未配置，请先在设置中填写语音模型。"
            } else {
                "语音转文字服务暂不可用，请稍后重试。"
            },
            retryable = true,
        )

    private fun networkFailure(): AiExtractionResult.Failure =
        AiExtractionResult.Failure(
            code = "NETWORK_ERROR",
            message = "连接失败请重试。当前未生成候选日程，可以到「日程」里本地新建。",
            retryable = true,
        )

    private fun modelFor(request: AiExtractionRequest): String =
        when (request.type) {
            AiInputType.Text -> textModel
            AiInputType.Image -> visionModel
            AiInputType.File -> if (request.attachments.any { !it.base64Data.isNullOrBlank() }) {
                visionModel.ifBlank { documentModel }
            } else {
                documentModel
            }
            AiInputType.Voice -> voiceModel
        }.trim()

    private fun AiExtractionRequest.validateAttachments(): AiExtractionResult.Failure? =
        when (type) {
            AiInputType.Text -> null
            AiInputType.Image -> if (attachments.none { it.base64Data.isNullOrBlank().not() }) {
                AiExtractionResult.Failure(
                    code = "IMAGE_CONTENT_EMPTY",
                    message = "图片内容读取失败，请重新选择图片。",
                    retryable = true,
                )
            } else {
                null
            }
            AiInputType.File -> if (attachments.none { it.hasReadableContent() }) {
                AiExtractionResult.Failure(
                    code = "FILE_CONTENT_EMPTY",
                    message = "文件内容读取失败，请重新选择文档。",
                    retryable = true,
                )
            } else {
                null
            }
            AiInputType.Voice -> null
        }
}

private fun extractionSystemPrompt(): String =
    """
        你是 Vela 的日程解析器。只输出 JSON，不要输出 Markdown。
        JSON 格式：
        {
          "summary": "一句中文总结",
          "candidates": [
            {
              "title": "日程标题",
              "startAt": "2026-05-18T10:00:00+08:00",
              "endAt": "2026-05-18T11:00:00+08:00",
              "timezone": "Asia/Shanghai",
              "location": {"name": "地点"},
              "description": "备注",
              "sourceEvidence": "从原文、图片或文档中提取该日程的依据",
              "confidence": 0.85,
              "missingFields": ["结束时间", "地点"]
            }
          ]
        }
        必须使用带 +08:00 偏移的 ISO 8601 时间。无法确定标题或开始时间时，不要生成候选。
        如果输入是图片或文档，请只提取其中真实出现或能明确推断的日程，不要补造不存在的信息。
    """.trimIndent()

private fun AiExtractionRequest.toOpenAiUserContent(): JsonElement {
    val prompt = """
        当前时区：$timezone
        语言：$locale
        输入类型：${type.toChineseLabel()}
        请从用户提供的内容中提取候选日程。
    """.trimIndent()

    return when (type) {
        AiInputType.Text -> JsonPrimitive(
            """
                $prompt
                文本内容：
                ${text.orEmpty()}
            """.trimIndent(),
        )
        AiInputType.Image -> buildJsonArray {
            addTextContentBlock("$prompt\n请识别图片里的日程、课程表、会议或待办时间信息。")
            attachments.forEach { attachment ->
                attachment.base64Data?.takeIf { it.isNotBlank() }?.let {
                    addImageContentBlock(attachment)
                }
            }
        }
        AiInputType.File -> buildJsonArray {
            addTextContentBlock("$prompt\n请识别文档里的日程、课程表、会议或待办时间信息。")
            attachments.forEach { attachment ->
                val extractedText = attachment.textContent?.takeIf { it.isNotBlank() }
                if (extractedText != null) {
                    addTextContentBlock(
                        """
                            文档：${attachment.fileName}
                            MIME：${attachment.mimeType}
                            已提取文本：
                            $extractedText
                        """.trimIndent(),
                    )
                } else if (!attachment.base64Data.isNullOrBlank()) {
                    addFileContentBlock(attachment)
                }
            }
        }
        AiInputType.Voice -> JsonPrimitive(prompt)
    }
}

private fun kotlinx.serialization.json.JsonArrayBuilder.addChatMessage(
    role: String,
    content: JsonElement,
) {
    add(
        buildJsonObject {
            put("role", role)
            put("content", content)
        },
    )
}

private fun kotlinx.serialization.json.JsonArrayBuilder.addTextChatMessage(
    role: String,
    content: String,
) {
    addChatMessage(role = role, content = JsonPrimitive(content))
}

private fun kotlinx.serialization.json.JsonArrayBuilder.addTextContentBlock(text: String) {
    add(
        buildJsonObject {
            put("type", "text")
            put("text", text)
        },
    )
}

private fun kotlinx.serialization.json.JsonArrayBuilder.addImageContentBlock(
    attachment: AiInputAttachment,
) {
    add(
        buildJsonObject {
            put("type", "image_url")
            put(
                "image_url",
                buildJsonObject {
                    put("url", attachment.toDataUrl())
                },
            )
        },
    )
}

private fun kotlinx.serialization.json.JsonArrayBuilder.addFileContentBlock(
    attachment: AiInputAttachment,
) {
    add(
        buildJsonObject {
            put("type", "file")
            put(
                "file",
                buildJsonObject {
                    put("filename", attachment.fileName)
                    put("file_data", attachment.toDataUrl())
                },
            )
        },
    )
}

private fun AiInputAttachment.toDataUrl(): String {
    val cleanMimeType = mimeType.ifBlank { "application/octet-stream" }
    return "data:$cleanMimeType;base64,${base64Data.orEmpty()}"
}

private fun AiInputAttachment.hasReadableContent(): Boolean =
    !textContent.isNullOrBlank() || !base64Data.isNullOrBlank()

private fun AiInputType.toChineseLabel(): String =
    when (this) {
        AiInputType.Text -> "文本"
        AiInputType.Image -> "图片"
        AiInputType.File -> "文档"
        AiInputType.Voice -> "语音"
    }

private fun String.extractJsonObjectText(): String {
    val withoutFence = trim()
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    val start = withoutFence.indexOf('{')
    val end = withoutFence.lastIndexOf('}')
    return if (start >= 0 && end > start) {
        withoutFence.substring(start, end + 1)
    } else {
        withoutFence
    }
}

@kotlinx.serialization.Serializable
private data class AiExtractionResponse(
    val summary: String = "",
    val candidates: List<EventCandidate> = emptyList(),
)

@Serializable
private data class OpenAiExtractionContent(
    val summary: String = "",
    val candidates: List<OpenAiCandidate> = emptyList(),
)

@Serializable
private data class OpenAiCandidate(
    val title: String = "",
    val startAt: String = "",
    val endAt: String? = null,
    val timezone: String? = "Asia/Shanghai",
    val location: OpenAiLocation? = null,
    val description: String? = null,
    val sourceEvidence: String? = null,
    val confidence: Float? = null,
    val missingFields: List<String> = emptyList(),
) {
    fun toEventCandidate(index: Int): EventCandidate? {
        val cleanTitle = title.trim()
        val cleanStartAt = startAt.trim()
        if (cleanTitle.isBlank() || cleanStartAt.isBlank()) {
            return null
        }
        return EventCandidate(
            id = "candidate-ai-${System.currentTimeMillis()}-$index",
            title = cleanTitle,
            startAt = cleanStartAt,
            endAt = endAt?.trim()?.takeIf { it.isNotBlank() },
            timezone = timezone?.takeIf { it.isNotBlank() } ?: "Asia/Shanghai",
            location = location?.toLocation(),
            description = description?.takeIf { it.isNotBlank() },
            sourceEvidence = sourceEvidence?.takeIf { it.isNotBlank() },
            confidence = confidence,
            isSelectedForImport = true,
            reviewStatus = EventCandidateReviewStatus.Pending,
            missingFields = missingFields,
        )
    }
}

@Serializable
private data class OpenAiLocation(
    val name: String = "",
    val address: String? = null,
) {
    fun toLocation(): com.vela.app.data.model.Location? =
        name.trim().takeIf { it.isNotBlank() }?.let { locationName ->
            com.vela.app.data.model.Location(
                name = locationName,
                address = address?.takeIf { it.isNotBlank() },
            )
        }
}

object UnavailableAiExtractionClient : AiExtractionClient {
    override fun extract(request: AiExtractionRequest): AiExtractionResult =
        AiExtractionResult.Failure(
            code = "SERVICE_UNAVAILABLE",
            message = when (request.type) {
                AiInputType.Text -> "连接失败请重试。当前未生成候选日程，可以到「日程」里本地新建。"
                AiInputType.Image -> "图片识别服务暂不可用，请稍后重试，或到「日程」里本地新建。"
                AiInputType.File -> "文件识别服务暂不可用，请稍后重试，或到「日程」里本地新建。"
                AiInputType.Voice -> "语音转文字服务暂不可用，请稍后重试。"
            },
            retryable = true,
        )
}
