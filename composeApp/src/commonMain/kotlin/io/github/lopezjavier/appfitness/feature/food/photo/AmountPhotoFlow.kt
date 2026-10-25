package io.github.lopezjavier.appfitness.feature.food.photo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_photo_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_photo_error
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_photo_offline
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_estimating
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_retake
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_use
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppButtonDefaults
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import org.jetbrains.compose.resources.stringResource

private const val TAG = "add_food_photo"

/**
 * Draws the source sheet, the preview and "Estimando…" of the "estimate the amount from a photo" flow (US-54) over
 * the add-food sheet. Nothing is drawn while closed or on a failure: [AmountPhotoNotice] shows that inline under the
 * amount. [readPhoto] returns the JPEG bytes of a photo handle.
 */
@Composable
fun AmountPhotoFlow(
    state: AmountPhotoUiState,
    onEvent: (AmountPhotoEvent) -> Unit,
    readPhoto: suspend (String) -> ByteArray,
    modifier: Modifier = Modifier,
) {
    when (val step = state.step) {
        FoodPhotoStep.SourceSheet, is FoodPhotoStep.Capturing -> PhotoSourceSheet(
            onSourceChoose = { onEvent(AmountPhotoEvent.SourceChosen(it)) },
            onDismiss = { onEvent(AmountPhotoEvent.Dismiss) },
            captureFailedFor = state.lastSource.takeIf { state.captureFailed },
            tagPrefix = TAG,
        )

        is FoodPhotoStep.Preview -> AmountPreview(step.handle, state.isOffline, readPhoto, onEvent, modifier)

        FoodPhotoStep.Estimating -> AmountEstimating(onEvent, modifier)

        else -> Unit
    }
}

@Composable
private fun AmountPreview(
    handle: String,
    isOffline: Boolean,
    readPhoto: suspend (String) -> ByteArray,
    onEvent: (AmountPhotoEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    FullScreenStep("${TAG}_preview", { onEvent(AmountPhotoEvent.Back) }, modifier) {
        Column(
            Modifier.widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth).fillMaxSize().navigationBarsPadding(),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
            ) {
                PhotoImage(handle, readPhoto)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SecondaryButton(
                    text = stringResource(Res.string.food_photo_retake),
                    onClick = { onEvent(AmountPhotoEvent.Retake) },
                    modifier = Modifier.widthIn(min = AppButtonDefaults.CompactMinWidth).testTag("${TAG}_retake"),
                )
                PrimaryButton(
                    text = stringResource(Res.string.food_photo_use),
                    onClick = { onEvent(AmountPhotoEvent.UsePhoto) },
                    modifier = Modifier.weight(1f).testTag("${TAG}_use"),
                    enabled = !isOffline,
                )
            }
        }
    }
}

/** "Estimando…" with a cancel button: unlike the add-from-photo flow it can be left (the late result is ignored). */
@Composable
private fun AmountEstimating(onEvent: (AmountPhotoEvent) -> Unit, modifier: Modifier = Modifier) {
    FullScreenStep("${TAG}_estimating", { onEvent(AmountPhotoEvent.Dismiss) }, modifier) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Decorative: the text carries the meaning.
                CircularProgressIndicator(color = AppTheme.colors.neonLime)
                Text(
                    text = stringResource(Res.string.food_photo_estimating),
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                )
            }
            SecondaryButton(
                text = stringResource(Res.string.add_food_photo_cancel),
                onClick = { onEvent(AmountPhotoEvent.Dismiss) },
                modifier = Modifier.testTag("${TAG}_cancel"),
            )
        }
    }
}

/**
 * Under the amount field: the offline reason while the icon is disabled, or the failure with "Reintentar". The amount
 * itself is never touched.
 */
@Composable
fun AmountPhotoNotice(state: AmountPhotoUiState, onEvent: (AmountPhotoEvent) -> Unit, modifier: Modifier = Modifier) {
    val failed = state.step is FoodPhotoStep.Error
    when {
        failed -> Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = stringResource(Res.string.add_food_photo_error),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("${TAG}_error"),
            )
            SecondaryButton(
                text = stringResource(Res.string.state_retry),
                onClick = { onEvent(AmountPhotoEvent.Retry) },
                modifier = Modifier.testTag("${TAG}_retry"),
                enabled = !state.isOffline,
            )
        }

        state.isOffline -> Text(
            text = stringResource(Res.string.add_food_photo_offline),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
            modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("${TAG}_offline"),
        )
    }
}
