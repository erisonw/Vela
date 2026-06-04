package com.vela.app.feature.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.alpha
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
import kotlin.math.absoluteValue

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
    var isCalendarExpanded by remember { mutableStateOf(false) }
    val eventsByDate = remember(events) { events.groupByDate() }
    val selectedEventCount = eventsByDate[selectedDate].orEmpty().size
    val selectedSpecialDay = chineseSpecialDayMap(selectedDate.year)[selectedDate]

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
                eventsByDate = eventsByDate,
                isExpanded = isCalendarExpanded,
                onExpandedChange = { isCalendarExpanded = it },
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
                holiday = selectedSpecialDay?.summaryLabel,
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
    eventsByDate: Map<LocalDate, List<Event>>,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onMonthSwipe: (Long) -> Unit,
) {
    var dragX by remember(selectedDate, isExpanded) { mutableStateOf(0f) }
    var dragY by remember(selectedDate, isExpanded) { mutableStateOf(0f) }
    val visibleMonth = selectedDate.withDayOfMonth(1)
    val transitionDirection = monthDirection.takeIf { it != 0 } ?: 1

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = Color(0x22000000)
            )
            .pointerInput(selectedDate, isExpanded) {
                detectDragGestures(
                    onDragEnd = {
                        when {
                            dragY.absoluteValue > dragX.absoluteValue && dragY >= 72f -> onExpandedChange(true)
                            dragY.absoluteValue > dragX.absoluteValue && dragY <= -72f -> onExpandedChange(false)
                            dragX <= -80f -> onMonthSwipe(1)
                            dragX >= 80f -> onMonthSwipe(-1)
                        }
                        dragX = 0f
                        dragY = 0f
                    },
                    onDragCancel = {
                        dragX = 0f
                        dragY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragX += dragAmount.x
                        dragY += dragAmount.y
                    },
                )
            },
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                Spacer(modifier = Modifier.weight(1f))
                CalendarExpandHandle(
                    isExpanded = isExpanded,
                    onClick = { onExpandedChange(!isExpanded) },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                WeekdayLabels.forEachIndexed { index, label ->
                    Text(
                        modifier = Modifier.weight(1f),
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (index == 0 || index == 6) {
                            VelaWarmRed.copy(alpha = 0.72f)
                        } else {
                            VelaTextSecondary.copy(alpha = 0.6f)
                        },
                        textAlign = TextAlign.Center
                    )
                }
            }

            AnimatedContent(
                targetState = visibleMonth,
                transitionSpec = { monthSlideTransition(transitionDirection) },
                label = "month-grid",
            ) { month ->
                Column(verticalArrangement = Arrangement.spacedBy(if (isExpanded) 2.dp else 1.dp)) {
                    buildMonthCells(month, eventsByDate).forEach { week ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            week.forEach { cell ->
                                CalendarDayCell(
                                    cell = cell,
                                    selectedDate = selectedDate,
                                    isNavigationPending = isNavigationPending,
                                    isExpanded = isExpanded,
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
private fun CalendarExpandHandle(
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(width = 42.dp, height = 28.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = VelaSoftLavender.copy(alpha = 0.92f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (isExpanded) "⌃" else "⌄",
                color = VelaPrimaryBlue,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun RowScope.CalendarDayCell(
    cell: CalendarDateCell,
    selectedDate: LocalDate,
    isNavigationPending: Boolean,
    isExpanded: Boolean,
    onDateSelected: (LocalDate) -> Unit,
) {
    val date = cell.date
    val isSelected = date == selectedDate
    val isToday = date == LocalDate.now(ZoneId.of("Asia/Shanghai"))
    val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    val contentAlpha = if (cell.isCurrentMonth) 1f else 0.2f
    val cellHeight by animateDpAsState(
        targetValue = if (isExpanded) 84.dp else 52.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "calendar-cell-height",
    )
    val detailAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(CalendarMotionSpec.ExpandTransitionMillis),
        label = "calendar-detail-alpha",
    )

    val cellBgColor by animateColorAsState(
        targetValue = when {
            isSelected -> VelaPrimaryBlue.copy(alpha = 0.12f)
            cell.specialDay?.kind == CalendarDayKind.Rest -> VelaWarmRed.copy(alpha = if (isExpanded) 0.035f else 0.02f)
            else -> Color.Transparent
        },
        label = "cell-outer-bg"
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .height(cellHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(cellBgColor)
            .border(
                width = if (isSelected) 1.dp else 0.dp,
                color = if (isSelected) VelaTextSecondary.copy(alpha = 0.55f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(enabled = !isNavigationPending) { onDateSelected(date) }
            .padding(horizontal = 0.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(if (isToday) VelaPrimaryBlue else Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isToday || isSelected || cell.specialDay != null) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        isToday -> Color.White
                        isSelected -> VelaPrimaryBlue
                        cell.specialDay?.kind == CalendarDayKind.Rest -> VelaWarmRed
                        isWeekend -> VelaWarmRed.copy(alpha = 0.72f)
                        else -> VelaTextPrimary
                    }.copy(alpha = if (isToday) 1f else contentAlpha)
                )
            }
            cell.specialDay?.let { specialDay ->
                SpecialDayBadge(
                    specialDay = specialDay,
                    modifier = Modifier.align(Alignment.TopEnd),
                    alpha = contentAlpha,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            if (isExpanded) {
                Text(
                    modifier = Modifier.alpha(detailAlpha * contentAlpha),
                    text = date.getLunarDayString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = VelaTextSecondary,
                    maxLines = 1,
                )
            }

            cell.specialDay?.takeIf { it.kind == CalendarDayKind.Rest }?.let { specialDay ->
                CalendarBand(
                    text = specialDay.label.takeIf {
                        isExpanded && cell.segment in listOf(CalendarRangeSegment.Single, CalendarRangeSegment.Start)
                    }.orEmpty(),
                    color = VelaWarmRed.copy(alpha = if (isExpanded) 0.34f else 0.56f),
                    textColor = VelaTextPrimary,
                    segment = cell.segment,
                    compact = !isExpanded,
                )
            }

            if (isExpanded) {
                cell.events.take(2).forEach { event ->
                    CalendarBand(
                        text = event.title,
                        color = event.calendarMarkerColor().copy(alpha = 0.28f),
                        textColor = VelaTextPrimary,
                        segment = CalendarRangeSegment.Single,
                        compact = false,
                    )
                }
                if (cell.events.size > 2) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(detailAlpha * contentAlpha),
                        text = "+${cell.events.size - 2}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = VelaTextSecondary,
                        textAlign = TextAlign.Start,
                    )
                }
            } else {
                cell.events.take(2).forEach { event ->
                    CalendarBand(
                        text = "",
                        color = event.calendarMarkerColor(),
                        textColor = Color.Transparent,
                        segment = CalendarRangeSegment.Single,
                        compact = true,
                    )
                }
                if (cell.specialDay == null && cell.events.isEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun SpecialDayBadge(
    specialDay: CalendarSpecialDay,
    modifier: Modifier,
    alpha: Float,
) {
    val color = when (specialDay.kind) {
        CalendarDayKind.Rest -> VelaWarmRed
        CalendarDayKind.Work -> VelaWorkBlue
    }
    Surface(
        modifier = modifier
            .size(17.dp)
            .alpha(alpha),
        shape = CircleShape,
        color = color,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = when (specialDay.kind) {
                    CalendarDayKind.Rest -> "休"
                    CalendarDayKind.Work -> "班"
                },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CalendarBand(
    text: String,
    color: Color,
    textColor: Color,
    segment: CalendarRangeSegment,
    compact: Boolean,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 4.dp else 15.dp),
        shape = segment.bandShape(),
        color = color,
    ) {
        if (!compact && text.isNotBlank()) {
            Text(
                modifier = Modifier.padding(horizontal = 2.dp),
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
        }
    }
}

@Composable
private fun CalendarRangeSegment.bandShape(): RoundedCornerShape =
    when (this) {
        CalendarRangeSegment.Single -> RoundedCornerShape(5.dp)
        CalendarRangeSegment.Start -> RoundedCornerShape(
            topStart = 5.dp,
            bottomStart = 5.dp,
            topEnd = 0.dp,
            bottomEnd = 0.dp,
        )
        CalendarRangeSegment.Middle -> RoundedCornerShape(0.dp)
        CalendarRangeSegment.End -> RoundedCornerShape(
            topStart = 0.dp,
            bottomStart = 0.dp,
            topEnd = 5.dp,
            bottomEnd = 5.dp,
        )
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
    const val ExpandTransitionMillis = 240
}

private val VelaPageBackground = Color(0xFFFAFBFF)
private val VelaPrimaryBlue = Color(0xFF2D6BFF)
private val VelaTextPrimary = Color(0xFF12162A)
private val VelaTextSecondary = Color(0xFF72788A)
private val VelaSoftLavender = Color(0xFFF1F0FF)
private val VelaWarmRed = Color(0xFFE45757)
private val VelaWorkBlue = Color(0xFF2F9AEF)
private val CalendarEventColors = listOf(
    Color(0xFF2D6BFF),
    Color(0xFF17A86B),
    Color(0xFFE6A629),
    Color(0xFFB763F6),
    Color(0xFFE45757),
    Color(0xFF00A7B8),
)

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

internal enum class CalendarDayKind {
    Rest,
    Work,
}

internal data class CalendarSpecialDay(
    val label: String,
    val kind: CalendarDayKind,
) {
    val summaryLabel: String
        get() = when (kind) {
            CalendarDayKind.Rest -> label
            CalendarDayKind.Work -> "调休上班"
        }
}

private enum class CalendarRangeSegment {
    Single,
    Start,
    Middle,
    End,
}

private data class CalendarDateCell(
    val date: LocalDate,
    val isCurrentMonth: Boolean,
    val events: List<Event> = emptyList(),
    val specialDay: CalendarSpecialDay? = null,
    val segment: CalendarRangeSegment = CalendarRangeSegment.Single,
)

private val WeekdayLabels = listOf("日", "一", "二", "三", "四", "五", "六")

private fun buildMonthCells(
    selectedDate: LocalDate,
    eventsByDate: Map<LocalDate, List<Event>>,
): List<List<CalendarDateCell>> {
    val firstDay = selectedDate.withDayOfMonth(1)
    val leadingDays = firstDay.dayOfWeek.value % 7
    val gridStart = firstDay.minusDays(leadingDays.toLong())
    val cells = buildList {
        repeat(42) { offset ->
            val date = gridStart.plusDays(offset.toLong())
            val specialDay = chineseSpecialDayMap(date.year)[date]
            add(
                CalendarDateCell(
                    date = date,
                    isCurrentMonth = date.month == selectedDate.month && date.year == selectedDate.year,
                    events = eventsByDate[date].orEmpty(),
                    specialDay = specialDay,
                    segment = specialDay?.let { rangeSegmentFor(date, it) } ?: CalendarRangeSegment.Single,
                ),
            )
        }
    }
    return cells.chunked(7)
}

private fun rangeSegmentFor(
    date: LocalDate,
    specialDay: CalendarSpecialDay,
): CalendarRangeSegment {
    val previousDate = date.minusDays(1)
    val nextDate = date.plusDays(1)
    val previous = chineseSpecialDayMap(previousDate.year)[previousDate]
    val next = chineseSpecialDayMap(nextDate.year)[nextDate]
    val hasPrevious = previous == specialDay
    val hasNext = next == specialDay
    return when {
        hasPrevious && hasNext -> CalendarRangeSegment.Middle
        hasPrevious -> CalendarRangeSegment.End
        hasNext -> CalendarRangeSegment.Start
        else -> CalendarRangeSegment.Single
    }
}

private fun List<Event>.groupByDate(): Map<LocalDate, List<Event>> =
    mapNotNull { event ->
        event.startAt.toLocalDateOrNull()?.let { date -> date to event }
    }.groupBy(
        keySelector = { it.first },
        valueTransform = { it.second },
    )

private fun String.toLocalDateOrNull(): LocalDate? =
    runCatching { java.time.OffsetDateTime.parse(this).toLocalDate() }.getOrNull()

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

internal fun chineseSpecialDayMap(year: Int): Map<LocalDate, CalendarSpecialDay> = when (year) {
    2026 -> buildMap {
        putSpecialRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), "元旦", CalendarDayKind.Rest)
        putSpecialDay(LocalDate.of(2026, 1, 4), CalendarDayKind.Work)
        putSpecialRange(LocalDate.of(2026, 2, 15), LocalDate.of(2026, 2, 23), "春节", CalendarDayKind.Rest)
        putSpecialDay(LocalDate.of(2026, 2, 14), CalendarDayKind.Work)
        putSpecialDay(LocalDate.of(2026, 2, 28), CalendarDayKind.Work)
        putSpecialRange(LocalDate.of(2026, 4, 4), LocalDate.of(2026, 4, 6), "清明", CalendarDayKind.Rest)
        putSpecialRange(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5), "劳动节", CalendarDayKind.Rest)
        putSpecialDay(LocalDate.of(2026, 5, 9), CalendarDayKind.Work)
        putSpecialRange(LocalDate.of(2026, 6, 19), LocalDate.of(2026, 6, 21), "端午", CalendarDayKind.Rest)
        putSpecialRange(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 27), "中秋", CalendarDayKind.Rest)
        putSpecialDay(LocalDate.of(2026, 9, 20), CalendarDayKind.Work)
        putSpecialRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), "国庆", CalendarDayKind.Rest)
        putSpecialDay(LocalDate.of(2026, 10, 10), CalendarDayKind.Work)
    }
    else -> emptyMap()
}

private fun MutableMap<LocalDate, CalendarSpecialDay>.putSpecialRange(
    start: LocalDate,
    endInclusive: LocalDate,
    label: String,
    kind: CalendarDayKind,
) {
    var date = start
    while (!date.isAfter(endInclusive)) {
        this[date] = CalendarSpecialDay(label = label, kind = kind)
        date = date.plusDays(1)
    }
}

private fun MutableMap<LocalDate, CalendarSpecialDay>.putSpecialDay(
    date: LocalDate,
    kind: CalendarDayKind,
) {
    this[date] = CalendarSpecialDay(
        label = when (kind) {
            CalendarDayKind.Rest -> "休"
            CalendarDayKind.Work -> "班"
        },
        kind = kind,
    )
}

internal fun calendarEventColorSlot(eventId: String): Int =
    Math.floorMod(eventId.hashCode(), CalendarEventColors.size)

private fun Event.calendarMarkerColor(): Color =
    CalendarEventColors[calendarEventColorSlot(id)]
