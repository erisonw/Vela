package com.vela.app.data.ai

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal data class AiFailureDescription(
    val code: String,
    val message: String,
    val retryable: Boolean,
)

internal fun describeAiHttpFailure(
    statusCode: Int,
    operation: String,
): AiFailureDescription =
    when (statusCode) {
        401, 403 -> AiFailureDescription(
            code = "HTTP_$statusCode",
            message = "${operation}鉴权失败，请检查 API Key。",
            retryable = false,
        )

        404 -> AiFailureDescription(
            code = "HTTP_404",
            message = "${operation}接口不存在，请检查 Base URL。",
            retryable = false,
        )

        408 -> AiFailureDescription(
            code = "HTTP_408",
            message = "${operation}请求超时，请稍后重试。",
            retryable = true,
        )

        413 -> AiFailureDescription(
            code = "HTTP_413",
            message = "${operation}内容过大，请缩小附件后重试。",
            retryable = false,
        )

        429 -> AiFailureDescription(
            code = "HTTP_429",
            message = "${operation}请求过于频繁或额度不足，请稍后重试。",
            retryable = true,
        )

        in 500..599 -> AiFailureDescription(
            code = "HTTP_$statusCode",
            message = "${operation}服务暂不可用（$statusCode），请稍后重试。",
            retryable = true,
        )

        else -> AiFailureDescription(
            code = "HTTP_$statusCode",
            message = "${operation}失败，服务返回 $statusCode。",
            retryable = false,
        )
    }

internal fun describeAiNetworkFailure(
    error: Throwable,
    operation: String,
): AiFailureDescription =
    when (error) {
        is SocketTimeoutException -> AiFailureDescription(
            code = "NETWORK_TIMEOUT",
            message = "${operation}连接超时，请检查网络或稍后重试。",
            retryable = true,
        )

        is UnknownHostException -> AiFailureDescription(
            code = "HOST_UNREACHABLE",
            message = "${operation}无法解析服务地址，请检查 Base URL 和网络。",
            retryable = true,
        )

        is ConnectException -> AiFailureDescription(
            code = "CONNECTION_REFUSED",
            message = "${operation}无法连接服务。使用本机代理时请确认代理已启动并执行 adb reverse。",
            retryable = true,
        )

        is SSLException -> AiFailureDescription(
            code = "TLS_ERROR",
            message = "${operation}安全连接失败，请检查 HTTPS 证书或服务地址。",
            retryable = false,
        )

        else -> AiFailureDescription(
            code = "NETWORK_ERROR",
            message = "${operation}连接失败，请检查网络和服务配置。",
            retryable = true,
        )
    }
