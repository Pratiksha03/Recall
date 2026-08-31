package com.recall.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.recall.app.srs.Rating

// A calm indigo/violet base with warm neutrals. Everything else derives from these.
val Indigo10 = Color(0xFF1A1036)
val Indigo20 = Color(0xFF2C1E5C)
val Indigo40 = Color(0xFF5B4BC4)
val Indigo80 = Color(0xFFC7C0FF)
val Indigo90 = Color(0xFFE6E1FF)

val Violet40 = Color(0xFF7C4DFF)
val Violet80 = Color(0xFFD6BDFF)

val Sand95 = Color(0xFFFBF8FF)
val Sand99 = Color(0xFFFFFBFF)
val Ink10 = Color(0xFF14121C)
val Ink20 = Color(0xFF1E1B26)
val Ink90 = Color(0xFFE6E1E9)

val Teal40 = Color(0xFF00A08A)
val Teal80 = Color(0xFF6FE3CE)
val Amber40 = Color(0xFFE0952A)
val Rose40 = Color(0xFFE2445C)

/**
 * The palette each deck gets painted with. `colorIndex` on a Deck is just a
 * position in this list, so decks stay visually distinct at a glance.
 */
val DeckAccents = listOf(
    Color(0xFF6C5CE7),  // indigo
    Color(0xFF00A08A),  // teal
    Color(0xFFE0952A),  // amber
    Color(0xFFE2445C),  // rose
    Color(0xFF2E86DE),  // blue
    Color(0xFF8E44AD)   // purple
)

fun accentFor(index: Int): Color = DeckAccents[index.mod(DeckAccents.size)]

/**
 * The colour each grade is drawn in — rose through amber and blue to teal.
 *
 * Shared by the rating buttons and the Progress screen so a bar meaning "Again"
 * is the same red as the button you pressed to make it.
 */
fun colorFor(rating: Rating): Color = when (rating) {
    Rating.AGAIN -> Rose40
    Rating.HARD -> Amber40
    Rating.GOOD -> Color(0xFF2E86DE)
    Rating.EASY -> Teal40
}

/** Answered correctly / answered wrong, wherever a chart splits the two. */
val RememberedColor = Teal40
val ForgottenColor = Rose40
