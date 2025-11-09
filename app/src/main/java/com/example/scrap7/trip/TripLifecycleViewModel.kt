package com.example.scrap7.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrap7.data.TripRepository
import com.example.scrap7.model.TripStatus
import com.example.scrap7.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TripUiState(
    val status: TripStatus = TripStatus.REQUESTED,
    val primaryLabel: String = "",
    val primaryEnabled: Boolean = true,
    val stepIndex: Int = 0
)

class TripLifecycleViewModel(
    private val repo: TripRepository,
    private val role: UserRole,
    private val tripId: String
) : ViewModel() {

    private val _ui = MutableStateFlow(TripUiState())
    val ui: StateFlow<TripUiState> = _ui.asStateFlow()

    init {
        // Observe the trip and derive UI state
        viewModelScope.launch {
            repo.observeTrip(tripId).collect { trip ->
                _ui.value = deriveUi(trip.status, role)
            }
        }
    }

    fun onPrimaryClick() = viewModelScope.launch {
        nextStatusFor(role, _ui.value.status)?.let { repo.updateStatus(tripId, it) }
    }

    private fun deriveUi(status: TripStatus, role: UserRole): TripUiState {
        val (label, enabled, step) = when (status) {
            TripStatus.REQUESTED   -> if (role == UserRole.DRIVER) Triple("Accept", true, 0) else Triple("Cancel request", true, 0)
            TripStatus.ACCEPTED    -> if (role == UserRole.DRIVER) Triple("Start pickup", true, 1) else Triple("Contact driver", true, 1)
            TripStatus.ARRIVING    -> if (role == UserRole.DRIVER) Triple("Start trip", true, 2) else Triple("Waiting…", false, 2)
            TripStatus.IN_PROGRESS -> if (role == UserRole.DRIVER) Triple("End trip", true, 3) else Triple("Trip in progress", false, 3)
            TripStatus.COMPLETED   -> Triple("View summary", true, 4)
            TripStatus.CANCELLED   -> Triple("Closed", false, 0)
        }
        return TripUiState(status, label, enabled, step)
    }

    private fun nextStatusFor(role: UserRole, cur: TripStatus): TripStatus? = when (role) {
        UserRole.DRIVER -> when (cur) {
            TripStatus.REQUESTED   -> TripStatus.ACCEPTED
            TripStatus.ACCEPTED    -> TripStatus.ARRIVING
            TripStatus.ARRIVING    -> TripStatus.IN_PROGRESS
            TripStatus.IN_PROGRESS -> TripStatus.COMPLETED
            else -> null
        }
        UserRole.RIDER -> when (cur) {
            TripStatus.REQUESTED -> TripStatus.CANCELLED
            else -> null
        }
    }
}