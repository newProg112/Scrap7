package com.example.scrap7

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrap7.Keys
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

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

    // UI one-off events (toasts/snackbars)
    sealed class UiEvent { data class Toast(val message: String): UiEvent() }

    // Buffer 1 so we can emit without suspending during quick updates
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<UiEvent> = _events

    private var pickupJob: Job? = null
    private var destJob: Job? = null

    private var lastPickupOrigin: LatLng? = null
    private var lastPickupDest:   LatLng? = null
    private var lastDestOrigin:   LatLng? = null
    private var lastDestDest:     LatLng? = null

    private var pickupRequestId = 0
    private var destRequestId = 0

    fun updateRole(newRole: String) {
        role = newRole
        maybeFetchRoutes()
    }

    fun maybeFetchRoutes() {
        val rider = pickup
        val dest = destination
        val driver = driverPosition
        val st = tripStatus

        // Driver → Rider when accepted/on_trip
        if (role == "driver" && (st == "INCOMING" || st == "ACCEPTED" || st == "ON_TRIP")) {
            if (driver != null && rider != null) {
                fetchDriverToRiderRouteIfMoved(driver, rider)
            }
        }

        // Rider (or driver) → Destination when accepted/on_trip
        if ((role == "rider" && (st == "ACCEPTED" || st == "ON_TRIP")) ||
            (role == "driver" && (st == "ACCEPTED" || st == "ON_TRIP"))
        ) {
            if (rider != null && dest != null) {
                fetchPickupToDestinationRouteIfMoved(rider, dest)
            }
        }

        // Optional: rider preview before accept
        if (role == "rider" && (st == "REQUESTED" || st == "INCOMING")) {
            if (rider != null && dest != null) {
                fetchPickupToDestinationRouteIfMoved(rider, dest, minMetersChange = 1f, debounceMs = 300L)
            }
        }

        if (st == "COMPLETED" || st == "CANCELLED") {
            clearRoutesAndAnchors()
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

    private suspend fun <T> retryIO(
        times: Int = 3,
        initialDelayMs: Long = 500,
        maxDelayMs: Long = 4000,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var cur = initialDelayMs
        repeat(times - 1) {
            try { return block() } catch (e: Exception) {
                if (e is java.io.IOException) {
                    delay(cur)
                    cur = (cur * factor).toLong().coerceAtMost(maxDelayMs)
                } else throw e
            }
        }
        return block()
    }

    suspend fun fetchRoute(
        origin: String,
        destination: String,
        onRouteDecoded: (List<LatLng>) -> Unit,
        onEncoded: ((String) -> Unit)? = null,
        legName: String? = null
    ) {
        try {
            // Retry up to 3x on network-ish failures with exponential backoff
            val response = retryIO(times = 3) {
                DirectionsClient.service.getRoute(
                    origin = origin,
                    destination = destination,
                    apiKey = Keys.MAPS_API_KEY
                ).also { r ->
                    if (!r.isSuccessful) {
                        // Non-2xx (quota / auth / bad request). Don't retry: throw to outer catch.
                        throw IllegalStateException("HTTP ${r.code()} ${r.message()}")
                    }
                }
            }

            val encoded = response.body()
                ?.routes?.firstOrNull()
                ?.overview_polyline?.points

            if (encoded.isNullOrEmpty()) {
                _events.emit(UiEvent.Toast("No route found${legName?.let { " for $it" } ?: ""}"))
                Log.w("RouteFetch", "Empty polyline${legName?.let { " ($it)" } ?: ""}")
                return
            }

            val decoded = decodePolylineInternal(encoded)
            Log.d("RouteFetch", "Decoded ${decoded.size} points${legName?.let { " ($it)" } ?: ""}")

            // Deliver callbacks on the main thread
            withContext(Dispatchers.Main) {
                onRouteDecoded(decoded)
                onEncoded?.invoke(encoded)
            }

        } catch (e: IllegalStateException) {
            // HTTP error (e.g., 403/429/400) – likely quota/key/problem
            _events.emit(UiEvent.Toast("Route error${legName?.let { " ($it)" } ?: ""}: ${e.message}"))
            Log.e("RouteFetch", "HTTP error${legName?.let { " ($it)" } ?: ""}: ${e.message}", e)
        } catch (e: java.io.IOException) {
            // After retries, still a network failure
            _events.emit(UiEvent.Toast("Network issue${legName?.let { " ($it)" } ?: ""}. Retried 3×."))
            Log.e("RouteFetch", "Network error${legName?.let { " ($it)" } ?: ""}: ${e.message}", e)
        } catch (e: CancellationException) {
            throw e // let coroutine cancel cleanly without error noise
        } catch (e: Exception) {
            _events.emit(UiEvent.Toast("Unexpected error fetching route"))
            Log.e("RouteFetch", "Unexpected${legName?.let { " ($it)" } ?: ""}: ${e.message}", e)
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

    private fun movedEnough(
        prev: com.google.android.gms.maps.model.LatLng?,
        now:  com.google.android.gms.maps.model.LatLng?,
        thresholdM: Float
    ): Boolean {
        if (prev == null || now == null) return true
        return distanceMeters(prev, now) >= thresholdM
    }

    fun clearRoutesAndAnchors() {
        clearRoutes()
        lastPickupOrigin = null; lastPickupDest = null
        lastDestOrigin   = null; lastDestDest = null
    }

    fun fetchDriverToRiderRouteIfMoved(
        origin: LatLng,
        dest: LatLng,
        minMetersChange: Float = 40f,
        debounceMs: Long = 900L
    ) {
        val needOrigin = movedEnough(lastPickupOrigin, origin, minMetersChange)
        val needDest   = movedEnough(lastPickupDest, dest, 1f)
        if (!needOrigin && !needDest && routeToPickup.isNotEmpty()) return

        pickupJob?.cancel()
        val myId = ++pickupRequestId
        pickupJob = viewModelScope.launch {
            delay(debounceMs)
            fetchRoute(
                origin = "${origin.latitude},${origin.longitude}",
                destination = "${dest.latitude},${dest.longitude}",
                onRouteDecoded = { decoded ->
                    // only apply if this is still the newest request
                    if (myId == pickupRequestId) {
                        updateRouteToPickup(decoded)
                        lastPickupOrigin = origin
                        lastPickupDest   = dest
                    }
                },
                legName = "driver→pickup"
            )
        }
    }

    fun fetchPickupToDestinationRouteIfMoved(
        origin: LatLng,
        dest: LatLng,
        minMetersChange: Float = 40f,
        debounceMs: Long = 900L
    ) {
        val needOrigin = movedEnough(lastDestOrigin, origin, minMetersChange)
        val needDest   = movedEnough(lastDestDest, dest, 1f)
        if (!needOrigin && !needDest && routeToDestination.isNotEmpty()) return

        destJob?.cancel()
        val myId = ++destRequestId
        destJob = viewModelScope.launch {
            delay(debounceMs)
            fetchRoute(
                origin = "${origin.latitude},${origin.longitude}",
                destination = "${dest.latitude},${dest.longitude}",
                onRouteDecoded = { decoded ->
                    if (myId == destRequestId) {
                        updateRouteToDestination(decoded)
                        lastDestOrigin = origin
                        lastDestDest   = dest
                    }
                },
                legName = "pickup→destination"
            )
        }
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