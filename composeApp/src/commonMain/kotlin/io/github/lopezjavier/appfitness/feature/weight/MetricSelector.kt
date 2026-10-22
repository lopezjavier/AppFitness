package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_bmr
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_bmr_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_body_fat
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_body_fat_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_bone
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_bone_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_hip
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_hip_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_lean_mass
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_lean_mass_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_waist
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_waist_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_water
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_water_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_weight
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_weight_cd
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.FilterChipRow
import io.github.lopezjavier.appfitness.domain.model.BodyMetric
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private class MetricFilter(
    val metric: BodyMetric,
    val tag: String,
    val label: StringResource,
    val description: StringResource,
)

private val MetricFilters = listOf(
    MetricFilter(
        BodyMetric.Weight,
        "weight_chart_metric_weight",
        Res.string.weight_chart_metric_weight,
        Res.string.weight_chart_metric_weight_cd,
    ),
    MetricFilter(
        BodyMetric.BodyFat,
        "weight_chart_metric_body_fat",
        Res.string.weight_chart_metric_body_fat,
        Res.string.weight_chart_metric_body_fat_cd,
    ),
    MetricFilter(
        BodyMetric.LeanMass,
        "weight_chart_metric_lean_mass",
        Res.string.weight_chart_metric_lean_mass,
        Res.string.weight_chart_metric_lean_mass_cd,
    ),
    MetricFilter(
        BodyMetric.Water,
        "weight_chart_metric_water",
        Res.string.weight_chart_metric_water,
        Res.string.weight_chart_metric_water_cd,
    ),
    MetricFilter(
        BodyMetric.Bone,
        "weight_chart_metric_bone",
        Res.string.weight_chart_metric_bone,
        Res.string.weight_chart_metric_bone_cd,
    ),
    MetricFilter(
        BodyMetric.Bmr,
        "weight_chart_metric_bmr",
        Res.string.weight_chart_metric_bmr,
        Res.string.weight_chart_metric_bmr_cd,
    ),
    MetricFilter(
        BodyMetric.Waist,
        "weight_chart_metric_waist",
        Res.string.weight_chart_metric_waist,
        Res.string.weight_chart_metric_waist_cd,
    ),
    MetricFilter(
        BodyMetric.Hip,
        "weight_chart_metric_hip",
        Res.string.weight_chart_metric_hip,
        Res.string.weight_chart_metric_hip_cd,
    ),
)

/** The full name of [metric] ("Grasa corporal"): readout, empty hint and summary use it. */
@Composable
internal fun metricName(metric: BodyMetric): String =
    stringResource(MetricFilters.first { it.metric == metric }.description)

/** The metric chips: an immediate view filter (no Guardar), scrolled so the selected one is always visible. */
@Composable
internal fun MetricChips(selected: BodyMetric, onSelect: (BodyMetric) -> Unit) {
    val options = MetricFilters.map {
        AppOption(stringResource(it.label), it.tag, stringResource(it.description))
    }
    FilterChipRow(
        options = options,
        selectedIndex = MetricFilters.indexOfFirst { it.metric == selected },
        onSelect = { onSelect(MetricFilters[it].metric) },
        modifier = Modifier.fillMaxWidth().testTag("weight_chart_metric_selector"),
        scrollToSelected = true,
    )
}
