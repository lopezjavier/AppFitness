package io.github.lopezjavier.appfitness.feature.food.photo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import io.github.lopezjavier.appfitness.composeapp.resources.action_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_photo_error
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_photo_offline
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_estimating
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_retake
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_use
import io.github.lopezjavier.appfitness.designsystem.AppButtonDefaults
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import org.jetbrains.compose.resources.stringResource

private const val TAG = "food_sheet_amount_photo"
private const val NOTICE_TAG = "food_sheet_amount"

/**
 * Draws the source sheet and the full-screen preview of the "estimate the amount from a photo" flow (US-54); the
 * add-food sheet is not composed while the preview shows. Nothing is drawn while closed, estimating or on a failure:
 * [AmountPhotoNotice] shows those inline under the amount, inside the sheet. [readPhoto] returns the JPEG bytes of
 * a photo handle.
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
            tagPrefix = "${TAG}_source",
        )

        is FoodPhotoStep.Preview -> AmountPreview(step.handle, state.isOffline, readPhoto, onEvent, modifier)

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

/**
 * Under the amount field, inside the add-food sheet (never in a window below it): "Estimando…" with a cancel button
 * while [AmountPhotoPhase.Estimating] (the late result of a cancelled estimate is ignored), the failure with
 * "Reintentar" (R-31) while [AmountPhotoPhase.Failed], or the offline reason while the icon is disabled. The amount
 * itself is never touched.
 */
@Composable
fun AmountPhotoNotice(state: AmountPhotoUiState, onEvent: (AmountPhotoEvent) -> Unit, modifier: Modifier = Modifier) {
    when (val phase = state.phase) {
        AmountPhotoPhase.Estimating -> Row(
            modifier.fillMaxWidth().testTag("${NOTICE_TAG}_estimating"),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Decorative: the text carries the meaning.
                CircularProgressIndicator(color = AppTheme.colors.neonLime, modifier = Modifier.size(Spacing.xl))
                Text(
                    text = stringResource(Res.string.food_photo_estimating),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textPrimary,
                )
            }
            SecondaryButton(
                text = stringResource(Res.string.action_cancel),
                onClick = { onEvent(AmountPhotoEvent.Dismiss) },
                modifier = Modifier.testTag("${NOTICE_TAG}_cancel"),
            )
        }

        is AmountPhotoPhase.Failed -> if (phase.retryable) {
            ErrorState(
                message = stringResource(Res.string.add_food_photo_error),
                action = AppStateAction(stringResource(Res.string.action_retry)) { onEvent(AmountPhotoEvent.Retry) },
                modifier = modifier,
                testTagPrefix = "${NOTICE_TAG}_error",
                actionTestTag = "${NOTICE_TAG}_retry",
            )
        } else {
            Text(
                text = stringResource(Res.string.add_food_photo_error),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.error,
                modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("${NOTICE_TAG}_error"),
            )
        }

        AmountPhotoPhase.Idle -> if (state.isOffline) {
            Text(
                text = stringResource(Res.string.add_food_photo_offline),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("${NOTICE_TAG}_offline"),
            )
        }
    }
}
