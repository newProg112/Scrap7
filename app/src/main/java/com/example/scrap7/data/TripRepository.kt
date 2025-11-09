package com.example.scrap7.data

import com.example.scrap7.model.LatLngDTO
import com.example.scrap7.model.Trip
import com.example.scrap7.model.TripStatus
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

class TripRepository(private val tripsRef: DatabaseReference) {

    fun observeTrip(tripId: String) = callbackFlow<Trip> {
        val ref = tripsRef.child(tripId)
        val listener = ValueEventListenerAdapter(
            onDataChangeCb = { snap ->
                if (!snap.exists()) return@ValueEventListenerAdapter
                val map = snap.value as Map<*, *>

                val status = (map["status"] as? String)?.let { runCatching { TripStatus.valueOf(it) }.getOrNull() }
                    ?: TripStatus.REQUESTED

                fun mapCoord(node: Any?): LatLngDTO {
                    val m = node as? Map<*, *> ?: return LatLngDTO()
                    return LatLngDTO(
                        lat = (m["lat"] as? Number)?.toDouble() ?: 0.0,
                        lng = (m["lng"] as? Number)?.toDouble() ?: 0.0,
                        address = (m["address"] as? String).orElse("")
                    )
                }

                trySend(
                    Trip(
                        id = snap.key ?: "",
                        status = status,
                        riderId = map["riderId"] as? String ?: "",
                        driverId = map["driverId"] as? String ?: "",
                        pickup = mapCoord(map["pickup"]),
                        destination = mapCoord(map["destination"])
                    )
                )
            },
            onCancelledCb = { close(it.toException()) }
        )
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun updateStatus(tripId: String, next: TripStatus) {
        val ref = tripsRef.child(tripId)
        ref.runTransaction(object : Transaction.Handler {
            override fun doTransaction(current: MutableData): Transaction.Result {
                val curStr = current.child("status").value as? String ?: "REQUESTED"
                val cur = runCatching { TripStatus.valueOf(curStr) }.getOrElse { TripStatus.REQUESTED }
                val allowed = mapOf(
                    TripStatus.REQUESTED   to setOf(TripStatus.ACCEPTED, TripStatus.CANCELLED),
                    TripStatus.ACCEPTED    to setOf(TripStatus.ARRIVING, TripStatus.CANCELLED),
                    TripStatus.ARRIVING    to setOf(TripStatus.IN_PROGRESS, TripStatus.CANCELLED),
                    TripStatus.IN_PROGRESS to setOf(TripStatus.COMPLETED, TripStatus.CANCELLED),
                    TripStatus.COMPLETED   to emptySet(),
                    TripStatus.CANCELLED   to emptySet(),
                )
                if (!allowed[cur]!!.contains(next)) return Transaction.abort()

                current.child("status").value = next.name
                val key = when (next) {
                    TripStatus.REQUESTED   -> "requested"
                    TripStatus.ACCEPTED    -> "accepted"
                    TripStatus.ARRIVING    -> "arriving"
                    TripStatus.IN_PROGRESS -> "in_progress"
                    TripStatus.COMPLETED   -> "completed"
                    TripStatus.CANCELLED   -> "cancelled"
                }
                current.child("timestamps").child(key).value = ServerValue.TIMESTAMP
                return Transaction.success(current)
            }
            override fun onComplete(error: com.google.firebase.database.DatabaseError?, committed: Boolean, snapshot: com.google.firebase.database.DataSnapshot?) {}
        })
    }

    private fun String?.orElse(fallback: String) = this ?: fallback
}