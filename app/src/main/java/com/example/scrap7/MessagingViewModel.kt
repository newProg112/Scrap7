package com.example.scrap7

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MessagingViewModel : ViewModel() {

    private val db: DatabaseReference =
        FirebaseDatabase.getInstance().reference.child("messages")

    private var tripNode: DatabaseReference? = null
    private var listener: ValueEventListener? = null

    // Expose pairs of (firebaseKey, Message) so the UI can use stable keys
    private val _messages = MutableStateFlow<List<Pair<String, Message>>>(emptyList())
    val messages: StateFlow<List<Pair<String, Message>>> = _messages

    private var _tripId: String? = null
    private var _myUserId: String? = null

    fun bind(tripId: String, myUserId: String) {
        if (_tripId == tripId && _myUserId == myUserId) return
        unbind()

        _tripId = tripId
        _myUserId = myUserId
        tripNode = db.child(tripId)

        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { snap ->
                    val id = snap.key ?: return@mapNotNull null
                    val senderId = snap.child("senderId").getValue(String::class.java) ?: return@mapNotNull null
                    val text = snap.child("text").getValue(String::class.java) ?: ""
                    val ts = snap.child("timeStamp").getValue(Long::class.java) ?: 0L
                    id to Message(senderId = senderId, text = text, timeStamp = ts)
                }.sortedBy { it.second.timeStamp }

                viewModelScope.launch { _messages.emit(list) }
            }

            override fun onCancelled(error: DatabaseError) {
                // no-op for now
            }
        }
        tripNode!!.addValueEventListener(listener as ValueEventListener)
    }

    fun unbind() {
        listener?.let { l -> tripNode?.removeEventListener(l) }
        listener = null
        tripNode = null
    }

    fun send(text: String) {
        val trip = _tripId ?: return
        val me = _myUserId ?: return
        val t = text.trim()
        if (t.isEmpty()) return

        val key = db.child(trip).push().key ?: return
        val payload = mapOf(
            "senderId" to me,
            "text" to t,
            // IMPORTANT: match your model's field name exactly
            "timeStamp" to System.currentTimeMillis()
        )
        db.child(trip).child(key).setValue(payload)
    }

    override fun onCleared() {
        super.onCleared()
        unbind()
    }
}