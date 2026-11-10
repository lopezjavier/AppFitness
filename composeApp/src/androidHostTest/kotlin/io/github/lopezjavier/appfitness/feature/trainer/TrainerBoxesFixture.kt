package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import kotlinx.coroutines.flow.Flow

private const val NONE = -1

/**
 * The Trainer root with "Tu plan" and "Tu bloque" behind their boxes, like the navigation host wires them: same
 * signature as [TrainerScreen] so a test opens a box with [openTrainerPlan] or [openTrainerBlock] before asserting its
 * content.
 */
@Composable
internal fun TrainerWithBoxes(
    state: TrainerUiState,
    isOnline: Flow<Boolean>,
    onEvent: (TrainerEvent) -> Unit,
    modifier: Modifier = Modifier,
    actions: TrainerActions = TrainerActions(),
    exerciseNames: Map<String, String> = emptyMap(),
) {
    var open by rememberSaveable { mutableIntStateOf(NONE) }
    if (open != NONE) {
        TrainerBoxScreen(
            TrainerBox.entries[open],
            state,
            isOnline,
            onEvent,
            onBack = { open = NONE },
            modifier = modifier,
            actions = actions,
            exerciseNames = exerciseNames,
        )
    } else {
        TrainerScreen(
            state,
            isOnline,
            onEvent,
            modifier,
            actions.copy(
                onOpenPlan = { open = TrainerBox.Plan.ordinal },
                onOpenBlockScreen = { open = TrainerBox.Block.ordinal },
            ),
            exerciseNames,
        )
    }
}

/** Taps the root's "Tu plan" box. */
internal fun ComposeTestRule.openTrainerPlan() {
    onNodeWithTag("trainer_box_plan").performClick()
    waitForIdle()
}

/** Taps the root's "Tu bloque" box. */
internal fun ComposeTestRule.openTrainerBlock() {
    onNodeWithTag("trainer_box_block").performClick()
    waitForIdle()
}

/**
 * The stateful root and its box screens over one [viewModel] (the app gives each its own instance, read again on
 * resume): [openTrainerPlan] / [openTrainerBlock] open them, their back returns to the root.
 */
@Composable
@Suppress("ViewModelForwarding") // A test host wires the real view model into both screens.
internal fun TrainerRoutesWithBoxes(
    viewModel: TrainerViewModel,
    connectivity: ConnectivityObserver,
    actions: TrainerActions = TrainerActions(),
) {
    var open by rememberSaveable { mutableIntStateOf(NONE) }
    if (open != NONE) {
        TrainerBoxRoot(
            box = TrainerBox.entries[open],
            actions = actions,
            onBack = { open = NONE },
            viewModel = viewModel,
            connectivity = connectivity,
        )
    } else {
        TrainerRoute(
            actions = actions.copy(
                onOpenPlan = { open = TrainerBox.Plan.ordinal },
                onOpenBlockScreen = { open = TrainerBox.Block.ordinal },
            ),
            viewModel = viewModel,
            connectivity = connectivity,
        )
    }
}
