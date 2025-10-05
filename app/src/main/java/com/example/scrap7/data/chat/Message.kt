package com.example.scrap7.data.chat

import com.google.firebase.database.Exclude

data class Message(
    val id: String = "",
    val tripId: String = "",
    val fromUid: String = "",
    val toUid: String = "",
    val text: String = "",
    val ts: Long = 0L,             // server-resolved or client fallback
    val delivery: Delivery = Delivery.Sent
) {
    enum class Delivery { Sent, Delivered, Read }

    @Exclude
    fun isMine(currentUid: String) = fromUid == currentUid
}