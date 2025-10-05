package com.example.scrap7.data.chat

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.getValue
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ChatRepository(
    private val db: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    private fun tripMessagesRef(tripId: String): DatabaseReference =
        db.getReference("trips").child(tripId).child("messages")

    fun streamMessages(tripId: String, limit: Int = 100): Flow<List<Message>> = callbackFlow {
        val ref = tripMessagesRef(tripId).orderByChild("ts").limitToLast(limit)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { it.getValue(Message::class.java) }
                    .sortedBy { it.ts }
                trySend(list).isSuccess
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException()) // close the flow if cancelled
            }
        }

        ref.addValueEventListener(listener)

        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun sendMessage(tripId: String, msg: Message): String {
        val ref = tripMessagesRef(tripId).push()
        val id = ref.key!!
        val payload = msg.copy(id = id)
        // Prefer server timestamp if client clock is off
        val map = mapOf(
            "id" to payload.id,
            "tripId" to payload.tripId,
            "fromUid" to payload.fromUid,
            "toUid" to payload.toUid,
            "text" to payload.text,
            "delivery" to payload.delivery.name,
            "ts" to com.google.firebase.database.ServerValue.TIMESTAMP
        )
        ref.setValue(map).await()
        return id
    }
}