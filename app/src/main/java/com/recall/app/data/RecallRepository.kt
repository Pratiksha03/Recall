package com.recall.app.data

import android.content.Context
import com.recall.app.srs.Rating
import com.recall.app.srs.Sm2
import com.recall.app.srs.Stats
import com.recall.app.srs.StatsSnapshot
import com.recall.app.srs.StatsWindow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * The single place the UI talks to for data. Screens never touch the DAO directly
 * — this keeps the "what do I do" logic out of the "how do I draw it" code.
 */
class RecallRepository(private val context: Context) {

    private val dao = RecallDatabase.get(context).dao()

    /**
     * Deck list with live due counts.
     *
     * Room re-runs a query when the *table* changes, but "how many cards are due"
     * also changes as the clock moves, which no table write announces. So this
     * re-subscribes on a slow ticker: you get an instant update on any edit, and
     * cards that come due while the app is open appear within a minute.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun decks(): Flow<List<DeckWithCounts>> =
        ticker().flatMapLatest { dao.observeDecks() }
    fun allDecks(): Flow<List<Deck>> = dao.observeAllDecks()
    fun deck(deckId: Long): Flow<Deck?> = dao.observeDeck(deckId)
    fun cardsInDeck(deckId: Long): Flow<List<Card>> = dao.observeCardsInDeck(deckId)
    @OptIn(ExperimentalCoroutinesApi::class)
    fun totalDue(): Flow<Int> = ticker().flatMapLatest { dao.observeTotalDue() }

    /** Emits immediately, then once a minute, to re-trigger clock-dependent queries. */
    private fun ticker(periodMs: Long = 60_000L): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(periodMs)
        }
    }

    suspend fun createDeck(name: String, colorIndex: Int): Long =
        dao.insertDeck(Deck(name = name.trim(), colorIndex = colorIndex))

    suspend fun renameDeck(deck: Deck, name: String) = dao.updateDeck(deck.copy(name = name.trim()))

    suspend fun deleteDeck(deck: Deck) = dao.deleteDeck(deck)

    suspend fun addCard(
        deckId: Long,
        question: String,
        answer: String,
        answerType: AnswerType,
        note: String
    ): Long = dao.insertCard(
        Card(
            deckId = deckId,
            question = question.trim(),
            answer = answer.trim(),
            answerType = answerType,
            note = note.trim()
        )
    )

    suspend fun updateCard(card: Card) = dao.updateCard(card)

    /**
     * Apply edits to an existing card.
     *
     * Scheduling state (interval, ease, dueAt, lapses) is deliberately untouched:
     * fixing a typo should not throw away the review history that earned this card
     * its current interval. If the answer was a file and has been replaced, the old
     * file is deleted here — the only point at which we know it is really orphaned.
     */
    suspend fun editCard(
        original: Card,
        deckId: Long,
        question: String,
        answer: String,
        answerType: AnswerType,
        note: String
    ) {
        val replacedFile = original.answerType.isFile && original.answer != answer
        dao.updateCard(
            original.copy(
                deckId = deckId,
                question = question.trim(),
                answer = if (answerType.isFile) answer else answer.trim(),
                answerType = answerType,
                note = note.trim()
            )
        )
        if (replacedFile) MediaStore.delete(original.answer)
    }

    suspend fun cardById(id: Long): Card? = dao.cardById(id)

    suspend fun deleteCard(card: Card) {
        if (card.answerType.isFile) MediaStore.delete(card.answer)
        dao.deleteCard(card)
    }

    suspend fun dueCards(deckId: Long): List<Card> =
        dao.dueCards(deckId, System.currentTimeMillis())

    /**
     * Grade a card and push it into the future.
     * Returns the rescheduled card so the caller keeps working from fresh state
     * rather than the snapshot it passed in.
     *
     * The journal row is written from the card as it was *before* the grade landed,
     * because that is the state the answer was actually given against — once
     * [Sm2.apply] has run, the interval you were tested on no longer exists anywhere.
     */
    suspend fun review(card: Card, rating: Rating): Card {
        val now = System.currentTimeMillis()
        val updated = Sm2.apply(card, rating, now)
        dao.updateCard(updated)
        dao.insertReview(
            ReviewLog(
                cardId = card.id,
                deckId = card.deckId,
                reviewedAt = now,
                rating = rating,
                remembered = rating != Rating.AGAIN,
                intervalBefore = card.intervalDays,
                intervalAfter = updated.intervalDays,
                easeAfter = updated.easeFactor
            )
        )
        return updated
    }

    // ----- progress -----

    /** Re-emits whenever a card is graded, so the Progress screen stays live. */
    fun reviewCount(): Flow<Int> = dao.observeReviewCount()

    /**
     * Gather everything the Progress screen shows in one pass.
     *
     * Five small reads and then pure Kotlin: the aggregation lives in [Stats] so it
     * can be tested without a database, and this function stays a list of the things
     * that have to be fetched.
     */
    suspend fun stats(window: StatsWindow, zone: ZoneId = ZoneId.systemDefault()): StatsSnapshot {
        val now = System.currentTimeMillis()
        val today = LocalDate.now(zone)
        // Midnight at the far edge of the window, not "now minus N x 24h" — the first
        // bar of the chart is a whole day, and half of it is not in the window.
        val since = today.minusDays((window.days - 1).toLong())
            .atStartOfDay(zone).toInstant().toEpochMilli()

        return Stats.build(
            window = window,
            logs = dao.reviewsSince(since),
            decks = dao.allDecksOnce(),
            schedules = dao.cardSchedules(),
            studiedStamps = dao.studiedDayStamps(tzOffsetMs(zone, now)),
            totalReviews = dao.totalReviews(),
            hardestCards = dao.mostLapsedCards(HARDEST_CARDS),
            zone = zone,
            today = today,
            now = now
        )
    }

    private fun tzOffsetMs(zone: ZoneId, now: Long): Long =
        TimeUnit.SECONDS.toMillis(
            zone.rules.getOffset(Instant.ofEpochMilli(now)).totalSeconds.toLong()
        )

    /** Reuse a deck of this name if it exists, otherwise make one. */
    suspend fun findOrCreateDeck(name: String, colorIndex: Int = 0): Long {
        val trimmed = name.trim()
        return dao.deckByName(trimmed)?.id ?: dao.insertDeck(Deck(name = trimmed, colorIndex = colorIndex))
    }

    /**
     * Write imported cards into a deck. Every card starts unseen and due now,
     * which is what Anki does with newly added notes.
     *
     * createdAt is nudged by the index so the browser's "newest first" ordering is
     * stable rather than arbitrary within a single import.
     */
    suspend fun importInto(deckId: Long, cards: List<ImportedCard>): Int {
        val now = System.currentTimeMillis()
        dao.insertCards(
            cards.mapIndexed { index, c ->
                Card(
                    deckId = deckId,
                    question = c.question,
                    answer = c.answer,
                    answerType = c.answerType,
                    note = c.tags,
                    createdAt = now + index,
                    dueAt = now
                )
            }
        )
        return cards.size
    }

    /** First launch: give the user something to look at instead of an empty screen. */
    suspend fun seedIfEmpty() {
        if (dao.deckCount() > 0) return
        val deckId = dao.insertDeck(Deck(name = "Getting started", colorIndex = 0))
        val samples = listOf(
            Triple(
                "What is spaced repetition?",
                "Reviewing material at growing intervals — right before you would have forgotten it. Recall gets harder each time, which is what makes it stick.",
                AnswerType.TEXT
            ),
            Triple(
                "Where can I read about the SM-2 algorithm?",
                "https://en.wikipedia.org/wiki/SuperMemo",
                AnswerType.LINK
            ),
            Triple(
                "What do the four buttons mean?",
                "Again resets the card to tomorrow. Hard, Good and Easy each push it further out, scaled by how easy the card has been for you so far.",
                AnswerType.TEXT
            )
        )
        samples.forEach { (q, a, type) ->
            dao.insertCard(Card(deckId = deckId, question = q, answer = a, answerType = type))
        }
    }

    private companion object {
        /** How many "you keep forgetting this" cards the Progress screen lists. */
        const val HARDEST_CARDS = 5
    }
}

