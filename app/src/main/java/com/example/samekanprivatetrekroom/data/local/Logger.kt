package com.example.samekanprivatetrekroom.data.local

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logger {
    private val _logsFlow = MutableStateFlow<List<String>>(emptyList())
    val logsFlow: StateFlow<List<String>> = _logsFlow.asStateFlow()

    fun info(tag: String, message: String) {
        val formatted = formatLog("INFO", tag, message)
        Log.i(tag, message)
        appendLog(formatted)
    }

    fun debug(tag: String, message: String) {
        val formatted = formatLog("DEBUG", tag, message)
        Log.d(tag, message)
        appendLog(formatted)
    }

    fun warn(tag: String, message: String) {
        val formatted = formatLog("WARN", tag, message)
        Log.w(tag, message)
        appendLog(formatted)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        val formatted = formatLog("ERROR", tag, "$message ${throwable?.message ?: ""}")
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
        appendLog(formatted)
    }

    private fun formatLog(level: String, tag: String, message: String): String {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        return "[$timestamp] [$level] [$tag] $message"
    }

    private fun appendLog(log: String) {
        val current = _logsFlow.value.toMutableList()
        current.add(0, log)
        if (current.size > 200) {
            current.removeAt(current.size - 1)
        }
        _logsFlow.value = current
    }

    fun clear() {
        _logsFlow.value = emptyList()
    }
}
