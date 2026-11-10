package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import kotlinx.coroutines.flow.Flow

/**
 * The Trainer root with "Detalles" behind its row, like the navigation host wires them: same signature as
 * [TrainerScreen] so a test opens the details with [openTrainerDetails] before asserting their content.
 */
@Composable
internal fun TrainerWithDetails(
    state: TrainerUiState,
    isOnline: Flow<Boolean>,
    onEvent: (TrainerEvent) -> Unit,
    modifier: Modifier = Modifier,
    actions: TrainerActions = TrainerActions(),
    exerciseNames: Map<String, String> = emptyMap(),
) {
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    if (detailsOpen) {
        TrainerDetailsScreen(
            state,
            isOnline,
            onEvent,
            onBack = { detailsOpen = false },
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
            actions.copy(onOpenDetails = { detailsOpen = true }),
            exerciseNames,
        )
    }
}

/** Taps the root's "Detalles" row. */
internal fun ComposeTestRule.openTrainerDetails() {
    onNodeWithTag("trainer_details_row").performClick()
    waitForIdle()
}

/**
 * The stateful root and "Detalles" over one [viewModel] (the app gives each its own instance, read again on resume):
 * [openTrainerDetails] opens the details, their back returns to the root.
 */
@Composable
@Suppress("ViewModelForwarding") // A test host wires the real view model into both screens.
internal fun TrainerRoutesWithDetails(
    viewModel: TrainerViewModel,
    connectivity: ConnectivityObserver,
    actions: TrainerActions = TrainerActions(),
) {
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    if (detailsOpen) {
        TrainerDetailsRoot(
            actions = actions,
            onBack = { detailsOpen = false },
            viewModel = viewModel,
            connectivity = connectivity,
        )
    } else {
        TrainerRoute(
            actions = actions.copy(onOpenDetails = { detailsOpen = true }),
            viewModel = viewModel,
            connectivity = connectivity,
        )
    }
}
