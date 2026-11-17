package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.live_timer_cd
import io.github.lopezjavier.appfitness.composeapp.resources.live_timer_paused_state
import org.jetbrains.compose.resources.stringResource

private const val DESCRIPTION_STEP_SECONDS = 10L
private const val SECONDS_PER_MINUTE = 60L

/**
 * Semantics of the session stopwatch shown in the live status strip. The spoken description changes in 10 second
 * steps so a screen reader is not chatty; a paused stopwatch also announces "En pausa" as its state.
 */
@Composable
internal fun liveTimerSemantics(elapsedSeconds: Long, isPaused: Boolean): SemanticsPropertyReceiver.() -> Unit {
    val spokenSeconds = elapsedSeconds.coerceAtLeast(0) / DESCRIPTION_STEP_SECONDS * DESCRIPTION_STEP_SECONDS
    val description = stringResource(
        Res.string.live_timer_cd,
        spokenSeconds / SECONDS_PER_MINUTE,
        spokenSeconds % SECONDS_PER_MINUTE,
    )
    val pausedState = stringResource(Res.string.live_timer_paused_state)
    return {
        testTag = "live_timer"
        contentDescription = description
        if (isPaused) stateDescription = pausedState
    }
}
