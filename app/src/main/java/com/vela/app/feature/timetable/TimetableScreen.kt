package com.vela.app.feature.timetable

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vela.app.data.model.Event
import com.vela.app.data.repository.VelaRepository
import com.vela.app.di.VelaGraph
import com.vela.app.notification.NotificationPermissionState
import com.vela.app.ui.EventEditorDialog
import com.vela.app.widget.VelaWidgetUpdater
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

class TimetableViewModel(
    private val repository: VelaRepository = VelaGraph.repository,
) : ViewModel() {
    val uiState: StateFlow<TimetableUiState> = combine(
        repository.events,
        repository.userPreferences,
    ) { events, preferences ->
        TimetableUiState(
            groups = buildTimetableGroups(events),
            defaultReminderMinutes = preferences.defaultReminderMinutes,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TimetableUiState(),
        )

    fun addCourse(event: Event) {
        repository.addEvent(event.copy(isCourse = true))
    }
}

@Composable
fun TimetableScreen(
    onEventClick: (String) -> Unit,
    viewModel: TimetableViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val groups = uiState.groups
    val courseCount = groups.sumOf { it.courses.size }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isAddingCourse by remember { mutableStateOf(false) }
    var notificationPermissionDenied by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationPermissionDenied = !granted
    }
    val showPermissionWarning = notificationPermissionDenied ||
        groups.any { group -> group.courses.any { it.event.reminders.isNotEmpty() } } &&
        (!NotificationPermissionState.canPostNotifications(context) ||
            NotificationPermissionState.needsExactAlarmPermission(context))

    fun requestReminderPermissionsIfNeeded(event: Event) {
        if (event.reminders.isEmpty()) return
        when {
            NotificationPermissionState.needsRuntimePermission(context) ->
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            NotificationPermissionState.needsExactAlarmPermission(context) ->
                NotificationPermissionState.requestExactAlarmPermission(context)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(TimetablePageBackground),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TimetableHeader(
                courseCount = courseCount,
                groupCount = groups.size,
                onAddCourseClick = { isAddingCourse = true },
            )
        }

        if (showPermissionWarning) {
            item {
                TimetablePermissionCard(
                    onExactAlarmClick = {
                        NotificationPermissionState.requestExactAlarmPermission(context)
                    },
                )
            }
        }

        if (groups.isEmpty()) {
            item {
                EmptyTimetableCard()
            }
        } else {
            items(groups, key = { it.time }) { group ->
                TimetableTimeSection(
                    group = group,
                    onEventClick = onEventClick,
                )
            }
        }
    }

    if (isAddingCourse) {
        EventEditorDialog(
            title = "添加课程",
            confirmText = "添加",
            initialEvent = null,
            defaultReminderMinutes = uiState.defaultReminderMinutes,
            onDismiss = { isAddingCourse = false },
            onSave = { event ->
                val course = event.copy(isCourse = true)
                viewModel.addCourse(course)
                requestReminderPermissionsIfNeeded(course)
                coroutineScope.launch {
                    VelaWidgetUpdater.updateAll(context)
                }
                isAddingCourse = false
            },
        )
    }
}

@Composable
private fun TimetableHeader(
    courseCount: Int,
    groupCount: Int,
    onAddCourseClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "课表",
                    style = MaterialTheme.typography.titleLarge,
                    color = TimetableTextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "按开始时间汇总课程",
                    style = MaterialTheme.typography.bodySmall,
                    color = TimetableTextSecondary,
                )
            }
            Surface(
                modifier = Modifier.clickable(onClick = onAddCourseClick),
                shape = RoundedCornerShape(16.dp),
                color = TimetableTextPrimary,
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    text = "添加",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimetableMetricChip(label = "$groupCount 个时间段")
            TimetableMetricChip(label = "$courseCount 门课程")
        }
    }
}

@Composable
private fun TimetableMetricChip(label: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White,
        tonalElevation = 0.dp,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TimetableTextSecondary,
        )
    }
}

@Composable
private fun TimetableTimeSection(
    group: TimetableTimeGroup,
    onEventClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = group.time,
                style = MaterialTheme.typography.titleMedium,
                color = TimetableTextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                text = "${group.courses.size} 门课",
                style = MaterialTheme.typography.labelSmall,
                color = TimetableTextSecondary,
            )
        }
        group.courses.forEach { course ->
            TimetableCourseRow(
                course = course,
                onClick = { onEventClick(course.event.id) },
            )
        }
    }
}

@Composable
private fun TimetableCourseRow(
    course: TimetableCourse,
    onClick: () -> Unit,
) {
    val color = course.event.courseColor()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(52.dp)
                    .background(color, RoundedCornerShape(999.dp)),
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(color.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = course.event.title.firstOrNull()?.toString() ?: "课",
                    style = MaterialTheme.typography.titleSmall,
                    color = color,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = course.event.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = TimetableTextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = color.copy(alpha = 0.12f),
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            text = course.weekday,
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Text(
                    text = course.meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = TimetableTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyTimetableCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "暂无课程",
                style = MaterialTheme.typography.titleMedium,
                color = TimetableTextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "点击右上角添加课程后，这里会按开始时间汇总。",
                style = MaterialTheme.typography.bodySmall,
                color = TimetableTextSecondary,
            )
        }
    }
}

@Composable
private fun TimetablePermissionCard(
    onExactAlarmClick: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = "课程提醒权限需要确认",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (NotificationPermissionState.needsExactAlarmPermission(context)) {
                TextButton(onClick = onExactAlarmClick) {
                    Text(text = "开启")
                }
            }
        }
    }
}

data class TimetableUiState(
    val groups: List<TimetableTimeGroup> = emptyList(),
    val defaultReminderMinutes: Int? = null,
)

data class TimetableTimeGroup(
    val time: String,
    val courses: List<TimetableCourse>,
)

data class TimetableCourse(
    val event: Event,
    val start: OffsetDateTime,
    val end: OffsetDateTime?,
) {
    val weekday: String = start.toWeekdayText()
    val meta: String = buildString {
        append("${start.monthValue}月${start.dayOfMonth}日")
        end?.let {
            append(" · ")
            append(start.toDisplayTime())
            append("-")
            append(it.toDisplayTime())
        } ?: run {
            append(" · ")
            append(start.toDisplayTime())
        }
        event.location?.name?.takeIf { it.isNotBlank() }?.let {
            append(" · ")
            append(it)
        }
    }
}

fun buildTimetableGroups(events: List<Event>): List<TimetableTimeGroup> =
    events
        .mapNotNull { event ->
            val start = event.startAt.toOffsetDateTimeOrNull() ?: return@mapNotNull null
            TimetableCourse(
                event = event,
                start = start,
                end = event.endAt?.toOffsetDateTimeOrNull(),
            )
        }
        .filter { it.event.isCourse }
        .sortedWith(compareBy<TimetableCourse> { it.start.toLocalTime() }.thenBy { it.start.toLocalDate() })
        .groupBy { it.start.toDisplayTime() }
        .map { (time, courses) ->
            TimetableTimeGroup(
                time = time,
                courses = courses.sortedBy { it.start.toEpochSecond() },
            )
        }

private fun Event.courseColor(): Color =
    CourseColors[Math.floorMod(id.hashCode(), CourseColors.size)]

private fun String.toOffsetDateTimeOrNull(): OffsetDateTime? =
    runCatching { OffsetDateTime.parse(this) }.getOrNull()

private fun OffsetDateTime.toDisplayTime(): String =
    toLocalTime().toString().take(5)

private fun OffsetDateTime.toWeekdayText(): String =
    "周" + when (dayOfWeek.value) {
        1 -> "一"
        2 -> "二"
        3 -> "三"
        4 -> "四"
        5 -> "五"
        6 -> "六"
        else -> "日"
    }

private val TimetablePageBackground = Color(0xFFFAFBFF)
private val TimetableTextPrimary = Color(0xFF12162A)
private val TimetableTextSecondary = Color(0xFF72788A)
private val TimetableBlue = Color(0xFF2D6BFF)
private val CourseColors = listOf(
    Color(0xFFC6A62D),
    Color(0xFFD8892F),
    Color(0xFF2D6BFF),
    Color(0xFF8B5CF6),
    Color(0xFF17A86B),
    Color(0xFFE45757),
)
