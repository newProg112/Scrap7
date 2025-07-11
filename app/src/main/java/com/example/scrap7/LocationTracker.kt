package com.example.scrap7

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng

class LocationTracker(context: Context) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
        .setMinUpdateIntervalMillis(1000L)
        .build()

    private var listener: ((LatLng) -> Unit)? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation
            if (location != null) {
                listener?.invoke(LatLng(location.latitude, location.longitude))
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startListening(onLocationUpdate: (LatLng) -> Unit) {
        listener = onLocationUpdate
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e("LocationTracker", "Location permission not granted", e)
        }
    }

    fun stopListening() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}