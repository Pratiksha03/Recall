package com.recall.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recall.app.data.AnswerType
import com.recall.app.data.Deck
import com.recall.app.data.MediaStore
import com.recall.app.ui.components.AnswerView
import com.recall.app.ui.theme.DeckAccents
import com.recall.app.ui.theme.accentFor

/**
 * The add screen — the part of the app that has to feel effortless.
 *
 * Flow: pick a deck (or make one inline), type the question, choose whether the
 * answer is text / a link / an image, fill that in, save. The save button stays
 * disabled until the card is actually valid, so there is nothing to get wrong.
 */
@Composable
fun AddCardScreen(
    decks: List<Deck>,
    initialDeckId: Long?,
    onSave: (deckId: Long, question: String, answer: String, type: AnswerType, note: String) -> Unit,
    onCreateDeck: (name: String, colorIndex: Int, onCreated: (Long) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // rememberSaveable, not remember: rotating the phone destroys and recreates the
    // Activity, and plain `remember` would throw away everything typed so far.
    //
    // selectedDeckId deliberately does NOT key off `decks`. That list is a live Flow
    // and gets a new instance on every write, which would reset the picker the moment
    // you created a deck from here. Null means "no explicit choice yet" and falls back
    // to the first deck at render time.
    var selectedDeckId by rememberSaveable { mutableStateOf(initialDeckId) }
    var question by rememberSaveable { mutableStateOf("") }
    var answerType by rememberSaveable { mutableStateOf(AnswerType.TEXT) }
    var textAnswer by rememberSaveable { mutableStateOf("") }
    var linkAnswer by rememberSaveable { mutableStateOf("") }
    var codeAnswer by rememberSaveable { mutableStateOf("") }
    // One slot for whichever file type is selected; switching type clears it, so a
    // half-picked image can never be saved as an audio answer.
    var filePath by rememberSaveable { mutableStateOf<String?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var showNote by rememberSaveable { mutableStateOf(false) }
    var showNewDeck by rememberSaveable { mutableStateOf(false) }

    val effectiveDeckId = selectedDeckId ?: decks.firstOrNull()?.id

    // The system photo picker. No storage permission needed — Android hands us
    // read access to exactly the one image the user chose.
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            filePath?.let(MediaStore::delete)
            filePath = MediaStore.copyImage(context, uri)
        }
    }

    // The photo picker only does images and video, so audio goes through the
    // document picker. Also permission-free: the user chooses the one file.
    val pickAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            filePath?.let(MediaStore::delete)
            filePath = MediaStore.copyAudio(context, uri)
        }
    }

    val answer = when (answerType) {
        AnswerType.TEXT -> textAnswer
        AnswerType.CODE -> codeAnswer
        AnswerType.LINK -> linkAnswer
        AnswerType.IMAGE, AnswerType.AUDIO -> filePath.orEmpty()
    }
    val canSave = effectiveDeckId != null && question.isNotBlank() && answer.isNotBlank()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("New card", style = MaterialTheme.typography.titleMedium) },
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
            SectionLabel("Deck")
            DeckPicker(
                decks = decks,
                selectedId = effectiveDeckId,
                onSelect = { selectedDeckId = it },
                onNewDeck = { showNewDeck = true }
            )

            Spacer(Modifier.height(22.dp))

            SectionLabel("Question")
            RecallTextField(
                value = question,
                onValueChange = { question = it },
                placeholder = "What do you want to remember?",
                minLines = 2
            )

            Spacer(Modifier.height(22.dp))

            SectionLabel("Answer")
            AnswerTypeSelector(
                selected = answerType,
                onSelect = { picked ->
                    if (picked != answerType && answerType.isFile) {
                        filePath?.let(MediaStore::delete)
                        filePath = null
                    }
                    answerType = picked
                }
            )
            Spacer(Modifier.height(12.dp))

            when (answerType) {
                AnswerType.TEXT -> RecallTextField(
                    value = textAnswer,
                    onValueChange = { textAnswer = it },
                    placeholder = "Type the answer",
                    minLines = 4
                )

                AnswerType.LINK -> RecallTextField(
                    value = linkAnswer,
                    onValueChange = { linkAnswer = it },
                    placeholder = "https://…",
                    minLines = 1,
                    keyboardType = KeyboardType.Uri
                )

                AnswerType.CODE -> CodeField(
                    value = codeAnswer,
                    onValueChange = { codeAnswer = it }
                )

                AnswerType.IMAGE -> FilePickerBox(
                    path = filePath,
                    answerType = AnswerType.IMAGE,
                    title = "Choose an image",
                    onPick = {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onClear = {
                        filePath?.let(MediaStore::delete)
                        filePath = null
                    }
                )

                AnswerType.AUDIO -> FilePickerBox(
                    path = filePath,
                    answerType = AnswerType.AUDIO,
                    title = "Choose an audio clip",
                    onPick = { pickAudio.launch(arrayOf("audio/*")) },
                    onClear = {
                        filePath?.let(MediaStore::delete)
                        filePath = null
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Optional note — hidden behind a tap so the default form stays short.
            if (!showNote) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showNote = true }
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Notes,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Add a note",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            AnimatedVisibility(visible = showNote) {
                Column {
                    SectionLabel("Note (optional)")
                    RecallTextField(
                        value = note,
                        onValueChange = { note = it },
                        placeholder = "Context, mnemonic, source…",
                        minLines = 2
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = {
                    val deckId = effectiveDeckId ?: return@Button
                    onSave(deckId, question, answer, answerType, note)
                },
                enabled = canSave,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save card", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    if (showNewDeck) {
        NewDeckDialog(
            onDismiss = { showNewDeck = false },
            onCreate = { name, colorIndex ->
                onCreateDeck(name, colorIndex) { newId -> selectedDeckId = newId }
                showNewDeck = false
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun RecallTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        minLines = minLines,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Five options no longer fit across a phone in one row, so this is a FlowRow:
 * it lays chips out left to right and wraps to the next line when it runs out of
 * width. Adding a sixth answer type needs no layout change.
 */
@Composable
private fun AnswerTypeSelector(selected: AnswerType, onSelect: (AnswerType) -> Unit) {
    val options = listOf(
        Triple(AnswerType.TEXT, "Text", Icons.AutoMirrored.Filled.Notes),
        Triple(AnswerType.CODE, "Code", Icons.Default.Code),
        Triple(AnswerType.LINK, "Link", Icons.Default.Link),
        Triple(AnswerType.IMAGE, "Image", Icons.Default.Image),
        Triple(AnswerType.AUDIO, "Audio", Icons.Default.GraphicEq)
    )
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        options.forEach { (type, label, icon) ->
            val active = type == selected
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(type) }
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Monospace input that does not wrap, so indentation survives typing. */
@Composable
private fun CodeField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text("Paste or type code", color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        minLines = 6,
        shape = RoundedCornerShape(16.dp),
        textStyle = LocalTextStyle.current.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 19.sp
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Ascii,
            autoCorrectEnabled = false,
            capitalization = KeyboardCapitalization.None
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun FilePickerBox(
    path: String?,
    answerType: AnswerType,
    title: String,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    if (path == null) {
        val icon = if (answerType == AnswerType.AUDIO) Icons.Default.GraphicEq
        else Icons.Default.Image
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 150.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .clickable(onClick = onPick)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "It gets copied into the app, so it stays even if you delete the original.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        Column {
            // Preview with the same renderer the review screen uses, so what you
            // see here is exactly what you will get when the card comes up.
            AnswerView(answerType = answerType, answer = path)
            Row(Modifier.padding(top = 8.dp)) {
                TextButton(onClick = onPick) { Text("Replace") }
                TextButton(onClick = onClear) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun DeckPicker(
    decks: List<Deck>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onNewDeck: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = decks.firstOrNull { it.id == selectedId }

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
                .clickable { expanded = true }
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accentFor(selected?.colorIndex ?: 0))
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    selected?.name ?: "Choose a deck",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected != null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Change",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            decks.forEach { deck ->
                DropdownMenuItem(
                    text = { Text(deck.name) },
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(accentFor(deck.colorIndex))
                        )
                    },
                    onClick = {
                        onSelect(deck.id)
                        expanded = false
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("New deck…") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = {
                    expanded = false
                    onNewDeck()
                }
            )
        }
    }
}

@Composable
fun NewDeckDialog(onDismiss: () -> Unit, onCreate: (String, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var colorIndex by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New deck") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Deck name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DeckAccents.forEachIndexed { index, color ->
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { colorIndex = index },
                            contentAlignment = Alignment.Center
                        ) {
                            if (index == colorIndex) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, colorIndex) },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
