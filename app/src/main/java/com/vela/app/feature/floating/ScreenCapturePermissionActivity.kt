package com.vela.app.feature.floating

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import com.vela.app.data.model.ImportTarget

class ScreenCapturePermissionActivity : Activity() {
    private val target: ImportTarget
        get() = intent.getImportTargetExtra()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
        @Suppress("DEPRECATION")
        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(),
            RequestScreenCapture,
        )
    }

    @Deprecated("Android framework callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != RequestScreenCapture) {
            finish()
            return
        }
        if (resultCode == RESULT_OK && data != null) {
            val captureIntent = ScreenCaptureService.createIntent(
                context = this,
                resultCode = resultCode,
                data = data,
                target = target,
            )
            startForegroundService(captureIntent)
        } else {
            FloatingImportStore.setError("未授权截图。")
        }
        finish()
    }

    companion object {
        private const val RequestScreenCapture = 4101
        const val ExtraTarget = "extra_import_target"

        fun createIntent(context: Context, target: ImportTarget): Intent =
            Intent(context, ScreenCapturePermissionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                .putExtra(ExtraTarget, target.name)
    }
}

fun Intent.getImportTargetExtra(): ImportTarget =
    getStringExtra(ScreenCapturePermissionActivity.ExtraTarget)
        ?.let { value -> runCatching { ImportTarget.valueOf(value) }.getOrNull() }
        ?: ImportTarget.Schedule
