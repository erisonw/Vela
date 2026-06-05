package com.vela.app.feature.floating

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import com.vela.app.data.mock.MockVelaRepository
import com.vela.app.data.model.Event
import com.vela.app.data.model.EventCandidate
import com.vela.app.data.model.ImportTarget
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FloatingImportService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository = MockVelaRepository
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var floatingParams: WindowManager.LayoutParams? = null
    private var collapsedEdge = FloatingEdge.End
    private var collapsedY = 0
    private var isCollapsedPeeking = false
    private val sideHideRunnable = Runnable { hideCollapsedToEdge() }

    override fun onCreate() {
        super.onCreate()
        repository.initialize(this)
        windowManager = getSystemService(WindowManager::class.java)
        collapsedY = 220.dp
        FloatingImportNotifications.ensureChannel(this)
        startOverlayForeground()
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先开启悬浮窗权限。", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }
        serviceScope.launch {
            FloatingImportStore.uiState.collect { state ->
                render(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        FloatingImportStore.expand()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        removeFloatingView()
        mainHandler.removeCallbacks(sideHideRunnable)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startOverlayForeground() {
        val notification = FloatingImportNotifications.foregroundNotification(
            context = this,
            title = "Vela 悬浮窗导入",
            text = "可从悬浮窗发起截图识别。",
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                FloatingImportNotifications.OverlayNotificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(FloatingImportNotifications.OverlayNotificationId, notification)
        }
    }

    private fun render(state: FloatingImportUiState) {
        if (!Settings.canDrawOverlays(this)) {
            return
        }
        if (!state.isOverlayVisible) {
            removeFloatingView()
            return
        }
        removeFloatingView()
        val view = if (state.isExpanded) {
            createPanelView(state)
        } else {
            createCollapsedView()
        }
        val params = if (state.isExpanded) {
            expandedLayoutParams()
        } else {
            isCollapsedPeeking = false
            collapsedLayoutParams(peeking = false)
        }
        floatingParams = params
        windowManager.addView(view, params)
        floatingView = view
        if (state.isExpanded) {
            animatePanelIn(view)
        } else {
            animateCollapsedIn(view)
            scheduleSideHide()
        }
    }

    private fun createCollapsedView(): View {
        val badge = TextView(this).apply {
            text = "V"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFE8A3.toInt())
            gravity = Gravity.CENTER
            background = cloudyDrawable(
                cornerRadius = 24.dp,
                startColor = 0xE81C1C20.toInt(),
                endColor = 0xC40E0E10.toInt(),
                strokeColor = 0x55FFFFFF,
                strokeWidth = 1.dp,
            )
            elevation = 18f
            setPadding(0, 0, 0, 1.dp)
            setOnClickListener {
                revealCollapsedFromEdge()
                FloatingImportStore.expand()
            }
        }
        return FrameLayout(this).apply {
            setPadding(7.dp, 7.dp, 7.dp, 7.dp)
            addView(
                badge,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            setOnTouchListener(createCollapsedTouchListener())
        }
    }

    private fun createPanelView(state: FloatingImportUiState): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp, 13.dp, 14.dp, 14.dp)
            background = cloudyDrawable(
                cornerRadius = 30.dp,
                startColor = 0xEC2A2825.toInt(),
                endColor = 0xD7121215.toInt(),
                strokeColor = 0x46FFFFFF,
                strokeWidth = 1.dp,
            )
            elevation = 22f
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }

        panel.addView(createPanelHeader(state))
        panel.addView(createActionGrid())

        state.message?.takeIf { it.isNotBlank() }?.let { message ->
            panel.addView(
                TextView(this).apply {
                    text = message
                    textSize = 13f
                    setTextColor(if (state.isLoading) 0xCFFFFFFF.toInt() else 0xE9FFFFFF.toInt())
                    setLineSpacing(1.dp.toFloat(), 1.0f)
                    maxLines = 3
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(3.dp, 10.dp, 3.dp, 4.dp)
                },
            )
        }

        if (state.candidates.isNotEmpty()) {
            panel.addView(createCandidateList(state))
        } else if (state.todayEvents.isNotEmpty()) {
            panel.addView(createTodayEventList(state.todayEvents))
        }

        return FrameLayout(this).apply {
            setPadding(8.dp, 8.dp, 8.dp, 8.dp)
            addView(panel)
        }
    }

    private fun createPanelHeader(state: FloatingImportUiState): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                TextView(context).apply {
                    text = if (state.target == ImportTarget.Timetable) "课表识别" else "Vela 导入"
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    includeFontPadding = false
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                TextView(context).apply {
                    text = if (state.isLoading) "识别中" else "Cloudy"
                    textSize = 12f
                    setTextColor(0xD8FFE8A3.toInt())
                    gravity = Gravity.CENTER
                    setPadding(11.dp, 5.dp, 11.dp, 5.dp)
                    background = cloudyDrawable(
                        cornerRadius = 999.dp,
                        startColor = 0x30FFFFFF,
                        endColor = 0x12FFFFFF,
                        strokeColor = 0x24FFFFFF,
                        strokeWidth = 1.dp,
                    )
                },
            )
        }

    private fun createActionGrid(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12.dp, 0, 0)
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addCloudyButton("日程导入", AccentColor.Gold) { requestCapture(ImportTarget.Schedule) }
                    addCloudyButton("课表导入", AccentColor.Blue) { requestCapture(ImportTarget.Timetable) }
                },
            )
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addCloudyButton("今日日程", AccentColor.Neutral) { showTodayEvents() }
                    addCloudyButton("收起", AccentColor.Neutral) { FloatingImportStore.collapse() }
                },
            )
        }

    private fun LinearLayout.addCloudyButton(
        label: String,
        accentColor: AccentColor,
        onClick: () -> Unit,
    ) {
        addView(
            TextView(context).apply {
                text = label
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(accentColor.textColor)
                includeFontPadding = false
                background = cloudyDrawable(
                    cornerRadius = 18.dp,
                    startColor = accentColor.startColor,
                    endColor = accentColor.endColor,
                    strokeColor = 0x28FFFFFF,
                    strokeWidth = 1.dp,
                )
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90L).start()
                        MotionEvent.ACTION_CANCEL,
                        MotionEvent.ACTION_UP,
                        -> view.animate().scaleX(1f).scaleY(1f).setDuration(130L).start()
                    }
                    false
                }
                setOnClickListener { onClick() }
            },
            LinearLayout.LayoutParams(0, 42.dp, 1f).apply {
                setMargins(3.dp, 3.dp, 3.dp, 3.dp)
            },
        )
    }

    private fun createCandidateList(state: FloatingImportUiState): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8.dp, 0, 0)
        }
        state.candidates.forEach { candidate ->
            container.addView(createCandidateCard(candidate, candidate.id in state.selectedCandidateIds))
        }
        container.addView(
            TextView(this).apply {
                text = "确认导入"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(0xFF17130A.toInt())
                includeFontPadding = false
                background = cloudyDrawable(
                    cornerRadius = 19.dp,
                    startColor = 0xFFFFE58F.toInt(),
                    endColor = 0xFFE0B94B.toInt(),
                    strokeColor = 0x44FFFFFF,
                    strokeWidth = 1.dp,
                )
                setOnClickListener {
                    val selectedCandidates = FloatingImportStore.uiState.value.candidates
                        .filter { it.id in FloatingImportStore.uiState.value.selectedCandidateIds }
                    val result = repository.importFloatingCandidates(selectedCandidates, state.target)
                    if (result.isSuccess) {
                        FloatingImportStore.markImported(result.importedCount)
                    } else {
                        FloatingImportStore.setError(result.blockedReasons.joinToString("\n"))
                    }
                }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                44.dp,
            ).apply {
                setMargins(3.dp, 7.dp, 3.dp, 0)
            },
        )
        return ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(container)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                340.dp,
            )
        }
    }

    private fun createCandidateCard(
        candidate: EventCandidate,
        selected: Boolean,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10.dp, 9.dp, 10.dp, 9.dp)
            background = cloudyDrawable(
                cornerRadius = 18.dp,
                startColor = 0x24FFFFFF,
                endColor = 0x14FFFFFF,
                strokeColor = if (selected) 0x66FFE58F else 0x1FFFFFFF,
                strokeWidth = 1.dp,
            )
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        CheckBox(context).apply {
                            isChecked = selected
                            buttonTintList = ColorStateList(
                                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                                intArrayOf(0xFFFFD866.toInt(), 0x99FFFFFF.toInt()),
                            )
                            setOnClickListener {
                                FloatingImportStore.toggleCandidate(candidate.id)
                            }
                        },
                        LinearLayout.LayoutParams(38.dp, 38.dp),
                    )
                    addView(
                        TextView(context).apply {
                            text = candidate.title.ifBlank { "未命名日程" }
                            textSize = 15f
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(Color.WHITE)
                            maxLines = 1
                            ellipsize = TextUtils.TruncateAt.END
                            includeFontPadding = false
                            gravity = Gravity.CENTER_VERTICAL
                        },
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                    )
                    addView(
                        TextView(context).apply {
                            text = "删除"
                            textSize = 12f
                            setTextColor(0xD8FFFFFF.toInt())
                            gravity = Gravity.CENTER
                            setPadding(10.dp, 5.dp, 10.dp, 5.dp)
                            background = cloudyDrawable(
                                cornerRadius = 999.dp,
                                startColor = 0x24FFFFFF,
                                endColor = 0x12FFFFFF,
                                strokeColor = 0x20FFFFFF,
                                strokeWidth = 1.dp,
                            )
                            setOnClickListener {
                                FloatingImportStore.removeCandidate(candidate.id)
                            }
                        },
                    )
                },
            )
            addView(candidate.detailTextView(context))
        }.withBottomGap()

    private fun createTodayEventList(events: List<Event>): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8.dp, 0, 0)
        }
        events.sortedBy { it.startAt }.forEach { event ->
            container.addView(
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(10.dp, 8.dp, 10.dp, 8.dp)
                    background = cloudyDrawable(
                        cornerRadius = 17.dp,
                        startColor = 0x22FFFFFF,
                        endColor = 0x12FFFFFF,
                        strokeColor = 0x1FFFFFFF,
                        strokeWidth = 1.dp,
                    )
                    addView(
                        TextView(context).apply {
                            text = event.title.ifBlank { "未命名日程" }
                            textSize = 14f
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(Color.WHITE)
                            maxLines = 1
                            ellipsize = TextUtils.TruncateAt.END
                            includeFontPadding = false
                        },
                    )
                    addView(
                        TextView(context).apply {
                            text = listOfNotNull(
                                event.startAt.toShortTimeText(),
                                event.location?.name?.takeIf { it.isNotBlank() },
                            ).joinToString("  ")
                            textSize = 12f
                            setTextColor(0xBFFFFFFF.toInt())
                            maxLines = 1
                            ellipsize = TextUtils.TruncateAt.END
                            setPadding(0, 5.dp, 0, 0)
                        },
                    )
                }.withBottomGap(),
            )
        }
        return ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(container)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                260.dp,
            )
        }
    }

    private fun requestCapture(target: ImportTarget) {
        FloatingImportStore.startCapture(target)
        startActivity(
            ScreenCapturePermissionActivity.createIntent(this, target)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun showTodayEvents() {
        val today = java.time.LocalDate.now(ZoneId.of("Asia/Shanghai"))
        val events = repository.events.value.filter { event ->
            event.startAt.toOffsetDateTimeOrNull()?.toLocalDate() == today
        }
        FloatingImportStore.showTodayEvents(events)
    }

    private fun removeFloatingView() {
        mainHandler.removeCallbacks(sideHideRunnable)
        floatingView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        floatingView = null
        floatingParams = null
    }

    private fun animatePanelIn(view: View) {
        view.alpha = 0f
        view.scaleX = 0.94f
        view.scaleY = 0.94f
        view.translationY = 18.dp.toFloat()
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(230L)
            .setInterpolator(OvershootInterpolator(0.72f))
            .start()
    }

    private fun animateCollapsedIn(view: View) {
        view.alpha = 0f
        view.scaleX = 0.86f
        view.scaleY = 0.86f
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(180L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun createCollapsedTouchListener(): View.OnTouchListener {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var isDragging = false
        return View.OnTouchListener { view, event ->
            val params = floatingParams ?: return@OnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    mainHandler.removeCallbacks(sideHideRunnable)
                    revealCollapsedFromEdge()
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    isDragging = false
                    view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90L).start()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!isDragging && (abs(dx) > 8.dp.toFloat() || abs(dy) > 8.dp.toFloat())) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = (startX + dx.toInt()).coerceIn(0, screenWidth() - CollapsedWidth.dp)
                        params.y = (startY + dy.toInt()).coerceIn(64.dp, screenHeight() - CollapsedHeight.dp - 80.dp)
                        collapsedY = params.y
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(130L).start()
                    if (isDragging) {
                        snapCollapsedToNearestEdge(view, params)
                        scheduleSideHide()
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                        FloatingImportStore.expand()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun snapCollapsedToNearestEdge(view: View, params: WindowManager.LayoutParams) {
        val middle = params.x + CollapsedWidth.dp / 2
        collapsedEdge = if (middle < screenWidth() / 2) FloatingEdge.Start else FloatingEdge.End
        val targetX = collapsedFullX(collapsedEdge)
        animateWindowX(view, params, targetX) {
            isCollapsedPeeking = false
        }
    }

    private fun scheduleSideHide() {
        mainHandler.removeCallbacks(sideHideRunnable)
        mainHandler.postDelayed(sideHideRunnable, SideHideDelayMillis)
    }

    private fun hideCollapsedToEdge() {
        val view = floatingView ?: return
        val params = floatingParams ?: return
        if (FloatingImportStore.uiState.value.isExpanded) {
            return
        }
        animateWindowX(view, params, collapsedPeekX(collapsedEdge)) {
            isCollapsedPeeking = true
        }
    }

    private fun revealCollapsedFromEdge() {
        if (!isCollapsedPeeking) {
            return
        }
        val view = floatingView ?: return
        val params = floatingParams ?: return
        animateWindowX(view, params, collapsedFullX(collapsedEdge)) {
            isCollapsedPeeking = false
        }
    }

    private fun animateWindowX(
        view: View,
        params: WindowManager.LayoutParams,
        targetX: Int,
        onEnd: () -> Unit,
    ) {
        ValueAnimator.ofInt(params.x, targetX).apply {
            duration = 220L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                params.x = animator.animatedValue as Int
                runCatching { windowManager.updateViewLayout(view, params) }
            }
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        onEnd()
                    }
                },
            )
            start()
        }
    }

    private fun collapsedLayoutParams(peeking: Boolean): WindowManager.LayoutParams =
        baseLayoutParams(
            width = CollapsedWidth.dp,
            height = CollapsedHeight.dp,
            allowOffscreen = true,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (peeking) collapsedPeekX(collapsedEdge) else collapsedFullX(collapsedEdge)
            y = collapsedY
        }

    private fun expandedLayoutParams(): WindowManager.LayoutParams =
        baseLayoutParams(
            width = (screenWidth() - 28.dp).coerceAtMost(420.dp),
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            allowOffscreen = false,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 78.dp
        }

    private fun baseLayoutParams(
        width: Int,
        height: Int,
        allowOffscreen: Boolean,
    ): WindowManager.LayoutParams {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            (if (allowOffscreen) WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS else 0)
        return WindowManager.LayoutParams(
            width,
            height,
            overlayType,
            flags,
            PixelFormat.TRANSLUCENT,
        )
    }

    private fun cloudyDrawable(
        cornerRadius: Int,
        startColor: Int,
        endColor: Int,
        strokeColor: Int,
        strokeWidth: Int,
    ): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(startColor, endColor)).apply {
            this.cornerRadius = cornerRadius.toFloat()
            setStroke(strokeWidth, strokeColor)
        }

    private fun collapsedFullX(edge: FloatingEdge): Int =
        when (edge) {
            FloatingEdge.Start -> 12.dp
            FloatingEdge.End -> screenWidth() - CollapsedWidth.dp - 12.dp
        }

    private fun collapsedPeekX(edge: FloatingEdge): Int =
        when (edge) {
            FloatingEdge.Start -> -(CollapsedWidth.dp - CollapsedVisibleStrip.dp)
            FloatingEdge.End -> screenWidth() - CollapsedVisibleStrip.dp
        }

    private fun screenWidth(): Int = resources.displayMetrics.widthPixels

    private fun screenHeight(): Int = resources.displayMetrics.heightPixels

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private fun LinearLayout.withBottomGap(): LinearLayout {
        addView(Space(context), LinearLayout.LayoutParams(1, 7.dp))
        return this
    }

    private fun EventCandidate.detailTextView(context: Context): TextView =
        TextView(context).apply {
            text = listOfNotNull(
                startAt.toShortDateTimeText(),
                location?.name?.takeIf { it.isNotBlank() },
                description?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            textSize = 12f
            setTextColor(0xC7FFFFFF.toInt())
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(38.dp, 2.dp, 4.dp, 2.dp)
        }

    private fun String.toShortDateTimeText(): String =
        toOffsetDateTimeOrNull()?.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
            ?: this

    private fun String.toShortTimeText(): String =
        toOffsetDateTimeOrNull()?.format(DateTimeFormatter.ofPattern("HH:mm"))
            ?: this

    private fun String.toOffsetDateTimeOrNull(): OffsetDateTime? =
        runCatching { OffsetDateTime.parse(this) }.getOrNull()

    private enum class FloatingEdge {
        Start,
        End,
    }

    private enum class AccentColor(
        val startColor: Int,
        val endColor: Int,
        val textColor: Int,
    ) {
        Gold(
            startColor = 0x40FFE58F,
            endColor = 0x20CBA73A,
            textColor = 0xFFFFE8A3.toInt(),
        ),
        Blue(
            startColor = 0x364DB8FF,
            endColor = 0x1E2E90C9,
            textColor = 0xFFCBEFFF.toInt(),
        ),
        Neutral(
            startColor = 0x24FFFFFF,
            endColor = 0x12FFFFFF,
            textColor = Color.WHITE,
        ),
    }

    private companion object {
        const val CollapsedWidth = 68
        const val CollapsedHeight = 68
        const val CollapsedVisibleStrip = 22
        const val SideHideDelayMillis = 1_800L
    }
}
