@file:OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.

package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.goal_target_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.goal_target_field_label
import io.github.lopezjavier.appfitness.composeapp.resources.goal_target_offline
import io.github.lopezjavier.appfitness.composeapp.resources.goal_target_range
import io.github.lopezjavier.appfitness.composeapp.resources.goal_target_save_error
import io.github.lopezjavier.appfitness.composeapp.resources.goal_target_save_error_offline
import io.github.lopezjavier.appfitness.composeapp.resources.goal_target_sheet_title
import io.github.lopezjavier.appfitness.composeapp.resources.metric_close
import io.github.lopezjavier.appfitness.composeapp.resources.metric_disclaimer
import io.github.lopezjavier.appfitness.composeapp.resources.weight_save
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.GhostTone
import io.github.lopezjavier.appfitness.designsystem.MetricInterpretationRow
import io.github.lopezjavier.appfitness.designsystem.NumberInput
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.usecase.MetricInterpretation
import org.jetbrains.compose.resources.stringResource

private const val TARGET_INTEGER_DIGITS = 3

/** The shared frame of the goal sheets: capped width, centred, scrolling body above the pinned [actions]. */
@Composable
private fun GoalSheetFrame(
    onDismiss: () -> Unit,
    tag: String,
    actions: @Composable ColumnScope.() -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    AppBottomSheet(onDismissRequest = onDismiss, testTagPrefix = tag) {
        Column(
            Modifier
                .widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = Spacing.screenMargin)
                .padding(bottom = Spacing.lg)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
                content = body,
            )
            actions()
        }
    }
}

/**
 * The target-weight sheet: one field prefilled with the saved target. "Guardar" needs a valid value different from the
 * saved one and connectivity; X, back, swipe, scrim and "Cancelar" discard the draft.
 */
@Composable
internal fun TargetWeightSheet(sheet: TargetSheetState, isOnline: Boolean, onEvent: (GoalEvent) -> Unit) {
    var text by remember { mutableStateOf(sheet.text) }
    val label = stringResource(Res.string.goal_target_field_label)
    GoalSheetFrame(
        onDismiss = { onEvent(GoalEvent.Dismiss) },
        tag = "weight_target_sheet",
        actions = { TargetActions(sheet, isOnline, onEvent) },
    ) {
        Text(
            text = stringResource(Res.string.goal_target_sheet_title),
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() }.testTag("weight_target_title"),
        )
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

@Composable
private fun TargetActions(sheet: TargetSheetState, isOnline: Boolean, onEvent: (GoalEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        val message = when {
            !isOnline -> stringResource(Res.string.goal_target_offline)
            sheet.saveError == WeightSaveError.Offline -> stringResource(Res.string.goal_target_save_error_offline)
            sheet.saveError == WeightSaveError.Failed -> stringResource(Res.string.goal_target_save_error)
            else -> null
        }
        message?.let {
            Text(
                text = it,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.error,
                modifier = Modifier.testTag("weight_target_message"),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            GhostButton(
                text = stringResource(Res.string.goal_target_cancel),
                onClick = { onEvent(GoalEvent.Dismiss) },
                modifier = Modifier.weight(1f).testTag("weight_target_cancel_button"),
                tone = GhostTone.Neutral,
            )
            PrimaryButton(
                text = stringResource(Res.string.weight_save),
                onClick = { onEvent(GoalEvent.SaveTarget) },
                modifier = Modifier.weight(1f).testTag("weight_target_save_button"),
                enabled = sheet.canSave,
                loading = sheet.isSaving,
            )
        }
    }
}

/**
 * Read-only detail of a metric: value, status, explanation and, at the end, the medical disclaimer. Closing it has
 * no effect on any data.
 */
@Composable
internal fun MetricDetailSheet(metric: MetricInterpretation, onEvent: (GoalEvent) -> Unit) {
    GoalSheetFrame(
        onDismiss = { onEvent(GoalEvent.CloseMetric) },
        tag = "weight_metric_sheet",
        actions = {
            GhostButton(
                text = stringResource(Res.string.metric_close),
                onClick = { onEvent(GoalEvent.CloseMetric) },
                modifier = Modifier.fillMaxWidth().testTag("weight_metric_close_button"),
                tone = GhostTone.Neutral,
            )
        },
    ) {
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
