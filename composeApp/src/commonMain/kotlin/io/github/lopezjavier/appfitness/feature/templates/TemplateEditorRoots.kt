package io.github.lopezjavier.appfitness.feature.templates

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Stateful editor of the comida [templateId]: connects [TemplateEditorViewModel]. [onSave] runs after the comida was
 * saved (overwritten or created), with haptic feedback, so the editor can be left.
 */
@Composable
fun TemplateEditorRoot(
    templateId: String,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TemplateEditorViewModel = koinViewModel(parameters = { parametersOf(templateId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var saveFailed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val currentOnSaved by rememberUpdatedState(onSave)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                TemplateEditorEffect.Saved -> {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    currentOnSaved()
                }

                is TemplateEditorEffect.SaveFailed -> saveFailed = true
            }
        }
    }
    TemplateEditorScreen(
        state = state,
        onEvent = {
            if (it == TemplateEditorEvent.Overwrite || it == TemplateEditorEvent.SubmitNewName) saveFailed = false
            viewModel.onEvent(it)
        },
        modifier = modifier,
        saveFailed = saveFailed,
    )
}

/**
 * Stateful details of the comida [templateId]: connects [TemplateDetailsViewModel]. [onEdit] opens the editor,
 * [onDelete] runs (with haptic feedback) after the comida was deleted so the details can be left. The comida is
 * reloaded when the screen resumes (the editor may have changed it).
 */
@Composable
fun TemplateDetailsRoot(
    templateId: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TemplateDetailsViewModel = koinViewModel(parameters = { parametersOf(templateId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var actionFailed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val currentOnDelete by rememberUpdatedState(onDelete)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(TemplateDetailsEvent.Refresh) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                TemplateDetailsEffect.Deleted -> {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    currentOnDelete()
                }

                is TemplateDetailsEffect.ActionFailed -> actionFailed = true
            }
        }
    }
    TemplateDetailsScreen(
        state = state,
        onEvent = {
            actionFailed = false
            viewModel.onEvent(it)
        },
        modifier = modifier,
        onEdit = onEdit,
        actionFailed = actionFailed,
    )
}
