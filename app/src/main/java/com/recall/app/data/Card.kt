package com.recall.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One question/answer pair, plus its spaced-repetition scheduling state.
 *
 * `answer` means different things depending on [answerType]:
 *  - TEXT  -> the answer text itself
 *  - LINK  -> the URL string
 *  - IMAGE -> a file:// style absolute path inside the app's private folder
 */
@Entity(
    tableName = "cards",
    foreignKeys = [
        ForeignKey(
            entity = Deck::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE   // delete a deck -> its cards go too
        )
    ],
    indices = [Index("deckId"), Index("dueAt")]
)
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deckId: Long,
    val question: String,
    val answer: String,
    val answerType: AnswerType = AnswerType.TEXT,
    val note: String = "",             // optional extra context shown under the answer

    // --- SM-2 scheduling state (see com.recall.app.srs.Sm2) ---
    val intervalDays: Int = 0,         // days until the next review
    val repetition: Int = 0,           // how many times in a row it was recalled
    val easeFactor: Double = 2.5,      // how "easy" this card is; 1.3 = brutal
    val dueAt: Long = System.currentTimeMillis(),   // epoch millis; <= now means due
    val createdAt: Long = System.currentTimeMillis(),
    val lapses: Int = 0                // times forgotten after having been learned
)
