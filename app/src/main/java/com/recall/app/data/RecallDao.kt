package com.recall.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * The data-access layer. Room writes the implementation at build time.
 *
 * `Flow<T>` is a stream: the UI subscribes once and Room re-emits automatically
 * whenever the underlying table changes. `suspend` marks a function that must be
 * called from a coroutine — it runs off the main thread.
 */
@Dao
interface RecallDao {

    /*
     * NOW_MS below is "right now" in epoch milliseconds, evaluated by SQLite each
     * time the query runs rather than passed in from Kotlin. Passing a timestamp in
     * would freeze it at whenever the Flow was created, which silently goes stale.
     *
     * It is spelled the long way because strftime('%s') only has whole-second
     * precision — a card saved 400 ms ago would not count as due until the next
     * second ticked over. julianday keeps the milliseconds.
     * (julianday('now') - 2440587.5) is days since the Unix epoch; x 86400000 is ms.
     */

    // ----- decks -----

    @Query(
        """
        SELECT d.id           AS id,
               d.name         AS name,
               d.colorIndex   AS colorIndex,
               (SELECT COUNT(*) FROM cards c WHERE c.deckId = d.id) AS totalCards,
               (SELECT COUNT(*) FROM cards c
                 WHERE c.deckId = d.id
                   AND c.dueAt <= CAST((julianday('now') - 2440587.5) * 86400000.0 AS INTEGER)
               ) AS dueCards
        FROM decks d
        ORDER BY d.createdAt DESC
        """
    )
    fun observeDecks(): Flow<List<DeckWithCounts>>

    @Query("SELECT * FROM decks ORDER BY createdAt DESC")
    fun observeAllDecks(): Flow<List<Deck>>

    @Query("SELECT * FROM decks WHERE id = :deckId")
    fun observeDeck(deckId: Long): Flow<Deck?>

    @Insert
    suspend fun insertDeck(deck: Deck): Long

    @Query("SELECT * FROM decks WHERE name = :name LIMIT 1")
    suspend fun deckByName(name: String): Deck?

    @Update
    suspend fun updateDeck(deck: Deck)

    @Delete
    suspend fun deleteDeck(deck: Deck)

    @Query("SELECT COUNT(*) FROM decks")
    suspend fun deckCount(): Int

    // ----- cards -----

    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY createdAt DESC")
    fun observeCardsInDeck(deckId: Long): Flow<List<Card>>

    @Query("SELECT * FROM cards ORDER BY createdAt DESC")
    fun observeAllCards(): Flow<List<Card>>

    /** The review queue: everything due now, oldest due first. */
    @Query("SELECT * FROM cards WHERE deckId = :deckId AND dueAt <= :now ORDER BY dueAt ASC")
    suspend fun dueCards(deckId: Long, now: Long): List<Card>

    @Query(
        "SELECT COUNT(*) FROM cards " +
            "WHERE dueAt <= CAST((julianday('now') - 2440587.5) * 86400000.0 AS INTEGER)"
    )
    fun observeTotalDue(): Flow<Int>

    /** One-shot version of the above, for the background reminder. */
    @Query(
        "SELECT COUNT(*) FROM cards " +
            "WHERE dueAt <= CAST((julianday('now') - 2440587.5) * 86400000.0 AS INTEGER)"
    )
    suspend fun dueCountNow(): Int

    @Insert
    suspend fun insertCard(card: Card): Long

    /** Bulk insert for imports — one transaction instead of N. */
    @Insert
    suspend fun insertCards(cards: List<Card>)

    @Update
    suspend fun updateCard(card: Card)

    @Delete
    suspend fun deleteCard(card: Card)

    @Query("SELECT * FROM cards WHERE id = :cardId")
    suspend fun cardById(cardId: Long): Card?

    /** Scheduling state only, for the forecast and the new/young/mature mix. */
    @Query("SELECT dueAt, intervalDays, repetition, lapses FROM cards")
    suspend fun cardSchedules(): List<CardSchedule>

    /** The cards you keep forgetting, worst first. */
    @Query("SELECT * FROM cards WHERE lapses > 0 ORDER BY lapses DESC, dueAt ASC LIMIT :limit")
    suspend fun mostLapsedCards(limit: Int): List<Card>

    @Query("SELECT * FROM decks")
    suspend fun allDecksOnce(): List<Deck>

    // ----- review history -----

    @Insert
    suspend fun insertReview(log: ReviewLog)

    /**
     * Every answer inside the window the Progress screen is showing.
     *
     * Rows rather than SUM()s on purpose: the day a review belongs to is a *local*
     * date, and SQLite has no idea what timezone you were in or whether the clocks
     * changed. Kotlin does. Ninety days of heavy study is a few thousand small rows,
     * which is cheaper to hand over than a wrong answer is to explain.
     */
    @Query("SELECT * FROM reviews WHERE reviewedAt >= :since ORDER BY reviewedAt ASC")
    suspend fun reviewsSince(since: Long): List<ReviewLog>

    @Query("SELECT COUNT(*) FROM reviews")
    suspend fun totalReviews(): Int

    /** Drives a refresh of the whole Progress screen whenever a card is graded. */
    @Query("SELECT COUNT(*) FROM reviews")
    fun observeReviewCount(): Flow<Int>

    /**
     * One timestamp per day you have ever studied — the streak needs the whole of
     * history, and this is how to get it without loading the whole of history.
     *
     * The bucket is a crude fixed-offset day, used only to *thin* the rows; Kotlin
     * then decides which local date each timestamp really falls on. Taking both the
     * first and the last review of each bucket means a bucket that straddles two
     * local dates (a clock change) still reports both of them.
     */
    @Query(
        """
        SELECT MIN(reviewedAt) FROM reviews GROUP BY (reviewedAt + :tzOffsetMs) / 86400000
        UNION
        SELECT MAX(reviewedAt) FROM reviews GROUP BY (reviewedAt + :tzOffsetMs) / 86400000
        """
    )
    suspend fun studiedDayStamps(tzOffsetMs: Long): List<Long>
}
