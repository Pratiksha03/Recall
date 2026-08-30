package com.recall.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recall.app.data.Card
import com.recall.app.srs.Rating
import com.recall.app.srs.Sm2
import com.recall.app.ui.ReviewState
import com.recall.app.ui.components.AnswerView

private val CardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val CARD_MS = 260

/**
 * The study screen. Question on top, tap anywhere to flip, then grade yourself.
 * The grade is what feeds the scheduler.
 */
@Composable
fun ReviewScreen(
    state: ReviewState,
    deckName: String,
    onReveal: () -> Unit,
    onRate: (Rating) -> Unit,
    onExit: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ReviewTopBar(state, deckName, onExit)

            when {
                // Deliberately blank, not a spinner. Loading the due queue is one
                // indexed query over a local table — it finishes well inside a frame,
                // so a spinner would appear and vanish as a flicker during the screen
                // transition, which reads as jank rather than as progress.
                state.loading -> Box(Modifier.fillMaxSize())

                state.finished -> SessionFinished(state.reviewed, onExit)

                else -> {
                    val card = state.current!!

                    // Grading a card used to hard-cut to the next one, which gives the
                    // eye nothing to follow and reads as a glitch. The new card slides
                    // in from the right as the old one fades, so the motion says
                    // "next" — the same direction the whole app moves forward in.
                    AnimatedContent(
                        targetState = card,
                        transitionSpec = {
                            (
                                slideInHorizontally(tween(CARD_MS, easing = CardEasing)) { it / 6 } +
                                    fadeIn(tween(CARD_MS))
                                ) togetherWith fadeOut(tween(CARD_MS / 2))
                        },
                        label = "reviewCard",
                        modifier = Modifier.weight(1f)
                    ) { shown ->
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp)
                        ) {
                            QuestionCard(shown, state.revealed, onReveal)

                            AnimatedVisibility(
                                visible = state.revealed,
                                enter = fadeIn(tween(200)) + expandVertically(
                                    animationSpec = tween(240, easing = CardEasing)
                                ),
                                exit = fadeOut(tween(120))
                            ) {
                                Column {
                                    Spacer(Modifier.height(16.dp))
                                    AnswerCard(shown)
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                        }
                    }

                    if (state.revealed) {
                        RatingBar(card, onRate)
                    } else {
                        RevealButton(onReveal)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewTopBar(state: ReviewState, deckName: String, onExit: () -> Unit) {
    val progress by animateFloatAsState(state.progress, label = "reviewProgress")
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    deckName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1
                )
                Text(
                    if (state.finished) "Session complete"
                    else "${state.remaining} card${if (state.remaining == 1) "" else "s"} left",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onExit) {
                Icon(Icons.Default.Close, contentDescription = "Exit review")
            }
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { progress },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
        )
    }
}

@Composable
private fun QuestionCard(card: Card, revealed: Boolean, onReveal: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(enabled = !revealed, onClick = onReveal)
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(
                "QUESTION",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                card.question,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!revealed) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "Tap to reveal the answer",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AnswerCard(card: Card) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(
                "ANSWER",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.height(12.dp))
            AnswerView(answerType = card.answerType, answer = card.answer)
            if (card.note.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    card.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RevealButton(onReveal: () -> Unit) {
    Box(Modifier.padding(20.dp)) {
        Button(
            onClick = onReveal,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Text("Show answer", fontWeight = FontWeight.SemiBold)
        }
    }
}

/** The four Anki buttons, each labelled with when you'd next see the card. */
@Composable
private fun RatingBar(card: Card, onRate: (Rating) -> Unit) {
    val colors = mapOf(
        Rating.AGAIN to Color(0xFFE2445C),
        Rating.HARD to Color(0xFFE0952A),
        Rating.GOOD to Color(0xFF2E86DE),
        Rating.EASY to Color(0xFF00A08A)
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Rating.entries.forEach { rating ->
            val color = colors.getValue(rating)
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(color.copy(alpha = 0.14f))
                    .clickable { onRate(rating) }
                    .padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    rating.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = color
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    Sm2.previewInterval(card, rating),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SessionFinished(reviewed: Int, onExit: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.TaskAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.height(22.dp))
        Text(
            if (reviewed == 0) "Nothing due here" else "Done for now",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (reviewed == 0) "Every card in this deck is scheduled for later."
            else "You reviewed $reviewed card${if (reviewed == 1) "" else "s"}. They'll come back when they're due.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onExit,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Back to decks", fontWeight = FontWeight.SemiBold)
        }
    }
}
