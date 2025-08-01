package com.example.scrap7

import android.Manifest
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.location.Location.distanceBetween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dot
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
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
fun MapScreen(userId: String, role: String, navController: NavController) {
    val context = LocalContext.current
    val cameraPositionState = rememberCameraPositionState()
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    val locationTracker = remember { LocationTracker(context) }

    val databaseRef = remember {
        FirebaseDatabase.getInstance().getReference("driversOnline")
    }
    val drivers = remember { mutableStateListOf<Pair<String, LatLng>>() }

    var incomingTrip by remember { mutableStateOf<DataSnapshot?>(null) }
    var showTripAcceptedToast by remember { mutableStateOf(false) }

    var selectedDestination by remember { mutableStateOf<LatLng?>(null) }

    var shouldFollowUser by remember { mutableStateOf(true) }

    var routeToPickup by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var routeToDestination by remember { mutableStateOf<List<LatLng>>(emptyList()) }

/*
    var routeToDestination by rememberSaveable(stateSaver = latLngListSaver) {
        mutableStateOf(emptyList())
    }
 */

    var completedTrip by remember { mutableStateOf<DataSnapshot?>(null) }

    if (role == "driver") {
        LaunchedEffect(userId) {
            FirebaseDatabase.getInstance().getReference("trips")
                .orderByChild("driverId")
                .equalTo(userId) // current driver's userId
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

        // check for trips in Firebase that are "accepted" or "in-progress" and restore it when moving back from message screen
        LaunchedEffect(userId, role) {
            if (role == "driver") {
                val tripRef = FirebaseDatabase.getInstance().getReference("trips")
                tripRef.orderByChild("driverId")
                    .equalTo(userId)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        for (tripSnapshot in snapshot.children) {
                            val status = tripSnapshot.child("status").getValue(String::class.java)
                            if (status == "accepted" || status == "in_progress") {
                                Log.d("TripRestore", "Restoring active trip: ${tripSnapshot.key}")
                                incomingTrip = tripSnapshot

                                // Re-fetch route
                                val pickupLat = tripSnapshot.child("riderLat").getValue(Double::class.java)
                                val pickupLng = tripSnapshot.child("riderLng").getValue(Double::class.java)
                                val destLat = tripSnapshot.child("destinationLat").getValue(Double::class.java)
                                val destLng = tripSnapshot.child("destinationLng").getValue(Double::class.java)

                                if (pickupLat != null && pickupLng != null && destLat != null && destLng != null) {
                                    val origin = "$pickupLat,$pickupLng"
                                    val destination = "$destLat,$destLng"

                                    Log.d("TripRestore", "Fetching route again: origin=$origin, destination=$destination")

                                    CoroutineScope(Dispatchers.IO).launch {
                                        try {
                                            val response = DirectionsClient.service.getRoute(
                                                origin = origin,
                                                destination = destination,
                                                apiKey = "AIzaSyA3vVBo46hVzhCKM-LDK_4KMEhfsFQeRwI"
                                            )

                                            if (response.isSuccessful) {
                                                val polyline = response.body()?.routes?.firstOrNull()?.overview_polyline?.points
                                                if (!polyline.isNullOrEmpty()) {
                                                    val decoded = decodePolyline(polyline)
                                                    Log.d("TripRestore", "Decoded polyline: ${decoded.size} points")
                                                    withContext(Dispatchers.Main) {
                                                        routeToDestination = decoded
                                                    }
                                                } else {
                                                    Log.w("TripRestore", "No polyline found")
                                                }
                                            } else {
                                                Log.e("TripRestore", "API error: ${response.code()} ${response.message()}")
                                            }
                                        } catch (e: Exception) {
                                            Log.e("TripRestore", "Route fetch failed: ${e.message}", e)
                                        }
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
                    CameraUpdateFactory.newLatLngZoom(location, 16f)
                )
            }

            // If user is a driver, update location in Firebase
            if (role == "driver") {
                val locationData = mapOf(
                    "lat" to location.latitude,
                    "lng" to location.longitude,
                    "timestamp" to System.currentTimeMillis()
                )

                Log.d("MapScreen", "Uploading driver location for userId=$userId: $locationData")

                FirebaseDatabase.getInstance()
                    .getReference("driversOnline")
                    .child(userId)
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
                .equalTo(userId)
                .addChildEventListener(object : ChildEventListener {
                    override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                        val status = snapshot.child("status").getValue(String::class.java)
                        if (status == "accepted") {
                            Log.d("RiderTrip", "Driver accepted the trip!")
                            incomingTrip = snapshot
                        }
                    }

                    override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                        val status = snapshot.child("status").getValue(String::class.java)
                        Log.d("TripMatch", "Trip status changed to: $status")

                        when (status) {
                            "accepted", "in_progress" -> incomingTrip = snapshot
                            "completed" -> {
                                completedTrip = snapshot
                                Log.d("TripCancelDebug", "incomingTrip manually set to null")
                                incomingTrip = null // Automatically clear the trip once completed
                            }
                        }
                    }

                    override fun onChildRemoved(snapshot: DataSnapshot) {}
                    override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                    override fun onCancelled(error: DatabaseError) {
                        Log.e("RiderTrip", "Trip listener cancelled", error.toException())
                    }
                })
        }
    }

    // Stop updates when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            locationTracker.stopListening()
            if (role == "driver") {
                databaseRef.child(userId).removeValue()
            }
        }
    }

    //Box(modifier = Modifier.fillMaxSize()) {
        // Show map
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = userLocation != null),
            onMapClick = { latLng ->
                shouldFollowUser = false // disable following when user taps map

                if (role == "rider") {
                    Log.d("MapScreen", "Map tapped: $latLng")
                    selectedDestination = latLng
                    Log.d("RiderDestination", "Destination set to: $latLng")
                }
            },
            content = {
            // Rider marker
            userLocation?.let {
                Marker(
                    state = MarkerState(position = it),
                    title = if (role == "rider") "You are here" else "Driver location",
                    icon = bitmapDescriptorFromVector(
                        context,
                        if (role == "rider") R.drawable.rider_icon else R.drawable.driver_icon
                    )
                )
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
                if (role == "rider") {
                drivers.forEach { (id, position) ->
                    Marker(
                        state = MarkerState(position = position),
                        title = "Driver: $id",
                        icon = bitmapDescriptorFromVector(context, R.drawable.driver_icon)
                    )
                }
            }

            selectedDestination?.let {
                Marker(
                    state = MarkerState(position = it),
                    title = "Selected Destination",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                )
            }

                // Route: Driver to Pickup (solid blue)
                if (routeToPickup.isNotEmpty()) {
                    Polyline(
                        points = routeToPickup,
                        color = Color.Blue,
                        width = 8f
                    )
                }

                // Route: Pickup to Destination (dashed green)
                if (routeToDestination.isNotEmpty()) {
                    Log.d("PolylineCheckReturnToMapScreen", "Driver routeToDestination size: ${routeToDestination.size}")
                    Polyline(
                        points = routeToDestination,
                        color = Color.Green,
                        width = 8f,
                        pattern = listOf(Dot(), Gap(10f)) // Dashed line
                    )
                }
        }
        )

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

            if (incomingTrip != null && incomingTrip?.key != null) {
                Button(
                    onClick = {
                        navController.navigate("chat/${incomingTrip?.key}/$userId")
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 128.dp)
                ) {
                    Text("Message")
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
                    requestPickup(userId, userLocation, selectedDestination, drivers)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Text("Request Pickup")
            }

            // Re-center button
            Button(
                onClick = {
                    shouldFollowUser = true
                    userLocation?.let {
                        cameraPositionState.move(
                            CameraUpdateFactory.newLatLngZoom(it, 16f)
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd) // bottom right of the screen
                    .padding(16.dp)
            ) {
                // Text("Re-center") // use icon instead for better UI
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = "Re-center"
                )
            }
        }

        if (role == "driver") {
            incomingTrip?.let { trip ->
                LaunchedEffect(trip.key) {
                    val pickupLat = trip.child("riderLat").getValue(Double::class.java)
                    val pickupLng = trip.child("riderLng").getValue(Double::class.java)
                    val destLat = trip.child("destinationLat").getValue(Double::class.java)
                    val destLng = trip.child("destinationLng").getValue(Double::class.java)

                    if (pickupLat != null && pickupLng != null && destLat != null && destLng != null) {
                        val origin = "$pickupLat,$pickupLng"
                        val destination = "$destLat,$destLng"

                        Log.d("RouteRestore", "Driver returned to Map. Refetching route: $origin → $destination")

                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val response = DirectionsClient.service.getRoute(
                                    origin = origin,
                                    destination = destination,
                                    apiKey = "AIzaSyA3vVBo46hVzhCKM-LDK_4KMEhfsFQeRwI"
                                )

                                if (response.isSuccessful) {
                                    val polyline = response.body()?.routes?.firstOrNull()?.overview_polyline?.points
                                    if (!polyline.isNullOrEmpty()) {
                                        val decoded = decodePolyline(polyline)
                                        withContext(Dispatchers.Main) {
                                            routeToDestination = decoded

                                            if (decoded.isNotEmpty()) {
                                                val boundsBuilder = LatLngBounds.builder()
                                                decoded.forEach { boundsBuilder.include(it) }
                                                val bounds = boundsBuilder.build()

                                                cameraPositionState.move(
                                                    CameraUpdateFactory.newLatLngBounds(bounds, 100)
                                                )
                                            }
                                        }
                                    } else {
                                        Log.w("RouteRestore", "No polyline found")
                                    }
                                } else {
                                    Log.e("RouteRestore", "API error: ${response.code()} ${response.message()}")
                                }
                            } catch (e: Exception) {
                                Log.e("RouteRestore", "Error refetching route: ${e.message}", e)
                            }
                        }
                    }
                }

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
                        onRouteToPickupDecoded = { routeToPickup = it },
                        onRouteToDestinationDecoded = { routeToDestination = it },
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
                            routeToPickup = emptyList()
                            routeToDestination = emptyList()
                        },
                        disableCameraFollow = { shouldFollowUser = false },
                        navController = navController,
                        userId = userId
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

                    LaunchedEffect(trip.key) {
                        if (pickupLat != null && pickupLng != null && destLat != null && destLng != null) {
                            val origin = "$pickupLat,$pickupLng"
                            val destination = "$destLat,$destLng"

                            Log.d("RouteToDestination", "Rider fetching route: origin=$origin, destination=$destination")

                            try {
                                val response = DirectionsClient.service.getRoute(
                                    origin = origin,
                                    destination = destination,
                                    apiKey = "AIzaSyA3vVBo46hVzhCKM-LDK_4KMEhfsFQeRwI"
                                )

                                if (response.isSuccessful) {
                                    val polyline = response.body()?.routes?.firstOrNull()?.overview_polyline?.points
                                    if (!polyline.isNullOrEmpty()) {
                                        val decoded = decodePolyline(polyline)
                                        Log.d("RouteToDestination", "Rider decoded ${decoded.size} points")
                                        routeToDestination = decoded
                                    } else {
                                        Log.w("RouteToDestination", "Rider: no polyline found")
                                    }
                                } else {
                                    Log.e("RouteToDestination", "Rider API error: ${response.code()} ${response.message()}")
                                }
                            } catch (e: Exception) {
                                Log.e("RouteToDestination", "Rider route fetch error: ${e.message}", e)
                            }
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
            Log.d("RequestPickup", "Trip created successfully")
        }
        .addOnFailureListener {
            Log.e("RequestPickup", "Failed to create trip", it)
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
    userId: String
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
                            val origin = "${driverLocation.latitude},${driverLocation.longitude}" // "52.9601,-1.1501" // or use driverLocation
                            val destination = "$pickupLat,$pickupLng" // "53.1451,-1.1514" // or use pickupLat/Lng

                            try {
                                val response = DirectionsClient.service.getRoute(
                                    origin = origin,
                                    destination = destination,
                                    apiKey = "AIzaSyA3vVBo46hVzhCKM-LDK_4KMEhfsFQeRwI"
                                )

                                Log.d("RouteDraw", "Requesting route: origin=$origin,destination=$destination")

                                if (response.isSuccessful) {
                                    val polyline = response.body()?.routes?.firstOrNull()?.overview_polyline?.points
                                    Log.d("RouteDraw", "Encoded polyline: $polyline")

                                    if (!polyline.isNullOrEmpty()) {
                                        val decoded = decodePolyline(polyline)
                                        Log.d("RouteDraw", "Decoded polyline has ${decoded.size} points")

                                        onRouteToPickupDecoded(decoded)
                                    } else {
                                        Log.w("RouteDraw", "No polyline found")
                                    }
                                } else {
                                    Log.e("RouteDraw", "API failed: ${response.code()} - ${response.message()}")
                                }
                            } catch (e: Exception) {
                                Log.e("RouteDraw", "Error calling directions API: ${e.message}")
                            }
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

                    // Message Button
                    Button(
                        onClick = {
                            val tripId = trip.key
                            if (tripId != null) {
                                navController.navigate("chat/$tripId/$userId")
                            } else {
                                Log.e("ChatNav", "Trip ID is null, cannot navigate to chat")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text("Message")
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
                            val origin = "$pickupLat,$pickupLng"
                            val destination = "$destLat,$destLng"

                            Log.d(
                                "RouteToDestination",
                                "Requesting destination route: origin=$origin, destination=$destination"
                            )

                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val response = DirectionsClient.service.getRoute(
                                        origin = origin,
                                        destination = destination,
                                        apiKey = "AIzaSyA3vVBo46hVzhCKM-LDK_4KMEhfsFQeRwI"
                                    )

                                    if (response.isSuccessful) {
                                        val polyline =
                                            response.body()?.routes?.firstOrNull()?.overview_polyline?.points
                                        if (!polyline.isNullOrEmpty()) {
                                            val decoded = decodePolyline(polyline)
                                            Log.d(
                                                "RouteToDestination",
                                                "Decoded polyline: ${decoded.size} points"
                                            )
                                            withContext(Dispatchers.Main) {
                                                onRouteToDestinationDecoded(decoded)
                                            }
                                        } else {
                                            Log.w("RouteToDestination", "No polyline found")
                                        }
                                    } else {
                                        Log.e(
                                            "RouteToDestination",
                                            "API error: ${response.code()} ${response.message()}"
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.e("RouteToDestination", "Error: ${e.message}", e)
                                }
                            }
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

                    // Message Button
                    Button(
                        onClick = {
                            val tripId = trip.key
                            if (tripId != null) {
                                navController.navigate("chat/$tripId/$userId")
                            } else {
                                Log.e("ChatNav", "Trip ID is null, cannot navigate to chat")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text("Message")
                    }
            }
            }
            }
        }
    }
}