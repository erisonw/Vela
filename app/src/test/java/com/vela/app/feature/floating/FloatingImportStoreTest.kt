package com.vela.app.feature.floating

import com.vela.app.data.model.ImportTarget
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingImportStoreTest {
    @Test
    fun startCaptureHidesOverlayUntilResultOrError() {
        FloatingImportStore.setError("reset")

        FloatingImportStore.startCapture(ImportTarget.Schedule)

        assertFalse(FloatingImportStore.uiState.value.isOverlayVisible)
        assertTrue(FloatingImportStore.uiState.value.isLoading)

        FloatingImportStore.setError("截图失败")

        assertTrue(FloatingImportStore.uiState.value.isOverlayVisible)
    }
}
