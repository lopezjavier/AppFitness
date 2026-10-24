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
import io.github.lopezjavier.appfitness.composeapp.resources.live_updateRoutine_error
import io.github.lopezjavier.appfitness.composeapp.resources.live_updateRoutine_exercises_added
import io.github.lopezjavier.appfitness.composeapp.resources.live_updateRoutine_exercises_removed
import io.github.lopezjavier.appfitness.composeapp.resources.live_updateRoutine_keep
import io.github.lopezjavier.appfitness.composeapp.resources.live_updateRoutine_more
import io.github.lopezjavier.appfitness.composeapp.resources.live_updateRoutine_order_changed
import io.github.lopezjavier.appfitness.composeapp.resources.live_updateRoutine_rests_changed
import io.github.lopezjavier.appfitness.composeapp.resources.live_updateRoutine_sets_added
import io.github.lopezjavier.appfitness.composeapp.resources.live_updateRoutine_sets_changed
import io.github.lopezjavier.appfitness.composeapp.resources.live_updateRoutine_sets_removed
import io.github.lopezjavier.appfitness.composeapp.resources.live_updateRoutine_title
import io.github.lopezjavier.appfitness.composeapp.resources.live_updateRoutine_update
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.GhostTone
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val SHEET_TAG = "live_updateRoutine_sheet"
private const val MAX_LINES = 4

/** One summary line: a plural with its count; the order/superset line has no plural (null). */
private data class SummaryLine(val plural: PluralStringResource?, val count: Int)

private fun RoutineChangeSummary.lines(): List<SummaryLine> = buildList {
    if (setsAdded > 0) add(SummaryLine(Res.plurals.live_updateRoutine_sets_added, setsAdded))
    if (setsRemoved > 0) add(SummaryLine(Res.plurals.live_updateRoutine_sets_removed, setsRemoved))
    if (setsChanged > 0) add(SummaryLine(Res.plurals.live_updateRoutine_sets_changed, setsChanged))
    if (restsChanged > 0) add(SummaryLine(Res.plurals.live_updateRoutine_rests_changed, restsChanged))
    if (exercisesAdded > 0) add(SummaryLine(Res.plurals.live_updateRoutine_exercises_added, exercisesAdded))
    if (exercisesRemoved > 0) add(SummaryLine(Res.plurals.live_updateRoutine_exercises_removed, exercisesRemoved))
    if (orderOrSupersetChanged) add(SummaryLine(null, 1))
}

/**
 * "Actualizar rutina" (US-36): shown when the finished session differs from its routine. Scrim, back and swipe equal
 * "Mantener rutina original" ([onKeep]); they are ignored while the update runs. A failed update keeps the sheet.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.
internal fun UpdateRoutineBottomSheet(
    sheet: UpdateRoutineSheet,
    onUpdate: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBottomSheet(
        onDismissRequest = { if (!sheet.loading) onKeep() },
        modifier = modifier,
        testTagPrefix = SHEET_TAG,
    ) {
        Column(
            Modifier.padding(horizontal = Spacing.screenMargin).padding(bottom = Spacing.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(Res.string.live_updateRoutine_title),
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            SummaryLines(sheet.summary)
            if (sheet.failed) {
                Text(
                    text = stringResource(Res.string.live_updateRoutine_error),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("live_updateRoutine_error"),
                )
            }
            PrimaryButton(
                text = stringResource(Res.string.live_updateRoutine_update),
                onClick = onUpdate,
                modifier = Modifier.fillMaxWidth().testTag("live_updateRoutine_update"),
                enabled = !sheet.loading,
                loading = sheet.loading,
            )
            GhostButton(
                text = stringResource(Res.string.live_updateRoutine_keep),
                onClick = onKeep,
                modifier = Modifier.fillMaxWidth().testTag("live_updateRoutine_keep"),
                tone = GhostTone.Neutral,
                enabled = !sheet.loading,
            )
        }
    }
}

/** One line per change type present; past [MAX_LINES] the rest fold into "y N cambios más". */
@Composable
private fun SummaryLines(summary: RoutineChangeSummary) {
    val lines = summary.lines()
    val shown = if (lines.size > MAX_LINES) lines.take(MAX_LINES - 1) else lines
    val hidden = lines.drop(shown.size).sumOf { it.count }
    Column(
        Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("live_updateRoutine_summary"),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        shown.forEach { line -> SummaryText(line.text()) }
        if (hidden > 0) SummaryText(pluralStringResource(Res.plurals.live_updateRoutine_more, hidden, hidden))
    }
}

@Composable
private fun SummaryLine.text(): String = if (plural == null) {
    stringResource(Res.string.live_updateRoutine_order_changed)
} else {
    pluralStringResource(plural, count, count)
}

@Composable
private fun SummaryText(text: String) {
    Text(text = text, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
}
