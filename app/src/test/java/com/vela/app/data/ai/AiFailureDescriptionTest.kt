package com.vela.app.data.ai

import java.net.ConnectException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiFailureDescriptionTest {
    @Test
    fun authenticationFailureIsNotRetryable() {
        val failure = describeAiHttpFailure(401, operation = "AI 解析")

        assertEquals("HTTP_401", failure.code)
        assertFalse(failure.retryable)
        assertTrue(failure.message.contains("API Key"))
    }

    @Test
    fun rateLimitAndServerFailuresAreRetryable() {
        assertTrue(describeAiHttpFailure(429, "AI 解析").retryable)
        assertTrue(describeAiHttpFailure(503, "AI 解析").retryable)
    }

    @Test
    fun networkFailuresExplainTimeoutAndLocalProxy() {
        val timeout = describeAiNetworkFailure(SocketTimeoutException(), "AI 服务")
        val refused = describeAiNetworkFailure(ConnectException(), "AI 服务")

        assertEquals("NETWORK_TIMEOUT", timeout.code)
        assertTrue(refused.message.contains("adb reverse"))
    }
}
