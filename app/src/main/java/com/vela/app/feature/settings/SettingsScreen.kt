package com.vela.app.feature.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vela.app.data.mock.MockVelaRepository
import com.vela.app.data.model.UserPreferences
import com.vela.app.ui.ReminderSelector
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel : ViewModel() {
    private val repository = MockVelaRepository
    val preferences: StateFlow<UserPreferences> = repository.userPreferences

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

    fun updateAiDocumentModel(model: String) {
        updateAiConfig(documentModel = model)
    }

    fun updateAiVoiceModel(model: String) {
        updateAiConfig(voiceModel = model)
    }

    private fun updateAiConfig(
        endpoint: String = preferences.value.aiEndpoint,
        apiKey: String = preferences.value.aiApiKey,
        textModel: String = preferences.value.aiTextModel,
        visionModel: String = preferences.value.aiVisionModel,
        documentModel: String = preferences.value.aiDocumentModel,
        voiceModel: String = preferences.value.aiVoiceModel,
    ) {
        repository.updateAiServiceConfig(
            endpoint = endpoint,
            apiKey = apiKey,
            textModel = textModel,
            visionModel = visionModel,
            documentModel = documentModel,
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
    val context = LocalContext.current
    var locationMessage by remember { mutableStateOf<String?>(null) }
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
                    description = "兼容中转平台的 OpenAI Chat Completions 接口。文本、图片、文档可分别配置模型；语音入口暂未接入录音转写。",
                ) {
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
                        value = preferences.aiDocumentModel,
                        onValueChange = viewModel::updateAiDocumentModel,
                        label = { Text(text = "文档解析模型") },
                        placeholder = { Text(text = "例如 gemini-2.5-flash") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = preferences.aiVoiceModel,
                        onValueChange = viewModel::updateAiVoiceModel,
                        label = { Text(text = "语音转文字模型（预留）") },
                        placeholder = { Text(text = "例如 whisper / asr 模型") },
                        singleLine = true,
                    )
                    Text(
                        text = preferences.aiServiceStatusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
    return "已用当前定位刷新天气。"
}

private fun Context.hasLocationPermission(): Boolean =
    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

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
