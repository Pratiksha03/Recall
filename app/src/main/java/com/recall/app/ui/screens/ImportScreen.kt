package com.recall.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recall.app.data.AnkiImport
import com.recall.app.data.Deck
import com.recall.app.data.ImportedCard
import com.recall.app.data.MediaStore
import com.recall.app.ui.components.AnswerView
import com.recall.app.ui.theme.accentFor

/**
 * Bulk import in Anki's plain-text format.
 *
 * Everything re-parses as you type, so the preview below is always the truth about
 * what pressing Import will actually create — no separate "validate" step to forget.
 */
@Composable
fun ImportScreen(
    decks: List<Deck>,
    onImport: (cards: List<ImportedCard>, existingDeckId: Long?, newDeckName: String?) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var raw by rememberSaveable { mutableStateOf("") }
    var chosenDeckId by rememberSaveable { mutableStateOf<Long?>(null) }
    var newDeckName by rememberSaveable { mutableStateOf("") }
    var deckMenuOpen by remember { mutableStateOf(false) }
    var nameEdited by rememberSaveable { mutableStateOf(false) }

    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) MediaStore.readText(context, uri)?.let { raw = it }
    }

    // Parsing is cheap and pure, so just redo it whenever the text changes.
    val result = remember(raw) { AnkiImport.parse(raw) }

    // A #deck: header names the destination unless the user has overridden it.
    val suggestedName = result.deckName ?: "Imported"
    val effectiveNewName = if (nameEdited) newDeckName else suggestedName
    val targetDeck = decks.firstOrNull { it.id == chosenDeckId }
    val canImport = result.cards.isNotEmpty() &&
        (targetDeck != null || effectiveNewName.isNotBlank())

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Import cards", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                "Paste an Anki text export, or anything with one card per line and " +
                    "the question and answer separated by a tab, comma or semicolon.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    .clickable {
                        pickFile.launch(arrayOf("text/*", "text/plain", "text/csv", "text/comma-separated-values"))
                    }
                    .padding(horizontal = 14.dp, vertical = 11.dp)
            ) {
                Icon(
                    Icons.Default.FileOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    "Load from a file",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = raw,
                onValueChange = { raw = it },
                placeholder = {
                    Text(
                        "#separator:tab\nWhat is 2+2?\t4",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                minLines = 6,
                maxLines = 12,
                shape = RoundedCornerShape(16.dp),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            AnimatedVisibility(visible = raw.isNotBlank()) {
                Column {
                    Spacer(Modifier.height(18.dp))
                    PreviewCard(result)

                    Spacer(Modifier.height(18.dp))
                    Text(
                        "IMPORT INTO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    Box {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { deckMenuOpen = true }
                        ) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(accentFor(targetDeck?.colorIndex ?: 0))
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    targetDeck?.name ?: "New deck: $effectiveNewName",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "Change",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = deckMenuOpen,
                            onDismissRequest = { deckMenuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("New deck") },
                                onClick = { chosenDeckId = null; deckMenuOpen = false }
                            )
                            decks.forEach { deck ->
                                DropdownMenuItem(
                                    text = { Text(deck.name) },
                                    onClick = { chosenDeckId = deck.id; deckMenuOpen = false }
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = targetDeck == null) {
                        Column {
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = effectiveNewName,
                                onValueChange = { newDeckName = it; nameEdited = true },
                                label = { Text("New deck name") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (result.deckName != null && !nameEdited) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Taken from the file's #deck: header.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(22.dp))
                    Button(
                        onClick = {
                            onImport(
                                result.cards,
                                targetDeck?.id,
                                if (targetDeck == null) effectiveNewName else null
                            )
                        },
                        enabled = canImport,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (result.cards.isEmpty()) "Nothing to import"
                            else "Import ${result.cards.size} card" +
                                if (result.cards.size == 1) "" else "s",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun PreviewCard(result: com.recall.app.data.ImportResult) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                if (result.cards.isEmpty()) "Nothing recognised yet"
                else "${result.cards.size} card${if (result.cards.size == 1) "" else "s"} ready",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Separated by ${result.separatorName}" +
                    (result.deckName?.let { " · deck \"$it\"" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            result.warnings.forEach { warning ->
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        warning,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (result.cards.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "FIRST FEW",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                result.cards.take(3).forEach { card ->
                    Spacer(Modifier.height(10.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(12.dp)
                    ) {
                        Text(
                            card.question,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        AnswerView(
                            answerType = card.answerType,
                            answer = card.answer,
                            compact = true
                        )
                    }
                }
                if (result.cards.size > 3) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "…and ${result.cards.size - 3} more",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
