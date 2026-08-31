package com.recall.app

import com.recall.app.data.CardSchedule
import com.recall.app.data.Deck
import com.recall.app.data.ReviewLog
import com.recall.app.srs.Rating
import com.recall.app.srs.Stats
import com.recall.app.srs.StatsWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The arithmetic behind the Progress screen.
 *
 * Everything here runs on a plain JVM because [Stats] takes the clock and the
 * timezone as arguments instead of reading them. The interesting cases are all
 * about *which* answers count, not about how to add up.
 */
class StatsTest {

    private val zone: ZoneId = ZoneId.of("America/New_York")
    private val today: LocalDate = LocalDate.of(2026, 3, 15)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    private fun log(
        date: LocalDate,
        hour: Int,
        rating: Rating,
        intervalBefore: Int,
        cardId: Long = 1,
        deckId: Long = 1,
        minute: Int = 0
    ) = ReviewLog(
        cardId = cardId,
        deckId = deckId,
        reviewedAt = at(date, hour, minute),
        rating = rating,
        remembered = rating != Rating.AGAIN,
        intervalBefore = intervalBefore,
        intervalAfter = if (rating == Rating.AGAIN) 1 else intervalBefore * 2,
        easeAfter = 2.5
    )

    private fun build(
        logs: List<ReviewLog>,
        schedules: List<CardSchedule> = emptyList(),
        decks: List<Deck> = listOf(Deck(id = 1, name = "Kotlin")),
        studiedStamps: List<Long> = logs.map { it.reviewedAt },
        window: StatsWindow = StatsWindow.MONTH
    ) = Stats.build(
        window = window,
        logs = logs.sortedBy { it.reviewedAt },
        decks = decks,
        schedules = schedules,
        studiedStamps = studiedStamps,
        totalReviews = logs.size,
        hardestCards = emptyList(),
        zone = zone,
        today = today,
        now = at(today, 12)
    )

    @Test
    fun `a first look at a new card cannot count against retention`() {
        val stats = build(
            listOf(
                log(today, 9, Rating.AGAIN, intervalBefore = 0, cardId = 1),
                log(today, 9, Rating.GOOD, intervalBefore = 0, cardId = 2),
                log(today, 10, Rating.GOOD, intervalBefore = 5, cardId = 3)
            )
        )

        assertEquals(1, stats.overall.attempts)
        assertEquals(1, stats.overall.remembered)
        // The chart still shows all three, because you did do three reviews.
        assertEquals(3, stats.days.last().total)
        assertEquals(2, stats.days.last().firstLooks)
    }

    @Test
    fun `failing then passing the same card in one session still counts as forgotten`() {
        // The exact shape of a session: fail it, it comes back, you get it right.
        val stats = build(
            listOf(
                log(today, 9, Rating.AGAIN, intervalBefore = 10, cardId = 7),
                log(today, 9, Rating.GOOD, intervalBefore = 1, cardId = 7, minute = 4)
            )
        )

        assertEquals(1, stats.overall.attempts)
        assertEquals(0, stats.overall.remembered)
        assertEquals(0f, stats.overall.rate!!, 0.0001f)
    }

    @Test
    fun `a new card failed then passed in one session counts neither way`() {
        // The tempting bug: drop the first look for being new, and the retry that
        // follows it becomes the card's answer for the day — a card you did not know
        // scoring 100%.
        val stats = build(
            listOf(
                log(today, 9, Rating.AGAIN, intervalBefore = 0, cardId = 7),
                log(today, 9, Rating.GOOD, intervalBefore = 1, cardId = 7, minute = 6)
            )
        )

        assertEquals(0, stats.overall.attempts)
        assertNull(stats.overall.rate)
        assertEquals(2, stats.days.last().total)
    }

    @Test
    fun `the same card on two different days counts twice`() {
        val stats = build(
            listOf(
                log(today.minusDays(3), 9, Rating.GOOD, intervalBefore = 4, cardId = 7),
                log(today, 9, Rating.AGAIN, intervalBefore = 8, cardId = 7)
            )
        )

        assertEquals(2, stats.overall.attempts)
        assertEquals(1, stats.overall.remembered)
    }

    @Test
    fun `young and mature are split at three weeks`() {
        val stats = build(
            listOf(
                log(today, 9, Rating.GOOD, intervalBefore = 20, cardId = 1),
                log(today, 9, Rating.AGAIN, intervalBefore = 21, cardId = 2),
                log(today, 9, Rating.GOOD, intervalBefore = 400, cardId = 3)
            )
        )

        assertEquals(1, stats.young.attempts)
        assertEquals(1, stats.young.remembered)
        assertEquals(2, stats.mature.attempts)
        assertEquals(1, stats.mature.remembered)
    }

    @Test
    fun `no answered card means no rate rather than zero percent`() {
        val stats = build(listOf(log(today, 9, Rating.GOOD, intervalBefore = 0)))

        assertNull(stats.overall.rate)
        assertEquals(1, stats.totalReviews)
    }

    @Test
    fun `the chart has one bar per day of the window, oldest first`() {
        val stats = build(
            logs = listOf(log(today.minusDays(2), 9, Rating.GOOD, intervalBefore = 3)),
            window = StatsWindow.WEEK
        )

        assertEquals(7, stats.days.size)
        assertEquals(today.minusDays(6), stats.days.first().date)
        assertEquals(today, stats.days.last().date)
        assertEquals(1, stats.days[4].total)
        assertEquals(1, stats.daysStudiedInWindow)
    }

    @Test
    fun `reviews late at night land on the day you were having, not UTC's`() {
        // 23:30 on the 14th in New York is already 04:30 on the 15th in UTC. Bucket
        // by anything but the local date and last night's session moves to today.
        val stats = build(listOf(log(today.minusDays(1), 23, Rating.GOOD, intervalBefore = 3, minute = 30)))

        assertEquals(1, stats.days[stats.days.size - 2].total)
        assertEquals(0, stats.days.last().total)
    }

    @Test
    fun `an unfinished today does not break the streak`() {
        val yesterday = today.minusDays(1)
        val dates = setOf(yesterday, today.minusDays(2), today.minusDays(3))

        assertEquals(3, Stats.streak(dates, today))
        assertEquals(4, Stats.streak(dates + today, today))
    }

    @Test
    fun `a gap ends the streak`() {
        val dates = setOf(today, today.minusDays(1), today.minusDays(3))

        assertEquals(2, Stats.streak(dates, today))
    }

    @Test
    fun `never studied is a streak of zero`() {
        assertEquals(0, Stats.streak(emptySet(), today))
    }

    @Test
    fun `overdue cards pile onto today's forecast bar`() {
        val schedules = listOf(
            CardSchedule(dueAt = at(today.minusDays(9), 8), intervalDays = 5, repetition = 2, lapses = 0),
            CardSchedule(dueAt = at(today, 6), intervalDays = 5, repetition = 2, lapses = 0),
            CardSchedule(dueAt = at(today.plusDays(3), 8), intervalDays = 30, repetition = 6, lapses = 0),
            // Beyond the fortnight the chart covers, so it appears nowhere.
            CardSchedule(dueAt = at(today.plusDays(60), 8), intervalDays = 90, repetition = 9, lapses = 0)
        )
        val stats = build(logs = emptyList(), schedules = schedules)

        assertEquals(14, stats.forecast.size)
        assertEquals(today, stats.forecast.first().date)
        assertEquals(2, stats.forecast.first().due)
        assertEquals(1, stats.forecast[3].due)
        assertEquals(3, stats.forecast.sumOf { it.due })
    }

    @Test
    fun `the mix counts an unseen card as new and a relearning card as young`() {
        val schedules = listOf(
            CardSchedule(dueAt = 0, intervalDays = 0, repetition = 0, lapses = 0),
            // Just lapsed: back to a one-day interval, so it is young again.
            CardSchedule(dueAt = 0, intervalDays = 1, repetition = 0, lapses = 3),
            CardSchedule(dueAt = 0, intervalDays = 20, repetition = 4, lapses = 0),
            CardSchedule(dueAt = 0, intervalDays = 21, repetition = 5, lapses = 0)
        )
        val stats = build(logs = emptyList(), schedules = schedules)

        assertEquals(1, stats.mix.new)
        assertEquals(2, stats.mix.young)
        assertEquals(1, stats.mix.mature)
    }

    @Test
    fun `decks are listed weakest first and silent decks are left out`() {
        val decks = listOf(
            Deck(id = 1, name = "Kotlin"),
            Deck(id = 2, name = "Spanish"),
            Deck(id = 3, name = "Untouched")
        )
        val stats = build(
            logs = listOf(
                log(today, 9, Rating.GOOD, intervalBefore = 5, cardId = 1, deckId = 1),
                log(today, 9, Rating.GOOD, intervalBefore = 5, cardId = 2, deckId = 1),
                log(today, 9, Rating.AGAIN, intervalBefore = 5, cardId = 3, deckId = 2)
            ),
            decks = decks
        )

        assertEquals(listOf("Spanish", "Kotlin"), stats.byDeck.map { it.name })
        assertEquals(0f, stats.byDeck.first().retention.rate!!, 0.0001f)
        assertEquals(1f, stats.byDeck.last().retention.rate!!, 0.0001f)
    }

    @Test
    fun `the grade breakdown counts every answer, first look or not`() {
        val stats = build(
            listOf(
                log(today, 9, Rating.AGAIN, intervalBefore = 0, cardId = 1),
                log(today, 9, Rating.EASY, intervalBefore = 0, cardId = 2),
                log(today, 9, Rating.EASY, intervalBefore = 9, cardId = 3)
            )
        )

        assertEquals(1, stats.grades[Rating.AGAIN])
        assertEquals(2, stats.grades[Rating.EASY])
        assertEquals(0, stats.grades[Rating.HARD])
        assertEquals(3, stats.reviewsInWindow)
    }
}
