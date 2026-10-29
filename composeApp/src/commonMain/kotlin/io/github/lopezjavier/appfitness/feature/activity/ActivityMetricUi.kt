package io.github.lopezjavier.appfitness.feature.activity

import androidx.compose.runtime.Composable
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.activity_active_kcal
import io.github.lopezjavier.appfitness.composeapp.resources.activity_bpm_value
import io.github.lopezjavier.appfitness.composeapp.resources.activity_kcal_value
import io.github.lopezjavier.appfitness.composeapp.resources.activity_resting_hr
import io.github.lopezjavier.appfitness.composeapp.resources.activity_sleep
import io.github.lopezjavier.appfitness.composeapp.resources.activity_steps
import io.github.lopezjavier.appfitness.composeapp.resources.activity_total_kcal
import io.github.lopezjavier.appfitness.composeapp.resources.ic_flame
import io.github.lopezjavier.appfitness.composeapp.resources.ic_heart
import io.github.lopezjavier.appfitness.composeapp.resources.ic_sleep
import io.github.lopezjavier.appfitness.composeapp.resources.ic_steps
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.ChartMetricOption
import io.github.lopezjavier.appfitness.designsystem.durationText
import io.github.lopezjavier.appfitness.domain.usecase.ActivityMetric
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val SECONDS_PER_MINUTE = 60L

internal fun ActivityMetric.nameRes(): StringResource = when (this) {
    ActivityMetric.Steps -> Res.string.activity_steps
    ActivityMetric.Sleep -> Res.string.activity_sleep
    ActivityMetric.ActiveKcal -> Res.string.activity_active_kcal
    ActivityMetric.TotalKcal -> Res.string.activity_total_kcal
    ActivityMetric.RestingHeartRate -> Res.string.activity_resting_hr
}

internal fun ActivityMetric.icon(): DrawableResource = when (this) {
    ActivityMetric.Steps -> Res.drawable.ic_steps
    ActivityMetric.Sleep -> Res.drawable.ic_sleep
    ActivityMetric.ActiveKcal, ActivityMetric.TotalKcal -> Res.drawable.ic_flame
    ActivityMetric.RestingHeartRate -> Res.drawable.ic_heart
}

/** The snake_case name in tags: `activity_<tag>_card` and `activity_chart_metric_<tag>`. */
internal fun ActivityMetric.tagName(): String = when (this) {
    ActivityMetric.Steps -> "steps"
    ActivityMetric.Sleep -> "sleep"
    ActivityMetric.ActiveKcal -> "active_kcal"
    ActivityMetric.TotalKcal -> "total_kcal"
    ActivityMetric.RestingHeartRate -> "resting_hr"
}

/** The picker rows of the five metrics, in summary order. */
@Composable
internal fun activityMetricOptions(): List<ChartMetricOption<ActivityMetric>> =
    ActivityMetric.entries.map { ChartMetricOption(it, stringResource(it.nameRes()), it.tagName()) }

/** A kcal amount rounded for display ("2.300 kcal"). */
@Composable
internal fun kcalText(kcal: Double): String =
    stringResource(Res.string.activity_kcal_value, AppNumberFormat.integer(kcal.roundToInt()))

/** Sleep minutes as "7 h 15 min". */
@Composable
internal fun sleepText(minutes: Int): String = durationText(minutes * SECONDS_PER_MINUTE)

@Composable
internal fun bpmText(bpm: Int): String = stringResource(Res.string.activity_bpm_value, bpm)
