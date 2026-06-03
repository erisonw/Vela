package com.vela.app.feature.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import android.icu.util.ChineseCalendar
import android.icu.util.ULocale
import java.util.Date
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import java.time.DayOfWeek
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vela.app.data.mock.MockVelaRepository
import com.vela.app.data.model.Event
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId

class CalendarViewModel : ViewModel() {
    private val repository = MockVelaRepository

    val events: StateFlow<List<Event>> = repository.events
        .map { events -> events.sortedBy { it.startAt } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
}

@Composable
fun CalendarScreen(
    onActivityDateClick: (LocalDate) -> Unit,
    viewModel: CalendarViewModel = viewModel(),
) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    var selectedDate by remember { mutableStateOf(LocalDate.now(ZoneId.of("Asia/Shanghai"))) }
    var monthDirection by remember { mutableStateOf(1) }
    var pendingNavigation by remember { mutableStateOf<PendingDateNavigation?>(null) }
    val eventCounts = remember(events) { events.countByDate() }
    val holidayMap = remember(selectedDate.year) { chineseHolidayMap(selectedDate.year) }
    val selectedEventCount = eventCounts[selectedDate].orZero()
    val selectedHoliday = holidayMap[selectedDate]

    LaunchedEffect(pendingNavigation) {
        val pending = pendingNavigation ?: return@LaunchedEffect
        delay(CalendarMotionSpec.TapFeedbackMillis)
        pendingNavigation = null
        onActivityDateClick(pending.date)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(VelaPageBackground),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            VelaPageHeader(
                eyebrow = "Vela",
                title = "日历",
                subtitle = "",
            )
        }

        item {
            MonthCalendarGrid(
                selectedDate = selectedDate,
                monthDirection = monthDirection,
                isNavigationPending = pendingNavigation != null,
                eventCounts = eventCounts,
                onDateSelected = { date ->
                    if (pendingNavigation != null) {
                        return@MonthCalendarGrid
                    }
                    selectedDate = date
                    pendingNavigation = PendingDateNavigation(
                        date = date,
                    )
                },
                onMonthSwipe = { monthOffset ->
                    monthDirection = if (monthOffset >= 0) 1 else -1
                    pendingNavigation = null
                    selectedDate = selectedDate.plusMonths(monthOffset)
                },
            )
        }

        item {
            SelectedDateSummary(
                selectedDate = selectedDate,
                eventCount = selectedEventCount,
                holiday = selectedHoliday,
                onScheduleClick = { onActivityDateClick(selectedDate) },
            )
        }
    }
}

@Composable
private fun MonthCalendarGrid(
    selectedDate: LocalDate,
    monthDirection: Int,
    isNavigationPending: Boolean,
    eventCounts: Map<LocalDate, Int>,
    onDateSelected: (LocalDate) -> Unit,
    onMonthSwipe: (Long) -> Unit,
) {
    var dragAmount by remember(selectedDate) { mutableStateOf(0f) }
    val visibleMonth = selectedDate.withDayOfMonth(1)
    val transitionDirection = monthDirection.takeIf { it != 0 } ?: 1

    // 提升卡片设计的品质感
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = Color(0x22000000)
            )
            .pointerInput(selectedDate) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            dragAmount <= -80f -> onMonthSwipe(1)
                            dragAmount >= 80f -> onMonthSwipe(-1)
                        }
                        dragAmount = 0f
                    },
                    onDragCancel = { dragAmount = 0f },
                    onHorizontalDrag = { _, dragDelta ->
                        dragAmount += dragDelta
                    },
                )
            },
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 月份抬头更简洁有力
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                AnimatedContent(
                    targetState = visibleMonth,
                    transitionSpec = { monthSlideTransition(transitionDirection) },
                    label = "month-title",
                ) { month ->
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${month.monthValue}月",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = VelaTextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${month.year}",
                            style = MaterialTheme.typography.titleMedium,
                            color = VelaTextSecondary,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
            }

            // 星期表头更淡雅
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                WeekdayLabels.forEach { label ->
                    Text(
                        modifier = Modifier.weight(1f),
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = VelaTextSecondary.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 核心日历网格
            AnimatedContent(
                targetState = visibleMonth,
                transitionSpec = { monthSlideTransition(transitionDirection) },
                label = "month-grid",
            ) { month ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    buildMonthCells(month, eventCounts, chineseHolidayMap(month.year)).forEach { week ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            week.forEach { cell ->
                                CalendarDayCell(
                                    cell = cell,
                                    selectedDate = selectedDate,
                                    isNavigationPending = isNavigationPending,
                                    onDateSelected = onDateSelected,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.CalendarDayCell(
    cell: CalendarDateCell,
    selectedDate: LocalDate,
    isNavigationPending: Boolean,
    onDateSelected: (LocalDate) -> Unit,
) {
    val date = cell.date
    if (date == null) {
        Spacer(modifier = Modifier.weight(1f).height(64.dp))
        return
    }

    val isSelected = date == selectedDate
    val isToday = date == LocalDate.now(ZoneId.of("Asia/Shanghai"))
    val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

    val cellBgColor by animateColorAsState(
        targetValue = if (isSelected) VelaPrimaryBlue.copy(alpha = 0.15f) else Color.Transparent,
        label = "cell-outer-bg"
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .height(68.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cellBgColor)
            .clickable(enabled = !isNavigationPending) { onDateSelected(date) }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 日期数字
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(if (isToday) VelaPrimaryBlue else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    isToday -> Color.White
                    isSelected -> VelaPrimaryBlue
                    isWeekend -> VelaTextSecondary.copy(alpha = 0.8f)
                    else -> VelaTextPrimary
                }
            )
        }

        // 节日/农历标记
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            cell.holiday?.let { holiday ->
                Text(
                    text = holiday,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    color = VelaWarmRed,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } ?: run {
                val lunar = date.getLunarDayString()
                Text(
                    text = lunar,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = if (isSelected) VelaPrimaryBlue.copy(alpha = 0.7f) else VelaTextSecondary.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }

            // 日程圆点
            if (cell.eventCount > 0) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(VelaPrimaryBlue, CircleShape)
                )
            } else {
                Spacer(modifier = Modifier.size(4.dp))
            }
        }
    }
}


@Composable
private fun SelectedDateSummary(
    selectedDate: LocalDate,
    eventCount: Int,
    holiday: String?,
    onScheduleClick: () -> Unit,
) {
    val summaryState = SelectedDateSummaryState(
        selectedDate = selectedDate,
        eventCount = eventCount,
        holiday = holiday,
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = VelaSoftLavender,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "历", color = VelaPrimaryBlue, fontWeight = FontWeight.Bold)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                AnimatedContent(
                    targetState = summaryState,
                    transitionSpec = {
                        summarySlideTransition()
                    },
                    label = "selected-date-summary",
                ) { state ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            text = "${state.selectedDate.monthValue} 月 ${state.selectedDate.dayOfMonth} 日",
                            style = MaterialTheme.typography.titleMedium,
                            color = VelaTextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = state.summaryText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = VelaTextSecondary,
                        )
                    }
                }
            }
            TextButton(onClick = onScheduleClick) {
                Text(text = "查看")
            }
        }
    }
}

@Composable
private fun VelaPageHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "◢", color = VelaPrimaryBlue, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = eyebrow,
                    color = VelaPrimaryBlue,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = VelaTextPrimary,
        )
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = VelaTextSecondary,
            )
        }
    }
}

private fun monthSlideTransition(direction: Int) =
    (slideInHorizontally(
        animationSpec = tween(CalendarMotionSpec.MonthTransitionMillis),
        initialOffsetX = { fullWidth -> direction * fullWidth },
    ) + fadeIn(animationSpec = tween(CalendarMotionSpec.MonthTransitionMillis))) togetherWith
        (slideOutHorizontally(
            animationSpec = tween(CalendarMotionSpec.MonthTransitionMillis),
            targetOffsetX = { fullWidth -> -direction * fullWidth },
        ) + fadeOut(animationSpec = tween(CalendarMotionSpec.MonthTransitionMillis)))

private fun summarySlideTransition() =
    (slideInVertically(
        animationSpec = tween(CalendarMotionSpec.SummaryTransitionMillis),
        initialOffsetY = { fullHeight -> fullHeight / 3 },
    ) + fadeIn(animationSpec = tween(CalendarMotionSpec.SummaryTransitionMillis)) +
        scaleIn(initialScale = 0.96f, animationSpec = tween(CalendarMotionSpec.SummaryTransitionMillis))) togetherWith
        (slideOutVertically(
            animationSpec = tween(CalendarMotionSpec.SummaryTransitionMillis),
            targetOffsetY = { fullHeight -> -fullHeight / 3 },
        ) + fadeOut(animationSpec = tween(CalendarMotionSpec.SummaryTransitionMillis)) +
            scaleOut(targetScale = 0.96f, animationSpec = tween(CalendarMotionSpec.SummaryTransitionMillis)))

private object CalendarMotionSpec {
    const val TapFeedbackMillis = 120L
    const val MonthTransitionMillis = 260
    const val SummaryTransitionMillis = 220
}

private val VelaPageBackground = Color(0xFFFAFBFF)
private val VelaPrimaryBlue = Color(0xFF2D6BFF)
private val VelaTextPrimary = Color(0xFF12162A)
private val VelaTextSecondary = Color(0xFF72788A)
private val VelaSoftLavender = Color(0xFFF1F0FF)
private val VelaWarmRed = Color(0xFFE45757)

private data class PendingDateNavigation(
    val date: LocalDate,
)

private data class SelectedDateSummaryState(
    val selectedDate: LocalDate,
    val eventCount: Int,
    val holiday: String?,
) {
    val summaryText: String = buildString {
        append(if (eventCount > 0) "日程安排：$eventCount 条" else "日程安排：暂无")
        if (holiday != null) {
            append(" · ")
            append(holiday)
        }
    }
}

private data class CalendarDateCell(
    val date: LocalDate?,
    val eventCount: Int = 0,
    val holiday: String? = null,
)

private val WeekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

private fun buildMonthCells(
    selectedDate: LocalDate,
    eventCounts: Map<LocalDate, Int>,
    holidays: Map<LocalDate, String>,
): List<List<CalendarDateCell>> {
    val firstDay = selectedDate.withDayOfMonth(1)
    val leadingEmptyCells = firstDay.dayOfWeek.value - 1
    val daysInMonth = selectedDate.lengthOfMonth()
    val cells = buildList {
        repeat(leadingEmptyCells) {
            add(CalendarDateCell(date = null))
        }
        (1..daysInMonth).forEach { day ->
            val date = selectedDate.withDayOfMonth(day)
            add(
                CalendarDateCell(
                    date = date,
                    eventCount = eventCounts[date].orZero(),
                    holiday = holidays[date],
                ),
            )
        }
        while (size % 7 != 0) {
            add(CalendarDateCell(date = null))
        }
    }
    return cells.chunked(7)
}

private fun List<Event>.countByDate(): Map<LocalDate, Int> =
    mapNotNull { event -> event.startAt.toLocalDateOrNull() }
        .groupingBy { it }
        .eachCount()

private fun String.toLocalDateOrNull(): LocalDate? =
    runCatching { java.time.OffsetDateTime.parse(this).toLocalDate() }.getOrNull()

private fun Int?.orZero(): Int = this ?: 0

private fun LocalDate.getLunarDayString(): String {
    return try {
        val date = Date.from(this.atStartOfDay(ZoneId.systemDefault()).toInstant())
        val chineseCalendar = ChineseCalendar(ULocale.CHINA)
        chineseCalendar.time = date

        val day = chineseCalendar.get(ChineseCalendar.DAY_OF_MONTH)
        val month = chineseCalendar.get(ChineseCalendar.MONTH) + 1

        if (day == 1) {
            val monthNames = arrayOf("正月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "冬月", "腊月")
            monthNames.getOrElse(month - 1) { "初一" }
        } else {
            val prefix = arrayOf("初", "十", "廿", "三")
            val suffix = arrayOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十")
            when (day) {
                10 -> "初十"
                20 -> "二十"
                30 -> "三十"
                else -> {
                    val p = (day - 1) / 10
                    val s = (day - 1) % 10
                    prefix[p] + suffix[s]
                }
            }
        }
    } catch (e: Exception) {
        ""
    }
}

private fun chineseHolidayMap(year: Int): Map<LocalDate, String> = when (year) {
    2026 -> buildMap {
        putHolidayRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), "元旦")
        putHolidayRange(LocalDate.of(2026, 2, 15), LocalDate.of(2026, 2, 23), "春节")
        putHolidayRange(LocalDate.of(2026, 4, 4), LocalDate.of(2026, 4, 6), "清明")
        putHolidayRange(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5), "劳动")
        putHolidayRange(LocalDate.of(2026, 6, 19), LocalDate.of(2026, 6, 21), "端午")
        putHolidayRange(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 27), "中秋")
        putHolidayRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), "国庆")
    }
    else -> emptyMap()
}

private fun MutableMap<LocalDate, String>.putHolidayRange(
    start: LocalDate,
    endInclusive: LocalDate,
    label: String,
) {
    var date = start
    while (!date.isAfter(endInclusive)) {
        this[date] = label
        date = date.plusDays(1)
    }
}
