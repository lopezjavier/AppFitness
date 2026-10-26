package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.goal_target_field_label
import io.github.lopezjavier.appfitness.composeapp.resources.goal_target_range
import io.github.lopezjavier.appfitness.composeapp.resources.goal_target_sheet_title
import io.github.lopezjavier.appfitness.composeapp.resources.metric_disclaimer
import io.github.lopezjavier.appfitness.designsystem.AppEditSheet
import io.github.lopezjavier.appfitness.designsystem.AppInfoSheet
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.MetricInterpretationRow
import io.github.lopezjavier.appfitness.designsystem.NumberInput
import io.github.lopezjavier.appfitness.designsystem.SheetCommit
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.offlineHint
import io.github.lopezjavier.appfitness.domain.usecase.MetricInterpretation
import org.jetbrains.compose.resources.stringResource

private const val TARGET_INTEGER_DIGITS = 3

/**
 * The target-weight sheet (edit archetype): one field prefilled with the saved target. "Guardar" needs a valid value
 * different from the saved one and connectivity; X, back, swipe and scrim discard the draft.
 */
@Composable
internal fun TargetWeightSheet(sheet: TargetSheetState, isOnline: Boolean, onEvent: (GoalEvent) -> Unit) {
    var text by remember { mutableStateOf(sheet.text) }
    val label = stringResource(Res.string.goal_target_field_label)
    AppEditSheet(
        title = stringResource(Res.string.goal_target_sheet_title),
        commit = SheetCommit.Save,
        onCommit = { onEvent(GoalEvent.SaveTarget) },
        onDismiss = { onEvent(GoalEvent.Dismiss) },
        commitEnabled = sheet.canSave,
        saving = sheet.isSaving,
        commitHint = if (isOnline) null else offlineHint("guardar"),
        testTagPrefix = "weight_target_sheet",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = label.uppercase(),
                style = AppTheme.typography.labelTechnical,
                color = AppTheme.colors.textSecondary,
            )
            NumberInput(
                value = text,
                onValueChange = {
                    text = it
                    onEvent(GoalEvent.EditTarget(it))
                },
                label = label,
                modifier = Modifier.fillMaxWidth().testTag("weight_target_field"),
                maxDecimals = 1,
                maxIntegerDigits = TARGET_INTEGER_DIGITS,
                showLabel = false,
            )
            if (sheet.problem != null) {
                Text(
                    text = stringResource(Res.string.goal_target_range),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.error,
                    modifier = Modifier.testTag("weight_target_error"),
                )
            }
        }
    }
}

/**
 * Read-only detail of a metric (info archetype): value, status, explanation and, at the end, the medical disclaimer.
 * The header X closes it; it has no effect on any data.
 */
@Composable
internal fun MetricDetailSheet(metric: MetricInterpretation, onEvent: (GoalEvent) -> Unit) {
    AppInfoSheet(
        title = stringResource(metricLabelRes(metric.metric)),
        onDismiss = { onEvent(GoalEvent.CloseMetric) },
        testTagPrefix = "weight_metric_sheet",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            MetricInterpretationRow(
                label = stringResource(metricLabelRes(metric.metric)),
                valueText = metricValueText(metric),
                statusText = stringResource(statusTextRes(metric.status)),
                tone = metric.status.tone.toStatusTone(),
                explanation = stringResource(explanationRes(metric.status)),
                testTagPrefix = "weight_metric",
            )
            Text(
                text = stringResource(Res.string.metric_disclaimer),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("weight_metric_disclaimer"),
            )
        }
    }
}
