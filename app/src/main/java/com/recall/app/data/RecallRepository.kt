package com.recall.app.data

import android.content.Context
import com.recall.app.srs.Rating
import com.recall.app.srs.Sm2
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

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
     */
    suspend fun review(card: Card, rating: Rating): Card {
        val updated = Sm2.apply(card, rating)
        dao.updateCard(updated)
        return updated
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
}
