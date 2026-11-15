package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeTestRule
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import kotlinx.coroutines.flow.Flow

/**
 * Transitional shim (T-283): the "Tu plan" and "Tu bloque" screens are gone, so this only renders the Trainer root.
 * The tests that still call [openTrainerPlan] / [openTrainerBlock] are listed in `tasks/ui-test-changes-trainer.md`
 * ("delete/retarget") for the test block; the shim goes with them.
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
    TrainerScreen(state, isOnline, onEvent, modifier, actions, exerciseNames)
}

/** No-op: the "Tu plan" screen no longer exists (retarget the caller to the root card). */
internal fun ComposeTestRule.openTrainerPlan() {
    waitForIdle()
}

/** No-op: the "Tu bloque" screen no longer exists (retarget the caller to the root card). */
internal fun ComposeTestRule.openTrainerBlock() {
    waitForIdle()
}

/** The stateful Trainer root over one [viewModel]; the box screens it used to open are gone. */
@Composable
@Suppress("ViewModelForwarding") // A test host wires the real view model into the screen.
internal fun TrainerRoutesWithBoxes(
    viewModel: TrainerViewModel,
    connectivity: ConnectivityObserver,
    actions: TrainerActions = TrainerActions(),
) {
    TrainerRoute(actions = actions, viewModel = viewModel, connectivity = connectivity)
}
