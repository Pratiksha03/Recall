package com.recall.app.srs

import com.recall.app.data.Card
import com.recall.app.data.CardSchedule
import com.recall.app.data.Deck
import com.recall.app.data.ReviewLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Turning a pile of [ReviewLog] rows into the numbers on the Progress screen.
 *
 * All of this is deliberately pure: dates, "now" and the timezone are arguments,
 * nothing here touches the database or the clock. That is what makes it testable on
 * a plain JVM (see StatsTest) instead of only on a phone, and it is why the day
 * bucketing lives in Kotlin rather than in SQL — java.time knows about local dates
 * and daylight saving, and SQLite does not.
 */

/** Anki's convention: a card is grown up once you can leave it for three weeks. */
const val MATURE_DAYS = 21

/** How much history the screen is looking at. */
enum class StatsWindow(val days: Int, val label: String) {
    WEEK(7, "7 days"),
    MONTH(30, "30 days"),
    QUARTER(90, "3 months")
}

/**
 * Reviews that counted, and how many of them you got right.
 *
 * [rate] is null rather than 0 when nothing counted — "you have not answered
 * anything yet" and "you got everything wrong" must not draw the same ring.
 */
data class Retention(val attempts: Int = 0, val remembered: Int = 0) {
    val rate: Float? get() = if (attempts == 0) null else remembered.toFloat() / attempts
    val forgotten: Int get() = attempts - remembered
    operator fun plus(other: Retention) =
        Retention(attempts + other.attempts, remembered + other.remembered)
}

/** One bar of the "reviews per day" chart. Counts every answer, not just the first. */
data class DayBar(
    val date: LocalDate,
    val remembered: Int = 0,
    val forgotten: Int = 0,
    val firstLooks: Int = 0
) {
    val total: Int get() = remembered + forgotten + firstLooks
}

/** One bar of the "what is coming" chart. */
data class ForecastDay(val date: LocalDate, val due: Int)

data class DeckRetention(val deckId: Long, val name: String, val retention: Retention)

/** How far along the collection is as a whole. */
data class CardMix(val new: Int = 0, val young: Int = 0, val mature: Int = 0) {
    val total: Int get() = new + young + mature
}

/** Everything the Progress screen draws, in one immutable snapshot. */
data class StatsSnapshot(
    val window: StatsWindow,
    val overall: Retention = Retention(),
    val young: Retention = Retention(),
    val mature: Retention = Retention(),
    val grades: Map<Rating, Int> = emptyMap(),
    val days: List<DayBar> = emptyList(),
    val forecast: List<ForecastDay> = emptyList(),
    val byDeck: List<DeckRetention> = emptyList(),
    val mix: CardMix = CardMix(),
    val streakDays: Int = 0,
    val daysStudiedInWindow: Int = 0,
    val reviewsInWindow: Int = 0,
    val totalReviews: Int = 0,
    val hardestCards: List<Card> = emptyList()
) {
    /** Nothing has ever been graded — the screen shows an explanation instead. */
    val isEmpty: Boolean get() = totalReviews == 0
}

object Stats {

    /** How many days of the forecast chart to draw. */
    private const val FORECAST_DAYS = 14

    /**
     * @param logs every review inside the window, oldest first
     * @param studiedStamps at least one timestamp from every day ever studied
     * @param today the local date to count backwards from
     */
    fun build(
        window: StatsWindow,
        logs: List<ReviewLog>,
        decks: List<Deck>,
        schedules: List<CardSchedule>,
        studiedStamps: List<Long>,
        totalReviews: Int,
        hardestCards: List<Card>,
        zone: ZoneId,
        today: LocalDate,
        now: Long
    ): StatsSnapshot {
        val dated = logs.map { it to it.reviewedAt.toLocalDate(zone) }

        /*
         * Retention counts the FIRST answer you gave a card on a given day, and only
         * for cards you had already learned.
         *
         * Both halves of that matter. A new card cannot be "forgotten" — you never
         * knew it — so counting first looks would drag the number down every time you
         * add cards. And a card you fail comes back later in the same session, so
         * counting every answer would let a card you got wrong repair its own score
         * the moment you got it right ten seconds later. First-answers-only is the
         * honest question: when this card came up today, did you remember it?
         *
         * The order matters. Grouping happens first and the new-card filter second,
         * so a new card you fail and then pass minutes later drops out of the sums
         * entirely — filtering first would have left the second attempt behind,
         * standing in as that card's answer for the day and scoring a clean 100%.
         */
        val counted = dated
            .groupBy { (log, date) -> log.cardId to date }
            .map { (_, sameDay) -> sameDay.first().first }
            .filter { it.intervalBefore > 0 }

        val overall = counted.toRetention()
        val young = counted.filter { it.intervalBefore < MATURE_DAYS }.toRetention()
        val mature = counted.filter { it.intervalBefore >= MATURE_DAYS }.toRetention()

        val byDeck = decks.mapNotNull { deck ->
            val slice = counted.filter { it.deckId == deck.id }.toRetention()
            if (slice.attempts == 0) null else DeckRetention(deck.id, deck.name, slice)
        }.sortedBy { it.retention.rate ?: 1f }

        // The chart, unlike retention, shows the work you actually did: every answer,
        // including the repeats and the new cards.
        val byDay = dated.groupBy({ it.second }, { it.first })
        val days = (window.days - 1 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            val onThatDay = byDay[date].orEmpty()
            DayBar(
                date = date,
                remembered = onThatDay.count { it.intervalBefore > 0 && it.remembered },
                forgotten = onThatDay.count { it.intervalBefore > 0 && !it.remembered },
                firstLooks = onThatDay.count { it.intervalBefore == 0 }
            )
        }

        // Anything already due (or overdue) is work for today, so it all lands on the
        // first bar rather than in the past where you cannot see it.
        val dueByDate = schedules
            .map { maxOf(it.dueAt, now).toLocalDate(zone) }
            .groupingBy { it }
            .eachCount()
        val forecast = (0 until FORECAST_DAYS).map { ahead ->
            val date = today.plusDays(ahead.toLong())
            ForecastDay(date, dueByDate[date] ?: 0)
        }

        val mix = schedules.fold(CardMix()) { acc, card ->
            when {
                // Sm2 never leaves a reviewed card at zero, so this is exactly
                // "never answered", lapses and repetitions included.
                card.intervalDays == 0 -> acc.copy(new = acc.new + 1)
                card.intervalDays < MATURE_DAYS -> acc.copy(young = acc.young + 1)
                else -> acc.copy(mature = acc.mature + 1)
            }
        }

        val studiedDates = studiedStamps.map { it.toLocalDate(zone) }.toSet()

        return StatsSnapshot(
            window = window,
            overall = overall,
            young = young,
            mature = mature,
            grades = Rating.entries.associateWith { rating -> logs.count { it.rating == rating } },
            days = days,
            forecast = forecast,
            byDeck = byDeck,
            mix = mix,
            streakDays = streak(studiedDates, today),
            daysStudiedInWindow = days.count { it.total > 0 },
            reviewsInWindow = logs.size,
            totalReviews = totalReviews,
            hardestCards = hardestCards
        )
    }

    /**
     * Consecutive days studied, counting back from today.
     *
     * A day you have not started yet does not break the streak — at 9am you have a
     * streak, not a zero — so counting begins at yesterday when today is empty.
     */
    fun streak(studiedDates: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in studiedDates) today else today.minusDays(1)
        var count = 0
        while (day in studiedDates) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    private fun List<ReviewLog>.toRetention() =
        Retention(attempts = size, remembered = count { it.remembered })

    private fun Long.toLocalDate(zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
}
