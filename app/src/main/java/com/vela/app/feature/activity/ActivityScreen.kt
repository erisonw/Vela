package com.vela.app.feature.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vela.app.data.repository.VelaRepository
import com.vela.app.data.time.VelaClock
import com.vela.app.di.VelaGraph
import com.vela.app.data.model.Event
import com.vela.app.data.model.EventAdvice
import com.vela.app.data.model.EventAdviceStatus
import com.vela.app.data.model.reminderLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ActivityUiState(
    val events: List<Event> = emptyList(),
    val eventAdvices: Map<String, EventAdvice> = emptyMap(),
)

class ActivityViewModel(
    private val repository: VelaRepository = VelaGraph.repository,
) : ViewModel() {

    val uiState: StateFlow<ActivityUiState> = combine(
        repository.events,
        repository.eventAdvices,
    ) { events, eventAdvices ->
        ActivityUiState(
            events = events,
            eventAdvices = eventAdvices,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ActivityUiState(),
    )
}

@Composable
fun ActivityScreen(
    dateText: String,
    onEventClick: (String) -> Unit,
    viewModel: ActivityViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val date = remember(dateText) {
        runCatching { LocalDate.parse(dateText) }
            .getOrDefault(VelaClock.today())
    }
    var selectedFilter by remember(dateText) { mutableStateOf(ActivityFilter.All) }
    val dayEvents = remember(uiState.events, date) {
        uiState.events
            .filter { it.startAt.toLocalDateOrNull() == date }
            .sortedBy { it.startAt }
    }
    val visibleEvents = remember(dayEvents, selectedFilter, uiState.eventAdvices) {
        dayEvents.filterBy(selectedFilter, uiState.eventAdvices)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ActivityBackground),
        contentPadding = PaddingValues(start = 22.dp, top = 22.dp, end = 22.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ActivityHeader(date = date, eventCount = dayEvents.size)
        }
        item {
            ActivityFilterRow(
                selectedFilter = selectedFilter,
                onSelected = { selectedFilter = it },
            )
        }
        if (visibleEvents.isEmpty()) {
            item {
                EmptyActivityCard(date = date, hasAnyEvent = dayEvents.isNotEmpty())
            }
        } else {
            items(visibleEvents, key = { it.id }) { event ->
                ActivityEventRow(
                    event = event,
                    advice = uiState.eventAdvices[event.id],
                    onClick = { onEventClick(event.id) },
                )
            }
        }
    }
}

@Composable
private fun ActivityHeader(
    date: LocalDate,
    eventCount: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Activity",
            style = MaterialTheme.typography.headlineLarge,
            color = ActivityInk,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "${date.format(DateTitleFormatter)} · ${date.weekdayText()} · $eventCount 条日程",
            style = MaterialTheme.typography.bodyMedium,
            color = ActivityMuted,
        )
    }
}

@Composable
private fun ActivityFilterRow(
    selectedFilter: ActivityFilter,
    onSelected: (ActivityFilter) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(ActivityFilter.values().toList(), key = { it.name }) { filter ->
            val selected = selectedFilter == filter
            Surface(
                modifier = Modifier.clickable { onSelected(filter) },
                shape = RoundedCornerShape(22.dp),
                color = if (selected) ActivityInk else Color.White,
                border = if (selected) {
                    null
                } else {
                    androidx.compose.foundation.BorderStroke(1.dp, ActivityLine)
                },
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    text = filter.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) Color.White else ActivityInk,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ActivityEventRow(
    event: Event,
    advice: EventAdvice?,
    onClick: () -> Unit,
) {
    val isPast = event.hasEndedAt(VelaClock.now())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (isPast) ActivityLine else ActivitySignal, CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(event.accentColor().copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = event.iconLabel(),
                style = MaterialTheme.typography.titleMedium,
                color = event.accentColor(),
                fontWeight = FontWeight.Bold,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = ActivityInk,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (isPast) "已结束" else "待开始",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ActivityMuted,
                )
            }
            Text(
                text = event.toDisplayTimeRange(),
                style = MaterialTheme.typography.bodyLarge,
                color = ActivityMuted,
            )
            event.location?.name?.takeIf { it.isNotBlank() }?.let { location ->
                Text(
                    text = location,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ActivityMuted,
                )
            }
            event.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ActivityInk,
                )
            }
            Text(
                text = "提醒 ${event.toReminderSummary()}",
                style = MaterialTheme.typography.bodySmall,
                color = ActivityMuted,
            )
            AdviceLine(advice = advice)
        }
    }
}

@Composable
private fun AdviceLine(advice: EventAdvice?) {
    val text = when {
        advice?.status == EventAdviceStatus.Ready && advice.adviceText.isNullOrBlank().not() ->
            advice.adviceText.orEmpty()
        advice?.status == EventAdviceStatus.Pending -> "AI 建议准备中"
        else -> "AI 建议待生成"
    }
    Row(
        modifier = Modifier.padding(top = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(24.dp)
                .background(ActivityLine, RoundedCornerShape(4.dp)),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (advice?.status == EventAdviceStatus.Ready) ActivityInk else ActivityMuted,
        )
    }
}

@Composable
private fun EmptyActivityCard(
    date: LocalDate,
    hasAnyEvent: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, ActivityLine),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (hasAnyEvent) "当前分类没有日程" else "这一天还没有日程",
                style = MaterialTheme.typography.titleMedium,
                color = ActivityInk,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${date.format(DateTitleFormatter)} 的日程会显示在这里。",
                style = MaterialTheme.typography.bodyMedium,
                color = ActivityMuted,
            )
        }
    }
}

private enum class ActivityFilter(val label: String) {
    All("全部"),
    Upcoming("待开始"),
    Ended("已结束"),
    WithAdvice("有建议"),
}

private fun List<Event>.filterBy(
    filter: ActivityFilter,
    advices: Map<String, EventAdvice>,
): List<Event> {
    val now = VelaClock.now()
    return when (filter) {
        ActivityFilter.All -> this
        ActivityFilter.Upcoming -> filterNot { it.hasEndedAt(now) }
        ActivityFilter.Ended -> filter { it.hasEndedAt(now) }
        ActivityFilter.WithAdvice -> filter { event ->
            advices[event.id]?.status == EventAdviceStatus.Ready &&
                advices[event.id]?.adviceText.isNullOrBlank().not()
        }
    }
}

private fun Event.hasEndedAt(now: OffsetDateTime): Boolean =
    effectiveEndAt()?.let { !it.isAfter(now) } == true

private fun Event.effectiveEndAt(): OffsetDateTime? =
    (endAt ?: startAt).toOffsetDateTimeOrNull()

private fun Event.toDisplayTimeRange(): String {
    val start = startAt.toDisplayTime()
    val end = endAt?.toDisplayTime()?.takeIf { it.isNotBlank() }
    return if (end == null) start else "$start - $end"
}

private fun Event.toReminderSummary(): String =
    reminders.firstOrNull()?.let { reminder ->
        reminder.label ?: reminderLabel(reminder.minutesBefore)
    } ?: "无"

private fun Event.iconLabel(): String = when (categoryLabel()) {
    "工作" -> "工"
    "健康" -> "动"
    "生活" -> "生"
    "学习" -> "学"
    else -> "历"
}

private fun Event.categoryLabel(): String {
    val text = listOfNotNull(title, location?.name, description).joinToString(" ")
    return when {
        text.contains("面试") || text.contains("会议") || text.contains("工作") -> "工作"
        text.contains("健身") || text.contains("跑步") || text.contains("运动") -> "健康"
        text.contains("礼物") || text.contains("购物") || text.contains("生活") -> "生活"
        text.contains("课") || text.contains("学习") -> "学习"
        else -> "日程"
    }
}

private fun Event.accentColor(): Color = when (categoryLabel()) {
    "工作" -> Color(0xFF111111)
    "健康" -> Color(0xFF18A66A)
    "生活" -> Color(0xFF7C3AED)
    "学习" -> Color(0xFF2563EB)
    else -> Color(0xFF111111)
}

private fun String.toLocalDateOrNull(): LocalDate? =
    toOffsetDateTimeOrNull()?.toLocalDate()

private fun String.toOffsetDateTimeOrNull(): OffsetDateTime? =
    runCatching { OffsetDateTime.parse(this) }.getOrNull()

private fun String.toDisplayTime(): String =
    substringAfter("T", this).take(5)

private fun LocalDate.weekdayText(): String =
    "周" + when (dayOfWeek.value) {
        1 -> "一"
        2 -> "二"
        3 -> "三"
        4 -> "四"
        5 -> "五"
        6 -> "六"
        else -> "日"
    }

private val DateTitleFormatter = DateTimeFormatter.ofPattern("M 月 d 日", Locale.CHINA)
private val ActivityBackground = Color(0xFFFCFCFD)
private val ActivityInk = Color(0xFF111111)
private val ActivityMuted = Color(0xFF8A8A8F)
private val ActivityLine = Color(0xFFE4E4E7)
private val ActivitySignal = Color(0xFFFF2D68)
