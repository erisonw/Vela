package com.vela.app.data.weather

import com.vela.app.data.model.Event
import kotlinx.serialization.SerialName
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class WeatherHint(
    val weatherHint: String,
    val prepHint: String,
    val statusText: String,
    val weatherCode: Int? = null,
    val temperature: Double? = null,
    val riskType: WeatherRiskType = WeatherRiskType.None,
)

enum class WeatherRiskType {
    None,
    Rain,
    Snow,
    Thunderstorm,
    Heat,
    Cold,
    Fog,
}

object WeatherHintProvider {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun forCoordinates(latitude: Double?, longitude: Double?): WeatherHint {
        if (latitude == null || longitude == null) {
            return WeatherHint(
                weatherHint = "定位后获取天气",
                prepHint = "授权定位后生成天气提醒",
                statusText = "未获得定位权限，天气待获取",
                riskType = WeatherRiskType.None,
            )
        }

        var result: WeatherHint? = null
        val worker = thread(name = "vela-weather") {
            result = requestOpenMeteo(latitude, longitude)
        }
        worker.join()
        return result ?: WeatherHint(
            weatherHint = "天气待获取",
            prepHint = "天气服务连接失败",
            statusText = "Open-Meteo 暂未返回天气，稍后可重试",
            riskType = WeatherRiskType.None,
        )
    }

    fun withEventPrepHint(
        weatherHint: WeatherHint,
        events: List<Event>,
    ): WeatherHint {
        if (weatherHint.riskType == WeatherRiskType.None) {
            return weatherHint
        }
        val sensitiveEvents = events.filter { it.isWeatherSensitive() }
        if (sensitiveEvents.isEmpty()) {
            return weatherHint
        }
        val eventText = sensitiveEvents
            .take(2)
            .joinToString("、") { it.title }
        val prepText = when (weatherHint.riskType) {
            WeatherRiskType.Rain -> "$eventText 可能受雨天影响，建议带伞并确认出行时间"
            WeatherRiskType.Thunderstorm -> "$eventText 可能受雷雨影响，建议减少户外停留"
            WeatherRiskType.Snow -> "$eventText 可能受雨雪影响，注意保暖和路面"
            WeatherRiskType.Heat -> "$eventText 遇到高温，建议防晒补水"
            WeatherRiskType.Cold -> "$eventText 遇到低温，建议加衣保暖"
            WeatherRiskType.Fog -> "$eventText 可能受雾天影响，出行留出余量"
            WeatherRiskType.None -> weatherHint.prepHint
        }
        return weatherHint.copy(prepHint = prepText)
    }

    private fun requestOpenMeteo(latitude: Double, longitude: Double): WeatherHint? =
        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,weather_code" +
                "&timezone=auto"
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
            }
            val responseCode = connection.responseCode
            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            if (responseCode !in 200..299) {
                return null
            }
            val response = json.decodeFromString<OpenMeteoResponse>(responseText)
            val current = response.current ?: return null
            val weatherText = weatherCodeText(current.weatherCode)
            val riskType = current.toRiskType()
            WeatherHint(
                weatherHint = "${current.temperature}°C $weatherText",
                prepHint = weatherPrepHint(riskType),
                statusText = "天气来自 Open-Meteo，按当前定位获取",
                weatherCode = current.weatherCode,
                temperature = current.temperature,
                riskType = riskType,
            )
        }.getOrNull()

    private fun weatherCodeText(code: Int?): String = when (code) {
        0 -> "晴"
        1, 2, 3 -> "多云"
        45, 48 -> "有雾"
        51, 53, 55, 56, 57 -> "小雨"
        61, 63, 65, 66, 67, 80, 81, 82 -> "有雨"
        71, 73, 75, 77, 85, 86 -> "有雪"
        95, 96, 99 -> "雷雨"
        else -> "天气已获取"
    }

    private fun weatherPrepHint(riskType: WeatherRiskType): String = when (riskType) {
        WeatherRiskType.Rain -> "可能下雨，出门前确认雨具"
        WeatherRiskType.Thunderstorm -> "可能雷雨，尽量减少户外停留"
        WeatherRiskType.Snow -> "可能有雪，注意保暖和路面"
        WeatherRiskType.Heat -> "气温偏高，注意防晒和补水"
        WeatherRiskType.Cold -> "气温偏低，注意保暖"
        WeatherRiskType.Fog -> "可能有雾，出行留出余量"
        else -> "天气无明显风险"
    }

    private fun OpenMeteoCurrent.toRiskType(): WeatherRiskType {
        val codeRisk = when (weatherCode) {
            45, 48 -> WeatherRiskType.Fog
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> WeatherRiskType.Rain
            71, 73, 75, 77, 85, 86 -> WeatherRiskType.Snow
            95, 96, 99 -> WeatherRiskType.Thunderstorm
            else -> WeatherRiskType.None
        }
        return when {
            codeRisk != WeatherRiskType.None -> codeRisk
            temperature != null && temperature >= 32.0 -> WeatherRiskType.Heat
            temperature != null && temperature <= 3.0 -> WeatherRiskType.Cold
            else -> WeatherRiskType.None
        }
    }

    private fun Event.isWeatherSensitive(): Boolean {
        val text = listOfNotNull(title, location?.name, description)
            .joinToString(" ")
        val keywords = listOf(
            "户外",
            "跑步",
            "运动",
            "公园",
            "球",
            "骑行",
            "徒步",
            "露营",
            "旅行",
            "出行",
            "通勤",
            "机场",
            "车站",
            "高铁",
            "公交",
            "地铁",
            "步行",
            "散步",
            "看展",
        )
        return keywords.any { keyword -> text.contains(keyword, ignoreCase = true) }
    }
}

@kotlinx.serialization.Serializable
private data class OpenMeteoResponse(
    val current: OpenMeteoCurrent? = null,
)

@kotlinx.serialization.Serializable
private data class OpenMeteoCurrent(
    @SerialName("temperature_2m")
    val temperature: Double? = null,
    @SerialName("weather_code")
    val weatherCode: Int? = null,
)
