package com.vela.app.feature.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vela.app.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vela.app.data.mock.MockVelaRepository
import com.vela.app.data.model.Event
import com.vela.app.data.model.UserPreferences
import com.vela.app.data.model.WidgetSnapshot
import com.vela.app.data.model.reminderLabel
import com.vela.app.notification.NotificationPermissionState
import com.vela.app.ui.EventEditorDialog
import com.vela.app.widget.VelaWidgetUpdater
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

data class HomeUiState(
    val events: List<Event> = emptyList(),
    val widgetSnapshot: WidgetSnapshot? = null,
    val userPreferences: UserPreferences = UserPreferences(),
)

class HomeViewModel : ViewModel() {
    private val repository = MockVelaRepository

    val uiState: StateFlow<HomeUiState> = combine(
        repository.events,
        repository.widgetSnapshot,
        repository.userPreferences,
    ) { events, widgetSnapshot, userPreferences ->
        HomeUiState(
            events = events,
            widgetSnapshot = widgetSnapshot,
            userPreferences = userPreferences,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun addEvent(event: Event) {
        repository.addEvent(event)
    }

    fun updateEvent(event: Event) {
        repository.updateEvent(event)
    }

    fun deleteEvent(eventId: String) {
        repository.deleteEvent(eventId)
    }
}

@Composable
fun HomeScreen(
    eventId: String?,
    onImportChatClick: () -> Unit,
    onCalendarClick: () -> Unit,
    onEventClick: (String) -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val homeTimeline = buildHomeTimeline(uiState.events)
    val selectedEvent = eventId?.let { selectedId ->
        uiState.events.firstOrNull { it.id == selectedId }
    }
    var isCreatingEvent by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<Event?>(null) }
    var deletingEvent by remember { mutableStateOf<Event?>(null) }
    var showTomorrowPreview by remember { mutableStateOf(false) }
    var notificationPermissionDenied by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationPermissionDenied = !granted
    }
    val tomorrowPreview = remember(uiState.events, uiState.widgetSnapshot) {
        buildTomorrowPreview(
            events = uiState.events,
            weatherHint = uiState.widgetSnapshot?.weatherHint ?: "天气待获取",
        )
    }
    val hasReminders = uiState.events.any { it.reminders.isNotEmpty() }
    val shouldShowNotificationWarning =
        notificationPermissionDenied ||
            (hasReminders && !NotificationPermissionState.canPostNotifications(context))

    fun requestNotificationPermissionIfNeeded(event: Event) {
        if (event.reminders.isNotEmpty() && NotificationPermissionState.needsRuntimePermission(context)) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(tomorrowPreview.autoShowKey, tomorrowPreview.shouldAutoShow) {
        if (tomorrowPreview.shouldAutoShow &&
            !TomorrowPreviewStore.wasShownToday(context, tomorrowPreview.autoShowKey)
        ) {
            TomorrowPreviewStore.markShownToday(context, tomorrowPreview.autoShowKey)
            showTomorrowPreview = true
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(VelaPageBackground),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
            item {
                ScheduleHero(
                    onCreateClick = { isCreatingEvent = true },
                    onImportChatClick = onImportChatClick,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { showTomorrowPreview = true },
                    ) {
                        Text(text = "明日预告")
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onCalendarClick,
                    ) {
                        Text(text = "日历")
                    }
                }
                    if (shouldShowNotificationWarning) {
                        Spacer(modifier = Modifier.height(10.dp))
                        NotificationPermissionCard()
                    }
            }

            if (eventId != null) {
                item {
                    if (selectedEvent == null) {
                        MissingEventCard(eventId = eventId)
                    } else {
                        EventDetailCard(
                            event = selectedEvent,
                            onEdit = { editingEvent = selectedEvent },
                            onDelete = { deletingEvent = selectedEvent },
                        )
                    }
                }
            }

            item {
                uiState.widgetSnapshot?.let { snapshot ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(10.dp, RoundedCornerShape(24.dp)),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF3F6FF),
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = snapshot.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = VelaTextPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = snapshot.nextEvent?.title ?: "暂无后续日程",
                                style = MaterialTheme.typography.bodyLarge,
                                color = VelaTextPrimary,
                            )
                            Text(
                                text = "剩余日程 ${snapshot.remainingEventCount} 个 | 待确认候选 ${snapshot.pendingCandidateCount} 个",
                                style = MaterialTheme.typography.bodySmall,
                                color = VelaTextSecondary,
                            )
                            Text(
                                text = "${snapshot.weatherHint} | ${snapshot.prepHint}",
                                style = MaterialTheme.typography.bodySmall,
                                color = VelaTextSecondary,
                            )
                            Text(
                                text = snapshot.weatherStatusText,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF6476FF),
                            )
                            Text(
                                text = "${snapshot.generatedAt.toDisplayTime()} 更新",
                                style = MaterialTheme.typography.labelMedium,
                                color = VelaTextSecondary,
                            )
                        }
                    }
                }
            }

            if (uiState.events.isEmpty()) {
                item {
                    HomeSectionHeader(
                        title = "即将开始",
                        subtitle = "未来安排会优先显示在这里",
                    )
                }
                item {
                    EmptyHomeCard(onImportChatClick = onImportChatClick)
                }
            } else {
                item {
                    HomeSectionHeader(
                        title = "今天 ${LocalDate.now(ZoneId.of("Asia/Shanghai")).monthValue}月${LocalDate.now(ZoneId.of("Asia/Shanghai")).dayOfMonth}日",
                        subtitle = if (homeTimeline.upcomingEvents.isEmpty()) {
                            "今天后面没有未完成日程"
                        } else {
                            "${homeTimeline.upcomingEvents.size} 个日程"
                        },
                    )
                }
                if (homeTimeline.upcomingEvents.isEmpty()) {
                    item {
                        EmptyUpcomingCard(onCalendarClick = onCalendarClick)
                    }
                } else {
                    items(homeTimeline.upcomingEvents, key = { it.id }) { event ->
                        EventRow(
                            event = event,
                            isPast = false,
                            onClick = { onEventClick(event.id) },
                        )
                    }
                }

                if (homeTimeline.completedEvents.isNotEmpty()) {
                    item {
                        HomeSectionHeader(
                            title = "已结束",
                            subtitle = "${homeTimeline.completedEvents.size.coerceAtMost(3)} 个日程",
                        )
                    }
                    items(homeTimeline.completedEvents.take(3), key = { "past-${it.id}" }) { event ->
                        EventRow(
                            event = event,
                            isPast = true,
                            onClick = { onEventClick(event.id) },
                        )
                    }
                }
            }
    }

    if (isCreatingEvent) {
        EventEditorDialog(
            title = "新建日程",
            confirmText = "添加",
            initialEvent = null,
            defaultReminderMinutes = uiState.userPreferences.defaultReminderMinutes,
            onDismiss = { isCreatingEvent = false },
            onSave = { event ->
                viewModel.addEvent(event)
                requestNotificationPermissionIfNeeded(event)
                coroutineScope.launch {
                    VelaWidgetUpdater.updateAll(context)
                }
                isCreatingEvent = false
            },
        )
    }

    editingEvent?.let { event ->
        EventEditorDialog(
            title = "编辑日程",
            confirmText = "保存",
            initialEvent = event,
            onDismiss = { editingEvent = null },
            onSave = { updatedEvent ->
                viewModel.updateEvent(updatedEvent)
                requestNotificationPermissionIfNeeded(updatedEvent)
                coroutineScope.launch {
                    VelaWidgetUpdater.updateAll(context)
                }
                editingEvent = null
            },
        )
    }

    deletingEvent?.let { event ->
        DeleteEventDialog(
            event = event,
            onDismiss = { deletingEvent = null },
            onConfirm = {
                viewModel.deleteEvent(event.id)
                coroutineScope.launch {
                    VelaWidgetUpdater.updateAll(context)
                }
                deletingEvent = null
            },
        )
    }

    if (showTomorrowPreview) {
        TomorrowPreviewDialog(
            preview = tomorrowPreview,
            onDismiss = { showTomorrowPreview = false },
        )
    }
}

@Composable
private fun ScheduleHero(
    onCreateClick: () -> Unit,
    onImportChatClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .shadow(8.dp, RoundedCornerShape(18.dp)),
                painter = painterResource(id = R.mipmap.ic_launcher),
                contentDescription = "Vela",
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "日程",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = VelaTextPrimary,
                )
                Text(
                    text = "管理您的日程安排，高效规划每一天",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VelaTextSecondary,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                onClick = onCreateClick,
                shape = RoundedCornerShape(27.dp),
            ) {
                Text(text = "创建日程")
            }
            OutlinedButton(
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                onClick = onImportChatClick,
                shape = RoundedCornerShape(27.dp),
            ) {
                Text(text = "AI 日程")
            }
        }
    }
}

@Composable
private fun EventDetailCard(
    event: Event,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "日程详情",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "时间：${event.toDisplayDateTimeRange()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            event.location?.name?.let { locationName ->
                Text(
                    text = "地点：$locationName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                text = "提醒：${event.toReminderSummary()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = if (event.sourceSessionId == null) "来源：手动新建" else "来源：导入确认",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            event.description?.let { description ->
                Text(
                    text = "备注：$description",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit) {
                    Text(text = "编辑")
                }
                TextButton(onClick = onDelete) {
                    Text(text = "删除")
                }
            }
        }
    }
}

@Composable
private fun MissingEventCard(eventId: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "未找到日程",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "本地没有找到 ID 为 $eventId 的日程，可能已经被删除。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeleteEventDialog(
    event: Event,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "删除日程") },
        text = { Text(text = "确定删除「${event.title}」吗？删除后会同步更新日程、日历和小组件。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        },
    )
}

@Composable
private fun HomeSectionHeader(
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(Color(0xFFEAF0FF), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "历", color = Color(0xFF2D6BFF), style = MaterialTheme.typography.labelSmall)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = VelaTextPrimary,
            fontWeight = FontWeight.Bold,
        )
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFEAF0FF),
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF6476FF),
            )
        }
    }
}

@Composable
private fun EventRow(
    event: Event,
    isPast: Boolean,
    onClick: () -> Unit,
) {
    val accentColor = event.accentColor()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPast) {
                Color(0xFFF4F6FB)
            } else {
                Color.White
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(82.dp)
                    .background(accentColor, RoundedCornerShape(12.dp)),
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(accentColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = event.iconLabel(),
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = event.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VelaTextPrimary,
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accentColor.copy(alpha = 0.10f),
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            text = event.categoryLabel(),
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "时间  ${event.toDisplayTimeRange()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VelaTextSecondary,
                )
                event.location?.name?.let { locationName ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "地点  $locationName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VelaTextSecondary,
                    )
                }
            }
            Text(text = "⋮", color = VelaTextSecondary, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun EmptyUpcomingCard(onCalendarClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "暂无未完成日程",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "今天的安排已经结束，可以查看日历确认明天的计划。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onCalendarClick) {
                Text(text = "查看日历")
            }
        }
    }
}

@Composable
private fun EmptyHomeCard(onImportChatClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "还没有日程",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "可以先本地新建日程，也可以到 AI 日程里解析候选。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onImportChatClick) {
                Text(text = "去导入")
            }
        }
    }
}

@Composable
private fun NotificationPermissionCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(
            modifier = Modifier.padding(12.dp),
            text = "通知权限未开启，日程会保存，但系统提醒可能无法弹出。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun TomorrowPreviewDialog(
    preview: TomorrowPreviewInfo,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "明日预告") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = preview.summary)
                Text(text = preview.timeRange)
                Text(text = preview.densityHint)
                Text(text = preview.locationHint)
                Text(text = preview.weatherHint)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "知道了")
            }
        },
    )
}

private data class TomorrowPreviewInfo(
    val autoShowKey: String,
    val shouldAutoShow: Boolean,
    val summary: String,
    val timeRange: String,
    val densityHint: String,
    val locationHint: String,
    val weatherHint: String,
)

private data class HomeTimeline(
    val upcomingEvents: List<Event>,
    val completedEvents: List<Event>,
)

private fun buildHomeTimeline(events: List<Event>): HomeTimeline {
    val now = OffsetDateTime.now(ZoneId.of("Asia/Shanghai"))
    val sortedEvents = events.sortedBy {
        it.startAt.toOffsetDateTimeOrNull()?.toEpochSecond() ?: Long.MAX_VALUE
    }
    return HomeTimeline(
        upcomingEvents = sortedEvents.filterNot { it.hasEndedAt(now) },
        completedEvents = sortedEvents
            .filter { it.hasEndedAt(now) }
            .sortedByDescending {
                it.effectiveEndAt()?.toEpochSecond() ?: Long.MIN_VALUE
            },
    )
}

private fun Event.hasEndedAt(now: OffsetDateTime): Boolean =
    effectiveEndAt()?.let { !it.isAfter(now) } == true

private fun Event.effectiveEndAt(): OffsetDateTime? =
    (endAt ?: startAt).toOffsetDateTimeOrNull()

private fun Event.toDisplayTimeRange(): String {
    val start = startAt.toDisplayTime()
    val end = endAt?.toDisplayTime()?.takeIf { it.isNotBlank() }
    return if (end == null) start else "$start-$end"
}

private fun Event.toDisplayDateTimeRange(): String {
    val start = "${startAt.toDisplayDate()} ${startAt.toDisplayTime()}"
    val end = endAt?.let { "${it.toDisplayDate()} ${it.toDisplayTime()}" }
    return if (end == null) start else "$start - $end"
}

private fun Event.toReminderSummary(): String =
    reminders.firstOrNull()?.let { reminder ->
        reminder.label ?: reminderLabel(reminder.minutesBefore)
    } ?: "无提醒"

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

private fun Event.iconLabel(): String = when (categoryLabel()) {
    "工作" -> "包"
    "健康" -> "动"
    "生活" -> "礼"
    "学习" -> "书"
    else -> "历"
}

private fun Event.accentColor(): Color = when (categoryLabel()) {
    "工作" -> Color(0xFF2D6BFF)
    "健康" -> Color(0xFF2BC96F)
    "生活" -> Color(0xFF8B5CF6)
    "学习" -> Color(0xFF3BA7FF)
    else -> Color(0xFF6476FF)
}

private fun buildTomorrowPreview(
    events: List<Event>,
    weatherHint: String,
): TomorrowPreviewInfo {
    val zoneId = ZoneId.of("Asia/Shanghai")
    val now = OffsetDateTime.now(zoneId)
    val today = now.toLocalDate()
    val tomorrow = today.plusDays(1)
    val todayEvents = events.filter { it.startAt.toLocalDateOrNull() == today }
    val tomorrowEvents = events
        .filter { it.startAt.toLocalDateOrNull() == tomorrow }
        .sortedBy { it.startAt }
    val todayEnded = todayEvents.isNotEmpty() && todayEvents.all { event ->
        val endAt = event.endAt?.toOffsetDateTimeOrNull()
            ?: event.startAt.toOffsetDateTimeOrNull()
        endAt != null && !endAt.isAfter(now)
    }
    val first = tomorrowEvents.firstOrNull()
    val last = tomorrowEvents.lastOrNull()
    val summary = if (tomorrowEvents.isEmpty()) {
        "明天暂时没有日程。"
    } else {
        "明天共有 ${tomorrowEvents.size} 个日程。"
    }
    val timeRange = when {
        first == null || last == null -> "时间：暂无安排"
        first.id == last.id -> "时间：${first.startAt.toDisplayTime()} 开始"
        else -> "时间：${first.startAt.toDisplayTime()} 到 ${last.startAt.toDisplayTime()}"
    }
    val densityHint = when {
        tomorrowEvents.size >= 4 -> "安排较密集，建议提前留出缓冲。"
        hasTightSchedule(tomorrowEvents) -> "存在连续日程，注意衔接时间。"
        tomorrowEvents.isNotEmpty() -> "安排较清晰，暂未发现明显冲突。"
        else -> "可以把明天的重要事项提前补进日历。"
    }
    val locationHint = tomorrowEvents
        .mapNotNull { it.location?.name }
        .distinct()
        .take(3)
        .joinToString("、")
        .ifBlank { "地点：暂无关键地点" }
        .let { if (it.startsWith("地点")) it else "地点：$it" }

    return TomorrowPreviewInfo(
        autoShowKey = today.toString(),
        shouldAutoShow = todayEnded,
        summary = summary,
        timeRange = timeRange,
        densityHint = densityHint,
        locationHint = locationHint,
        weatherHint = "天气：$weatherHint",
    )
}

private fun hasTightSchedule(events: List<Event>): Boolean =
    events.zipWithNext().any { (left, right) ->
        val leftEnd = left.endAt?.toOffsetDateTimeOrNull() ?: left.startAt.toOffsetDateTimeOrNull()
        val rightStart = right.startAt.toOffsetDateTimeOrNull()
        leftEnd != null && rightStart != null &&
            !leftEnd.plusMinutes(30).isBefore(rightStart)
    }

private fun String.toLocalDateOrNull(): LocalDate? =
    toOffsetDateTimeOrNull()?.toLocalDate()

private fun String.toOffsetDateTimeOrNull(): OffsetDateTime? =
    runCatching { OffsetDateTime.parse(this) }.getOrNull()

private fun String.toDisplayDate(): String = substringBefore("T")

private fun String.toDisplayTime(): String = substringAfter("T", this)
    .take(5)

private val VelaPageBackground = Color(0xFFFAFBFF)
private val VelaTextPrimary = Color(0xFF12162A)
private val VelaTextSecondary = Color(0xFF72788A)
