package com.recall.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recall.app.data.AnswerType
import com.recall.app.data.Card
import com.recall.app.data.Deck
import com.recall.app.data.DeckWithCounts
import com.recall.app.data.ImportedCard
import com.recall.app.data.RecallRepository
import com.recall.app.data.ReminderPrefs
import com.recall.app.reminder.ReminderScheduler
import com.recall.app.srs.Rating
import com.recall.app.srs.StatsSnapshot
import com.recall.app.srs.StatsWindow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One ViewModel for the whole app — small enough that splitting it would add
 * ceremony without adding clarity.
 *
 * A ViewModel survives screen rotation and holds the state the UI reads. Anything
 * that touches the database happens inside `viewModelScope.launch { ... }`, which
 * is a coroutine: it runs asynchronously and is cancelled automatically when the
 * screen goes away. Think "managed background task" rather than raw Thread.
 */
class RecallViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = RecallRepository(app)
    private val prefs = ReminderPrefs(app)

    /** Decks + their card counts, refreshed automatically by Room. */
    val decks: StateFlow<List<DeckWithCounts>> = repo.decks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allDecks: StateFlow<List<Deck>> = repo.allDecks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Cards due across every deck — drives the settings screen's "right now" line. */
    val totalDue: StateFlow<Int> = repo.totalDue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _reviewState = MutableStateFlow(ReviewState())
    val reviewState: StateFlow<ReviewState> = _reviewState.asStateFlow()

    private val _statsWindow = MutableStateFlow(StatsWindow.MONTH)
    val statsWindow: StateFlow<StatsWindow> = _statsWindow.asStateFlow()

    /**
     * The Progress screen's numbers, recomputed when you change the window and again
     * whenever a card is graded — so finishing a session and stepping back into the
     * screen never shows you a figure from before it.
     *
     * null means "not loaded yet", which the screen draws as a spinner rather than
     * as a collection with no history.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val stats: StateFlow<StatsSnapshot?> =
        combine(_statsWindow, repo.reviewCount()) { window, _ -> window }
            .mapLatest { repo.stats(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setStatsWindow(window: StatsWindow) {
        _statsWindow.value = window
    }

    private val _reminder = MutableStateFlow(
        ReminderSettings(prefs.enabled, prefs.hour, prefs.minute)
    )
    val reminder: StateFlow<ReminderSettings> = _reminder.asStateFlow()

    init {
        viewModelScope.launch { repo.seedIfEmpty() }
        // Re-arm on every launch: WorkManager jobs can be dropped by "force stop"
        // or an app data clear, and this costs nothing when one is already queued.
        ReminderScheduler.sync(app)
    }

    // ----- reminder settings -----

    fun setReminderEnabled(enabled: Boolean) {
        prefs.enabled = enabled
        _reminder.value = _reminder.value.copy(enabled = enabled)
        ReminderScheduler.sync(getApplication())
    }

    fun setReminderTime(hour: Int, minute: Int) {
        prefs.hour = hour
        prefs.minute = minute
        _reminder.value = _reminder.value.copy(hour = hour, minute = minute)
        ReminderScheduler.sync(getApplication())
    }

    fun cardsInDeck(deckId: Long): Flow<List<Card>> = repo.cardsInDeck(deckId)

    fun deck(deckId: Long): Flow<Deck?> = repo.deck(deckId)

    // ----- editing -----

    fun createDeck(name: String, colorIndex: Int, onCreated: (Long) -> Unit = {}) {
        if (name.isBlank()) return
        viewModelScope.launch { onCreated(repo.createDeck(name, colorIndex)) }
    }

    fun renameDeck(deck: Deck, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.renameDeck(deck, name) }
    }

    fun deleteDeck(deck: Deck) {
        viewModelScope.launch { repo.deleteDeck(deck) }
    }

    fun addCard(
        deckId: Long,
        question: String,
        answer: String,
        answerType: AnswerType,
        note: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repo.addCard(deckId, question, answer, answerType, note)
            onDone()
        }
    }

    /** Loads one card so the edit screen can pre-fill itself. */
    fun cardById(id: Long): Flow<Card?> = flow { emit(repo.cardById(id)) }

    fun editCard(
        original: Card,
        deckId: Long,
        question: String,
        answer: String,
        answerType: AnswerType,
        note: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repo.editCard(original, deckId, question, answer, answerType, note)
            onDone()
        }
    }

    /**
     * Import parsed cards, creating the target deck if it does not exist yet.
     * Reports how many landed so the UI can confirm rather than just closing.
     */
    fun importCards(
        cards: List<ImportedCard>,
        existingDeckId: Long?,
        newDeckName: String?,
        onDone: (Int) -> Unit = {}
    ) {
        if (cards.isEmpty()) return
        viewModelScope.launch {
            val deckId = existingDeckId
                ?: repo.findOrCreateDeck(
                    newDeckName?.takeIf { it.isNotBlank() } ?: "Imported",
                    colorIndex = decks.value.size
                )
            onDone(repo.importInto(deckId, cards))
        }
    }

    fun deleteCard(card: Card) {
        viewModelScope.launch { repo.deleteCard(card) }
    }

    // ----- review session -----

    /** Load every card that is due in this deck and show the first one. */
    fun startReview(deckId: Long) {
        viewModelScope.launch {
            val queue = repo.dueCards(deckId)
            _reviewState.value = ReviewState(
                queue = queue,
                index = 0,
                revealed = false,
                total = queue.size,
                reviewed = 0,
                loading = false
            )
        }
    }

    fun reveal() {
        _reviewState.value = _reviewState.value.copy(revealed = true)
    }

    /** Grade the current card, then advance. */
    fun rate(rating: Rating) {
        val state = _reviewState.value
        val card = state.current ?: return
        viewModelScope.launch {
            val updated = repo.review(card, rating)
            val again = rating == Rating.AGAIN
            _reviewState.value = state.copy(
                // "Again" cards go to the back of this session's queue so you
                // actually see them again before you finish. Re-queue the *updated*
                // card: the stale pre-rating copy would recompute from scratch when
                // graded again and quietly undo this lapse.
                queue = if (again) state.queue + updated else state.queue,
                index = state.index + 1,
                revealed = false,
                reviewed = state.reviewed + 1
            )
        }
    }

    fun endReview() {
        _reviewState.value = ReviewState(loading = false)
    }
}

/** The daily reminder settings, mirrored out of SharedPreferences for the UI. */
data class ReminderSettings(
    val enabled: Boolean = false,
    val hour: Int = 20,
    val minute: Int = 0
)

/** Everything the review screen needs, in one immutable snapshot. */
data class ReviewState(
    val queue: List<Card> = emptyList(),
    val index: Int = 0,
    val revealed: Boolean = false,
    val total: Int = 0,
    val reviewed: Int = 0,
    val loading: Boolean = true
) {
    val current: Card? get() = queue.getOrNull(index)
    val finished: Boolean get() = !loading && current == null

    /**
     * How many cards are still ahead of you.
     *
     * A card you rate "Again" is pushed back onto the queue, so queue.size grows
     * as you go. Counting raw positions produced "4 of 4", then "5 of 5" — a
     * total that ran away from you and a progress bar that never filled.
     * distinctBy{id} counts a repeated card once, so this only ever goes down.
     */
    val remaining: Int get() = queue.drop(index).distinctBy { it.id }.size

    /** Fraction of the original due pile that is fully behind you. */
    val progress: Float
        get() {
            if (total == 0) return 0f
            return ((total - remaining).coerceAtLeast(0).toFloat() / total).coerceIn(0f, 1f)
        }
}
