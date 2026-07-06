package com.vela.app.data.ai

import com.vela.app.data.ai.HttpJsonTransport.readResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.DataOutputStream

data class AiVoiceRecording(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)

sealed interface VoiceTranscriptionResult {
    data class Success(val text: String) : VoiceTranscriptionResult

    data class Failure(
        val code: String,
        val message: String,
        val retryable: Boolean,
    ) : VoiceTranscriptionResult
}

interface VoiceTranscriptionClient {
    suspend fun transcribe(recording: AiVoiceRecording): VoiceTranscriptionResult
}

class HttpVoiceTranscriptionClient(
    private val endpoint: String,
    private val apiKey: String,
    private val model: String,
) : VoiceTranscriptionClient {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    override suspend fun transcribe(recording: AiVoiceRecording): VoiceTranscriptionResult {
        val serviceUrl = endpoint.toAudioTranscriptionUrl()
        val cleanModel = model.trim()
        if (serviceUrl.isBlank()) {
            return notConfiguredFailure()
        }
        if (cleanModel.isBlank()) {
            return VoiceTranscriptionResult.Failure(
                code = "MODEL_NOT_CONFIGURED",
                message = "语音转文字模型未配置，请先在设置中填写语音模型。",
                retryable = false,
            )
        }
        if (recording.bytes.isEmpty()) {
            return VoiceTranscriptionResult.Failure(
                code = "AUDIO_EMPTY",
                message = "录音内容为空，请重新录音。",
                retryable = true,
            )
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                executeRequest(serviceUrl, cleanModel, recording)
            }.getOrElse {
                VoiceTranscriptionResult.Failure(
                    code = "NETWORK_ERROR",
                    message = "语音转写连接失败，请稍后重试。",
                    retryable = true,
                )
            }
        }
    }

    private fun executeRequest(
        serviceUrl: String,
        cleanModel: String,
        recording: AiVoiceRecording,
    ): VoiceTranscriptionResult {
        val boundary = "VelaVoiceBoundary${System.currentTimeMillis()}"
        val connection = HttpJsonTransport.openConnection(
            url = serviceUrl,
            apiKey = apiKey,
            contentType = "multipart/form-data; boundary=$boundary",
            connectTimeoutMillis = 15_000,
            readTimeoutMillis = 90_000,
        )

        DataOutputStream(connection.outputStream).use { output ->
            output.writeFormField(boundary, "model", cleanModel)
            output.writeFormField(boundary, "language", "zh")
            output.writeFormField(boundary, "response_format", "json")
            output.writeFileField(
                boundary = boundary,
                name = "file",
                fileName = recording.fileName,
                mimeType = recording.mimeType,
                bytes = recording.bytes,
            )
            output.writeBytes("--$boundary--\r\n")
            output.flush()
        }

        val response = connection.readResponse()

        if (!response.isSuccess) {
            return VoiceTranscriptionResult.Failure(
                code = "HTTP_${response.code}",
                message = "语音转写失败。ASR 服务返回 ${response.code}。",
                retryable = true,
            )
        }

        val transcript = json.parseToJsonElement(response.body)
            .jsonObject["text"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.toCleanVoiceTranscript()
            .orEmpty()
        return if (transcript.isBlank()) {
            VoiceTranscriptionResult.Failure(
                code = "EMPTY_TRANSCRIPT",
                message = "语音转写没有返回文字，请重新录音。",
                retryable = true,
            )
        } else {
            VoiceTranscriptionResult.Success(transcript)
        }
    }

    private fun String.toAudioTranscriptionUrl(): String {
        val trimmed = trim().trimEnd('/')
        return when {
            trimmed.isBlank() -> ""
            trimmed.endsWith("/audio/transcriptions") -> trimmed
            trimmed.endsWith("/chat/completions") ->
                "${trimmed.removeSuffix("/chat/completions")}/audio/transcriptions"
            trimmed.endsWith("/v1") -> "$trimmed/audio/transcriptions"
            else -> "$trimmed/v1/audio/transcriptions"
        }
    }

    private fun notConfiguredFailure(): VoiceTranscriptionResult.Failure =
        VoiceTranscriptionResult.Failure(
            code = "SERVICE_NOT_CONFIGURED",
            message = "语音转写服务未配置，请先在设置中填写 AI Base URL。",
            retryable = true,
        )
}

object UnavailableVoiceTranscriptionClient : VoiceTranscriptionClient {
    override suspend fun transcribe(recording: AiVoiceRecording): VoiceTranscriptionResult =
        VoiceTranscriptionResult.Failure(
            code = "SERVICE_UNAVAILABLE",
            message = "语音转写服务未配置，请先在设置中填写 AI 服务。",
            retryable = false,
        )
}

internal fun String.toCleanVoiceTranscript(): String {
    val trimmed = trim()
    val normalized = trimmed
        .replace(Regex("\\s+"), "")
        .replace("请", "請")
        .replace("点赞", "點贊")
        .replace("订阅", "訂閱")
        .replace("转发", "轉發")
        .replace("打赏", "打賞")
        .replace("支持明镜", "支持明鏡")
        .replace("点点栏目", "點點欄目")
    return if (normalized in SilentTranscriptionHallucinations) {
        ""
    } else {
        trimmed
    }
}

private val SilentTranscriptionHallucinations = setOf(
    "請不吝點贊訂閱轉發打賞支持明鏡與點點欄目",
)

private fun DataOutputStream.writeFormField(
    boundary: String,
    name: String,
    value: String,
) {
    writeBytes("--$boundary\r\n")
    writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
    writeBytes(value)
    writeBytes("\r\n")
}

private fun DataOutputStream.writeFileField(
    boundary: String,
    name: String,
    fileName: String,
    mimeType: String,
    bytes: ByteArray,
) {
    val cleanFileName = fileName.replace("\"", "")
    writeBytes("--$boundary\r\n")
    writeBytes("Content-Disposition: form-data; name=\"$name\"; filename=\"$cleanFileName\"\r\n")
    writeBytes("Content-Type: ${mimeType.ifBlank { "application/octet-stream" }}\r\n\r\n")
    write(bytes)
    writeBytes("\r\n")
}
