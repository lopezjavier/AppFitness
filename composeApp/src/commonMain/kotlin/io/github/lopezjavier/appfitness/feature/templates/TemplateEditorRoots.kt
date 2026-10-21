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
