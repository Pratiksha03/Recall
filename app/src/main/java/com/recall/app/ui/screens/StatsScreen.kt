package com.recall.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import com.recall.app.data.Card
import com.recall.app.srs.CardMix
import com.recall.app.srs.DayBar
import com.recall.app.srs.DeckRetention
import com.recall.app.srs.ForecastDay
import com.recall.app.srs.MATURE_DAYS
import com.recall.app.srs.Rating
import com.recall.app.srs.Retention
import com.recall.app.srs.StatsSnapshot
import com.recall.app.srs.StatsWindow
import com.recall.app.ui.theme.ForgottenColor
import com.recall.app.ui.theme.RememberedColor
import com.recall.app.ui.theme.colorFor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Progress: what your reviewing has actually been doing.
 *
 * The screen owns no state and computes nothing — every number arrives in the
 * [StatsSnapshot], which is built and tested away from Compose entirely.
 */
@Composable
fun StatsScreen(
    stats: StatsSnapshot?,
    window: StatsWindow,
    onSetWindow: (StatsWindow) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Progress", style = MaterialTheme.typography.titleMedium) },
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
        when {
            // Still reading the database. A spinner for a few milliseconds beats
            // flashing "no history yet" at someone who has months of it.
            stats == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            stats.isEmpty -> NothingReviewedYet(Modifier.padding(padding))

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { WindowChips(window, onSetWindow) }
                item { RetentionCard(stats) }
                item { StreakRow(stats) }
                item { DailyChart(stats.days, stats.window) }
                item { GradeBreakdown(stats.grades, stats.reviewsInWindow) }
                item { ForecastChart(stats.forecast) }
                item { MixCard(stats.mix) }
                if (stats.byDeck.isNotEmpty()) item { DeckBreakdown(stats.byDeck) }
                if (stats.hardestCards.isNotEmpty()) item { HardestCards(stats.hardestCards) }
            }
        }
    }
}

@Composable
private fun WindowChips(selected: StatsWindow, onSelect: (StatsWindow) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatsWindow.entries.forEach { window ->
            FilterChip(
                selected = window == selected,
                onClick = { onSelect(window) },
                label = { Text(window.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

/** The headline number: of the cards you had learned, how many did you still know? */
@Composable
private fun RetentionCard(stats: StatsSnapshot) {
    StatCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RetentionRing(stats.overall)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Retention",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (stats.overall.attempts == 0)
                        "No learned card has come up in this window yet."
                    else
                        "You remembered ${stats.overall.remembered} of " +
                            "${stats.overall.attempts} cards you had already learned.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                SplitRetention("Young", stats.young, "under $MATURE_DAYS days")
                Spacer(Modifier.height(6.dp))
                SplitRetention("Mature", stats.mature, "$MATURE_DAYS days or more")
            }
        }
        Spacer(Modifier.height(14.dp))
        Caption(
            "Counts the first answer you gave each card each day. New cards are left " +
                "out — you cannot forget something you never knew."
        )
    }
}

@Composable
private fun RetentionRing(retention: Retention) {
    val target = retention.rate ?: 0f
    val sweep by animateFloatAsState(targetValue = target, label = "retentionRing")
    val track = MaterialTheme.colorScheme.surfaceVariant
    // Below about 80% you are being shown cards too late to be worth the effort;
    // the colour says so without a paragraph of explanation.
    val color = when {
        retention.rate == null -> MaterialTheme.colorScheme.outline
        target >= 0.9f -> RememberedColor
        target >= 0.8f -> MaterialTheme.colorScheme.primary
        else -> ForgottenColor
    }

    Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().aspectRatio(1f)) {
            val stroke = 11.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (sweep > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,           // start at twelve o'clock
                    sweepAngle = 360f * sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Text(
            retention.rate?.let { "${(it * 100).roundToInt()}%" } ?: "—",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun SplitRetention(label: String, retention: Retention, hint: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(58.dp)
        )
        Text(
            retention.rate?.let { "${(it * 100).roundToInt()}%" } ?: "—",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(46.dp)
        )
        Text(
            if (retention.attempts == 0) hint else "${retention.attempts} reviewed",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StreakRow(stats: StatsSnapshot) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        StatTile(
            Modifier.weight(1f),
            value = stats.streakDays.toString(),
            label = "day streak"
        )
        StatTile(
            Modifier.weight(1f),
            value = "${stats.daysStudiedInWindow}/${stats.window.days}",
            label = "days studied"
        )
        StatTile(
            Modifier.weight(1f),
            value = stats.totalReviews.compact(),
            label = "reviews ever"
        )
    }
}

@Composable
private fun StatTile(modifier: Modifier = Modifier, value: String, label: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier
    ) {
        Column(
            Modifier.padding(vertical = 16.dp, horizontal = 10.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Work done per day: pass, fail and first looks stacked into one bar. */
@Composable
private fun DailyChart(days: List<DayBar>, window: StatsWindow) {
    val busiest = days.maxOfOrNull { it.total } ?: 0
    val newColor = MaterialTheme.colorScheme.primary

    StatCard {
        SectionTitle("Reviews per day", "$busiest at the busiest")
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().height(CHART_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(if (days.size > 40) 1.dp else 3.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            days.forEach { day ->
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // A day off is a flat tick on the baseline rather than nothing at
                    // all, so gaps in the habit are visible instead of invisible.
                    if (day.total == 0) {
                        Box(
                            Modifier.fillMaxWidth().height(2.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    } else {
                        Segment(day.firstLooks, busiest, newColor, top = true)
                        Segment(day.forgotten, busiest, ForgottenColor)
                        Segment(day.remembered, busiest, RememberedColor)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Caption(days.first().date.short())
            Caption(days.last().date.short())
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot(RememberedColor, "Remembered")
            LegendDot(ForgottenColor, "Forgotten")
            LegendDot(newColor, "New")
        }
        Spacer(Modifier.height(8.dp))
        Caption("Every answer, including cards you saw twice in one session.")
    }
}

/** One stacked piece of a bar, sized as a fraction of the tallest day. */
@Composable
private fun Segment(count: Int, max: Int, color: Color, top: Boolean = false) {
    if (count == 0) return
    val height = CHART_HEIGHT * (count.toFloat() / max)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height.coerceAtLeast(2.dp))
            .clip(
                if (top) RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
                else RoundedCornerShape(0.dp)
            )
            .background(color)
    )
}

@Composable
private fun GradeBreakdown(grades: Map<Rating, Int>, total: Int) {
    StatCard {
        SectionTitle("Which button you pressed", "$total answer${if (total == 1) "" else "s"}")
        Spacer(Modifier.height(12.dp))
        Rating.entries.forEach { rating ->
            val count = grades[rating] ?: 0
            val fraction = if (total == 0) 0f else count.toFloat() / total
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    rating.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = colorFor(rating),
                    modifier = Modifier.width(56.dp)
                )
                Box(
                    Modifier.weight(1f).height(10.dp).clip(CircleShape)
                        .background(colorFor(rating).copy(alpha = 0.14f))
                ) {
                    if (fraction > 0f) {
                        Box(
                            Modifier.fillMaxWidth(fraction).fillMaxHeight()
                                .clip(CircleShape).background(colorFor(rating))
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "${(fraction * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(36.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

/** What the scheduler has queued up for you next. */
@Composable
private fun ForecastChart(forecast: List<ForecastDay>) {
    val busiest = forecast.maxOfOrNull { it.due } ?: 0
    val total = forecast.sumOf { it.due }

    StatCard {
        SectionTitle("Coming up", "$total in the next two weeks")
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().height(FORECAST_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            forecast.forEach { day ->
                // Today's bar carries everything already overdue, so it gets the
                // accent colour and the rest stay quiet.
                val today = day.date == forecast.first().date
                val color = if (today) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (day.due > 0) {
                        Text(
                            day.due.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(3.dp))
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(
                                if (busiest == 0) 2.dp
                                else (FORECAST_HEIGHT * (day.due.toFloat() / busiest))
                                    .coerceAtLeast(2.dp)
                            )
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(if (day.due == 0) MaterialTheme.colorScheme.outlineVariant else color)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Caption("Today's bar includes anything already overdue.")
    }
}

@Composable
private fun MixCard(mix: CardMix) {
    // Grey, then indigo, then teal: not started, being learned, stored. Primary and
    // secondary would have been the obvious pair, but in the dark theme they are two
    // pale lavenders and the boundary between them disappears.
    val newColor = MaterialTheme.colorScheme.outline
    val youngColor = MaterialTheme.colorScheme.primary

    StatCard {
        SectionTitle("Your collection", "${mix.total} card${if (mix.total == 1) "" else "s"}")
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().height(14.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            listOf(mix.new to newColor, mix.young to youngColor, mix.mature to RememberedColor)
                .forEach { (count, color) ->
                    if (count > 0) {
                        Box(Modifier.weight(count.toFloat()).fillMaxHeight().background(color))
                    }
                }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot(newColor, "${mix.new} new")
            LegendDot(youngColor, "${mix.young} young")
            LegendDot(RememberedColor, "${mix.mature} mature")
        }
        Spacer(Modifier.height(8.dp))
        Caption("Mature means the interval has passed $MATURE_DAYS days — the point where it is really stored.")
    }
}

@Composable
private fun DeckBreakdown(decks: List<DeckRetention>) {
    StatCard {
        SectionTitle("By deck", "weakest first")
        Spacer(Modifier.height(10.dp))
        decks.forEach { deck ->
            val rate = deck.retention.rate ?: 0f
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        deck.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(
                        Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            Modifier.fillMaxWidth(rate).fillMaxHeight().clip(CircleShape)
                                .background(if (rate >= 0.8f) RememberedColor else ForgottenColor)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                // Fixed columns, so the percentages line up down the list however
                // many reviews each deck has behind it.
                Text(
                    "${(rate * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(46.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "of ${deck.retention.attempts}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(58.dp)
                )
            }
        }
    }
}

@Composable
private fun HardestCards(cards: List<Card>) {
    StatCard {
        SectionTitle("Cards you keep forgetting", "all time")
        Spacer(Modifier.height(4.dp))
        cards.forEach { card ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    card.question,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "${card.lapses}×",
                    style = MaterialTheme.typography.labelLarge,
                    color = ForgottenColor
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Caption("Forgotten this many times after you had learned them. Usually a sign the card is asking two questions at once.")
    }
}

@Composable
private fun NothingReviewedYet(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Nothing graded yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Review a deck and this fills in: how much you remember, what you " +
                    "study each day, and what is coming next.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ----- small shared pieces -----

private val CHART_HEIGHT = 108.dp
private val FORECAST_HEIGHT = 76.dp

@Composable
private fun StatCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) { content() }
    }
}

@Composable
private fun SectionTitle(title: String, trailing: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            trailing,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private val ShortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

private fun LocalDate.short(): String = format(ShortDate)

/** 12,405 reviews does not fit in a tile; 12.4k does. */
private fun Int.compact(): String = when {
    this < 1_000 -> toString()
    this < 10_000 -> "${(this / 100) / 10.0}k"
    else -> "${this / 1000}k"
}
