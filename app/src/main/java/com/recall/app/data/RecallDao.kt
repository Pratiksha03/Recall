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

    @Update
    suspend fun updateCard(card: Card)

    @Delete
    suspend fun deleteCard(card: Card)

    @Query("SELECT * FROM cards WHERE id = :cardId")
    suspend fun cardById(cardId: Long): Card?
}
