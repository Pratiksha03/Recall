package com.recall.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A named group of cards, e.g. "Kotlin basics".
 *
 * In Kotlin a `data class` with `val` properties is roughly a Java class with
 * final fields + constructor + getters + equals/hashCode/toString, generated for you.
 */
@Entity(tableName = "decks")
data class Deck(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorIndex: Int = 0,           // which accent colour to paint this deck
    val createdAt: Long = System.currentTimeMillis()
)
