package com.vela.app.feature.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vela.app.data.ai.AiLocalProxyConfig
import com.vela.app.data.ai.AiServiceConnectionReport
import com.vela.app.data.ai.AiServiceConnectionTester
import com.vela.app.data.model.UserPreferences
import com.vela.app.data.repository.VelaRepository
import com.vela.app.di.VelaGraph
import com.vela.app.feature.floating.FloatingImportService
import com.vela.app.notification.NotificationPermissionState
import com.vela.app.ui.ReminderSelector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AiConnectionUiState(
    val isChecking: Boolean = false,
    val report: AiServiceConnectionReport? = null,
)

class SettingsViewModel(
    private val repository: VelaRepository = VelaGraph.repository,
) : ViewModel() {
    val preferences: StateFlow<UserPreferences> = repository.userPreferences
    private val _aiConnectionState = MutableStateFlow(AiConnectionUiState())
    val aiConnectionState: StateFlow<AiConnectionUiState> = _aiConnectionState.asStateFlow()

    fun updateDefaultReminder(minutesBefore: Int?) {
        repository.updateDefaultReminderMinutes(minutesBefore)
    }

    fun updateWeatherLocation(latitude: Double, longitude: Double) {
        repository.updateWeatherLocation(latitude = latitude, longitude = longitude)
    }

    fun updateAiEndpoint(endpoint: String) {
        updateAiConfig(endpoint = endpoint)
    }

    fun updateAiApiKey(apiKey: String) {
        updateAiConfig(apiKey = apiKey)
    }

    fun updateAiTextModel(model: String) {
        updateAiConfig(textModel = model)
    }

    fun updateAiVisionModel(model: String) {
        updateAiConfig(visionModel = model)
    }

    fun updateAiVoiceModel(model: String) {
        updateAiConfig(voiceModel = model)
    }

    fun applyLocalProxyConfig() {
        updateAiConfig(
            endpoint = AiLocalProxyConfig.Endpoint,
            apiKey = AiLocalProxyConfig.ApiKey,
            textModel = AiLocalProxyConfig.TextModel,
            visionModel = AiLocalProxyConfig.VisionModel,
            voiceModel = AiLocalProxyConfig.VoiceModel,
        )
    }

    fun testAiConnection() {
        viewModelScope.launch {
            _aiConnectionState.value = AiConnectionUiState(isChecking = true)
            val report = AiServiceConnectionTester.test(preferences.value)
            _aiConnectionState.value = AiConnectionUiState(report = report)
        }
    }

    private fun updateAiConfig(
        endpoint: String = preferences.value.aiEndpoint,
        apiKey: String = preferences.value.aiApiKey,
        textModel: String = preferences.value.aiTextModel,
        visionModel: String = preferences.value.aiVisionModel,
        voiceModel: String = preferences.value.aiVoiceModel,
    ) {
        _aiConnectionState.value = AiConnectionUiState()
        repository.updateAiServiceConfig(
            endpoint = endpoint,
            apiKey = apiKey,
            textModel = textModel,
            visionModel = visionModel,
            voiceModel = voiceModel,
        )
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val aiConnectionState by viewModel.aiConnectionState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var locationMessage by remember { mutableStateOf<String?>(null) }
    var floatingMessage by remember { mutableStateOf<String?>(null) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    var permissionRefreshKey by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionRefreshKey += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            locationMessage = updateWeatherFromDeviceLocation(context, viewModel)
        } else {
            locationMessage = "未获得定位权限，天气无法自动获取。"
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionMessage = if (granted) {
            "通知权限已开启。"
        } else {
            "通知权限未开启，日程提醒不会显示。"
        }
        permissionRefreshKey += 1
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionMessage = if (granted) {
            "麦克风权限已开启。"
        } else {
            "麦克风权限未开启，语音输入不可用。"
        }
        permissionRefreshKey += 1
    }
    permissionRefreshKey
    val canPostNotifications = NotificationPermissionState.canPostNotifications(context)
    val canScheduleExactAlarms = NotificationPermissionState.canScheduleExactAlarms(context)
    val canRecordAudio = context.hasAudioPermission()
    val canDrawOverlays = AndroidSettings.canDrawOverlays(context)

    fun requestOrUpdateWeatherLocation() {
        if (context.hasLocationPermission()) {
            locationMessage = updateWeatherFromDeviceLocation(context, viewModel)
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "设置") },
                navigationIcon = {
                    TextButton(onClick = onBackClick) {
                        Text(text = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsSection(
                    title = "提醒偏好",
                    description = "新建日程和导入候选会默认使用这里的提醒时间。",
                ) {
                    ReminderSelector(
                        selectedMinutes = preferences.defaultReminderMinutes,
                        onSelected = viewModel::updateDefaultReminder,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                SettingsSection(
                    title = "AI 服务",
                    description = "兼容中转平台的 OpenAI Chat Completions 和 Audio Transcriptions 接口。语音会先转文字，再复用文本解析。",
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = viewModel::applyLocalProxyConfig,
                    ) {
                        Text(text = "使用本机代理测试配置")
                    }
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = preferences.aiEndpoint,
                        onValueChange = viewModel::updateAiEndpoint,
                        label = { Text(text = "Base URL") },
                        placeholder = { Text(text = "https://api.example.com/v1") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = preferences.aiApiKey,
                        onValueChange = viewModel::updateAiApiKey,
                        label = { Text(text = "AI API Key") },
                        placeholder = { Text(text = "可留空") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = preferences.aiTextModel,
                        onValueChange = viewModel::updateAiTextModel,
                        label = { Text(text = "文本解析模型") },
                        placeholder = { Text(text = "例如 gpt-4o-mini / deepseek-chat") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = preferences.aiVisionModel,
                        onValueChange = viewModel::updateAiVisionModel,
                        label = { Text(text = "图片识别模型") },
                        placeholder = { Text(text = "例如 gemini-2.5-flash") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = preferences.aiVoiceModel,
                        onValueChange = viewModel::updateAiVoiceModel,
                        label = { Text(text = "语音转文字模型") },
                        placeholder = { Text(text = "例如 whisper-1 / gpt-4o-mini-transcribe") },
                        singleLine = true,
                    )
                    Text(
                        text = preferences.aiServiceStatusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !aiConnectionState.isChecking,
                        onClick = viewModel::testAiConnection,
                    ) {
                        Text(
                            text = if (aiConnectionState.isChecking) {
                                "正在检查连接..."
                            } else {
                                "检查 AI 服务连接"
                            },
                        )
                    }
                    aiConnectionState.report?.let { report ->
                        Text(
                            text = report.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (report.isReachable) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                        Text(
                            text = listOf(
                                report.textStatus,
                                report.visionStatus,
                                report.voiceStatus,
                            ).joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                SettingsSection(
                    title = "权限状态",
                    description = "内部测试前请确认提醒、精确闹钟、语音和悬浮窗权限。",
                ) {
                    Text(
                        text = "通知：${if (canPostNotifications) "可用" else "未开启"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (NotificationPermissionState.needsRuntimePermission(context)) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                context.openNotificationSettings()
                            }
                        },
                    ) {
                        Text(text = if (canPostNotifications) "打开通知设置" else "开启通知权限")
                    }
                    Text(
                        text = "精确闹钟：${if (canScheduleExactAlarms) "可用" else "未授权，将使用非精确提醒"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (!canScheduleExactAlarms) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                NotificationPermissionState.requestExactAlarmPermission(context)
                            },
                        ) {
                            Text(text = "开启精确闹钟权限")
                        }
                    }
                    Text(
                        text = "麦克风：${if (canRecordAudio) "可用" else "未开启"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (!canRecordAudio) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                        ) {
                            Text(text = "开启麦克风权限")
                        }
                    }
                    Text(
                        text = "悬浮窗：${if (canDrawOverlays) "可用" else "未开启"}；截屏识别会由系统逐次确认。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    permissionMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                SettingsSection(
                    title = "天气服务",
                    description = "天气默认使用 Open-Meteo，通过定位权限获取经纬度，不需要手动输入地址。",
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = ::requestOrUpdateWeatherLocation,
                    ) {
                        Text(text = "授权并更新定位天气")
                    }
                    Text(
                        text = preferences.weatherCoordinateText(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    locationMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                SettingsSection(
                    title = "悬浮窗导入",
                    description = "通过悬浮窗发起截图识别，候选日程确认后才会写入。",
                ) {
                    if (canDrawOverlays) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                context.startFloatingImportService()
                                floatingMessage = "悬浮窗已启动。"
                            },
                        ) {
                            Text(text = "启动悬浮窗")
                        }
                    } else {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                context.openOverlayPermissionSettings()
                                floatingMessage = "开启权限后返回设置页启动悬浮窗。"
                            },
                        ) {
                            Text(text = "开启悬浮窗权限")
                        }
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                        onClick = {},
                    ) {
                        Text(text = "识别屏幕（实验，暂未开放）")
                    }
                    Text(
                        text = floatingMessage ?: "当前仅支持截图识别，不读取屏幕文本。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SettingsSection(
                    title = "隐私说明",
                    description = "当前日程保存在本机；AI 和天气未接入时，不会上传你的文本、图片或位置。",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "本地优先",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "失败不伪造结果",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

private fun updateWeatherFromDeviceLocation(
    context: Context,
    viewModel: SettingsViewModel,
): String {
    val location = context.findLastKnownLocation()
        ?: return "暂时没有可用定位，请确认系统定位已开启后重试。"
    viewModel.updateWeatherLocation(
        latitude = location.latitude,
        longitude = location.longitude,
    )
    return "已保存当前定位，天气正在后台刷新。"
}

private fun Context.hasLocationPermission(): Boolean =
    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun Context.hasAudioPermission(): Boolean =
    checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")
private fun Context.findLastKnownLocation(): Location? {
    if (!hasLocationPermission()) {
        return null
    }
    val locationManager = getSystemService(LocationManager::class.java)
    return listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    ).mapNotNull { provider ->
        runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
    }.maxByOrNull { it.time }
}

private fun Context.openOverlayPermissionSettings() {
    val intent = Intent(
        AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:$packageName"),
    )
    startActivity(intent)
}

private fun Context.openNotificationSettings() {
    val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(AndroidSettings.EXTRA_APP_PACKAGE, packageName)
    }
    runCatching {
        startActivity(intent)
    }.onFailure {
        startActivity(
            Intent(
                AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName"),
            ),
        )
    }
}

private fun Context.startFloatingImportService() {
    val intent = Intent(this, FloatingImportService::class.java)
    startForegroundService(intent)
}

private fun UserPreferences.weatherCoordinateText(): String {
    val latitude = weatherLatitude
    val longitude = weatherLongitude
    return if (latitude == null || longitude == null) {
        "尚未获取定位，日程和小组件会显示天气待获取。"
    } else {
        val updatedText = weatherUpdatedAt?.substringBefore("T")?.let { "，更新于 $it" }.orEmpty()
        "已保存定位：${latitude.formatCoordinate()}, ${longitude.formatCoordinate()}$updatedText"
    }
}

private fun Double.formatCoordinate(): String =
    String.format(java.util.Locale.CHINA, "%.4f", this)

@Composable
private fun SettingsSection(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}
