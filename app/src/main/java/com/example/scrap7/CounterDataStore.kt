package com.example.scrap7

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.counterDataStore by preferencesDataStore(name = "counter_prefs")

object CounterPreferences {
    private val COUNTER_KEY = intPreferencesKey("counter_value")

    fun getCounterFlow(context: Context): Flow<Int> =
        context.counterDataStore.data.map { prefs ->
            prefs[COUNTER_KEY] ?: 0
        }

    suspend fun saveCounter(context: Context, value: Int) {
        context.counterDataStore.edit { prefs ->
            prefs[COUNTER_KEY] = value
        }
    }
}