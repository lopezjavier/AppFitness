package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.live_updateRoutine_error
import io.github.lopezjavier.appfitness.composeapp.resources.live_updateRoutine_exercises_added
import io.github.lopezjavier.appfitness.composeapp.resources.live_updateRoutine_exercises_removed
import io.github.lopezjavier.appfitness.composeapp.resources.live_updateRoutine_keep
import io.github.lopezjavier.appfitness.composeapp.resources.live_updateRoutine_keep_desc
import io.github.lopezjavier.appfitness.composeapp.resources.live_updateRoutine_more
import io.github.lopezjavier.appfitness.composeapp.resources.live_updateRoutine_order_changed
import io.github.lopezjavier.appfitness.composeapp.resources.live_updateRoutine_rests_changed
import io.github.lopezjavier.appfitness.composeapp.resources.live_updateRoutine_sets_added
import io.github.lopezjavier.appfitness.composeapp.resources.live_updateRoutine_sets_changed
import io.github.lopezjavier.appfitness.composeapp.resources.live_updateRoutine_sets_removed
import io.github.lopezjavier.appfitness.composeapp.resources.live_updateRoutine_title
import io.github.lopezjavier.appfitness.composeapp.resources.live_updateRoutine_update
import io.github.lopezjavier.appfitness.composeapp.resources.live_updateRoutine_update_desc
import io.github.lopezjavier.appfitness.designsystem.AppEditSheet
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.SelectableCard
import io.github.lopezjavier.appfitness.designsystem.SheetCommit
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
 * "Actualizar rutina" (US-36): shown when the finished session differs from its routine (edit archetype, commit
 * "Guardar"). Two exclusive choices; nothing is written until the commit. Closing (X, scrim, back, swipe) equals
 * "Mantener la rutina" ([onKeep]) and is ignored while the update runs. A failed update keeps the sheet.
 */
@Composable
internal fun UpdateRoutineBottomSheet(
    sheet: UpdateRoutineSheet,
    onUpdate: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var update by rememberSaveable { mutableStateOf(true) }
    AppEditSheet(
        title = stringResource(Res.string.live_updateRoutine_title),
        commit = SheetCommit.Save,
        onCommit = { if (update) onUpdate() else onKeep() },
        onDismiss = { if (!sheet.loading) onKeep() },
        modifier = modifier,
        saving = sheet.loading,
        testTagPrefix = SHEET_TAG,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SummaryLines(sheet.summary)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
                SelectableCard(
                    title = stringResource(Res.string.live_updateRoutine_update),
                    description = stringResource(Res.string.live_updateRoutine_update_desc),
                    selected = update,
                    onClick = { update = true },
                    modifier = Modifier.testTag("live_updateRoutine_update"),
                )
                SelectableCard(
                    title = stringResource(Res.string.live_updateRoutine_keep),
                    description = stringResource(Res.string.live_updateRoutine_keep_desc),
                    selected = !update,
                    onClick = { update = false },
                    modifier = Modifier.testTag("live_updateRoutine_keep"),
                )
            }
            if (sheet.failed) {
                Text(
                    text = stringResource(Res.string.live_updateRoutine_error),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("live_updateRoutine_error"),
                )
            }
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
