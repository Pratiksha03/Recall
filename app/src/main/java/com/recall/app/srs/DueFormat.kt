package com.recall.app.srs

import java.util.concurrent.TimeUnit

/** Turns a due timestamp into something readable, e.g. "due now", "in 3 days". */
fun formatDue(dueAt: Long, now: Long = System.currentTimeMillis()): String {
    val diff = dueAt - now
    if (diff <= 0) return "due now"
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    return when {
        hours < 1 -> "in <1 h"
        days < 1 -> "in ${hours}h"
        days == 1L -> "tomorrow"
        days < 30 -> "in $days days"
        days < 365 -> "in ${days / 30} mo"
        else -> "in ${days / 365} yr"
    }
}
