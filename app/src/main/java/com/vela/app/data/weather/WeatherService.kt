package com.vela.app.data.weather

import com.vela.app.data.model.Event

interface WeatherService {
    fun pendingForCoordinates(latitude: Double?, longitude: Double?): WeatherHint

    fun forCoordinates(latitude: Double?, longitude: Double?): WeatherHint

    fun withEventPrepHint(weatherHint: WeatherHint, events: List<Event>): WeatherHint
}

object DefaultWeatherService : WeatherService {
    override fun pendingForCoordinates(
        latitude: Double?,
        longitude: Double?,
    ): WeatherHint =
        WeatherHintProvider.pendingForCoordinates(latitude, longitude)

    override fun forCoordinates(
        latitude: Double?,
        longitude: Double?,
    ): WeatherHint =
        WeatherHintProvider.forCoordinates(latitude, longitude)

    override fun withEventPrepHint(
        weatherHint: WeatherHint,
        events: List<Event>,
    ): WeatherHint =
        WeatherHintProvider.withEventPrepHint(weatherHint, events)
}
