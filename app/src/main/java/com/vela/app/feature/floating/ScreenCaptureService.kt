package com.vela.app.feature.floating

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import com.vela.app.data.ai.AiInputAttachment
import com.vela.app.data.mock.MockVelaRepository
import com.vela.app.data.model.ImportTarget
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScreenCaptureService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository = MockVelaRepository
    private val hasCompleted = AtomicBoolean(false)
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var imageReader: ImageReader? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startCaptureForeground()
        repository.initialize(this)
        val resultCode = intent?.getIntExtra(ExtraResultCode, 0) ?: 0
        val data = intent?.getParcelableIntentExtra(ExtraData)
        val target = intent?.getImportTargetExtra() ?: ImportTarget.Schedule
        val captureDelayMillis = intent?.getLongExtra(ExtraCaptureDelayMillis, 0L) ?: 0L
        if (resultCode == 0 || data == null) {
            FloatingImportStore.setError("截图授权信息无效。")
            stopSelf()
            return START_NOT_STICKY
        }
        if (captureDelayMillis > 0L) {
            Handler(Looper.getMainLooper()).postDelayed(
                {
                    captureOnce(resultCode = resultCode, data = data, target = target)
                },
                captureDelayMillis,
            )
        } else {
            captureOnce(resultCode = resultCode, data = data, target = target)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseCapture()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startCaptureForeground() {
        val notification = FloatingImportNotifications.foregroundNotification(
            context = this,
            title = "Vela 正在截图识别",
            text = "截图只用于本次 AI 导入。",
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FloatingImportNotifications.CaptureNotificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(FloatingImportNotifications.CaptureNotificationId, notification)
        }
    }

    private fun captureOnce(
        resultCode: Int,
        data: Intent,
        target: ImportTarget,
    ) {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val density = metrics.densityDpi
        val thread = HandlerThread("vela-screen-capture").also { it.start() }
        val captureHandler = Handler(thread.looper)
        handlerThread = thread
        handler = captureHandler
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
        val projection = mediaProjectionManager.getMediaProjection(resultCode, data)
        if (projection == null) {
            FloatingImportStore.setError("无法启动系统截屏。")
            stopSelf()
            return
        }
        mediaProjection = projection.apply {
            registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        releaseCapture()
                    }
                },
                captureHandler,
            )
        }

        imageReader?.setOnImageAvailableListener(
            { reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                if (!hasCompleted.compareAndSet(false, true)) {
                    image.close()
                    return@setOnImageAvailableListener
                }
                val attachment = runCatching {
                    image.toJpegAttachment()
                }.getOrElse { error ->
                    image.close()
                    FloatingImportStore.setError(error.message ?: "截图失败。")
                    stopSelf()
                    return@setOnImageAvailableListener
                }
                image.close()
                submitScreenshot(attachment = attachment, target = target)
            },
            captureHandler,
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "VelaScreenCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            captureHandler,
        )

        captureHandler.postDelayed(
            {
                if (hasCompleted.compareAndSet(false, true)) {
                    FloatingImportStore.setError("截图超时，请重试。")
                    stopSelf()
                }
            },
            4_000L,
        )
    }

    private fun submitScreenshot(
        attachment: AiInputAttachment,
        target: ImportTarget,
    ) {
        serviceScope.launch {
            val result = repository.submitFloatingImportImage(
                attachment = attachment,
                target = target,
            )
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    FloatingImportStore.setResult(
                        target = target,
                        message = result.message,
                        candidates = result.candidates,
                    )
                } else {
                    FloatingImportStore.setError(result.message)
                }
                stopSelf()
            }
        }
    }

    private fun releaseCapture() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        val projection = mediaProjection
        mediaProjection = null
        runCatching { projection?.stop() }
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
    }

    private fun android.media.Image.toJpegAttachment(): AiInputAttachment {
        val plane = planes.first()
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val bitmapWidth = width + rowPadding / pixelStride
        val fullBitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
        fullBitmap.copyPixelsFromBuffer(plane.buffer)
        val croppedBitmap = Bitmap.createBitmap(fullBitmap, 0, 0, width, height)
        val output = ByteArrayOutputStream()
        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)
        fullBitmap.recycle()
        croppedBitmap.recycle()
        return AiInputAttachment(
            fileName = "screen-${System.currentTimeMillis()}.jpg",
            mimeType = "image/jpeg",
            base64Data = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP),
        )
    }

    companion object {
        private const val ExtraResultCode = "extra_result_code"
        private const val ExtraData = "extra_data"
        private const val ExtraCaptureDelayMillis = "extra_capture_delay_millis"
        private const val DefaultCaptureDelayMillis = 900L

        fun createIntent(
            context: Context,
            resultCode: Int,
            data: Intent,
            target: ImportTarget,
        ): Intent =
            Intent(context, ScreenCaptureService::class.java)
                .putExtra(ExtraResultCode, resultCode)
                .putExtra(ExtraData, data)
                .putExtra(ExtraCaptureDelayMillis, DefaultCaptureDelayMillis)
                .putExtra(ScreenCapturePermissionActivity.ExtraTarget, target.name)
    }
}

private fun Intent.getParcelableIntentExtra(key: String): Intent? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, Intent::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key)
    }
