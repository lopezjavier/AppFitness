package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_write_failed
import io.github.lopezjavier.appfitness.composeapp.resources.diary_write_offline
import io.github.lopezjavier.appfitness.domain.AppError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The snackbar texts, read in composition so the effect collector always has the current resources. */
private class DiaryMessages(val failed: String, val offline: String)

/**
 * Stateful "Diario" tab: connects [DiaryViewModel]. [onOpenPreferences] leads the "Configura tu objetivo" prompt to
 * Preferencias; [onAddToMeal] receives the meal and day to add food to and [onAddFood] the day for the Diary-level
 * add action (no meal preselected). [onSaveTemplate] receives the meal and day whose entries are saved as a comida.
 * On resume the goals are reloaded so a goal set in Preferencias shows up. Deleting
 * an entry is immediate and silent; a failed write shows a message.
 */
@Composable
@Suppress("LongParameterList") // One callback per destination plus the edit host.
fun DiaryRoot(
    onOpenPreferences: () -> Unit,
    onAddToMeal: (slotId: String, date: LocalDate) -> Unit,
    onAddFood: (date: LocalDate) -> Unit,
    onSaveTemplate: (slotId: String, date: LocalDate) -> Unit,
    editHost: DiaryEditHost,
    modifier: Modifier = Modifier,
    viewModel: DiaryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnAddToMeal by rememberUpdatedState(onAddToMeal)
    val currentOnAddFood by rememberUpdatedState(onAddFood)
    val currentOnSaveTemplate by rememberUpdatedState(onSaveTemplate)
    val currentOnOpenPreferences by rememberUpdatedState(onOpenPreferences)
    val currentEditHost by rememberUpdatedState(editHost)
    val snackbarHost = remember { SnackbarHostState() }
    val messages = DiaryMessages(
        failed = stringResource(Res.string.diary_write_failed),
        offline = stringResource(Res.string.diary_write_offline),
    )
    val currentMessages by rememberUpdatedState(messages)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DiaryEffect.AddToMeal -> currentOnAddToMeal(effect.slotId, effect.date)
                is DiaryEffect.AddFood -> currentOnAddFood(effect.date)
                is DiaryEffect.OpenSaveTemplate -> currentOnSaveTemplate(effect.slotId, effect.date)
                DiaryEffect.OpenPreferences -> currentOnOpenPreferences()
                is DiaryEffect.OpenEditEntry -> currentEditHost.onEditEntry(effect.entryId, effect.date)
                is DiaryEffect.WriteFailed -> showFailure(snackbarHost, currentMessages, effect.error)
            }
        }
    }
    LaunchedEffect(editHost.pendingDeleteEntryId) {
        editHost.pendingDeleteEntryId?.let {
            viewModel.onEvent(DiaryEvent.DeleteEntry(it))
            currentEditHost.onConsumeDelete()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(DiaryEvent.Refresh) }
    DiaryScreen(state, viewModel::onEvent, modifier, snackbarHost)
}

private fun CoroutineScope.showFailure(host: SnackbarHostState, messages: DiaryMessages, error: AppError?) {
    launch { host.showSnackbar(if (error == AppError.Network) messages.offline else messages.failed) }
}
