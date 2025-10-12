package com.example.scrap7

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrap7.model.ChatMessage
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MessagingViewModel(
    private val db: FirebaseDatabase = FirebaseDatabase.getInstance(),
    private val auth: FirebaseAuth = Firebase.auth
) : ViewModel() {

    private var tripMessagesRef: DatabaseReference? = null
    private var childListener: ChildEventListener? = null

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private var _tripId: String? = null
    private var _myUserId: String? = null

    /**
     * Begin listening to /trips/{tripId}/messages. Call again if tripId/user changes.
     */
    fun bind(tripId: String, myUserId: String) {
        if (_tripId == tripId && _myUserId == myUserId) return
        unbind()

        _tripId = tripId
        _myUserId = myUserId

        val ref = db.reference.child("trips").child(tripId).child("messages")
        tripMessagesRef = ref

        val buffer = mutableListOf<ChatMessage>()

        childListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                snapshot.toChatMessage()?.let { msg ->
                    buffer.add(msg)
                    buffer.sortBy { it.timestamp }
                    viewModelScope.launch { _messages.emit(buffer.toList()) }
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val updated = snapshot.toChatMessage() ?: return
                val idx = buffer.indexOfFirst { it.id == updated.id }
                if (idx >= 0) {
                    buffer[idx] = updated
                    buffer.sortBy { it.timestamp }
                    viewModelScope.launch { _messages.emit(buffer.toList()) }
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val id = snapshot.key ?: return
                val idx = buffer.indexOfFirst { it.id == id }
                if (idx >= 0) {
                    buffer.removeAt(idx)
                    viewModelScope.launch { _messages.emit(buffer.toList()) }
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onCancelled(error: DatabaseError) = Unit
        }

        // Load the latest chunk and stream new ones
        ref.limitToLast(200).addChildEventListener(childListener as ChildEventListener)
    }

    /**
     * Stop listening (call when panel/screen leaves composition).
     */
    fun unbind() {
        childListener?.let { l -> tripMessagesRef?.removeEventListener(l) }
        childListener = null
        tripMessagesRef = null
        _messages.value = emptyList()
        _tripId = null
        _myUserId = null
    }

    /**
     * Push a message. Uses ServerValue.TIMESTAMP (server clock).
     */
    fun send(textRaw: String) {
        val tripId = _tripId ?: return
        val myId = _myUserId ?: auth.currentUser?.uid ?: return
        val text = textRaw.trim()
        if (text.isEmpty()) return

        val ref = db.reference.child("trips").child(tripId).child("messages").push()
        val payload = mapOf(
            "senderId" to myId,
            "text" to text,
            "timestamp" to ServerValue.TIMESTAMP
        )
        ref.updateChildren(payload)
    }

    override fun onCleared() {
        super.onCleared()
        unbind()
    }
}

/* ---------- Helpers ---------- */

private fun DataSnapshot.toChatMessage(): ChatMessage? {
    val id = key ?: return null
    val senderId = child("senderId").getValue(String::class.java) ?: return null
    val text = child("text").getValue(String::class.java) ?: ""
    // Backward-compat: accept either "timestamp" or legacy "timeStamp"
    val ts = when {
        child("timestamp").exists() -> child("timestamp").getValue(Long::class.java) ?: 0L
        child("timeStamp").exists() -> child("timeStamp").getValue(Long::class.java) ?: 0L
        else -> 0L
    }
    return ChatMessage(id = id, senderId = senderId, text = text, timestamp = ts)
}