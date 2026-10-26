package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_bmr_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_body_fat_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_bone_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_hip_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_lean_mass_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_picker_title
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_waist_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_water_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_weight_cd
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppPickerSheet
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.BodyMetric
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private fun BodyMetric.nameRes(): StringResource = when (this) {
    BodyMetric.Weight -> Res.string.weight_chart_metric_weight_cd
    BodyMetric.BodyFat -> Res.string.weight_chart_metric_body_fat_cd
    BodyMetric.LeanMass -> Res.string.weight_chart_metric_lean_mass_cd
    BodyMetric.Water -> Res.string.weight_chart_metric_water_cd
    BodyMetric.Bone -> Res.string.weight_chart_metric_bone_cd
    BodyMetric.Bmr -> Res.string.weight_chart_metric_bmr_cd
    BodyMetric.Waist -> Res.string.weight_chart_metric_waist_cd
    BodyMetric.Hip -> Res.string.weight_chart_metric_hip_cd
}

private fun BodyMetric.tagName(): String = when (this) {
    BodyMetric.Weight -> "weight"
    BodyMetric.BodyFat -> "body_fat"
    BodyMetric.LeanMass -> "lean_mass"
    BodyMetric.Water -> "water"
    BodyMetric.Bone -> "bone"
    BodyMetric.Bmr -> "bmr"
    BodyMetric.Waist -> "waist"
    BodyMetric.Hip -> "hip"
}

/** The glossary name of [metric] ("Grasa corporal"): the chart title, readout, empty hint and summary use it. */
@Composable
internal fun metricName(metric: BodyMetric): String = stringResource(metric.nameRes())

/**
 * The picker of the chart metric (picker archetype): the eight metrics, the current one checked. A tap applies at
 * once through [onSelect]; the caller closes the sheet. Tags: `weight_chart_metric_picker_sheet` (+ `_close`) and
 * `weight_chart_metric_<name>` per row.
 */
@Composable
internal fun MetricPickerSheet(selected: BodyMetric, onSelect: (BodyMetric) -> Unit, onDismiss: () -> Unit) {
    AppPickerSheet(
        title = stringResource(Res.string.weight_chart_metric_picker_title),
        onDismiss = onDismiss,
        testTagPrefix = "weight_chart_metric_picker_sheet",
    ) {
        BodyMetric.entries.forEach { metric ->
            AppListRow(
                headline = metricName(metric),
                modifier = Modifier
                    .testTag("weight_chart_metric_${metric.tagName()}")
                    .semantics { this.selected = metric == selected },
                onClick = { onSelect(metric) },
                trailing = if (metric == selected) {
                    {
                        Icon(
                            painter = painterResource(Res.drawable.ic_check),
                            contentDescription = null,
                            tint = AppTheme.colors.athleticCyan,
                            modifier = Modifier.size(Spacing.xl),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}
