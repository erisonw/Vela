package com.vela.app.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.vela.app.data.mock.MockVelaRepository
import com.vela.app.data.model.Event
import com.vela.app.data.model.WidgetSnapshot
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class VelaTodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        MockVelaRepository.initialize(context)
        val snapshot = MockVelaRepository.widgetSnapshot.value

        provideContent {
            VelaTodayWidgetContent(
                context = context,
                snapshot = snapshot,
            )
        }
    }
}

@Composable
private fun VelaTodayWidgetContent(
    context: Context,
    snapshot: WidgetSnapshot,
) {
    val plan = snapshot.toWidgetPlan()

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetColors.Background)
            .clickable(actionStartActivity(deepLinkIntent(context, "vela://home"))),
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DatePanel(
                context = context,
                plan = plan,
                pendingCount = snapshot.pendingCandidateCount,
            )

            Spacer(modifier = GlanceModifier.width(6.dp))

            SchedulePanel(
                context = context,
                plan = plan,
            )
        }
    }
}

@Composable
private fun DatePanel(
    context: Context,
    plan: WidgetPlan,
    pendingCount: Int,
) {
    Column(
        modifier = GlanceModifier
            .width(88.dp)
            .fillMaxHeight(),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = plan.dayOfMonth,
                style = TextStyle(
                    color = WidgetColors.OnBackground,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            Column {
                Text(
                    text = plan.monthText,
                    style = TextStyle(
                        color = WidgetColors.OnMuted,
                        fontSize = 12.sp,
                    ),
                )
                Text(
                    text = plan.lunarText,
                    style = TextStyle(
                        color = WidgetColors.Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(4.dp))

        Text(
            text = plan.scopeLabel,
            style = TextStyle(
                color = WidgetColors.OnMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
        )

        Spacer(modifier = GlanceModifier.height(5.dp))

        WeatherSlot(text = plan.weatherHint)

        Spacer(modifier = GlanceModifier.defaultWeight())

        FooterHint(text = plan.prepHint)

        Spacer(modifier = GlanceModifier.defaultWeight())

        ImportEntry(
            context = context,
            pendingCount = pendingCount,
        )
    }
}

@Composable
private fun WeatherSlot(text: String) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(WidgetColors.Weather)
            .cornerRadius(6.dp)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "天气",
            style = TextStyle(
                color = WidgetColors.OnWeather,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(modifier = GlanceModifier.width(4.dp))
        Text(
            text = text,
            style = TextStyle(
                color = WidgetColors.OnWeather,
                fontSize = 10.sp,
            ),
        )
    }
}

@Composable
private fun FooterHint(text: String) {
    Text(
        text = text,
        style = TextStyle(
            color = WidgetColors.OnMuted,
            fontSize = 10.sp,
        ),
    )
}

@Composable
private fun ImportEntry(
    context: Context,
    pendingCount: Int,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(WidgetColors.ImportButton)
            .cornerRadius(10.dp)
            .clickable(actionStartActivity(deepLinkIntent(context, "vela://import/chat")))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Text(
            text = "AI 日程",
            style = TextStyle(
                color = WidgetColors.OnImportButton,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            text = if (pendingCount > 0) "$pendingCount 条待确认" else "快速添加",
            style = TextStyle(
                color = WidgetColors.OnImportButton,
                fontSize = 10.sp,
            ),
        )
    }
}

@Composable
private fun SchedulePanel(
    context: Context,
    plan: WidgetPlan,
) {
    Column(
        modifier = GlanceModifier
            .width(218.dp)
            .fillMaxHeight(),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = GlanceModifier.defaultWeight(),
                text = plan.listTitle,
                style = TextStyle(
                    color = WidgetColors.OnBackground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = if (plan.events.isEmpty()) "暂无" else "最近 ${plan.events.size} 条",
                style = TextStyle(
                    color = WidgetColors.OnMuted,
                    fontSize = 10.sp,
                ),
            )
        }

        Spacer(modifier = GlanceModifier.height(4.dp))

        if (plan.events.isEmpty()) {
            EmptySchedule(plan = plan)
        } else {
            plan.events.forEachIndexed { index, event ->
                EventStrip(
                    context = context,
                    event = event,
                    color = WidgetColors.eventAccent(index),
                )
                if (index < plan.events.lastIndex) {
                    Spacer(modifier = GlanceModifier.height(3.dp))
                }
            }
            Spacer(modifier = GlanceModifier.height(3.dp))
            Text(
                text = plan.footerText,
                style = TextStyle(
                    color = WidgetColors.OnMuted,
                    fontSize = 10.sp,
                ),
            )
        }

    }
}

@Composable
private fun EmptySchedule(plan: WidgetPlan) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(WidgetColors.EmptyCard)
            .padding(12.dp),
    ) {
        Text(
            text = plan.footerText,
            style = TextStyle(
                color = WidgetColors.OnBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = if (plan.scopeLabel == "今天") "今天已收尾，打开日程看明日预告" else "可以从 AI 日程或新建补充",
            style = TextStyle(
                color = WidgetColors.OnMuted,
                fontSize = 12.sp,
            ),
        )
    }
}

@Composable
private fun EventStrip(
    context: Context,
    event: Event,
    color: ColorProvider,
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(WidgetColors.EventCard)
            .cornerRadius(5.dp)
            .clickable(actionStartActivity(deepLinkIntent(context, "vela://event/${event.id}")))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .width(4.dp)
                .height(33.dp)
                .background(color),
        ) {}

        Spacer(modifier = GlanceModifier.width(6.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = event.title,
                style = TextStyle(
                    color = color,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(modifier = GlanceModifier.height(1.dp))
            Text(
                text = event.toTimeRange(),
                style = TextStyle(
                    color = WidgetColors.OnMuted,
                    fontSize = 11.sp,
                ),
            )
        }
    }
}

private fun WidgetSnapshot.toWidgetPlan(): WidgetPlan {
    val now = generatedAt.toOffsetDateTime()
    val allEvents = upcomingEvents.ifEmpty { todayEvents }
        .sortedBy { it.startAt }
    val today = now.toLocalDate()
    val todayEvents = allEvents.filter { it.startAt.toOffsetDateTime().toLocalDate() == today }
    val todayHasUnfinishedEvents = todayEvents.any { event ->
        val endAt = event.endAt?.toOffsetDateTime() ?: event.startAt.toOffsetDateTime()
        endAt.isAfter(now)
    }
    val targetDate = if (todayHasUnfinishedEvents) today else today.plusDays(1)
    val allTargetEvents = allEvents
        .filter { event -> event.startAt.toOffsetDateTime().toLocalDate() == targetDate }
        .filter { event ->
            if (targetDate != today) {
                true
            } else {
                val endAt = event.endAt?.toOffsetDateTime() ?: event.startAt.toOffsetDateTime()
                endAt.isAfter(now)
            }
        }
    val targetEvents = allTargetEvents
        .take(3)
    val hiddenCount = (allTargetEvents.size - targetEvents.size).coerceAtLeast(0)

    return WidgetPlan(
        targetDate = targetDate,
        scopeLabel = if (targetDate == today) "今天" else "明天",
        listTitle = if (targetDate == today) "今天日程" else "明天日程",
        monthText = "${targetDate.monthValue}月",
        dayOfMonth = targetDate.dayOfMonth.toString(),
        lunarText = "初二",
        events = targetEvents,
        weatherHint = weatherHint,
        prepHint = prepHint,
        footerText = when {
            hiddenCount > 0 -> "共 ${allTargetEvents.size} 个日程\n还有 $hiddenCount 条未显示"
            targetEvents.isNotEmpty() -> "下一场 ${targetEvents.first().startAt.toOffsetDateTime().format(TimeFormatter)} 开始"
            targetDate == today -> "今天安排已清空"
            else -> "明天暂无日程"
        },
    )
}

private fun String.toOffsetDateTime(): OffsetDateTime = OffsetDateTime.parse(this)

private fun Event.toTimeRange(): String {
    val start = startAt.toOffsetDateTime().format(TimeFormatter)
    val end = endAt?.toOffsetDateTime()?.format(TimeFormatter)
    val locationText = location?.name
    return listOfNotNull(
        if (end == null) start else "$start-$end",
        locationText,
    ).joinToString(" | ")
}

private fun deepLinkIntent(context: Context, uri: String): Intent = Intent(
    Intent.ACTION_VIEW,
    Uri.parse(uri),
).apply {
    setPackage(context.packageName)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

private data class WidgetPlan(
    val targetDate: LocalDate,
    val scopeLabel: String,
    val listTitle: String,
    val monthText: String,
    val dayOfMonth: String,
    val lunarText: String,
    val events: List<Event>,
    val weatherHint: String,
    val prepHint: String,
    val footerText: String,
)

private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

private object WidgetColors {
    val Background = ColorProvider(Color(0xFF181A1F))
    val OnBackground = ColorProvider(Color(0xFFF7F3EE))
    val OnMuted = ColorProvider(Color(0xFFB7B2AA))
    val Accent = ColorProvider(Color(0xFFFF5A5F))
    val Weather = ColorProvider(Color(0xFF26313A))
    val OnWeather = ColorProvider(Color(0xFFE8F6FF))
    val EmptyCard = ColorProvider(Color(0xFF24262D))
    val EventCard = ColorProvider(Color(0xFF24201B))
    val ImportButton = ColorProvider(Color(0xFF5068A8))
    val OnImportButton = ColorProvider(Color(0xFFFFFFFF))

    fun eventAccent(index: Int): ColorProvider = when (index % 3) {
        0 -> ColorProvider(Color(0xFFFFD84D))
        1 -> ColorProvider(Color(0xFFFF4FD8))
        else -> ColorProvider(Color(0xFFFFB13B))
    }
}
