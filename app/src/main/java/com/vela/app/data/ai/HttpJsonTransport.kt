package com.vela.app.data.ai

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

internal data class HttpTextResponse(
    val code: Int,
    val body: String,
) {
    val isSuccess: Boolean = code in 200..299
}

/**
 * 三个 AI 客户端共用的 HTTP 传输层：统一连接、超时、鉴权和读流。
 * 调用方负责在 IO 线程上执行（客户端方法都是 suspend + Dispatchers.IO）。
 */
internal object HttpJsonTransport {
    fun postJson(
        url: String,
        apiKey: String,
        body: String,
        readTimeoutMillis: Int,
        connectTimeoutMillis: Int = 15_000,
    ): HttpTextResponse {
        val connection = openConnection(
            url = url,
            apiKey = apiKey,
            contentType = "application/json; charset=utf-8",
            connectTimeoutMillis = connectTimeoutMillis,
            readTimeoutMillis = readTimeoutMillis,
        )
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(body)
        }
        return connection.readResponse()
    }

    fun openConnection(
        url: String,
        apiKey: String,
        contentType: String,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int,
    ): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            doOutput = true
            setRequestProperty("Content-Type", contentType)
            if (apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
        }

    fun HttpURLConnection.readResponse(): HttpTextResponse {
        val code = responseCode
        val body = if (code in 200..299) {
            inputStream.bufferedReader().use { it.readText() }
        } else {
            errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        disconnect()
        return HttpTextResponse(code = code, body = body)
    }
}
