package io.github.lopezjavier.appfitness.feature.food.photo

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Draws the current step of the "add from photo" flow over the food search: the mode and source sheets, then the
 * full-screen preview, "Estimando…" and failure steps. Nothing is drawn while the flow is closed. [readPhoto] returns
 * the JPEG bytes of a photo handle.
 */
@Composable
fun FoodPhotoFlow(
    state: FoodPhotoUiState,
    onEvent: (FoodPhotoEvent) -> Unit,
    readPhoto: suspend (String) -> ByteArray,
    modifier: Modifier = Modifier,
) {
    when (val step = state.step) {
        FoodPhotoStep.Closed -> Unit

        FoodPhotoStep.ModeSheet -> PhotoModeSheet(
            onModeChoose = { onEvent(FoodPhotoEvent.ModeChosen(it)) },
            onDismiss = { onEvent(FoodPhotoEvent.Dismiss) },
        )

        FoodPhotoStep.SourceSheet, is FoodPhotoStep.Capturing -> PhotoSourceSheet(
            onSourceChoose = { onEvent(FoodPhotoEvent.SourceChosen(it)) },
            onDismiss = { onEvent(FoodPhotoEvent.Dismiss) },
            captureFailedFor = state.lastSource.takeIf { state.captureFailed },
        )

        is FoodPhotoStep.Preview -> PhotoPreview(
            handle = step.handle,
            state = state,
            readPhoto = readPhoto,
            onEvent = onEvent,
            modifier = modifier,
        )

        FoodPhotoStep.Estimating -> PhotoEstimating(modifier)

        is FoodPhotoStep.Error -> PhotoError(step.kind, onEvent, modifier)
    }
}
