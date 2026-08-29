package com.recall.app.ui.components

import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

/**
 * A tiny audio player scoped to a Composable.
 *
 * MediaPlayer holds a real system resource, so it has to be released explicitly —
 * this is the one place in the app with manual cleanup. DisposableEffect is how
 * Compose expresses "and when this leaves the screen, undo it": the onDispose block
 * runs when the composable goes away, when the file path changes, or when the
 * screen is destroyed. Without it, playing a few cards would leak a player each time.
 *
 * It also stops on Lifecycle.ON_STOP so audio does not keep playing after you
 * background the app.
 */
class AudioPlayerState(
    private val player: MediaPlayer,
    val durationMs: Int
) {
    var isPlaying by mutableStateOf(false)
        internal set

    var positionMs by mutableIntStateOf(0)
        internal set

    val progress: Float
        get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    fun toggle() {
        if (player.isPlaying) pause() else play()
    }

    fun play() {
        // Starting again after it ran to the end should replay, not do nothing.
        if (positionMs >= durationMs) {
            player.seekTo(0)
            positionMs = 0
        }
        player.start()
        isPlaying = true
    }

    fun pause() {
        if (player.isPlaying) player.pause()
        isPlaying = false
    }

    /** Hands the system resource back. After this the player must not be reused. */
    internal fun release() {
        runCatching {
            if (player.isPlaying) player.stop()
            player.release()
        }
        isPlaying = false
    }

    internal fun syncPosition() {
        positionMs = runCatching { player.currentPosition }.getOrDefault(positionMs)
    }
}

@Composable
fun rememberAudioPlayer(path: String): AudioPlayerState {
    val lifecycleOwner = LocalLifecycleOwner.current

    // remember(path) so switching to another card builds a new player.
    val state = remember(path) {
        val player = MediaPlayer().apply {
            runCatching {
                setDataSource(path)
                prepare()
            }
        }
        AudioPlayerState(player, runCatching { player.duration }.getOrDefault(0))
            .also { s -> player.setOnCompletionListener { s.isPlaying = false } }
    }

    DisposableEffect(path, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) state.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            state.release()
        }
    }

    // Poll the play head only while something is actually playing.
    LaunchedEffect(state.isPlaying) {
        while (state.isPlaying) {
            state.syncPosition()
            delay(200)
        }
        state.syncPosition()
    }

    return state
}
