package com.example.scrap7

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.scrap7.Message
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase

@Composable
fun ChatScreen(tripId: String, userId: String) {
    val messages = remember { mutableStateListOf<Message>() }
    val messageText = remember { mutableStateOf("") }

    val messagesRef = FirebaseDatabase.getInstance().getReference("messages").child(tripId)

    // Listen for new messages
    LaunchedEffect(tripId) {
        messagesRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, prevChildKey: String?) {
                val msg = snapshot.getValue(Message::class.java)
                msg?.let { messages.add(it) }
            }
            override fun onCancelled(error: DatabaseError) {}
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
        })
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            reverseLayout = true
        ) {
            items(messages.reversed()) { msg ->
                Text(
                    text = "${if (msg.senderId == userId) "You" else "Them"}: ${msg.text}",
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = messageText.value,
                onValueChange = { messageText.value = it },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val newMessage = Message(
                        senderId = userId,
                        text = messageText.value,
                        timeStamp = System.currentTimeMillis()
                    )
                    messagesRef.push().setValue(newMessage)
                    messageText.value = ""
                },
                enabled = messageText.value.isNotBlank()
            ) {
                Text("Send")
            }
        }
    }
}
