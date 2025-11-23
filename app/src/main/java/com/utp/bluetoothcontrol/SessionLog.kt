package com.utp.bluetoothcontrol

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.*

enum class LogLevel { INFO, IN, OUT, WARN, ERR }
data class LogEntry(val time: String, val level: LogLevel, val text: String)

object SessionLog {
    private val TIME_FMT = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private const val MAX_ENTRIES = 300

    // observable list used directly by Compose UI
    val items = mutableStateListOf<LogEntry>()

    // same behavior as your UI helper: newest at index 0, trimmed to MAX_ENTRIES
    fun add(level: LogLevel, text: String) {
        val entry = LogEntry(TIME_FMT.format(Date()), level, text)
        items.add(0, entry)
        if (items.size > MAX_ENTRIES) items.removeLast()
    }

    fun clear() {
        items.clear()
        add(LogLevel.INFO, "Log cleared")
    }
}
