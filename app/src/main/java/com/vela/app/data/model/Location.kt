package com.vela.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Location(
    val name: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
