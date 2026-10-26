package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
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
    var saveFailed by remember { mutableStateOf(false) }
    val haptic = LocalAppHaptics.current
    val currentOnSave by rememberUpdatedState(onSave)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SaveTemplateEffect.Saved -> {
                    haptic.confirm()
                    currentOnSave()
                }

                is SaveTemplateEffect.SaveFailed -> saveFailed = true
            }
        }
    }
    SaveTemplateSheet(
        state = state,
        onEvent = {
            if (it == SaveTemplateEvent.Submit) saveFailed = false
            viewModel.onEvent(it)
        },
        onDismiss = onDismiss,
        modifier = modifier,
        saveFailed = saveFailed,
    )
}
