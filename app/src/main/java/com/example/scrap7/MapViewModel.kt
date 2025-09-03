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

    fun setTrip(pickup: LatLng, destination: LatLng) {
        this.pickup = pickup
        this.destination = destination
        clearRoutes()
    }

    fun updatePickup(p: LatLng) {
        pickup = p
        routeToPickup = emptyList()
    }

    fun updateDestination(d: LatLng) {
        destination = d
        routeToDestination = emptyList()
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

    fun fetchRoute(
        origin: String,
        destination: String,
        onRouteDecoded: (List<LatLng>) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = DirectionsClient.service.getRoute(
                    origin = origin,
                    destination = destination,
                    apiKey = Keys.MAPS_API_KEY
                )

                if (response.isSuccessful) {
                    val polyline = response.body()
                        ?.routes?.firstOrNull()
                        ?.overview_polyline?.points

                    if (!polyline.isNullOrEmpty()) {
                        val decoded = decodePolylineInternal(polyline)
                        withContext(Dispatchers.Main) {
                            onRouteDecoded(decoded)
                        }
                    }
                } else {
                    Log.e("VMRouteFetch", "API error: ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e("VMRouteFetch", "Error: ${e.message}", e)
            }
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
