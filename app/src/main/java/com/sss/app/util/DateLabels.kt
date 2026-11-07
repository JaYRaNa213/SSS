package com.sss.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DateLabels {
    fun day(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

    /** "Today", "Yesterday", else d/M/yyyy (example 26/2/2026). */
    fun dayLabel(d: LocalDate): String {
        val today = LocalDate.now()
        return when (d) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> "${d.dayOfMonth}/${d.monthValue}/${d.year}"
        }
    }

    /** Folder card subtitle part: "today 7:38", "yesterday", "26/2/2026", or "empty". */
    fun updated(ms: Long?): String {
        if (ms == null) return "empty"
        val d = day(ms)
        return when (val label = dayLabel(d)) {
            "Today" -> "Today " + Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("H:mm"))
            "Yesterday" -> "Yesterday"
            else -> label
        }
    }
}