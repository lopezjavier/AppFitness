package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.live_paused_label
import io.github.lopezjavier.appfitness.composeapp.resources.live_timer_cd
import io.github.lopezjavier.appfitness.composeapp.resources.live_timer_paused_state
import io.github.lopezjavier.appfitness.composeapp.resources.live_title
import org.jetbrains.compose.resources.stringResource

private const val DESCRIPTION_STEP_SECONDS = 10L
private const val SECONDS_PER_MINUTE = 60L

/** The stopwatch line of the live top bar ("EN VIVO · 12:34", paused "EN PAUSA · 12:34") and its spoken semantics. */
internal class LiveSubtitle(val text: String, val semantics: SemanticsPropertyReceiver.() -> Unit)

/**
 * Builds the subtitle of the live top bar. The spoken description changes in 10 second steps so a screen reader is
 * not chatty; a paused stopwatch also announces "En pausa" as its state.
 */
@Composable
internal fun liveSubtitle(elapsedSeconds: Long, isPaused: Boolean): LiveSubtitle {
    val spokenSeconds = elapsedSeconds.coerceAtLeast(0) / DESCRIPTION_STEP_SECONDS * DESCRIPTION_STEP_SECONDS
    val description = stringResource(
        Res.string.live_timer_cd,
        spokenSeconds / SECONDS_PER_MINUTE,
        spokenSeconds % SECONDS_PER_MINUTE,
    )
    val pausedState = stringResource(Res.string.live_timer_paused_state)
    val label = stringResource(if (isPaused) Res.string.live_paused_label else Res.string.live_title)
    return LiveSubtitle("$label · ${formatElapsed(elapsedSeconds)}") {
        contentDescription = description
        if (isPaused) stateDescription = pausedState
    }
}
