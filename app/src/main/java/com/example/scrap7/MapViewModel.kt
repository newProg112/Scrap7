package com.example.scrap7

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrap7.Keys
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapViewModel : ViewModel() {

    var routeToPickup by mutableStateOf<List<LatLng>>(emptyList())
        private set

    var routeToDestination by mutableStateOf<List<LatLng>>(emptyList())
        private set

    var pickup by mutableStateOf<LatLng?>(null)
    private set

    var destination by mutableStateOf<LatLng?>(null)
    private set

    // --- Unread badge state ---
    var unreadCount by mutableStateOf(0)
    private set

    var lastRead by mutableStateOf(0L)
    private set

    var driverPosition by mutableStateOf<LatLng?>(null)
        private set

    var role by mutableStateOf("rider")
        private set
    var tripStatus by mutableStateOf<String?>(null)
        private set

    fun updateRole(newRole: String) {
        role = newRole
        maybeFetchRoutes()
    }

    fun maybeFetchRoutes() {
        val rider = pickup
        val dest = destination
        val driver = driverPosition
        val st = tripStatus

        if (role == "driver" && (st == "INCOMING" || st == "ACCEPTED" || st == "ON_TRIP")) {
            if (driver != null && rider != null) {
                viewModelScope.launch {
                    fetchRoute(
                        "${driver.latitude},${driver.longitude}",
                        "${rider.latitude},${rider.longitude}",
                        onRouteDecoded = { updateRouteToPickup(it) }
                    )
                }
            }
        }

        if ((role == "rider" && (st == "ACCEPTED" || st == "ON_TRIP")) ||
            (role == "driver" && (st == "ACCEPTED" || st == "ON_TRIP"))
        ) {
            if (rider != null && dest != null) {
                viewModelScope.launch {
                    fetchRoute(
                        "${rider.latitude},${rider.longitude}",
                        "${dest.latitude},${dest.longitude}",
                        onRouteDecoded = { updateRouteToDestination(it) }
                    )
                }
            }
        }

        // Optional: preview before accept
        if (role == "rider" && (st == "REQUESTED" || st == "INCOMING")) {
            if (rider != null && dest != null) {
                viewModelScope.launch {
                    fetchRoute(
                        "${rider.latitude},${rider.longitude}",
                        "${dest.latitude},${dest.longitude}",
                        onRouteDecoded = { updateRouteToDestination(it) }
                    )
                }
            }
        }

        if (st == "COMPLETED" || st == "CANCELLED") {
            clearRoutes()
        }
    }

    fun setTrip(pickup: LatLng, destination: LatLng) {
        this.pickup = pickup
        this.destination = destination
        clearRoutes()
    }

    fun updatePickup(p: LatLng) {
        pickup = p
        routeToPickup = emptyList()
        maybeFetchRoutes()
    }

    fun updateDestination(d: LatLng) {
        destination = d
        routeToDestination = emptyList()
        maybeFetchRoutes()
    }

    fun updateRouteToPickup(route: List<LatLng>) {
        routeToPickup = route
    }

    fun updateRouteToDestination(route: List<LatLng>) {
        routeToDestination = route
    }

    fun clearRoutes() {
        routeToPickup = emptyList()
        routeToDestination = emptyList()
    }

    suspend fun fetchRoute(
        origin: String,
        destination: String,
        onRouteDecoded: (List<LatLng>) -> Unit,
        onEncoded: ((String) -> Unit)? = null
    ) {
        try {
            val response = DirectionsClient.service.getRoute(
                origin = origin,
                destination = destination,
                apiKey = Keys.MAPS_API_KEY // or BuildConfig.MAPS_API_KEY if you switched
            )
            if (response.isSuccessful) {
                val encoded = response.body()
                    ?.routes?.firstOrNull()
                    ?.overview_polyline?.points

                if (!encoded.isNullOrEmpty()) {
                    val decoded = decodePolylineInternal(encoded)
                    Log.d("RouteFetch", "Decoded ${decoded.size} points")
                    onRouteDecoded(decoded)
                    onEncoded?.invoke(encoded) // hand back the encoded polyline for history saving
                } else {
                    Log.w("RouteFetch", "No polyline in response")
                }
            } else {
                Log.e("RouteFetch", "API error: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e("RouteFetch", "Error: ${e.message}", e)
        }
    }

    /** Set a baseline so we don't count all historical messages on first attach */
    fun markUnreadBaselineNowIfUnset() {
        if (lastRead == 0L) lastRead = System.currentTimeMillis()
    }

    /** Clear badge and mark this moment as read */
    fun clearUnread() {
        unreadCount = 0
        lastRead = System.currentTimeMillis()
    }

    /** Increment badge for messages from the other user that arrived after lastRead */
    fun bumpUnreadIfNeeded(senderId: String?, timestamp: Long?, currentUserId: String) {
        if (senderId == null || timestamp == null) return
        if (senderId != currentUserId && timestamp > lastRead) {
            unreadCount++
        }
    }

    // --- Re-route policy state ---
    var lastRouteOrigin: com.google.android.gms.maps.model.LatLng? = null
        private set
    private var lastRouteRecalcAtMs: Long = 0L

    /** Should we re-fetch a route from the current origin?
     *  Triggers only if we've moved at least [minMeters] *and* [minIntervalMs] has passed. */
    fun shouldRecalcRoute(
        currentOrigin: com.google.android.gms.maps.model.LatLng,
        minMeters: Float = 150f,
        minIntervalMs: Long = 30_000L
    ): Boolean {
        val now = System.currentTimeMillis()
        val last = lastRouteOrigin ?: return true
        if (now - lastRouteRecalcAtMs < minIntervalMs) return false
        return distanceMeters(last, currentOrigin) >= minMeters
    }

    /** Call after a successful re-route to set a new baseline */
    fun markRouteRecalculated(origin: com.google.android.gms.maps.model.LatLng) {
        lastRouteOrigin = origin
        lastRouteRecalcAtMs = System.currentTimeMillis()
    }

    private fun distanceMeters(
        a: com.google.android.gms.maps.model.LatLng,
        b: com.google.android.gms.maps.model.LatLng
    ): Float {
        val out = FloatArray(1)
        android.location.Location.distanceBetween(
            a.latitude, a.longitude, b.latitude, b.longitude, out
        )
        return out[0]
    }

    fun setDriverPosition(lat: Double, lng: Double) {
        driverPosition = LatLng(lat, lng)
        maybeFetchRoutes()
    }

    fun setTripStatusAndRefresh(status: String) {
        tripStatus = status
        maybeFetchRoutes()
    }

    fun clearDriverPosition() {
        driverPosition = null
    }

    fun clearRouteToPickup() { routeToPickup = emptyList() }
    fun clearRouteToDestination() { routeToDestination = emptyList() }
}

private fun decodePolylineInternal(encoded: String): List<LatLng> {
    val poly = ArrayList<LatLng>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0

    while (index < len) {
        var b: Int
        var shift = 0
        var result = 0
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
        lat += dlat

        shift = 0
        result = 0
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
        lng += dlng

        poly.add(
            LatLng(
                lat / 1E5,
                lng / 1E5
            )
        )
    }
    return poly
}
