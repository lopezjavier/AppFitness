package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_arrow_back
import io.github.lopezjavier.appfitness.composeapp.resources.ic_more_vert
import io.github.lopezjavier.appfitness.composeapp.resources.ic_pause
import io.github.lopezjavier.appfitness.composeapp.resources.ic_play_arrow
import io.github.lopezjavier.appfitness.composeapp.resources.live_menu_cd
import io.github.lopezjavier.appfitness.composeapp.resources.live_pause
import io.github.lopezjavier.appfitness.composeapp.resources.live_paused_label
import io.github.lopezjavier.appfitness.composeapp.resources.live_resume
import io.github.lopezjavier.appfitness.composeapp.resources.live_timer_cd
import io.github.lopezjavier.appfitness.composeapp.resources.live_timer_paused_state
import io.github.lopezjavier.appfitness.composeapp.resources.live_title
import io.github.lopezjavier.appfitness.composeapp.resources.topbar_back_cd
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.AppTopBarDefaults
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource

private const val DESCRIPTION_STEP_SECONDS = 10L
private const val SECONDS_PER_MINUTE = 60L

/**
 * Top bar of the live screen: back arrow (the session stays in progress), "EN VIVO" with the stopwatch and, when
 * [onMenu] is given, the overflow button. The stopwatch speaks in 10 second steps so a screen reader is not chatty.
 */
@Composable
internal fun LiveSessionTopBar(
    elapsedSeconds: Long,
    onBack: () -> Unit,
    onMenu: (() -> Unit)?,
    modifier: Modifier = Modifier,
    isPaused: Boolean = false,
    onTogglePause: (() -> Unit)? = null,
) {
    val spokenSeconds = elapsedSeconds.coerceAtLeast(0) / DESCRIPTION_STEP_SECONDS * DESCRIPTION_STEP_SECONDS
    val timerDescription = stringResource(
        Res.string.live_timer_cd,
        spokenSeconds / SECONDS_PER_MINUTE,
        spokenSeconds % SECONDS_PER_MINUTE,
    )
    val pausedState = stringResource(Res.string.live_timer_paused_state)
    Row(
        modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surfaceBase)
            .heightIn(min = AppTopBarDefaults.Height)
            .padding(start = AppTopBarDefaults.StartPaddingWithNavigation, end = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        AppIconButton(stringResource(Res.string.topbar_back_cd), Res.drawable.ic_arrow_back, "live_back", onBack)
        Column(Modifier.weight(1f)) {
            if (isPaused) {
                Text(
                    text = stringResource(Res.string.live_paused_label),
                    style = AppTheme.typography.labelTechnical,
                    color = AppTheme.colors.energyAmber,
                    modifier = Modifier.testTag("live_pause_label"),
                )
            } else {
                Text(
                    text = stringResource(Res.string.live_title),
                    style = AppTheme.typography.labelTechnical,
                    color = AppTheme.colors.athleticCyan,
                )
            }
            Text(
                text = formatElapsed(elapsedSeconds),
                style = AppTheme.typography.labelNumeric,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.semantics {
                    contentDescription = timerDescription
                    if (isPaused) stateDescription = pausedState
                }.testTag("live_timer"),
            )
        }
        onTogglePause?.let { PauseToggle(isPaused, it) }
        if (onMenu != null) {
            AppIconButton(
                stringResource(Res.string.live_menu_cd),
                Res.drawable.ic_more_vert,
                "live_menu_button",
                onMenu,
            )
        }
    }
}

@Composable
private fun PauseToggle(isPaused: Boolean, onClick: () -> Unit) {
    AppIconButton(
        contentDescription = stringResource(if (isPaused) Res.string.live_resume else Res.string.live_pause),
        icon = if (isPaused) Res.drawable.ic_play_arrow else Res.drawable.ic_pause,
        testTag = "live_pause_toggle",
        onClick = onClick,
    )
}
