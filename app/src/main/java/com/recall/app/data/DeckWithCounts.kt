package com.recall.app.data

/**
 * A deck plus the numbers we show on its card in the deck list.
 * Room can map a query straight onto a plain class like this.
 */
data class DeckWithCounts(
    val id: Long,
    val name: String,
    val colorIndex: Int,
    val totalCards: Int,
    val dueCards: Int
)
