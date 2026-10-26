package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.edit_entry_save_error
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Stateful edit-entry sheet for [entryId] of the ISO day [date]: connects [EditEntryViewModel]. [onSave] runs after
 * the entry was updated (one Confirm vibration), [onDelete] when the user chose "Eliminar" (one Confirm; the Diary
 * owns the delete) and [onDismiss] when the sheet is closed without saving. A failed save keeps the sheet open, fires
 * Reject and shows the error through the app messages (R-28).
 */
@Composable
fun EditEntryRoot(
    entryId: String,
    date: String,
    onSave: () -> Unit,
    onDelete: (entryId: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditEntryViewModel = koinViewModel(parameters = { parametersOf(entryId, date) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalAppHaptics.current
    val messages = LocalAppMessages.current
    val saveError = stringResource(Res.string.edit_entry_save_error)
    val currentSaveError by rememberUpdatedState(saveError)
    val currentOnSave by rememberUpdatedState(onSave)
    val currentOnDelete by rememberUpdatedState(onDelete)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                EditEntryEffect.Saved -> {
                    haptic.confirm()
                    currentOnSave()
                }

                is EditEntryEffect.SaveFailed -> {
                    haptic.reject()
                    messages.show(currentSaveError)
                }

                is EditEntryEffect.DeleteRequested -> {
                    haptic.confirm()
                    currentOnDelete(effect.entryId)
                }
            }
        }
    }
    EditEntrySheet(state = state, onEvent = viewModel::onEvent, onDismiss = onDismiss, modifier = modifier)
}
