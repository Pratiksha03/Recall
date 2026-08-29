package com.recall.app.srs

import com.recall.app.data.Card
import java.util.concurrent.TimeUnit

/**
 * How well you remembered a card. These map onto Anki's four buttons.
 * `quality` is the 0..5 grade the SM-2 algorithm expects.
 */
enum class Rating(val label: String, val quality: Int) {
    AGAIN("Again", 0),
    HARD("Hard", 3),
    GOOD("Good", 4),
    EASY("Easy", 5)
}

/**
 * SuperMemo-2, the algorithm Anki is built on, in about twenty lines.
 *
 * The idea: every card carries an "ease factor" (how easy it is for you) and an
 * interval (how many days until you see it again). Rate it well and the interval
 * multiplies by the ease factor. Fail it and the interval resets to one day.
 *
 * `object` is Kotlin for a singleton — the equivalent of a Java class with only
 * static methods.
 */
object Sm2 {

    private const val MIN_EASE = 1.3

    fun apply(card: Card, rating: Rating, now: Long = System.currentTimeMillis()): Card {
        val quality = rating.quality

        // Failed: start the card over, but keep (a slightly reduced) ease.
        if (quality < 3) {
            return card.copy(
                repetition = 0,
                intervalDays = 1,
                easeFactor = (card.easeFactor - 0.20).coerceAtLeast(MIN_EASE),
                lapses = card.lapses + 1,
                dueAt = now + TimeUnit.DAYS.toMillis(1)
            )
        }

        val repetition = card.repetition + 1
        val interval = when (repetition) {
            1 -> if (rating == Rating.EASY) 3 else 1
            2 -> if (rating == Rating.EASY) 6 else 4
            else -> Math.round(card.intervalDays * card.easeFactor).toInt().coerceAtLeast(1)
        }

        // The SM-2 ease update formula, verbatim.
        val ease = (card.easeFactor + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02)))
            .coerceAtLeast(MIN_EASE)

        return card.copy(
            repetition = repetition,
            intervalDays = interval,
            easeFactor = ease,
            dueAt = now + TimeUnit.DAYS.toMillis(interval.toLong())
        )
    }

    /** "Good in 4 days" — the little hint printed under each rating button. */
    fun previewInterval(card: Card, rating: Rating): String {
        val next = apply(card, rating)
        return when (val d = next.intervalDays) {
            1 -> "1 day"
            in 2..30 -> "$d days"
            in 31..364 -> "${Math.round(d / 30.0)} mo"
            else -> "${Math.round(d / 365.0)} yr"
        }
    }
}
