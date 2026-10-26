package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.chart_date_short
import io.github.lopezjavier.appfitness.composeapp.resources.metric_value_percent
import io.github.lopezjavier.appfitness.composeapp.resources.weight_composition_absent
import io.github.lopezjavier.appfitness.composeapp.resources.weight_composition_row_absent_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_composition_row_bmr
import io.github.lopezjavier.appfitness.composeapp.resources.weight_composition_row_bone
import io.github.lopezjavier.appfitness.composeapp.resources.weight_composition_row_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_composition_row_fat
import io.github.lopezjavier.appfitness.composeapp.resources.weight_composition_row_lean
import io.github.lopezjavier.appfitness.composeapp.resources.weight_composition_row_water
import io.github.lopezjavier.appfitness.composeapp.resources.weight_composition_title
import io.github.lopezjavier.appfitness.composeapp.resources.weight_composition_value_kcal
import io.github.lopezjavier.appfitness.composeapp.resources.weight_composition_value_kg
import io.github.lopezjavier.appfitness.designsystem.AppInfoSheet
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.Dated
import io.github.lopezjavier.appfitness.domain.model.LatestComposition
import io.github.lopezjavier.appfitness.feature.workout.shortLabel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/**
 * "Composición corporal": the latest value of each metric with the day it was measured, "—" when never measured.
 * Read-only, so it closes only by dismissing the sheet (US-27).
 */
@Composable
internal fun CompositionSheet(
    composition: LatestComposition?,
    timeZone: TimeZone,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppInfoSheet(
        title = stringResource(Res.string.weight_composition_title),
        onDismiss = onDismiss,
        modifier = modifier,
        testTagPrefix = "weight_composition_sheet",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            val value = composition ?: LatestComposition()
            CompositionRow(
                "weight_composition_fat",
                stringResource(Res.string.weight_composition_row_fat),
                value.bodyFat?.map { stringResource(Res.string.metric_value_percent, decimalText(it)) },
                timeZone,
            )
            CompositionRow(
                "weight_composition_lean",
                stringResource(Res.string.weight_composition_row_lean),
                value.leanMass.asKg(),
                timeZone,
            )
            CompositionRow(
                "weight_composition_water",
                stringResource(Res.string.weight_composition_row_water),
                value.water.asKg(),
                timeZone,
            )
            CompositionRow(
                "weight_composition_bone",
                stringResource(Res.string.weight_composition_row_bone),
                value.bone.asKg(),
                timeZone,
            )
            CompositionRow(
                "weight_composition_bmr",
                stringResource(Res.string.weight_composition_row_bmr),
                value.bmr?.map { stringResource(Res.string.weight_composition_value_kcal, it.toString()) },
                timeZone,
            )
        }
    }
}

private inline fun <T> Dated<T>.map(transform: (T) -> String): Dated<String> = Dated(transform(value), measuredAt)

@Composable
private fun Dated<Double>?.asKg(): Dated<String>? =
    this?.map { stringResource(Res.string.weight_composition_value_kg, decimalText(it)) }

/** One metric: label, tabular value and date; one merged spoken description ("Grasa corporal, 18,5 %, 3 oct"). */
@Composable
private fun CompositionRow(tag: String, label: String, value: Dated<String>?, timeZone: TimeZone) {
    val date = value?.let {
        val local = it.measuredAt.toLocalDateTime(timeZone)
        stringResource(Res.string.chart_date_short, local.day, local.month.shortLabel())
    }
    val spoken = if (value != null && date != null) {
        stringResource(Res.string.weight_composition_row_cd, label, value.value, date)
    } else {
        stringResource(Res.string.weight_composition_row_absent_cd, label)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Spacing.minTouchTarget)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = label,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.weight(1f).clearAndSetSemantics {},
        )
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.clearAndSetSemantics {}) {
            Text(
                text = value?.value ?: stringResource(Res.string.weight_composition_absent),
                style = AppTheme.typography.labelNumeric,
                color = AppTheme.colors.textPrimary,
            )
            date?.let {
                Text(text = it, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
            }
        }
    }
}
