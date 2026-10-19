package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.live_finish_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.live_finish_confirm
import io.github.lopezjavier.appfitness.composeapp.resources.live_finish_sheet_title
import io.github.lopezjavier.appfitness.composeapp.resources.live_finish_warning
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.GhostTone
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val SHEET_TAG = "live_finish_sheet"

/**
 * Confirmation of "Finalizar entreno". It warns about the sets that were not completed (they are not saved) only
 * when there are some; [busy] shows progress on the confirm button while the session is being closed.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.
internal fun FinishSessionSheet(
    sheet: FinishSheet,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBottomSheet(onDismissRequest = onDismiss, modifier = modifier, testTagPrefix = SHEET_TAG) {
        Column(
            Modifier.padding(horizontal = Spacing.screenMargin).padding(bottom = Spacing.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(Res.string.live_finish_sheet_title),
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            if (sheet.pendingCount > 0) {
                Text(
                    text = pluralStringResource(
                        Res.plurals.live_finish_warning,
                        sheet.pendingCount,
                        sheet.pendingCount,
                    ),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.energyAmber,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("${SHEET_TAG}_warning"),
                )
            }
            PrimaryButton(
                text = stringResource(Res.string.live_finish_confirm),
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().testTag("${SHEET_TAG}_confirm"),
                enabled = !busy,
                loading = busy,
            )
            GhostButton(
                text = stringResource(Res.string.live_finish_cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().testTag("${SHEET_TAG}_cancel"),
                tone = GhostTone.Neutral,
                enabled = !busy,
            )
        }
    }
}
