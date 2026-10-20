package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_entry_deleted
import io.github.lopezjavier.appfitness.composeapp.resources.diary_undo
import io.github.lopezjavier.appfitness.composeapp.resources.diary_write_failed
import io.github.lopezjavier.appfitness.composeapp.resources.diary_write_offline
import io.github.lopezjavier.appfitness.domain.AppError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The snackbar texts, read in composition so the effect collector always has the current resources. */
private class DiaryMessages(val deleted: String, val undo: String, val failed: String, val offline: String)

/**
 * Stateful "Diario" tab: connects [DiaryViewModel]. [onOpenPreferences] leads the "Configura tu objetivo" prompt to
 * Preferencias; [onAddToMeal] receives the meal and day to add food to and [onAddFood] the day for the Diary-level
 * add action (no meal preselected). On resume the goals are reloaded so a goal set in Preferencias shows up. Deleting
 * an entry shows a "Deshacer" snackbar; a failed write shows a message.
 */
@Composable
@Suppress("LongParameterList") // One callback per destination plus the edit host.
fun DiaryRoot(
    onOpenPreferences: () -> Unit,
    onAddToMeal: (slotId: String, date: LocalDate) -> Unit,
    onAddFood: (date: LocalDate) -> Unit,
    onOpenMealSettings: (date: LocalDate) -> Unit,
    editHost: DiaryEditHost,
    modifier: Modifier = Modifier,
    viewModel: DiaryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnOpenMealSettings by rememberUpdatedState(onOpenMealSettings)
    val currentOnAddToMeal by rememberUpdatedState(onAddToMeal)
    val currentOnAddFood by rememberUpdatedState(onAddFood)
    val currentOnOpenPreferences by rememberUpdatedState(onOpenPreferences)
    val currentEditHost by rememberUpdatedState(editHost)
    val snackbarHost = remember { SnackbarHostState() }
    val messages = DiaryMessages(
        deleted = stringResource(Res.string.diary_entry_deleted),
        undo = stringResource(Res.string.diary_undo),
        failed = stringResource(Res.string.diary_write_failed),
        offline = stringResource(Res.string.diary_write_offline),
    )
    val currentMessages by rememberUpdatedState(messages)
    val copyDraft = rememberCopyDraft()
    var copiedNotice by remember { mutableStateOf<CopiedNotice?>(null) }
    val content = (state as? DiaryUiState.Content)?.content
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DiaryEffect.AddToMeal -> currentOnAddToMeal(effect.slotId, effect.date)

                is DiaryEffect.AddFood -> currentOnAddFood(effect.date)

                DiaryEffect.OpenPreferences -> currentOnOpenPreferences()

                is DiaryEffect.OpenMealSettings -> currentOnOpenMealSettings(effect.date)

                is DiaryEffect.OpenEditEntry -> currentEditHost.onEditEntry(effect.entryId, effect.date)

                is DiaryEffect.OpenCopy -> copyDraft.open(
                    effect.entryIds,
                    firstSlotOf(viewModel.uiState.value, effect.entryIds),
                    effect.date,
                )

                // A copy names where it landed (built in composition, it needs the string resources and the meals).
                is DiaryEffect.ShowUndo -> effect.target?.let { copiedNotice = CopiedNotice(it) }
                    ?: showDeleted(snackbarHost, currentMessages) { viewModel.onEvent(DiaryEvent.UndoDelete) }

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
    CopiedSnackbarEffect(copiedNotice, content, snackbarHost, viewModel::onEvent)
    DiaryScreen(state, viewModel::onEvent, modifier, snackbarHost)
    content?.let { CopyEntriesHost(copyDraft, it, viewModel::onEvent) }
}

/** Shown from its own coroutine: the snackbar suspends until it is dismissed. */
private fun CoroutineScope.showDeleted(host: SnackbarHostState, messages: DiaryMessages, onUndo: () -> Unit) {
    launch {
        val shown = host.showSnackbar(messages.deleted, messages.undo, duration = SnackbarDuration.Long)
        if (shown == SnackbarResult.ActionPerformed) onUndo()
    }
}

private fun CoroutineScope.showFailure(host: SnackbarHostState, messages: DiaryMessages, error: AppError?) {
    launch { host.showSnackbar(if (error == AppError.Network) messages.offline else messages.failed) }
}

/** The meal of the first of [entryIds] on screen: the copy sheet starts on it. */
private fun firstSlotOf(state: DiaryUiState, entryIds: List<String>): String =
    (state as? DiaryUiState.Content)?.content?.meals
        ?.firstOrNull { section -> section.entries.any { it.id == entryIds.first() } }?.slot?.id.orEmpty()
