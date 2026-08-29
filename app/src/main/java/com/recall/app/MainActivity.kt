package com.recall.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.recall.app.ui.RecallViewModel
import com.recall.app.ui.screens.AddCardScreen
import com.recall.app.ui.screens.DeckDetailScreen
import com.recall.app.ui.screens.DeckListScreen
import com.recall.app.ui.screens.NewDeckDialog
import com.recall.app.ui.screens.ReviewScreen
import com.recall.app.ui.screens.SettingsScreen
import com.recall.app.ui.theme.RecallTheme
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.platform.LocalContext
import com.recall.app.reminder.Notifications
import kotlinx.coroutines.flow.map

/**
 * The one and only Activity. Everything else is Compose functions drawn inside it.
 *
 * If you're coming from Java/Swing: an Activity is roughly the window, and the
 * NavHost below is the card-layout that swaps which screen is showing.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Notifications.ensureChannel(this)
        setContent {
            RecallTheme {
                RecallNavGraph()
            }
        }
    }
}

private object Routes {
    const val DECKS = "decks"
    const val DECK_DETAIL = "deck/{deckId}"
    const val REVIEW = "review/{deckId}"
    const val ADD = "add?deckId={deckId}"
    const val SETTINGS = "settings"

    fun deckDetail(id: Long) = "deck/$id"
    fun review(id: Long) = "review/$id"
    fun add(deckId: Long?) = "add?deckId=${deckId ?: -1L}"
}

@Composable
private fun RecallNavGraph(vm: RecallViewModel = viewModel()) {
    val navController = rememberNavController()
    val decks by vm.decks.collectAsStateWithLifecycle()
    val allDecks by vm.allDecks.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = Routes.DECKS) {

        composable(Routes.DECKS) {
            var showNewDeck by remember { mutableStateOf(false) }

            DeckListScreen(
                decks = decks,
                onOpenDeck = { navController.navigate(Routes.deckDetail(it)) },
                onReviewDeck = { navController.navigate(Routes.review(it)) },
                onAddCard = { navController.navigate(Routes.add(null)) },
                onNewDeck = { showNewDeck = true },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )

            if (showNewDeck) {
                NewDeckDialog(
                    onDismiss = { showNewDeck = false },
                    onCreate = { name, colorIndex ->
                        vm.createDeck(name, colorIndex)
                        showNewDeck = false
                    }
                )
            }
        }

        composable(
            route = Routes.DECK_DETAIL,
            arguments = listOf(navArgument("deckId") { type = NavType.LongType })
        ) { entry ->
            val deckId = entry.arguments?.getLong("deckId") ?: return@composable
            val deck by vm.deck(deckId).collectAsStateWithLifecycle(initialValue = null)
            val cards by vm.cardsInDeck(deckId).collectAsStateWithLifecycle(initialValue = emptyList())

            DeckDetailScreen(
                deck = deck,
                cards = cards,
                onBack = { navController.popBackStack() },
                onReview = { navController.navigate(Routes.review(deckId)) },
                onAddCard = { navController.navigate(Routes.add(deckId)) },
                onDeleteCard = vm::deleteCard,
                onDeleteDeck = {
                    deck?.let(vm::deleteDeck)
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Routes.REVIEW,
            arguments = listOf(navArgument("deckId") { type = NavType.LongType })
        ) { entry ->
            val deckId = entry.arguments?.getLong("deckId") ?: return@composable
            val state by vm.reviewState.collectAsStateWithLifecycle()
            val deckName by vm.deck(deckId).map { it?.name ?: "Review" }
                .collectAsStateWithLifecycle(initialValue = "Review")

            // Load the due queue once, when this screen first appears.
            LaunchedEffect(deckId) { vm.startReview(deckId) }

            ReviewScreen(
                state = state,
                deckName = deckName,
                onReveal = vm::reveal,
                onRate = vm::rate,
                onExit = {
                    vm.endReview()
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Routes.ADD,
            arguments = listOf(navArgument("deckId") {
                type = NavType.LongType
                defaultValue = -1L
            })
        ) { entry ->
            val passedId = entry.arguments?.getLong("deckId") ?: -1L

            AddCardScreen(
                decks = allDecks,
                initialDeckId = passedId.takeIf { it > 0 },
                onSave = { deckId, question, answer, type, note ->
                    vm.addCard(deckId, question, answer, type, note) {
                        navController.popBackStack()
                    }
                },
                onCreateDeck = { name, colorIndex, onCreated ->
                    vm.createDeck(name, colorIndex, onCreated)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            val context = LocalContext.current
            val reminder by vm.reminder.collectAsStateWithLifecycle()

            // Android 13+ requires asking before an app may post notifications.
            val requestPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted -> vm.setReminderEnabled(granted) }

            SettingsScreen(
                reminderEnabled = reminder.enabled,
                reminderHour = reminder.hour,
                reminderMinute = reminder.minute,
                notificationsBlocked = !NotificationManagerCompat.from(context)
                    .areNotificationsEnabled(),
                onToggleReminder = { wanted ->
                    if (wanted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        // Turn the switch on only if the user says yes.
                        requestPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        vm.setReminderEnabled(wanted)
                    }
                },
                onSetTime = vm::setReminderTime,
                onOpenSystemSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", context.packageName, null))
                    )
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
