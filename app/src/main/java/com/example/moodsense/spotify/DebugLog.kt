package com.example.moodsense.spotify

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max

object DebugLog {
    private const val MAX_LOG_ENTRIES = 200
    private val logBuffer: ArrayDeque<String> = ArrayDeque()
    private val timestampFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun i(tag: String, message: String) {
        Log.d(tag, message)
        append("I/$tag", message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        val composed = if (throwable != null) {
            "$message | " + formatThrowable(throwable)
        } else {
            message
        }
        append("E/$tag", composed)
    }

    fun recentLogLines(): List<String> = logBuffer.toList()

    private fun append(level: String, message: String) {
        val timestamp = timestampFormatter.format(Date())
        val lines = message.split('\n')
        lines.forEach { line ->
            logBuffer.addLast("[$timestamp] $level: ${line.trim()}")
        }
        trimIfNeeded()
    }

    private fun trimIfNeeded() {
        val overflow = logBuffer.size - MAX_LOG_ENTRIES
        if (overflow > 0) {
            repeat(max(overflow, 0)) {
                if (logBuffer.isNotEmpty()) {
                    logBuffer.removeFirst()
                }
            }
        }
    }

    fun formatThrowable(throwable: Throwable): String {
        val builder = StringBuilder()
        var current: Throwable? = throwable
        var depth = 0
        while (current != null && depth < 10) {
            if (depth > 0) builder.append(" -> ")
            builder.append(current::class.java.simpleName)
            current.message?.let { builder.append(": ").append(it) }
            current = current.cause
            depth += 1
        }
        return builder.toString()
    }
}
