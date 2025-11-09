package com.example.scrap7.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import com.example.scrap7.MapScreen
import com.example.scrap7.MapViewModel
import com.example.scrap7.data.TripRepository
import com.example.scrap7.model.UserRole
import com.example.scrap7.trip.TripLifecycleViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

@Composable
fun TripHostScreen(
    role: UserRole,
    tripId: String,
    navController: NavController,
    mapViewModel: MapViewModel
) {
    // 1) Firebase ref + repo
    val tripsRef = remember { FirebaseDatabase.getInstance().getReference("trips") }
    val tripRepo = remember { TripRepository(tripsRef) }

    // 2) Build lifecycle VM
    val vm: TripLifecycleViewModel = remember(role, tripId) {
        TripLifecycleViewModel(tripRepo, role, tripId)
    }

    // 3) Current user id (for MapScreen's existing API)
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

    // 4) Render your real map, passing lifecycle VM
    MapScreen(
        userId = uid,
        role = if (role == UserRole.DRIVER) "driver" else "rider",
        navController = navController,
        viewModel = mapViewModel,
        vm = vm
    )
}