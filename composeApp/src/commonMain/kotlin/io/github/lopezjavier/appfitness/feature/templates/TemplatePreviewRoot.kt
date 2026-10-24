package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Stateful read-only details of the comida [templateId] (US-53): connects [TemplatePreviewViewModel] for the meal
 * [slotId] (null = chosen by the time of day) and the ISO day [date]. [onAdd] runs, with haptic feedback, once the
 * comida was logged; [onEdit] opens its editor. The comida is reloaded when the screen resumes (the editor may have
 * changed it).
 */
@Composable
fun TemplatePreviewRoot(
    templateId: String,
    slotId: String?,
    date: String,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TemplatePreviewViewModel =
        koinViewModel(parameters = { parametersOf(templateId, slotId.orEmpty(), date) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val currentOnAdd by rememberUpdatedState(onAdd)
    val currentOnEdit by rememberUpdatedState(onEdit)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(TemplatePreviewEvent.Refresh) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                TemplatePreviewEffect.Added -> {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    currentOnAdd()
                }

                // The state already shows the failure text.
                is TemplatePreviewEffect.Failed -> Unit

                TemplatePreviewEffect.OpenEditor -> currentOnEdit()
            }
        }
    }
    TemplatePreviewScreen(state, viewModel::onEvent, modifier)
}
