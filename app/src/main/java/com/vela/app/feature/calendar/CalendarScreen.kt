package com.vela.app.feature.calendar

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
    onScheduleClick: () -> Unit,
    viewModel: CalendarViewModel = viewModel(),
) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    var selectedDate by remember { mutableStateOf(LocalDate.now(ZoneId.of("Asia/Shanghai"))) }
    val eventCounts = remember(events) { events.countByDate() }
    val holidayMap = remember(selectedDate.year) { chineseHolidayMap(selectedDate.year) }
    val selectedEventCount = eventCounts[selectedDate].orZero()
    val selectedHoliday = holidayMap[selectedDate]

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
                subtitle = "显示公历、节假日和日程高亮",
            )
        }

        item {
            MonthCalendarGrid(
                selectedDate = selectedDate,
                eventCounts = eventCounts,
                holidays = holidayMap,
                onDateSelected = { selectedDate = it },
                onMonthSwipe = { monthOffset ->
                    selectedDate = selectedDate.plusMonths(monthOffset)
                },
            )
        }

        item {
            SelectedDateSummary(
                selectedDate = selectedDate,
                eventCount = selectedEventCount,
                holiday = selectedHoliday,
                onScheduleClick = onScheduleClick,
            )
        }
    }
}

@Composable
private fun MonthCalendarGrid(
    selectedDate: LocalDate,
    eventCounts: Map<LocalDate, Int>,
    holidays: Map<LocalDate, String>,
    onDateSelected: (LocalDate) -> Unit,
    onMonthSwipe: (Long) -> Unit,
) {
    var dragAmount by remember(selectedDate) { mutableStateOf(0f) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, MaterialTheme.shapes.large)
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
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = "${selectedDate.year} 年 ${selectedDate.monthValue} 月",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "左右滑动",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                WeekdayLabels.forEach { label ->
                    Text(
                        modifier = Modifier.weight(1f),
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            buildMonthCells(selectedDate, eventCounts, holidays).forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    week.forEach { cell ->
                        CalendarDayCell(
                            cell = cell,
                            selectedDate = selectedDate,
                            onDateSelected = onDateSelected,
                        )
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
    onDateSelected: (LocalDate) -> Unit,
) {
    val date = cell.date
    val isSelected = date == selectedDate
    val isToday = date == LocalDate.now(ZoneId.of("Asia/Shanghai"))
    val clickModifier = if (date == null) {
        Modifier
    } else {
        Modifier.clickable { onDateSelected(date) }
    }

    Surface(
        modifier = Modifier
            .weight(1f)
            .height(52.dp)
            .then(clickModifier),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        color = when {
            isSelected -> VelaPrimaryBlue
            else -> Color.Transparent
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = date?.dayOfMonth?.toString().orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) {
                    Color.White
                } else if (isToday) {
                    VelaPrimaryBlue
                } else {
                    VelaTextPrimary
                },
            )
            cell.holiday?.let { holiday ->
                Text(
                    text = holiday,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) Color.White.copy(alpha = 0.88f) else VelaWarmRed,
                    maxLines = 1,
                )
            }
            if (cell.eventCount > 0) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) {
                        Color.White
                    } else {
                        VelaPrimaryBlue
                    },
                )
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
                    .background(Color.White, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "历", color = VelaPrimaryBlue, fontWeight = FontWeight.Bold)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = "${selectedDate.monthValue} 月 ${selectedDate.dayOfMonth} 日",
                    style = MaterialTheme.typography.titleMedium,
                    color = VelaTextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = buildString {
                        append(if (eventCount > 0) "日程安排：$eventCount 条" else "日程安排：暂无")
                        if (holiday != null) {
                            append(" · ")
                            append(holiday)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = VelaTextSecondary,
                )
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
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = VelaTextSecondary,
        )
    }
}

private val VelaPageBackground = Color(0xFFFAFBFF)
private val VelaPrimaryBlue = Color(0xFF2D6BFF)
private val VelaTextPrimary = Color(0xFF12162A)
private val VelaTextSecondary = Color(0xFF72788A)
private val VelaSoftLavender = Color(0xFFF1F0FF)
private val VelaWarmRed = Color(0xFFE45757)

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
