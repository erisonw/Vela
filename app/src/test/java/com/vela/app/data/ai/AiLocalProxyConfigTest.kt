package com.vela.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AiLocalProxyConfigTest {
    @Test
    fun debugProxyConfigUsesLocalReverseEndpointAndProxyModels() {
        assertEquals("http://127.0.0.1:8787/v1", AiLocalProxyConfig.Endpoint)
        assertEquals("", AiLocalProxyConfig.ApiKey)
        assertEquals("proxy-text", AiLocalProxyConfig.TextModel)
        assertEquals("proxy-vision", AiLocalProxyConfig.VisionModel)
        assertEquals("proxy-voice", AiLocalProxyConfig.VoiceModel)
    }
}
