package com.example.scrap7

import android.app.Application
import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class CounterViewModel(application: Application): AndroidViewModel(application) {

    private val context = getApplication<Application>().applicationContext

    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow() //as State<Int>

    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    init {
        // Load saved count from DataStore
        viewModelScope.launch {
            CounterPreferences.getCounterFlow(context)
                .collect { saved ->
                    _count.value = saved
                    _history.value = listOf("Restored count to $saved")
                }
        }
    }

    fun increment() {
        _count.value++
        save()
        addToHistory("Incremented to ${_count.value}")
    }

    fun reset() {
        _count.value = 0
        save()
        addToHistory("Reset to 0")
    }

    private fun addToHistory(message: String) {
        _history.value = _history.value + message
    }

    private fun save() {
        viewModelScope.launch {
            CounterPreferences.saveCounter(context, _count.value)
        }
    }

    fun getHistoryAsCsv():String {
        val header = "Index,Action"
        val rows = _history.value.mapIndexed { index, entry ->
            "${index + 1},\"$entry\""
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    fun saveHistoryCsvToFile(context: Context): File {
        val csv = getHistoryAsCsv()

        val fileName = "counter_history.csv"
        val file = File(context.cacheDir, fileName)

        file.writeText(csv)

        return file
    }

    init {
        println("CounterViewModel created: ${this.hashCode()}")
    }
}