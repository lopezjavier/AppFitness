package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.runtime.Composable
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_bmr_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_body_fat_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_bone_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_hip_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_lean_mass_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_waist_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_water_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_weight_cd
import io.github.lopezjavier.appfitness.designsystem.ChartMetricOption
import io.github.lopezjavier.appfitness.domain.model.BodyMetric
import org.jetbrains.compose.resources.StringResource
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

/** The picker rows of the eight body metrics, in order; tags `weight_chart_metric_<name>`. */
@Composable
internal fun bodyMetricOptions(): List<ChartMetricOption<BodyMetric>> =
    BodyMetric.entries.map { ChartMetricOption(it, metricName(it), it.tagName()) }

/** The glossary name of [metric] ("Grasa corporal"): the chart title, readout, empty hint and summary use it. */
@Composable
internal fun metricName(metric: BodyMetric): String = stringResource(metric.nameRes())
