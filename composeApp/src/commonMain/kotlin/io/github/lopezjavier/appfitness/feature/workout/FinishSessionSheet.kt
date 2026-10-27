package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.live_finish_sheet_title
import io.github.lopezjavier.appfitness.composeapp.resources.live_finish_warning
import io.github.lopezjavier.appfitness.designsystem.AppEditSheet
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.SheetCommit
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val SHEET_TAG = "live_finish_sheet"

/**
 * Confirmation of "Finalizar entreno" (edit archetype, commit "Finalizar"). It warns about the sets that were not
 * completed (they are not saved) only when there are some; [busy] shows progress on the commit button while the
 * session is being closed. Closing the sheet keeps the session running.
 */
@Composable
internal fun FinishSessionSheet(
    sheet: FinishSheet,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppEditSheet(
        title = stringResource(Res.string.live_finish_sheet_title),
        commit = SheetCommit.Finish,
        onCommit = onConfirm,
        onDismiss = onDismiss,
        modifier = modifier,
        saving = busy,
        testTagPrefix = SHEET_TAG,
    ) {
        if (sheet.pendingCount > 0) {
            Text(
                text = pluralStringResource(Res.plurals.live_finish_warning, sheet.pendingCount, sheet.pendingCount),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.energyAmber,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("${SHEET_TAG}_warning"),
            )
        }
    }
}
