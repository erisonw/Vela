package com.vela.app.feature.calendar

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vela.app.data.model.Event
import com.vela.app.data.repository.VelaRepository
import com.vela.app.data.time.VelaClock
import com.vela.app.di.VelaGraph
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.absoluteValue

class CalendarViewModel(
    private val repository: VelaRepository = VelaGraph.repository,
) : ViewModel() {

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
    var selectedDate by remember { mutableStateOf(VelaClock.today()) }
    var monthDirection by remember { mutableStateOf(1) }
    var pendingNavigation by remember { mutableStateOf<PendingDateNavigation?>(null) }
    var isCalendarExpanded by remember { mutableStateOf(true) }
    val eventsByDate = remember(events) { events.groupByDate() }
    val selectedEventCount = eventsByDate[selectedDate].orEmpty().size
    val selectedSpecialDay = chineseSpecialDayMap(selectedDate.year)[selectedDate]

    LaunchedEffect(pendingNavigation) {
        val pending = pendingNavigation ?: return@LaunchedEffect
        delay(CalendarMotionSpec.TapFeedbackMillis)
        pendingNavigation = null
        onActivityDateClick(pending.date)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VelaPageBackground)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CalendarTopBar(
            selectedDate = selectedDate,
            monthDirection = monthDirection,
            isExpanded = isCalendarExpanded,
            onExpandedChange = { isCalendarExpanded = it },
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            MonthCalendarGrid(
                modifier = Modifier.fillMaxSize(),
                availableHeight = maxHeight,
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

        CompactSelectedDateSummary(
            selectedDate = selectedDate,
            eventCount = selectedEventCount,
            holiday = selectedSpecialDay?.summaryLabel,
            onScheduleClick = { onActivityDateClick(selectedDate) },
        )
    }
}

@Composable
private fun MonthCalendarGrid(
    modifier: Modifier = Modifier,
    availableHeight: Dp,
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
        modifier = modifier
            .fillMaxWidth()
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
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
                val monthCells = remember(month, eventsByDate) {
                    buildMonthCells(month, eventsByDate)
                }
                val layoutProfile = remember(availableHeight, monthCells.size, isExpanded) {
                    calendarGridLayoutProfile(
                        availableHeight = availableHeight,
                        rowCount = monthCells.size,
                        isExpanded = isExpanded,
                    )
                }
                val rowGap by animateDpAsState(
                    targetValue = layoutProfile.rowGap,
                    animationSpec = tween(CalendarMotionSpec.ExpandTransitionMillis),
                    label = "calendar-grid-row-gap",
                )
                val cellHeight by animateDpAsState(
                    targetValue = layoutProfile.cellHeight,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                    label = "calendar-grid-cell-height",
                )
                val detailAlpha by animateFloatAsState(
                    targetValue = if (isExpanded) 1f else 0f,
                    animationSpec = tween(CalendarMotionSpec.ExpandTransitionMillis),
                    label = "calendar-grid-detail-alpha",
                )
                Column(verticalArrangement = Arrangement.spacedBy(rowGap)) {
                    monthCells.forEach { week ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            week.forEach { cell ->
                                CalendarDayCell(
                                    cell = cell,
                                    cellHeight = cellHeight,
                                    selectedDate = selectedDate,
                                    isNavigationPending = isNavigationPending,
                                    isExpanded = isExpanded,
                                    detailAlpha = detailAlpha,
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
private fun CalendarTopBar(
    selectedDate: LocalDate,
    monthDirection: Int,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val transitionDirection = monthDirection.takeIf { it != 0 } ?: 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable { onExpandedChange(!isExpanded) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isExpanded) "⌃" else "⌄",
                color = VelaTextPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = selectedDate.withDayOfMonth(1),
                transitionSpec = { monthSlideTransition(transitionDirection) },
                label = "compact-month-title",
            ) { month ->
                Text(
                    text = "${month.monthValue}月",
                    style = MaterialTheme.typography.headlineLarge,
                    color = VelaTextPrimary,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = selectedDate.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = VelaTextPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun RowScope.CalendarDayCell(
    cell: CalendarDateCell,
    cellHeight: Dp,
    selectedDate: LocalDate,
    isNavigationPending: Boolean,
    isExpanded: Boolean,
    detailAlpha: Float,
    onDateSelected: (LocalDate) -> Unit,
) {
    val date = cell.date
    val isSelected = date == selectedDate
    val isToday = date == VelaClock.today()
    val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    val contentAlpha = if (cell.isCurrentMonth) 1f else 0.2f
    val cellBgColor = when {
        isSelected -> VelaPrimaryBlue.copy(alpha = 0.12f)
        cell.specialDay?.kind == CalendarDayKind.Rest -> VelaWarmRed.copy(alpha = if (isExpanded) 0.035f else 0.02f)
        else -> Color.White.copy(alpha = 0.84f)
    }

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
            .padding(horizontal = 0.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(23.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(if (isToday) 23.dp else 20.dp)
                    .background(if (isToday) VelaPrimaryBlue else Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
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
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            if (isExpanded) {
                Text(
                    modifier = Modifier.alpha(detailAlpha * contentAlpha),
                    text = cell.lunarLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, lineHeight = 9.sp),
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
                cell.events.take(3).forEach { event ->
                    CalendarEventBlock(
                        event = event,
                        alpha = detailAlpha * contentAlpha,
                    )
                }
                if (cell.events.size > 3) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(detailAlpha * contentAlpha),
                        text = "+${cell.events.size - 3}",
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
private fun CalendarEventBlock(
    event: Event,
    alpha: Float,
) {
    val markerColor = event.calendarMarkerColor()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .alpha(alpha),
        shape = RoundedCornerShape(5.dp),
        color = markerColor.copy(alpha = 0.78f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = event.startAt.toDisplayTime(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = Color.White.copy(alpha = 0.82f),
                maxLines = 1,
            )
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

private data class CalendarGridLayoutProfile(
    val rowGap: Dp,
    val cellHeight: Dp,
)

private fun calendarGridLayoutProfile(
    availableHeight: Dp,
    rowCount: Int,
    isExpanded: Boolean,
): CalendarGridLayoutProfile {
    val normalizedRowCount = rowCount.coerceIn(
        minimumValue = CalendarGridLayout.MinRows,
        maximumValue = CalendarGridLayout.MaxRows,
    )
    val rowGap = if (isExpanded) {
        CalendarGridLayout.ExpandedRowGap
    } else {
        CalendarGridLayout.CompactRowGap
    }
    if (!isExpanded) {
        return CalendarGridLayoutProfile(
            rowGap = rowGap,
            cellHeight = CalendarGridLayout.compactCellHeight(normalizedRowCount),
        )
    }
    val reservedHeight = CalendarGridLayout.WeekdayHeaderHeight +
        CalendarGridLayout.WeekdayToGridGap +
        rowGap * (normalizedRowCount - 1)
    val rawHeight = (availableHeight - reservedHeight) / normalizedRowCount
    return CalendarGridLayoutProfile(
        rowGap = rowGap,
        cellHeight = rawHeight.coerceIn(
            minimumValue = CalendarGridLayout.ExpandedMinCellHeight,
            maximumValue = CalendarGridLayout.ExpandedMaxCellHeight,
        ),
    )
}

private object CalendarGridLayout {
    const val DaysPerWeek = 7
    const val MinRows = 5
    const val MaxRows = 6

    val CompactRowGap = 2.dp
    val ExpandedRowGap = 5.dp
    val WeekdayHeaderHeight = 24.dp
    val WeekdayToGridGap = 8.dp
    val CompactFiveRowCellHeight = 52.dp
    val CompactSixRowCellHeight = 52.dp
    val ExpandedMinCellHeight = 58.dp
    val ExpandedMaxCellHeight = 112.dp

    fun compactCellHeight(rowCount: Int): Dp = when (rowCount) {
        MinRows -> CompactFiveRowCellHeight
        else -> CompactSixRowCellHeight
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
private fun CompactSelectedDateSummary(
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .shadow(8.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = VelaSoftLavender,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedContent(
                modifier = Modifier.weight(1f),
                targetState = summaryState,
                transitionSpec = { summarySlideTransition() },
                label = "compact-selected-date",
            ) { state ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = state.selectedDate.dayOfMonth.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = VelaTextPrimary,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "${state.selectedDate.dayOfWeek.toChineseLabel()} · ${state.selectedDate.getLunarDayString()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VelaTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = state.summaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = VelaTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
    val lunarLabel: String,
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
    val rowCount = calendarMonthRowCount(
        leadingDays = leadingDays,
        daysInMonth = firstDay.lengthOfMonth(),
    )
    val gridStart = firstDay.minusDays(leadingDays.toLong())
    val cells = buildList {
        repeat(rowCount * CalendarGridLayout.DaysPerWeek) { offset ->
            val date = gridStart.plusDays(offset.toLong())
            val specialDay = chineseSpecialDayMap(date.year)[date]
            add(
                CalendarDateCell(
                    date = date,
                    isCurrentMonth = date.month == selectedDate.month && date.year == selectedDate.year,
                    lunarLabel = date.getLunarDayString(),
                    events = eventsByDate[date].orEmpty().sortedBy { it.startAt },
                    specialDay = specialDay,
                    segment = specialDay?.let { rangeSegmentFor(date, it) } ?: CalendarRangeSegment.Single,
                ),
            )
        }
    }
    return cells.chunked(CalendarGridLayout.DaysPerWeek)
}

private fun calendarMonthRowCount(
    leadingDays: Int,
    daysInMonth: Int,
): Int =
    ((leadingDays + daysInMonth + CalendarGridLayout.DaysPerWeek - 1) / CalendarGridLayout.DaysPerWeek)
        .coerceIn(
            minimumValue = CalendarGridLayout.MinRows,
            maximumValue = CalendarGridLayout.MaxRows,
        )

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

private fun String.toDisplayTime(): String =
    substringAfter("T", this).take(5)

private fun DayOfWeek.toChineseLabel(): String =
    when (this) {
        DayOfWeek.MONDAY -> "周一"
        DayOfWeek.TUESDAY -> "周二"
        DayOfWeek.WEDNESDAY -> "周三"
        DayOfWeek.THURSDAY -> "周四"
        DayOfWeek.FRIDAY -> "周五"
        DayOfWeek.SATURDAY -> "周六"
        DayOfWeek.SUNDAY -> "周日"
    }

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
