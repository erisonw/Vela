package com.vela.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AiServiceConnectionTesterTest {
    @Test
    fun localProxyEndpointUsesHealthRoute() {
        assertEquals(
            "http://127.0.0.1:8787/health",
            AiServiceConnectionTester.probeUrl("http://127.0.0.1:8787/v1"),
        )
    }

    @Test
    fun remoteEndpointKeepsConfiguredPath() {
        assertEquals(
            "https://api.example.com/v1",
            AiServiceConnectionTester.probeUrl("https://api.example.com/v1/"),
        )
    }
}
