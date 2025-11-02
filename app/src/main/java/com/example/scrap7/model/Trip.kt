package com.example.scrap7.model

data class LatLngDTO(val lat: Double = 0.0, val lng: Double = 0.0, val address: String = "")

data class TripTimestamps(
    val requested: Long? = null,
    val accepted: Long? = null,
    val arriving: Long? = null,
    val in_progress: Long? = null,
    val completed: Long? = null,
    val cancelled: Long? = null
)

data class Trip(
    val id: String = "",
    val status: TripStatus = TripStatus.REQUESTED,
    val riderId: String = "",
    val driverId: String = "",
    val pickup: LatLngDTO = LatLngDTO(),
    val destination: LatLngDTO = LatLngDTO(),
    val timestamps: TripTimestamps = TripTimestamps()
)