package com.recall.app.srs

import com.recall.app.data.Card
import java.util.concurrent.TimeUnit

/**
 * What to show against a card in the deck browser.
 *
 * A card you have never reviewed is technically due right now, but labelling a
 * card you just created "due now" reads like a warning about something overdue.
 * Until it has been graded at least once it is simply new.
 */
fun dueLabel(card: Card): String =
    if (card.repetition == 0 && card.lapses == 0) "New" else formatDue(card.dueAt)

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
