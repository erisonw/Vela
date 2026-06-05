package com.vela.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceTranscriptSanitizerTest {
    @Test
    fun removesKnownSilentTranscriptionHallucination() {
        val transcript = "請不吝點贊訂閱轉發打賞支持明鏡與點點欄目"

        assertEquals("", transcript.toCleanVoiceTranscript())
    }

    @Test
    fun keepsNormalScheduleTranscription() {
        val transcript = "明天早上八点提醒我去上网安基础课"

        assertEquals(transcript, transcript.toCleanVoiceTranscript())
    }
}
