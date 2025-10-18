package com.example.scrap7

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.scrap7.ui.MessagingPanel
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
val latLngListSaver = run {
    val itemSaver = Saver<LatLng, String>(
        save = { "${it.latitude},${it.longitude}" },
        restore = {
            val (lat, lng) = it.split(",")
            LatLng(lat.toDouble(), lng.toDouble())
        }
    )
    listSaver(itemSaver)
}
 */

fun bitmapDescriptorFromVector(context: Context, @DrawableRes resId: Int): BitmapDescriptor {
    val drawable = ContextCompat.getDrawable(context, resId)!!
    drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
    val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.draw(canvas)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

@Composable
fun MapScreen(
    userId: String,
    role: String,
    navController: NavController,
    viewModel: MapViewModel
) {
    //val viewModel: MapViewModel = viewModel()
    val routeToPickup = viewModel.routeToPickup
    val routeToDestination = viewModel.routeToDestination

    val context = LocalContext.current
    val cameraPositionState = rememberCameraPositionState()
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    val locationTracker = remember { LocationTracker(context) }

    val databaseRef = remember {
        FirebaseDatabase.getInstance().getReference("driversOnline")
    }
    val drivers = remember { mutableStateListOf<Pair<String, LatLng>>() }

    val uiScope = rememberCoroutineScope()

    var incomingTrip by remember { mutableStateOf<DataSnapshot?>(null) }
    var showTripAcceptedToast by remember { mutableStateOf(false) }

    var selectedDestination by remember { mutableStateOf<LatLng?>(null) }

    var shouldFollowUser by remember { mutableStateOf(true) }

    // var routeToPickup by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    // var routeToDestination by remember { mutableStateOf<List<LatLng>>(emptyList()) }

/*
    var routeToDestination by rememberSaveable(stateSaver = latLngListSaver) {
        mutableStateOf(emptyList())
    }
 */

    var completedTrip by remember { mutableStateOf<DataSnapshot?>(null) }

    // Fit-once flags
    var pickupFitted by remember { mutableStateOf(false) }
    var destFitted by remember { mutableStateOf(false) }

    var showChat by remember { mutableStateOf(false) }
    val tripId = incomingTrip?.key
    val myUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        ?: run {
            Log.e("AUTH", "No Firebase user (not signed in)"); return
        }

    val driverId = incomingTrip?.child("driverId")?.getValue(String::class.java)
    val riderId  = incomingTrip?.child("riderId")?.getValue(String::class.java)
    val otherUserId = when (myUserId) {
        driverId -> riderId
        riderId  -> driverId
        else     -> null
    }
    val tripStatus: String = incomingTrip?.child("status")?.getValue(String::class.java) ?: ""

    LaunchedEffect(Unit) {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        android.util.Log.d("AUTH", "role=$role  auth.uid=$uid")
    }

    LaunchedEffect(showChat, tripId) {
        if (showChat && tripId != null) {
            viewModel.clearUnread()
        }
    }

// Reset flags when trip status changes
    LaunchedEffect(incomingTrip?.child("status")?.getValue(String::class.java)) {
        val s = incomingTrip?.child("status")?.getValue(String::class.java)
        if (s != "accepted") pickupFitted = false
        if (s != "in_progress") destFitted = false
    }

// Fit once when pickup leg arrives
    LaunchedEffect(viewModel.routeToPickup) {
        val pts = viewModel.routeToPickup
        if (!pickupFitted && pts.isNotEmpty()) {
            val b = LatLngBounds.builder().apply { pts.forEach { include(it) } }.build()
            cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(b, 100))
            pickupFitted = true
        }
    }

// Fit once when destination leg arrives
    LaunchedEffect(viewModel.routeToDestination) {
        val pts = viewModel.routeToDestination
        if (!destFitted && pts.isNotEmpty()) {
            val b = LatLngBounds.builder().apply { pts.forEach { include(it) } }.build()
            cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(b, 100))
            destFitted = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { ev ->
            when (ev) {
                is MapViewModel.UiEvent.Toast -> Toast.makeText(context, ev.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(tripStatus, incomingTrip?.key, role) {
        val trip = incomingTrip ?: return@LaunchedEffect

        // Read coords safely
        fun d(name: String) = trip.child(name).getValue(Double::class.java)
        val riderLat = d("riderLat"); val riderLng = d("riderLng")
        val destLat  = d("destinationLat"); val destLng  = d("destinationLng")
        val driverLat = d("driverLat"); val driverLng = d("driverLng")

        Log.d("Routes",
            "status=$tripStatus role=$role " +
                    "driver=(${driverLat},${driverLng}) rider=(${riderLat},${riderLng}) dest=(${destLat},${destLng})"
        )

        when (tripStatus) {
            "accepted" -> {
                // Driver should see driver→rider
                if (role == "driver" && driverLat != null && driverLng != null && riderLat != null && riderLng != null) {
                    viewModel.clearRouteToDestination()
                    viewModel.fetchDriverToRiderRouteIfMoved(
                        origin = com.google.android.gms.maps.model.LatLng(driverLat, driverLng),
                        dest   = com.google.android.gms.maps.model.LatLng(riderLat,  riderLng)
                    )
                }
                // Rider should see driver→rider too (optional)
                if (role == "rider" && driverLat != null && driverLng != null && riderLat != null && riderLng != null) {
                    viewModel.clearRouteToDestination()
                    viewModel.fetchDriverToRiderRouteIfMoved(
                        origin = com.google.android.gms.maps.model.LatLng(driverLat, driverLng),
                        dest   = com.google.android.gms.maps.model.LatLng(riderLat,  riderLng)
                    )
                }
            }

            "in_progress" -> {
                // Both see rider→destination (and drop the pickup leg)
                viewModel.clearRouteToPickup()
                if (riderLat != null && riderLng != null && destLat != null && destLng != null) {
                    viewModel.fetchPickupToDestinationRouteIfMoved(
                        origin = com.google.android.gms.maps.model.LatLng(riderLat, riderLng),
                        dest   = com.google.android.gms.maps.model.LatLng(destLat,  destLng)
                    )
                }
            }

            else -> {
                // requested / completed / cancelled
                viewModel.clearRouteToPickup()
                viewModel.clearRouteToDestination()
            }
        }
    }


    if (role == "driver") {
        LaunchedEffect(myUserId) {
            FirebaseDatabase.getInstance().getReference("trips")
                .orderByChild("driverId")
                .equalTo(myUserId) // current driver's userId
                .addChildEventListener(object : ChildEventListener {
                    override fun onChildAdded(
                        snapshot: DataSnapshot,
                        previousChildName: String?
                    ) {
                        val status = snapshot.child("status").getValue(String::class.java)

                        Log.d(
                            "TripDebug",
                            "onChildAdded fired: tripId=${snapshot.key}, status=$status"
                        )

                        if (status == "requested") {
                            Log.d(
                                "TripDebug",
                                "Setting incomingTrip for tripId=${snapshot.key}"
                            )
                            incomingTrip = snapshot
                        }
                    }

                    override fun onChildChanged(
                        snapshot: DataSnapshot,
                        previousChildName: String?
                    ) {
                        val status = snapshot.child("status").getValue(String::class.java)
                        Log.d("TripMatch", "Trip status changed to: $status")

                        when (status) {
                            "accepted", "in_progress" -> incomingTrip = snapshot
                            "completed" -> {
                                completedTrip = snapshot
                                Log.d("TripCancelDebug", "incomingTrip manually set to null")
                                incomingTrip = null
                            }
                        }
                    }

                    override fun onChildRemoved(snapshot: DataSnapshot) {}
                    override fun onChildMoved(
                        snapshot: DataSnapshot,
                        previousChildName: String?
                    ) {
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("TripMatch", "Trip listener cancelled", error.toException())
                    }
                })
        }

        LaunchedEffect(myUserId) {
            val ref = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("trips")

            // 3a) Does my filtered query return anything?
            ref.orderByChild("driverId").equalTo(myUserId).get()
                .addOnSuccessListener { snap ->
                    android.util.Log.d("TripsProbe", "equalTo(myUserId) count=${snap.childrenCount}")
                    for (c in snap.children) {
                        android.util.Log.d(
                            "TripsProbe",
                            "tripId=${c.key} status=${c.child("status").value}"
                        )
                    }
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("TripsProbe", "Permission denied or error: ${e.message}", e)
                }

            // 3b) Optional: root peek (may be denied by rules; that's fine)
            ref.limitToFirst(1).get()
                .addOnSuccessListener { snap ->
                    android.util.Log.d("TripsProbe", "/trips exists? hasChildren=${snap.hasChildren()}")
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("TripsProbe", "Root read blocked (expected): ${e.message}")
                }
        }

        // check for trips in Firebase that are "accepted" or "in-progress" and restore it when moving back from message screen
        LaunchedEffect(myUserId, role) {
            if (role == "driver") {
                val tripRef = FirebaseDatabase.getInstance().getReference("trips")
                tripRef.orderByChild("driverId")
                    .equalTo(myUserId)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        for (tripSnapshot in snapshot.children) {
                            val status = tripSnapshot.child("status").getValue(String::class.java)
                            if (status == "accepted" || status == "in_progress") {
                                Log.d("TripRestore", "Restoring active trip: ${tripSnapshot.key}")
                                incomingTrip = tripSnapshot

                                val statusNow = tripSnapshot.child("status").getValue(String::class.java)

                                if (statusNow == "accepted") {
                                    val dLat = tripSnapshot.child("driverLat").getValue(Double::class.java)
                                    val dLng = tripSnapshot.child("driverLng").getValue(Double::class.java)
                                    val rLat = tripSnapshot.child("riderLat").getValue(Double::class.java)
                                    val rLng = tripSnapshot.child("riderLng").getValue(Double::class.java)
                                    if (dLat != null && dLng != null && rLat != null && rLng != null) {
                                        viewModel.fetchDriverToRiderRouteIfMoved(
                                            origin = LatLng(dLat, dLng),
                                            dest   = LatLng(rLat, rLng),
                                            debounceMs = 0L
                                        )
                                    }
                                } else if (statusNow == "in_progress") {
                                    val rLat = tripSnapshot.child("riderLat").getValue(Double::class.java)
                                    val rLng = tripSnapshot.child("riderLng").getValue(Double::class.java)
                                    val destLat = tripSnapshot.child("destinationLat").getValue(Double::class.java)
                                    val destLng = tripSnapshot.child("destinationLng").getValue(Double::class.java)
                                    if (rLat != null && rLng != null && destLat != null && destLng != null) {
                                        viewModel.fetchPickupToDestinationRouteIfMoved(
                                            origin = LatLng(rLat, rLng),
                                            dest   = LatLng(destLat, destLng),
                                            debounceMs = 0L
                                        )
                                    }
                                }
                            }
                        }
                    }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Start location tracking
        RequestLocationPermission {
            locationTracker.startListening { location ->
                val latLng = LatLng(location.latitude, location.longitude)
                userLocation = latLng

                // Move the map camera to current location
                if (shouldFollowUser) {
                    cameraPositionState.move(
                        CameraUpdateFactory.newLatLngZoom(latLng, 16f)
                    )
                }

                // If user is a driver, update location in Firebase
                if (role == "driver") {
                    val locationData = mapOf(
                        "lat" to location.latitude,
                        "lng" to location.longitude,
                        "timestamp" to System.currentTimeMillis()
                    )

                    Log.d(
                        "MapScreen",
                        "Uploading driver location for userId=$userId: $locationData"
                    )

                    FirebaseDatabase.getInstance()
                        .getReference("driversOnline")
                        .child(myUserId)
                        .setValue(locationData)
                        .addOnSuccessListener {
                            Log.d("MapScreen", "Driver location uploaded successfully")
                        }
                        .addOnFailureListener {
                            Log.e("MapScreen", "Failed to upload driver location", it)
                        }
                }


            }

            if (role == "rider") {
                Log.d("MapScreen", "Role: $role")
                FirebaseDatabase.getInstance().getReference("driversOnline")
                    .addValueEventListener(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            drivers.clear()
                            for (driverSnapshot in snapshot.children) {
                                val lat = driverSnapshot.child("lat").getValue(Double::class.java)
                                val lng = driverSnapshot.child("lng").getValue(Double::class.java)
                                if (lat != null && lng != null) {
                                    drivers.add(driverSnapshot.key!! to LatLng(lat, lng))
                                }
                            }
                            Log.d("MapScreen", "Drivers loaded: ${drivers.size}")
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("MapScreen", "Driver listener cancelled", error.toException())
                        }
                    })

                FirebaseDatabase.getInstance().getReference("trips")
                    .orderByChild("riderId")
                    .equalTo(myUserId)
                    .addChildEventListener(object : ChildEventListener {
                        override fun onChildAdded(
                            snapshot: DataSnapshot,
                            previousChildName: String?
                        ) {
                            val status = snapshot.child("status").getValue(String::class.java)
                            if (status == "accepted") {
                                Log.d("RiderTrip", "Driver accepted the trip!")
                                incomingTrip = snapshot
                            }
                        }

                        override fun onChildChanged(
                            snapshot: DataSnapshot,
                            previousChildName: String?
                        ) {
                            val status = snapshot.child("status").getValue(String::class.java)
                            Log.d("TripMatch", "Trip status changed to: $status")

                            when (status) {
                                "accepted", "in_progress" -> incomingTrip = snapshot
                                "completed" -> {
                                    completedTrip = snapshot
                                    Log.d("TripCancelDebug", "incomingTrip manually set to null")
                                    incomingTrip =
                                        null // Automatically clear the trip once completed
                                }
                            }
                        }

                        override fun onChildRemoved(snapshot: DataSnapshot) {}
                        override fun onChildMoved(
                            snapshot: DataSnapshot,
                            previousChildName: String?
                        ) {
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("RiderTrip", "Trip listener cancelled", error.toException())
                        }
                    })
            }
        }

        // Stop updates when leaving screen
        DisposableEffect(Unit) {
            onDispose {
                // Stop GPS updates while this screen isn't visible.
                locationTracker.stopListening()
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            /*
            LaunchedEffect(routeToDestination) {
                if (routeToDestination.isNotEmpty()) {
                    val center = routeToDestination[routeToDestination.size / 2]
                    Log.d("DebugCamera", "Moving camera to center of route: $center")
                    cameraPositionState.move(
                        CameraUpdateFactory.newLatLngZoom(center, 15f)
                    )
                }
            }
             */

            // Re-fetch route when coming back from another screen (like Message)
            LaunchedEffect(Unit) {
                navController.currentBackStackEntryFlow.collect { backStackEntry ->
                    if (backStackEntry.destination.route?.startsWith("map") == true) {

                        // Skip if a polyline is already cached
                        val hasAny = viewModel.routeToPickup.isNotEmpty() || viewModel.routeToDestination.isNotEmpty()
                        if (hasAny) return@collect

                        Log.d("RouteRefetch", "Returned to MapScreen, re-fetching routes") // log after the guard

                        incomingTrip?.let { trip ->
                            val status = trip.child("status").getValue(String::class.java)

                            if (status == "accepted") {
                                if (role == "driver") {
                                    val driverLat = trip.child("driverLat").getValue(Double::class.java)
                                    val driverLng = trip.child("driverLng").getValue(Double::class.java)
                                    val riderLat = trip.child("riderLat").getValue(Double::class.java)
                                    val riderLng = trip.child("riderLng").getValue(Double::class.java)

                                    if (driverLat != null && driverLng != null && riderLat != null && riderLng != null) {
                                        val tripId = trip.key ?: return@let
                                        viewModel.fetchRoute(
                                            origin = "$driverLat,$driverLng",
                                            destination = "$riderLat,$riderLng",
                                            onRouteDecoded = { decoded ->
                                                viewModel.updateRouteToPickup(decoded)
                                                viewModel.markRouteRecalculated(
                                                    com.google.android.gms.maps.model.LatLng(driverLat, driverLng)
                                                )
                                            },
                                            onEncoded = { encoded ->
                                                FirebaseDatabase.getInstance()
                                                    .getReference("trips").child(tripId)
                                                    .child("history").child("toPickupPolyline")
                                                    .setValue(encoded)
                                            },
                                            legName = "driver->pickup"
                                        )
                                    }
                                }

                                /*
                                if (role == "rider") {
                                    val driverLat = trip.child("driverLat").getValue(Double::class.java)
                                    val driverLng = trip.child("driverLng").getValue(Double::class.java)
                                    val riderLat = trip.child("riderLat").getValue(Double::class.java)
                                    val riderLng = trip.child("riderLng").getValue(Double::class.java)

                                    if (driverLat != null && driverLng != null && riderLat != null && riderLng != null) {
                                        val tripId = trip.key ?: return@let
                                        viewModel.fetchRoute(
                                            origin = "$driverLat,$driverLng",
                                            destination = "$riderLat,$riderLng",
                                            onRouteDecoded = { decoded ->
                                                viewModel.updateRouteToPickup(decoded)
                                                viewModel.markRouteRecalculated(
                                                    com.google.android.gms.maps.model.LatLng(driverLat, driverLng)
                                                )
                                            },
                                            onEncoded = { encoded ->
                                                FirebaseDatabase.getInstance()
                                                    .getReference("trips").child(tripId)
                                                    .child("history").child("toPickupPolyline")
                                                    .setValue(encoded)
                                            }
                                        )
                                    }
                                }
                                */
                            }
                        }
                    }
                }
            }

            // Re-route from the current driver position when they've moved enough / after a small time window
            LaunchedEffect(viewModel.driverPosition, incomingTrip?.key) {
                val pos = viewModel.driverPosition ?: return@LaunchedEffect
                val status = incomingTrip?.child("status")?.getValue(String::class.java) ?: return@LaunchedEffect

                // optional guard you may already have
                val allow = viewModel.shouldRecalcRoute(
                    currentOrigin = LatLng(pos.latitude, pos.longitude),
                    minMeters = 40f,
                    minIntervalMs = 9_000L
                )
                android.util.Log.d("DriverPosReRoute", "pos=$pos status=$status allow=$allow")
                if (!allow) return@LaunchedEffect

                when (status) {
                    "accepted" -> {
                        val rLat = incomingTrip?.child("riderLat")?.getValue(Double::class.java)
                        val rLng = incomingTrip?.child("riderLng")?.getValue(Double::class.java)
                        if (rLat != null && rLng != null) {
                            viewModel.fetchDriverToRiderRouteIfMoved(
                                origin = LatLng(pos.latitude, pos.longitude),
                                dest   = LatLng(rLat, rLng),
                                minMetersChange = 25f,
                                debounceMs = 700L
                            )
                        }
                    }
                    "in_progress" -> {
                        val dLat = incomingTrip?.child("destinationLat")?.getValue(Double::class.java)
                        val dLng = incomingTrip?.child("destinationLng")?.getValue(Double::class.java)
                        if (dLat != null && dLng != null) {
                            viewModel.fetchPickupToDestinationRouteIfMoved(
                                origin = LatLng(pos.latitude, pos.longitude),
                                dest   = LatLng(dLat, dLng),
                                minMetersChange = 25f,
                                debounceMs = 700L
                            )
                        }
                    }
                }
            }

            DisposableEffect(role, incomingTrip?.key, userId, context) {
                // Only the DRIVER mirrors location into the active trip
                val tripId = incomingTrip?.key
                if (role != "driver" || tripId == null) {
                    onDispose { }
                } else {
                    // Permission guard (avoid crash if location isn't granted)
                    val hasFine = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!hasFine) return@DisposableEffect onDispose { }

                    val fused = LocationServices.getFusedLocationProviderClient(context)

                    val tripsRef   = FirebaseDatabase.getInstance().getReference("trips").child(tripId)
                    val onlineRef  = FirebaseDatabase.getInstance().getReference("driversOnline").child(myUserId)

                    // ~2s desired interval; min 1s between updates
                    val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
                        .setMinUpdateIntervalMillis(1000L)
                        .setMinUpdateDistanceMeters(10f)
                        .build()

                    var running = false
                    val callback = object : LocationCallback() {
                        private var lastSentMs = 0L
                        override fun onLocationResult(result: LocationResult) {
                            val loc = result.lastLocation ?: return
                            val now = System.currentTimeMillis()
                            if (now - lastSentMs < 1000L) return   // throttle 1s
                            lastSentMs = now

                            val lat = loc.latitude
                            val lng = loc.longitude

                            // Mirror into the trip (rider will listen to this)
                            tripsRef.updateChildren(mapOf(
                                "driverLat" to lat,
                                "driverLng" to lng
                            ))
                            // Keep your existing presence path updated too (optional)
                            onlineRef.updateChildren(mapOf(
                                "lat" to lat,
                                "lng" to lng,
                                "lastUpdated" to ServerValue.TIMESTAMP
                            ))

                            android.util.Log.d("TripPosWrite", "trip=$tripId lat=$lat lng=$lng")
                        }
                    }

                    // Start/stop location updates based on trip status
                    val statusListener = object : ValueEventListener {
                        override fun onDataChange(snap: DataSnapshot) {
                            val status = snap.child("status").getValue(String::class.java)
                            val shouldRun = status == "accepted" || status == "in_progress"

                            // Check either FINE or COARSE location permission
                            val hasLocationPermission =
                                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

                            if (shouldRun && !running) {
                                if (!hasLocationPermission) {
                                    android.util.Log.w("TripPosWrite", "No location permission; not starting updates")
                                    return
                                }
                                try {
                                    fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
                                    running = true
                                    android.util.Log.d("TripPosWrite", "Started location updates (status=$status)")
                                } catch (se: SecurityException) {
                                    android.util.Log.e("TripPosWrite", "Permission error starting updates", se)
                                }
                            } else if (!shouldRun && running) {
                                try {
                                    fused.removeLocationUpdates(callback)
                                } catch (se: SecurityException) {
                                    android.util.Log.e("TripPosWrite", "Permission error stopping updates", se)
                                } finally {
                                    running = false
                                    android.util.Log.d("TripPosWrite", "Stopped location updates (status=$status)")
                                }
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {
                            android.util.Log.e("TripPosWrite", "status listener cancelled: ${error.message}")
                        }
                    }

                    // Attach status listener
                    tripsRef.addValueEventListener(statusListener)

                    // Clean up on leaving screen or trip/role change
                    onDispose {
                        tripsRef.removeEventListener(statusListener)
                        if (running) fused.removeLocationUpdates(callback)
                    }
                }
            }

            DisposableEffect(incomingTrip?.key) {
                val tripId = incomingTrip?.key ?: return@DisposableEffect onDispose { }
                val ref = FirebaseDatabase.getInstance()
                    .getReference("trips")
                    .child(tripId)

                val listener = object : ValueEventListener {
                    override fun onDataChange(snap: DataSnapshot) {
                        // --- status + clear non-active leg ---
                        val status = snap.child("status").getValue(String::class.java)
                        android.util.Log.d("TripListen", "status=$status")

                        if (status == "in_progress" && viewModel.routeToPickup.isNotEmpty()) {
                            viewModel.clearRouteToPickup()
                        }
                        if (status == "accepted" && viewModel.routeToDestination.isNotEmpty()) {
                            viewModel.clearRouteToDestination()
                        }
                        if (status == "completed" || status == "cancelled") {
                            viewModel.clearRouteToPickup()
                            viewModel.clearRouteToDestination()
                        }

                        // --- live driver position for re-route + rider marker ---
                        val dLat = snap.child("driverLat").getValue(Double::class.java)
                        val dLng = snap.child("driverLng").getValue(Double::class.java)
                        if (dLat != null && dLng != null) {
                            viewModel.setDriverPosition(dLat, dLng)
                        }

                        when (status) {
                            "accepted" -> {
                                if (viewModel.routeToPickup.isEmpty()) {
                                    val rLat = snap.child("riderLat").getValue(Double::class.java)
                                    val rLng = snap.child("riderLng").getValue(Double::class.java)
                                    if (dLat != null && dLng != null && rLat != null && rLng != null) {
                                        viewModel.fetchDriverToRiderRouteIfMoved(
                                            origin = LatLng(dLat, dLng),
                                            dest   = LatLng(rLat, rLng),
                                            debounceMs = 0L
                                        )
                                    }
                                }
                            }
                            "in_progress" -> {
                                if (viewModel.routeToDestination.isEmpty()) {
                                    val rLat = snap.child("riderLat").getValue(Double::class.java)
                                    val rLng = snap.child("riderLng").getValue(Double::class.java)
                                    val dstLat = snap.child("destinationLat").getValue(Double::class.java)
                                    val dstLng = snap.child("destinationLng").getValue(Double::class.java)
                                    if (rLat != null && rLng != null && dstLat != null && dstLng != null) {
                                        viewModel.fetchPickupToDestinationRouteIfMoved(
                                            origin = LatLng(rLat, rLng),
                                            dest   = LatLng(dstLat, dstLng),
                                            debounceMs = 0L
                                        )
                                    }
                                }
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        android.util.Log.e("DriverPosListen", "onCancelled: ${error.message}")
                    }
                }

                ref.addValueEventListener(listener)
                onDispose { ref.removeEventListener(listener) }
            }

            // Bump unread when new messages arrive from the *other* user
            DisposableEffect(incomingTrip?.key, myUserId) {
                val tripId = incomingTrip?.key
                if (tripId == null) {
                    onDispose { }
                } else {
                    viewModel.markUnreadBaselineNowIfUnset()

                    val ref = FirebaseDatabase.getInstance()
                        .getReference("trips").child(tripId).child("messages")

                    val listener = object : ChildEventListener {
                        override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                            val from = snapshot.child("senderId").getValue(String::class.java)
                            val ts   = snapshot.child("timestamp").getValue(Long::class.java)
                            viewModel.bumpUnreadIfNeeded(from, ts, myUserId)
                        }
                        override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                        override fun onChildRemoved(snapshot: DataSnapshot) {}
                        override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                        override fun onCancelled(error: DatabaseError) {
                            Log.e("UnreadListen", "messages listener cancelled: ${error.message} @ trips/$tripId/messages")

                        }
                    }

                    ref.addChildEventListener(listener)
                    onDispose { ref.removeEventListener(listener) }
                }
            }

            // Seed the correct leg once per active trip (don’t depend on viewModel.pickup/destination)
            LaunchedEffect(incomingTrip?.key) {
                // If either leg is already drawn, do nothing
                if (viewModel.routeToPickup.isNotEmpty() || viewModel.routeToDestination.isNotEmpty()) return@LaunchedEffect

                val trip = incomingTrip ?: return@LaunchedEffect
                val status = trip.child("status").getValue(String::class.java) ?: return@LaunchedEffect

                when (status) {
                    "accepted" -> {
                        val dLat = trip.child("driverLat").getValue(Double::class.java)
                        val dLng = trip.child("driverLng").getValue(Double::class.java)
                        val rLat = trip.child("riderLat").getValue(Double::class.java)
                        val rLng = trip.child("riderLng").getValue(Double::class.java)
                        if (dLat != null && dLng != null && rLat != null && rLng != null) {
                            viewModel.fetchDriverToRiderRouteIfMoved(
                                origin = LatLng(dLat, dLng),
                                dest   = LatLng(rLat, rLng),
                                debounceMs = 0L
                            )
                        }
                    }
                    "in_progress" -> {
                        val rLat   = trip.child("riderLat").getValue(Double::class.java)
                        val rLng   = trip.child("riderLng").getValue(Double::class.java)
                        val destLat= trip.child("destinationLat").getValue(Double::class.java)
                        val destLng= trip.child("destinationLng").getValue(Double::class.java)
                        if (rLat != null && rLng != null && destLat != null && destLng != null) {
                            viewModel.fetchPickupToDestinationRouteIfMoved(
                                origin = LatLng(rLat, rLng),
                                dest   = LatLng(destLat, destLng),
                                debounceMs = 0L
                            )
                        }
                    }
                }
            }

            // Show map
            GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            onMapLoaded = { Log.d("MapHealth", "Map tiles loaded")},
            properties = MapProperties(isMyLocationEnabled = userLocation != null),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,     // disable built-in zoom
                    myLocationButtonEnabled = false  // (optional) we already recenter ourselves
                ),
            onMapClick = { latLng ->
                shouldFollowUser = false // disable following when user taps map

                if (role == "rider") {
                    Log.d("MapScreen", "Map tapped: $latLng")
                    selectedDestination = latLng
                    Log.d("RiderDestination", "Destination set to: $latLng")
                }
            },
            content = {
                // "You" marker, explicit per role
                userLocation?.let {
                    if (role == "rider") {
                        Marker(
                            state = MarkerState(position = it),
                            title = "You",
                            icon = bitmapDescriptorFromVector(context, R.drawable.rider_icon)
                        )
                    } else if (role == "driver") {
                        Marker(
                            state = MarkerState(position = it),
                            title = "You (driver)",
                            icon = bitmapDescriptorFromVector(context, R.drawable.driver_icon)
                        )
                    }
                }

                // Destination marker
                if (role == "rider" && selectedDestination != null) {
                    Marker(
                        state = MarkerState(position = selectedDestination!!),
                        title = "Destination",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                    )
                }

                // Driver markers
                if (role == "rider" && incomingTrip == null) {
                    drivers.forEach { (id, position) ->
                        Marker(
                            state = MarkerState(position = position),
                            title = "Driver: $id",
                            icon = bitmapDescriptorFromVector(context, R.drawable.driver_icon)
                        )
                    }
                }

                // Assigned driver's live position — rider only
                if (role == "rider") {
                    viewModel.driverPosition?.let { pos ->
                        Marker(
                            state = MarkerState(position = pos),
                            title = "Your driver",
                            icon = bitmapDescriptorFromVector(context, R.drawable.driver_icon)
                        )
                    }
                }

                // Route: Driver to Pickup (show to both during ACCEPTED)
                if (tripStatus == "accepted" && viewModel.routeToPickup.isNotEmpty()) {
                    androidx.compose.runtime.key("pickup-$tripStatus") {
                        Log.d(
                            "PolylineDraw",
                            "Drawing routeToPickup with ${routeToPickup.size} points"
                        )
                        Polyline(
                            points = routeToPickup,
                            color = Color.Red, // color = Color.Blue,
                            width = 12f, // width = 8f
                            pattern = null
                        )
                    }
                }

                // Route: Pickup to Destination (dashed green)
                if (tripStatus == "in_progress" && viewModel.routeToDestination.isNotEmpty()) {
                    androidx.compose.runtime.key("dest-$tripStatus") {
                        Log.d(
                            "PolylineDraw",
                            "Driver routeToDestination with ${routeToDestination.size} points (role = $role)"
                        )
                        Polyline(
                            points = routeToDestination,
                            color = Color.Magenta, // Color.Green,
                            width = 20f, // 8f,
                            pattern = null // listOf(Dot(), Gap(10f)) // Dashed line
                        )
                    }
                }
            }
        )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Zoom in
                androidx.compose.material3.SmallFloatingActionButton(
                    onClick = { cameraPositionState.move(CameraUpdateFactory.zoomIn()) }
                ) { Text("+") }

                // Zoom out
                androidx.compose.material3.SmallFloatingActionButton(
                    onClick = { cameraPositionState.move(CameraUpdateFactory.zoomOut()) }
                ) { Text("–") }

                // Center on me (now for BOTH rider & driver)
                androidx.compose.material3.FloatingActionButton(
                    onClick = {
                        shouldFollowUser = true
                        userLocation?.let {
                            cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(it, 16f))
                        }
                    }
                ) { Icon(Icons.Default.Place, contentDescription = "Re-center") }

                // Chat (badged)
                BadgedBox(
                    badge = {
                        val count = viewModel.unreadCount
                        if (!showChat && count > 0) Badge { Text(if (count > 9) "9+" else "$count") }
                    }
                ) {
                    androidx.compose.material3.FloatingActionButton(
                        onClick = {
                            val opening = !showChat
                            showChat = opening
                        }
                    ) { Text("Chat") }
                }
            }

            if (showChat && tripId != null && otherUserId != null) {
                Surface(
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 360.dp)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    MessagingPanel(
                        tripId = tripId,
                        myUserId = myUserId,
                        otherUserId = otherUserId,
                        visible = true,
                        tripStatus = tripStatus,
                        onClose = { showChat = false }
                        )
                }
            }
        }

        // Request Pickup Button (only for riders)
        if (role == "rider") {
            val autocompleteLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val place = Autocomplete.getPlaceFromIntent(result.data!!)
                    val latLng = place.latLng
                    Log.d("Places", "Place selected: ${place.name} - ${place.latLng}")

                    // TODO: Save this destination (e.g., in ViewModel or local state)
                } else if (result.resultCode == Activity.RESULT_CANCELED) {
                    Log.d("Places", "Autocomplete canceled")
                }
            }

            // Set Destination button
            Button(
                onClick = {
                    val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
                    val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
                        .build(context)
                    autocompleteLauncher.launch(intent)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 72.dp) // stack above "Request Pickup"
            ) {
                Text("Set Destination")
            }

            // Request Pickup button
            Button(
                onClick = {
                    Log.d("MapScreen", "Request Pickup button pressed. Selected destination: $selectedDestination")
                    requestPickup(myUserId, userLocation, selectedDestination, drivers)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Text("Request Pickup")
            }
        }

        if (role == "driver") {
            incomingTrip?.let { trip ->
                Log.d("TripUI", "Rendering IncomingTripCard for tripId=${trip.key}")

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    IncomingTripCard(
                        trip = trip,
                        driverLocation = userLocation,
                        context = context,
                        /*
                        onRouteDecoded = { decodedPoints ->
                            routePoints = decodedPoints

                            if (decodedPoints.isNotEmpty()) {
                                val bounds = LatLngBounds.builder().apply {
                                    decodedPoints.forEach { include(it) }
                                }.build()

                                cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                            }
                        },

                         */
                        onRouteToPickupDecoded = { viewModel.updateRouteToPickup(it) },
                        onRouteToDestinationDecoded = { viewModel.updateRouteToDestination(it) },
                        onAccept = {
                            trip.ref.child("status").setValue("accepted")
                            Log.d("TripMatch", "Trip accepted by driver")
                            shouldFollowUser = false
                        },
                        onDecline = {
                            trip.ref.child("status").setValue("declined")
                            Log.d("TripCancelDebug", "incomingTrip manually set to null")
                            incomingTrip = null
                            Log.d("TripMatch", "Trip declined by driver")
                        },
                        onComplete = {
                            Log.d("TripCancelDebug", "incomingTrip manually set to null")
                            incomingTrip = null
                            viewModel.clearRoutes()
                        },
                        disableCameraFollow = { shouldFollowUser = false },
                        navController = navController,
                        userId = myUserId,
                        viewModel = viewModel,
                        onOpenChat = {
                            showChat = true
                        }
                    )
                }

            }
        }

        if (role == "rider") {
            incomingTrip?.let { trip ->
                val status = trip.child("status").getValue(String::class.java)
                if (status == "accepted" || status == "in_progress") {
                    val pickupLat = trip.child("riderLat").getValue(Double::class.java)
                    val pickupLng = trip.child("riderLng").getValue(Double::class.java)
                    val destLat = trip.child("destinationLat").getValue(Double::class.java)
                    val destLng = trip.child("destinationLng").getValue(Double::class.java)

                    /*
                    // Fetch route to Pickup destination, for the rider
                    LaunchedEffect(trip.key + role) {
                        val status = trip.child("status").getValue(String::class.java)
                        if (status != "accepted" || viewModel.routeToPickup.isNotEmpty()) return@LaunchedEffect

                        val driverLat = trip.child("driverLat").getValue(Double::class.java)
                        val driverLng = trip.child("driverLng").getValue(Double::class.java)
                        val riderLat = trip.child("riderLat").getValue(Double::class.java)
                        val riderLng = trip.child("riderLng").getValue(Double::class.java)

                        if (driverLat != null && driverLng != null && riderLat != null && riderLng != null) {
                            val origin = "$driverLat,$driverLng"
                            val destination = "$riderLat,$riderLng"

                            Log.d("RouteToPickup", "Rider fetching routeToPickup: $origin → $destination")

                            try {
                                val response = DirectionsClient.service.getRoute(
                                    origin = origin,
                                    destination = destination,
                                    apiKey = Keys.MAPS_API_KEY
                                )

                                if (response.isSuccessful) {
                                    val polyline = response.body()?.routes?.firstOrNull()?.overview_polyline?.points
                                    if (!polyline.isNullOrEmpty()) {
                                        val decoded = decodePolyline(polyline)
                                        Log.d("RouteToPickup", "Rider decoded ${decoded.size} points")
                                        viewModel.updateRouteToPickup(decoded)

                                        // Save encoded once
                                        trip.ref.child("history").child("toPickupPolyline").setValue(polyline)
                                    } else {
                                        Log.w("RouteToPickup", "No polyline found")
                                    }
                                } else {
                                    Log.e("RouteToPickup", "API error: ${response.code()} ${response.message()}")
                                }
                            } catch (e: Exception) {
                                Log.e("RouteToPickup", "Error fetching route: ${e.message}", e)
                            }
                        }
                    }
                    */

                    Log.d("RiderRouteCheck", "incomingTrip = ${trip.key}, status = $status")

                    LaunchedEffect(trip.key) {
                        val s = trip.child("status").getValue(String::class.java)
                        if (s != "in_progress") return@LaunchedEffect

                        val pickupLat = trip.child("riderLat").getValue(Double::class.java)
                        val pickupLng = trip.child("riderLng").getValue(Double::class.java)
                        val destLat   = trip.child("destinationLat").getValue(Double::class.java)
                        val destLng   = trip.child("destinationLng").getValue(Double::class.java)

                        if (pickupLat != null && pickupLng != null && destLat != null && destLng != null) {
                            viewModel.fetchPickupToDestinationRouteIfMoved(
                                origin = LatLng(pickupLat, pickupLng),
                                dest   = LatLng(destLat, destLng),
                                debounceMs = 0L
                            )
                        }
                    }
                }
            }
        }


        if (showTripAcceptedToast) {
            LaunchedEffect(Unit) {
                Toast.makeText(context, "Driver is on the way!", Toast.LENGTH_LONG).show()
                showTripAcceptedToast = false
            }
        }

        completedTrip?.let { trip ->
            TripSummaryScreen(
                tripSnapshot = trip,
                onClose = {
                    completedTrip = null
                }
            )
        }
    }

    /*
    // add an optional onEncoded param (default null)
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
                apiKey = Keys.MAPS_API_KEY
            )
            if (response.isSuccessful) {
                val encoded = response.body()?.routes?.firstOrNull()?.overview_polyline?.points
                if (!encoded.isNullOrEmpty()) {
                    val decoded = decodePolyline(encoded)
                    onRouteDecoded(decoded)
                    onEncoded?.invoke(encoded) // <-- hand back the encoded string for storage
                }
            } else {
                Log.e("RouteRefetch", "API error: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e("RouteRefetch", "Error: ${e.message}", e)
        }
    }
    */
}

@Composable
fun RequestLocationPermission(onGranted: () -> Unit) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) onGranted()
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
}

fun getCurrentLocation(context: Context, onLocationReceived: (LatLng) -> Unit) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    try {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    onLocationReceived(LatLng(location.latitude, location.longitude))
                }
            }
    } catch (e: SecurityException) {
        Log.e("Location", "Permission not granted", e)
    }
}

fun requestPickup(
    riderId: String,
    riderLocation: LatLng?,
    destination: LatLng?,
    drivers: List<Pair<String, LatLng>>
) {
    if (riderLocation == null) {
        Log.w("RequestPickup", "Rider location is NULL")
    } else {
        Log.d("RequestPickup", "Rider location: ${riderLocation.latitude}, ${riderLocation.longitude}")
    }

    if (drivers.isEmpty()) {
        Log.w("RequestPickup", "No drivers available")
    } else {
        Log.d("RequestPickup", "${drivers.size} drivers found")
    }

    if (riderLocation == null || drivers.isEmpty()) return

    // Find nearest driver
    val nearest = drivers.minByOrNull { (_, driverLoc) ->
        distanceBetween(riderLocation, driverLoc)
    } ?: return

    val (driverId, driverLoc) = nearest

    val tripId = "$riderId-$driverId"
    val tripData = mapOf(
        "riderId" to riderId,
        "driverId" to driverId,
        "riderLat" to riderLocation.latitude,
        "riderLng" to riderLocation.longitude,
        "destinationLat" to destination?.latitude,
        "destinationLng" to destination?.longitude,
        "driverLat" to driverLoc.latitude,
        "driverLng" to driverLoc.longitude,
        "status" to "requested",
        "timestamp" to System.currentTimeMillis()
    )

    Log.d("RequestPickup", "Destination: $destination")

    FirebaseDatabase.getInstance()
        .getReference("trips")
        .child(tripId)
        .setValue(tripData)
        .addOnSuccessListener {
            android.util.Log.d(
                "TripsCreate",
                "OK trip=$tripId riderId=${tripData["riderId"]} driverId=${tripData["driverId"]}"
            )
        }
        .addOnFailureListener { e ->
            android.util.Log.e("TripsCreate", "FAIL: ${e.message}", e)
        }
}

fun distanceBetween(start: LatLng, end: LatLng): Float {
    val result = FloatArray(1)
    Location.distanceBetween(
        start.latitude, start.longitude,
        end.latitude, end.longitude,
        result
    )
    return result[0] // in meters
}

@Composable
fun IncomingTripCard(
    trip: DataSnapshot,
    driverLocation: LatLng?,
    //onRouteDecoded: (List<LatLng>) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onComplete: () -> Unit,
    context: Context,
    disableCameraFollow: () -> Unit,
    onRouteToPickupDecoded: (List<LatLng>) -> Unit,
    onRouteToDestinationDecoded: (List<LatLng>) -> Unit,
    navController: NavController,
    userId: String,
    viewModel: MapViewModel,
    onOpenChat: () -> Unit
) {
    Log.d("TripUI", "Trip popup triggered for rider: ${trip.child("riderId").value}")
    val riderId = trip.child("riderId").getValue(String::class.java) ?: "Unknown"
    val pickupLat = trip.child("riderLat").getValue(Double::class.java)
    val pickupLng = trip.child("riderLng").getValue(Double::class.java)

    if (pickupLat != null && pickupLng != null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(Color.White.copy(alpha = 0.8f)), // add transparency to see if route is visible behind
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                val status = rememberUpdatedState(trip.child("status").getValue(String::class.java))

                LaunchedEffect(status.value) {
                    if (status.value == "accepted") {
                        val pickupLat = trip.child("riderLat").getValue(Double::class.java)
                        val pickupLng = trip.child("riderLng").getValue(Double::class.java)

                        if (pickupLat != null && pickupLng != null && driverLocation != null) {
                            viewModel.fetchDriverToRiderRouteIfMoved(
                                origin = LatLng(driverLocation.latitude, driverLocation.longitude),
                                dest   = LatLng(pickupLat, pickupLng),
                                debounceMs = 0L
                            )
                        }
                    }
                }

                Text("New Trip Request", style = MaterialTheme.typography.titleMedium)
                Text("Pickup location: $pickupLat, $pickupLng")

                Log.d("TripFlow", "Current trip status in card: ${status.value}")

                when (status.value) {
                    // Accept/Decline
                    "requested" -> {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    ) {
                        Button(onClick = onAccept) {
                            Text("Accept")
                        }
                        Button(onClick = onDecline) {
                            Text("Decline")
                        }
                    }
                }

                // Show "Start Trip"
                "accepted" -> {
                    Button(
                        onClick = {
                            trip.ref.child("status").setValue("in_progress")
                            Log.d("TripFlow", "Trip marked as in_progress")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Text("Start Trip")
                    }
                }

                // Optionally show route or completion
                "in_progress" -> {
                    val pickupLat = trip.child("riderLat").getValue(Double::class.java)
                    val pickupLng = trip.child("riderLng").getValue(Double::class.java)
                    val destLat = trip.child("destinationLat").getValue(Double::class.java)
                    val destLng = trip.child("destinationLng").getValue(Double::class.java)

                    // Launch route fetch inside LaunchedEffect to avoid Compose recomposition issues
                    LaunchedEffect(trip.key) {
                        if (pickupLat != null && pickupLng != null && destLat != null && destLng != null) {
                            viewModel.fetchPickupToDestinationRouteIfMoved(
                                origin = LatLng(pickupLat, pickupLng),
                                dest   = LatLng(destLat, destLng),
                                debounceMs = 0L
                            )
                        }
                    }

                // Show "Complete Trip" button
                Button(
                    onClick = {
                        val pickupLat = trip.child("riderLat").getValue(Double::class.java)
                        val pickupLng = trip.child("riderLng").getValue(Double::class.java)
                        val destLat = trip.child("destinationLat").getValue(Double::class.java)
                        val destLng = trip.child("destinationLng").getValue(Double::class.java)

                        if (pickupLat != null && pickupLng != null && destLat != null && destLng != null) {
                            val pickup = LatLng(pickupLat, pickupLng)
                            val destination = LatLng(destLat, destLng)
                            val distance = distanceBetween(pickup, destination)
                            val fare = calculateFare(distance)

                            // Save fare to Firebase
                            trip.ref.child("fare").setValue(fare)
                        }

                        // Mark trip as completed
                        trip.ref.child("status").setValue("completed")
                        Log.d("TripFlow", "Trip marked as completed")

                        // Optional delete the trip from Firebase
                        //trip.ref.removeValue()

                        onComplete() // hides the popup
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Text("Complete Trip")
                }
            }
            }
            }
        }
    }
}