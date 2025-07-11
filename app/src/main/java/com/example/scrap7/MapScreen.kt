package com.example.scrap7

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.Location.distanceBetween
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
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


@Composable
fun MapScreen(userId: String, role: String) {
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

    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }

    Box(modifier = Modifier.fillMaxSize()) {
    // Start location tracking
    RequestLocationPermission {
        locationTracker.startListening { location ->
            val latLng = LatLng(location.latitude, location.longitude)
            userLocation = latLng

            // Move the map camera to current location
            cameraPositionState.move(
                CameraUpdateFactory.newLatLngZoom(location, 16f)
            )

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

            if (role == "driver") {
                FirebaseDatabase.getInstance().getReference("trips")
                    .orderByChild("driverId")
                    .equalTo(userId) // current driver's userId
                    .addChildEventListener(object : ChildEventListener {
                        override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                            val status = snapshot.child("status").getValue(String::class.java)

                            Log.d("TripDebug", "onChildAdded fired: tripId=${snapshot.key}, status=$status")

                            if (status == "requested") {
                                Log.d("TripDebug", "Setting incomingTrip for tripId=${snapshot.key}")
                                incomingTrip = snapshot
                            }
                        }

                        override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                        override fun onChildRemoved(snapshot: DataSnapshot) {}
                        override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                        override fun onCancelled(error: DatabaseError) {
                            Log.e("TripMatch", "Trip listener cancelled", error.toException())
                        }
                    })
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
                            showTripAcceptedToast = true
                        }
                    }

                    override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                        val status = snapshot.child("status").getValue(String::class.java)
                        if (status == "accepted") {
                            Log.d("RiderTrip", "Trip status changed to accepted")
                            showTripAcceptedToast = true
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
            content = {
            // Rider marker
            userLocation?.let {
                Marker(state = MarkerState(position = it), title = "You are here")
            }

            // Driver markers
            drivers.forEach { (id, position) ->
                Marker(
                    state = MarkerState(position = position),
                    title = "Driver: $id",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
            }

            // Route polyline
            if (routePoints.isNotEmpty()) {
                Polyline(
                    points = routePoints,
                    color = Color.Blue,
                    width = 8f
                )
            }
        }
        )


        // Request Pickup Button (only for riders)
        if (role == "rider") {
            Button(
                onClick = {
                    Log.d("MapScreen", "Request Pickup button pressed")
                    requestPickup(userId, userLocation, drivers)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Text("Request Pickup")
            }
        }

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
                    onRouteDecoded = { decodedPoints ->
                        routePoints = decodedPoints
                    },
                    onAccept = {
                        trip.ref.child("status").setValue("accepted")
                        incomingTrip = null
                        Log.d("TripMatch", "Trip accepted by driver")
                    },
                    onDecline = {
                        trip.ref.child("status").setValue("declined")
                        incomingTrip = null
                        Log.d("TripMatch", "Trip declined by driver")
                    }
                )
            }
        }

        if (showTripAcceptedToast) {
            LaunchedEffect(Unit) {
                Toast.makeText(context, "Driver is on the way!", Toast.LENGTH_LONG).show()
                showTripAcceptedToast = false
            }
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
        "driverLat" to driverLoc.latitude,
        "driverLng" to driverLoc.longitude,
        "status" to "requested",
        "timestamp" to System.currentTimeMillis()
    )

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
    onRouteDecoded: (List<LatLng>) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    context: Context
) {
    Log.d("TripUI", "Trip popup triggered for rider: ${trip.child("riderId").value}")
    val riderId = trip.child("riderId").getValue(String::class.java) ?: "Unknown"
    val pickupLat = trip.child("riderLat").getValue(Double::class.java)
    val pickupLng = trip.child("riderLng").getValue(Double::class.java)

    if (pickupLat != null && pickupLng != null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text("New Trip Request", style = MaterialTheme.typography.titleMedium)
                Text("Pickup location: $pickupLat, $pickupLng")

                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    Button(onClick = {
                        trip.ref.child("status").setValue("accepted")
                        Log.d("TripMatch", "Trip accepted by driver")

                        val pickupLat = trip.child("riderLat").getValue(Double::class.java)
                        val pickupLng = trip.child("riderLng").getValue(Double::class.java)

                        if (pickupLat != null && pickupLng != null && driverLocation != null) {
                            // val origin = "${driverLocation.latitude},${driverLocation.longitude}"
                            val origin = "52.9601,-1.1501" // hard-coded for testing
                            // val destination = "$pickupLat,$pickupLng"
                            val destination = "52.9620,-1.1400" // hard-coded for testing

                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val response = DirectionsClient.service.getRoute(
                                        origin = origin,
                                        destination = destination,
                                        apiKey = "AIzaSyA3vVBo46hVzhCKM-LDK_4KMEhfsFQeRwI"
                                    )

                                    // Log the coordinates being sent
                                    Log.d("RouteDraw", "Requesting route: origin=$origin,destination=$destination")

                                    // Check if the API call failed
                                    if (!response.isSuccessful) {
                                        Log.e("RouteDraw", "API failed: ${response.code()} - ${response.message()}")
                                        return@launch
                                    }

                                    if (response.isSuccessful) {
                                        // Log the size of the routes list
                                        val routes = response.body()?.routes
                                        Log.d("RouteDraw", "Routes list size: ${routes?.size}")

                                        val polyline = response.body()?.routes?.firstOrNull()?.overview_polyline?.points
                                        Log.d("RouteDraw", "Encoded polyline: $polyline")

                                        if (!polyline.isNullOrEmpty()) {
                                            val decoded = decodePolyline(polyline)
                                            Log.d("RouteDraw", "Decoded polyline has ${decoded.size} points")

                                            withContext(Dispatchers.Main) {
                                                onRouteDecoded(decoded)
                                            }
                                        } else {
                                            Log.w("RouteDraw", "No polyline found")
                                        }
                                    } else {
                                        Log.e("RouteDraw", "API call failed: ${response.code()}")
                                    }
                                } catch (e: Exception) {
                                    Log.e("RouteDraw", "Error calling directions API: ${e.message}")
                                    e.printStackTrace() // forces full stack trace to show in Logcat

                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Route fetch failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }

                        // Clear the trip so the popup disappears
                        onAccept()
                    }) {
                        Text("Accept")
                    }



                    Button(onClick = onDecline) {
                        Text("Decline")
                    }
                }
            }
        }
    }
}