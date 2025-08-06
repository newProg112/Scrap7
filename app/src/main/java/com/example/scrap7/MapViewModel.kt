package com.example.scrap7

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng

class MapViewModel : ViewModel() {

    var routeToPickup by mutableStateOf<List<LatLng>>(emptyList())
        private set

    var routeToDestination by mutableStateOf<List<LatLng>>(emptyList())
        private set

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
}