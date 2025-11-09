package com.example.scrap7.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class ValueEventListenerAdapter(
    private val onDataChangeCb: (DataSnapshot) -> Unit,
    private val onCancelledCb: (DatabaseError) -> Unit
) : ValueEventListener {
    override fun onDataChange(snapshot: DataSnapshot) = onDataChangeCb(snapshot)
    override fun onCancelled(error: DatabaseError) = onCancelledCb(error)
}