package com.vela.app.feature.importchat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportChatUiStateTest {
    @Test
    fun parsingStatusTextShowsWhileTextOrImageIsBeingParsed() {
        assertEquals(
            "正在解析中...",
            ImportChatUiState(isSubmittingText = true).parsingStatusText,
        )
        assertEquals(
            "正在解析中...",
            ImportChatUiState(isSubmittingAttachment = true).parsingStatusText,
        )
    }

    @Test
    fun parsingStatusTextIsAbsentWhenImportIsIdle() {
        assertNull(ImportChatUiState().parsingStatusText)
    }
}
