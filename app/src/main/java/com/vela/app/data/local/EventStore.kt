package com.vela.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.vela.app.data.model.DefaultReminderMinutes
import com.vela.app.data.model.Event
import com.vela.app.data.model.EventAdvice
import com.vela.app.data.model.UserPreferences
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface VelaDataStore {
    fun loadEvents(): List<Event>?

    fun saveEvents(events: List<Event>)

    fun loadEventAdvices(): Map<String, EventAdvice>

    fun saveEventAdvices(advices: Collection<EventAdvice>)

    fun loadUserPreferences(): UserPreferences

    fun saveUserPreferences(preferences: UserPreferences)
}

/**
 * SharedPreferences 本地持久化。只负责读写，不做业务逻辑。
 * 后续替换为 Room/DataStore 时，只需要改这一个类。
 */
class EventStore(context: Context) : VelaDataStore {
    private val sharedPreferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    override fun loadEvents(): List<Event>? {
        val storedEvents = sharedPreferences.getString(EventsKey, null) ?: return null
        return EventStoreCodec.decodeEvents(storedEvents)
    }

    override fun saveEvents(events: List<Event>) {
        sharedPreferences.edit()
            .putString(EventsKey, EventStoreCodec.encodeEvents(events))
            .apply()
    }

    override fun loadEventAdvices(): Map<String, EventAdvice> {
        val storedAdvices = sharedPreferences.getString(EventAdvicesKey, null) ?: return emptyMap()
        return EventStoreCodec.decodeEventAdvices(storedAdvices)
    }

    override fun saveEventAdvices(advices: Collection<EventAdvice>) {
        sharedPreferences.edit()
            .putString(EventAdvicesKey, EventStoreCodec.encodeEventAdvices(advices))
            .apply()
    }

    override fun loadUserPreferences(): UserPreferences {
        val storedReminder = sharedPreferences.getInt(DefaultReminderKey, DefaultReminderMinutes)
        val aiEndpoint = sharedPreferences.getString(AiEndpointKey, "").orEmpty()
        val aiTextModel = sharedPreferences.getString(AiTextModelKey, "").orEmpty()
        return UserPreferences(
            defaultReminderMinutes = storedReminder.takeIf { it != NoReminderValue },
            aiServiceStatusText = aiServiceStatusText(aiEndpoint, aiTextModel),
            aiEndpoint = aiEndpoint,
            aiApiKey = sharedPreferences.getString(AiApiKeyKey, "").orEmpty(),
            aiTextModel = aiTextModel,
            aiVisionModel = sharedPreferences.getString(AiVisionModelKey, "").orEmpty(),
            aiVoiceModel = sharedPreferences.getString(AiVoiceModelKey, "").orEmpty(),
            weatherLatitude = sharedPreferences.getDoubleOrNull(WeatherLatitudeKey),
            weatherLongitude = sharedPreferences.getDoubleOrNull(WeatherLongitudeKey),
            weatherUpdatedAt = sharedPreferences.getString(WeatherUpdatedAtKey, null),
        )
    }

    override fun saveUserPreferences(preferences: UserPreferences) {
        sharedPreferences.edit()
            .putInt(DefaultReminderKey, preferences.defaultReminderMinutes ?: NoReminderValue)
            .putString(AiEndpointKey, preferences.aiEndpoint)
            .putString(AiApiKeyKey, preferences.aiApiKey)
            .putString(AiTextModelKey, preferences.aiTextModel)
            .putString(AiVisionModelKey, preferences.aiVisionModel)
            .putString(AiVoiceModelKey, preferences.aiVoiceModel)
            .putNullableDouble(WeatherLatitudeKey, preferences.weatherLatitude)
            .putNullableDouble(WeatherLongitudeKey, preferences.weatherLongitude)
            .putString(WeatherUpdatedAtKey, preferences.weatherUpdatedAt)
            .apply()
    }

    private fun SharedPreferences.getDoubleOrNull(key: String): Double? =
        if (contains(key)) {
            Double.fromBits(getLong(key, 0L))
        } else {
            null
        }

    private fun SharedPreferences.Editor.putNullableDouble(
        key: String,
        value: Double?,
    ): SharedPreferences.Editor =
        if (value == null) {
            remove(key)
        } else {
            putLong(key, value.toBits())
        }

    companion object {
        private const val PreferencesName = "vela_local_store"
        private const val EventsKey = "events_json"
        private const val EventAdvicesKey = "event_advices_json"
        private const val DefaultReminderKey = "default_reminder_minutes"
        private const val WeatherLatitudeKey = "weather_latitude"
        private const val WeatherLongitudeKey = "weather_longitude"
        private const val WeatherUpdatedAtKey = "weather_updated_at"
        private const val AiEndpointKey = "ai_endpoint"
        private const val AiApiKeyKey = "ai_api_key"
        private const val AiTextModelKey = "ai_text_model"
        private const val AiVisionModelKey = "ai_vision_model"
        private const val AiVoiceModelKey = "ai_voice_model"
        private const val NoReminderValue = -1

        fun aiServiceStatusText(endpoint: String, textModel: String): String =
            if (endpoint.isBlank() || textModel.isBlank()) {
                "未接入真实 AI 服务"
            } else {
                "已配置 AI 服务，文本模型：$textModel"
            }
    }
}

internal object EventStoreCodec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encodeEvents(events: List<Event>): String =
        json.encodeToString(events)

    fun decodeEvents(value: String): List<Event>? =
        runCatching {
            json.decodeFromString<List<Event>>(value)
        }.getOrNull()

    fun encodeEventAdvices(advices: Collection<EventAdvice>): String =
        json.encodeToString(advices.toList())

    fun decodeEventAdvices(value: String): Map<String, EventAdvice> =
        runCatching {
            json.decodeFromString<List<EventAdvice>>(value)
                .associateBy { it.eventId }
        }.getOrDefault(emptyMap())
}
