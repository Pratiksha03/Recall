package com.recall.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.recall.app.srs.Rating

/**
 * One graded answer, written every time you rate a card.
 *
 * The cards table only ever holds a card's *current* state — grade a card and the
 * old interval is gone. This table is the journal that state was computed from, and
 * it is what every number on the Progress screen is derived from.
 *
 * Deliberately **not** a foreign key on cards or decks. A journal that rewrites
 * itself when you delete a card is not a journal: your retention for last month
 * should not change because you tidied up a deck today. The ids are references, and
 * anything that needs a name (the per-deck breakdown) joins against decks and simply
 * drops rows whose deck has gone.
 *
 * Everything the stats need is denormalised in here on purpose, so reading history
 * never has to reconstruct a card's past from its present.
 */
@Entity(
    tableName = "reviews",
    indices = [Index("reviewedAt"), Index("cardId"), Index("deckId")]
)
data class ReviewLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val deckId: Long,
    val reviewedAt: Long,              // epoch millis
    val rating: Rating,
    val remembered: Boolean,           // anything but Again

    /**
     * The card's interval *before* this answer, in days. This is what separates the
     * three kinds of review:
     *
     *  - 0        first look at a new card — you never knew it, so it cannot count
     *             against retention
     *  - 1..20    a young card
     *  - 21+      a mature card, in Anki's sense
     */
    val intervalBefore: Int,
    val intervalAfter: Int,
    val easeAfter: Double
)

/**
 * Just the scheduling columns of a card. The Progress screen counts thousands of
 * cards but draws none of them, so pulling questions and answers out of the
 * database would be paging in text nothing is going to render.
 */
data class CardSchedule(
    val dueAt: Long,
    val intervalDays: Int,
    val repetition: Int,
    val lapses: Int
)
