package com.example.scrap7

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("Scrap7", "BroadcastReceiver received something")

        if (intent.action == "com.example.scrap7.ACTION_RESET") {
            Log.d("Scrap7", "Matched ACTION_RESET")

            // Save flag to SharedPreferences
            context.getSharedPreferences("scrap7_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("reset_requested", true)
                .apply()

            Log.d("Scrap7", "Reset flag saved to SharedPreferences")
        }
    }
}