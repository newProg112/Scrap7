package com.example.scrap7

import android.health.connect.datatypes.ExerciseRoute
import android.location.Location
import android.view.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.firebase.database.DataSnapshot
import java.util.Date

@Composable
fun TripSummaryScreen(
    tripSnapshot: DataSnapshot,
    onClose: () -> Unit
) {
    val riderId = tripSnapshot.child("riderId").getValue(String::class.java) ?: "Unknown"
    val driverId = tripSnapshot.child("driverId").getValue(String::class.java) ?: "Unknown"
    val riderLat = tripSnapshot.child("riderLat").getValue(Double::class.java)
    val riderLng = tripSnapshot.child("riderLng").getValue(Double::class.java)
    val destLat = tripSnapshot.child("destinationLat").getValue(Double::class.java)
    val destLng = tripSnapshot.child("destinationLng").getValue(Double::class.java)
    val timestamp = tripSnapshot.child("timestamp").getValue(Long::class.java)

    val distanceKm: Float? = if (riderLat != null && riderLng != null && destLat != null && destLng != null) {
        val result = FloatArray(1)
        Location.distanceBetween(riderLat, riderLng, destLat, destLng, result)
        result[0] / 1000f // km
    } else {
        null
    }

    val fare = distanceKm?.let { calculateFare(it) }


    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Trip Summary", style = MaterialTheme.typography.headlineSmall)

            Spacer(Modifier.height(16.dp))
            Text("Rider ID: $riderId")
            Text("Driver ID: $driverId")
            Text("Start: $riderLat, $riderLng")
            Text("Destination: $destLat, $destLng")
            Text("Distance: ${distanceKm?.let { String.format("%.2f km", it) } ?: "N/A"}")
            Text("Estimated Fare: £${fare?.let { String.format("%.2f", it) } ?: "N/A"}")
            Text("Timestamp: ${timestamp?.let { Date(it).toString() } ?: "N/A"}")

            Spacer(Modifier.height(24.dp))

            Button(onClick = onClose, modifier = Modifier.align(Alignment.End)) {
                Text("Close")
            }
        }
    }
}
