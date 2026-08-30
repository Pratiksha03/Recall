package com.recall.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recall.app.data.AnswerType
import com.recall.app.data.Card
import com.recall.app.data.Deck
import com.recall.app.srs.dueLabel
import com.recall.app.ui.components.AnswerView
import com.recall.app.ui.theme.accentFor

/**
 * Everything inside one deck: browse the cards, see when each is next due,
 * delete one, start a review, or add another card.
 */
@Composable
fun DeckDetailScreen(
    deck: Deck?,
    cards: List<Card>,
    onBack: () -> Unit,
    onReview: () -> Unit,
    onAddCard: () -> Unit,
    onDeleteCard: (Card) -> Unit,
    onEditCard: (Card) -> Unit,
    onDeleteDeck: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDeleteDeck by remember { mutableStateOf(false) }
    var cardToDelete by remember { mutableStateOf<Card?>(null) }

    val now = System.currentTimeMillis()
    val dueCount = cards.count { it.dueAt <= now }
    val colorIndex = deck?.colorIndex ?: 0

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        deck?.name ?: "Deck",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Deck options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete deck") },
                            leadingIcon = {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                confirmDeleteDeck = true
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddCard,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add card", fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Button(
                    onClick = onReview,
                    enabled = dueCount > 0,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (dueCount > 0) "Review $dueCount card${if (dueCount == 1) "" else "s"}"
                        else "Nothing due right now",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            item {
                Text(
                    "${cards.size} card${if (cards.size == 1) "" else "s"} in this deck",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(cards, key = { it.id }) { card ->
                CardRow(
                    card = card,
                    accentIndex = colorIndex,
                    onEdit = { onEditCard(card) },
                    onDelete = { cardToDelete = card }
                )
            }

            if (cards.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onAddCard)
                    ) {
                        Column(
                            Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("This deck is empty", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Tap to add your first card.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDeleteDeck) {
        AlertDialog(
            onDismissRequest = { confirmDeleteDeck = false },
            title = { Text("Delete this deck?") },
            text = {
                Text(
                    "Its ${cards.size} card${if (cards.size == 1) "" else "s"} will be deleted too. " +
                        "This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteDeck = false
                    onDeleteDeck()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteDeck = false }) { Text("Cancel") }
            }
        )
    }

    cardToDelete?.let { card ->
        AlertDialog(
            onDismissRequest = { cardToDelete = null },
            title = { Text("Delete this card?") },
            text = { Text(card.question) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteCard(card)
                    cardToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { cardToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun CardRow(
    card: Card,
    accentIndex: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val accent = accentFor(accentIndex)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { expanded = !expanded }
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeChip(card.answerType, accent)
                Spacer(Modifier.width(10.dp))
                Text(
                    dueLabel(card),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit card",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete card",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                card.question,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 2
            )
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                AnswerView(answerType = card.answerType, answer = card.answer)
                if (card.note.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        card.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TypeChip(type: AnswerType, accent: Color) {
    val (label, icon) = when (type) {
        AnswerType.TEXT -> "Text" to Icons.AutoMirrored.Filled.Notes
        AnswerType.CODE -> "Code" to Icons.Default.Code
        AnswerType.LINK -> "Link" to Icons.Default.Link
        AnswerType.IMAGE -> "Image" to Icons.Default.Image
        AnswerType.AUDIO -> "Audio" to Icons.Default.GraphicEq
    }
    Row(
        Modifier
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = accent)
    }
}
