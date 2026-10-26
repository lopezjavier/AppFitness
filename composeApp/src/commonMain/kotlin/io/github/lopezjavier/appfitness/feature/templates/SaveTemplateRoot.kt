package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.save_template_error_failed
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Stateful save-as-comida sheet for the meal [slotId] of the ISO day [date]: connects [SaveTemplateViewModel].
 * [onSave] runs after the comida was created (with haptic feedback) and [onDismiss] when the sheet is closed without
 * saving.
 */
@Composable
fun SaveTemplateRoot(
    slotId: String,
    date: String,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SaveTemplateViewModel = koinViewModel(parameters = { parametersOf(slotId, date) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalAppHaptics.current
    val messages = LocalAppMessages.current
    val failedText = stringResource(Res.string.save_template_error_failed)
    val currentFailedText by rememberUpdatedState(failedText)
    val currentOnSave by rememberUpdatedState(onSave)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SaveTemplateEffect.Saved -> {
                    haptic.confirm()
                    currentOnSave()
                }

                is SaveTemplateEffect.SaveFailed -> {
                    haptic.reject()
                    messages.show(currentFailedText)
                }
            }
        }
    }
    SaveTemplateSheet(
        state = state,
        onEvent = viewModel::onEvent,
        onDismiss = onDismiss,
        modifier = modifier,
    )
}
