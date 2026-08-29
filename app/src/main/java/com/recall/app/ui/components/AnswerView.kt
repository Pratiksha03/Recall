package com.recall.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.recall.app.data.AnswerType
import java.io.File

/**
 * Renders a card's answer according to its type. Used by both the review screen
 * and the browse screen, so each answer kind is drawn in exactly one place.
 */
@Composable
fun AnswerView(
    answerType: AnswerType,
    answer: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    when (answerType) {
        AnswerType.TEXT -> Text(
            text = answer,
            style = if (compact) MaterialTheme.typography.bodyMedium
            else MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = modifier
        )

        AnswerType.CODE -> CodeAnswer(answer, modifier, compact)
        AnswerType.LINK -> LinkAnswer(answer, modifier)
        AnswerType.IMAGE -> ImageAnswer(answer, modifier, compact)
        AnswerType.AUDIO -> AudioAnswer(answer, modifier)
    }
}

/**
 * Code gets a monospace font, literal whitespace (softWrap = false), and its own
 * horizontal scrollbar. Wrapping a long line would misalign everything below it,
 * so we scroll sideways instead — the same trade every code viewer makes.
 */
@Composable
private fun CodeAnswer(code: String, modifier: Modifier = Modifier, compact: Boolean) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
    ) {
        Text(
            text = code,
            fontFamily = FontFamily.Monospace,
            fontSize = if (compact) 12.sp else 13.sp,
            lineHeight = if (compact) 18.sp else 20.sp,
            softWrap = false,
            maxLines = if (compact) 6 else Int.MAX_VALUE,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(14.dp)
        )
    }
}

@Composable
private fun LinkAnswer(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
            .clickable {
                val normalized = if (url.startsWith("http")) url else "https://$url"
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show()
                }
            }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = url,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            maxLines = 2
        )
    }
}

@Composable
private fun ImageAnswer(path: String, modifier: Modifier = Modifier, compact: Boolean) {
    if (!remember(path) { File(path).exists() }) {
        MissingFile("Image is missing", modifier)
        return
    }
    AsyncImage(
        model = File(path),
        contentDescription = "Card answer image",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = if (compact) 120.dp else 360.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
    )
}

@Composable
private fun AudioAnswer(path: String, modifier: Modifier = Modifier) {
    if (!remember(path) { File(path).exists() }) {
        MissingFile("Audio is missing", modifier)
        return
    }
    val player = rememberAudioPlayer(path)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f))
            .clickable { player.toggle() }
            .padding(14.dp)
    ) {
        Icon(
            imageVector = if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (player.isPlaying) "Pause" else "Play",
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f))
                .padding(9.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = formatMillis(player.positionMs) + " / " + formatMillis(player.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { player.progress },
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                strokeCap = StrokeCap.Round,
                modifier = Modifier.fillMaxWidth().height(5.dp)
            )
        }
    }
}

@Composable
private fun MissingFile(label: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(
            Icons.Default.BrokenImage,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatMillis(ms: Int): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
