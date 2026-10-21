package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Stateful edit-entry sheet for [entryId] of the ISO day [date]: connects [EditEntryViewModel]. [onSave] runs after
 * the entry was updated (with haptic feedback), [onDelete] when the user chose "Eliminar" (the Diary owns the delete)
 * and [onDismiss] when the sheet is closed without saving.
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
    var saveFailed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val currentOnSave by rememberUpdatedState(onSave)
    val currentOnDelete by rememberUpdatedState(onDelete)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                EditEntryEffect.Saved -> {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    currentOnSave()
                }

                is EditEntryEffect.SaveFailed -> saveFailed = true

                is EditEntryEffect.DeleteRequested -> currentOnDelete(effect.entryId)
            }
        }
    }
    EditEntrySheet(
        state = state,
        onEvent = {
            if (it == EditEntryEvent.Save) saveFailed = false
            viewModel.onEvent(it)
        },
        onDismiss = onDismiss,
        modifier = modifier,
        saveFailed = saveFailed,
    )
}
