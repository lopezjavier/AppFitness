package io.github.lopezjavier.appfitness.feature.checkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_load_error
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_save_error
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_save_error_offline
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource

private const val SKELETON_CARDS = 3
private const val SKELETON_CARD_UNITS = 3

/** A saved check-in lets the editor close; a failed save is one Reject haptic and one message (R-28, R-37). */
@Composable
internal fun CheckInEffects(effects: Flow<CheckInEffect>, onSave: () -> Unit) {
    val haptics = LocalAppHaptics.current
    val messages = LocalAppMessages.current
    val failed = stringResource(Res.string.checkin_save_error)
    val offline = stringResource(Res.string.checkin_save_error_offline)
    val currentOnSaved by rememberUpdatedState(onSave)
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                CheckInEffect.Saved -> currentOnSaved()

                is CheckInEffect.SaveFailed -> {
                    haptics.reject()
                    messages.show(if (effect.offline) offline else failed)
                }
            }
        }
    }
}

/** The check-in while it loads or when it could not be determined (no form yet). */
@Composable
internal fun CheckInPlaceholder(
    state: CheckInUiState,
    onEvent: (CheckInEvent) -> Unit,
    onClose: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
) {
    AppScreen(title = title, modifier = modifier, onBack = onClose, testTagPrefix = "checkin") {
        Box(Modifier.fillMaxSize().testTag("checkin_screen")) {
            if (state == CheckInUiState.LoadFailed) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ErrorState(
                        message = stringResource(Res.string.checkin_load_error),
                        action = AppStateAction(stringResource(Res.string.action_retry)) {
                            onEvent(CheckInEvent.Retry)
                        },
                        actionTestTag = "checkin_retry",
                        testTagPrefix = "checkin_error",
                    )
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(Spacing.screenMargin).testTag("checkin_loading"),
                    verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
                ) { SummarySkeleton() }
            }
        }
    }
}

@Composable
internal fun SummarySkeleton() {
    repeat(SKELETON_CARDS) {
        SkeletonBox(Modifier.fillMaxWidth().height(Spacing.xl * SKELETON_CARD_UNITS), AppShapes.lg)
    }
}
