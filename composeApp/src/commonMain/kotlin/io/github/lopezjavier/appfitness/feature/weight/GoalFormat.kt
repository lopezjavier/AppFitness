package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.runtime.Composable
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.goal_distance_above
import io.github.lopezjavier.appfitness.composeapp.resources.goal_distance_below
import io.github.lopezjavier.appfitness.composeapp.resources.goal_distance_reached
import io.github.lopezjavier.appfitness.composeapp.resources.goal_distance_remaining
import io.github.lopezjavier.appfitness.composeapp.resources.metric_bmi_label
import io.github.lopezjavier.appfitness.composeapp.resources.metric_body_fat_label
import io.github.lopezjavier.appfitness.composeapp.resources.metric_explain_fat_high
import io.github.lopezjavier.appfitness.composeapp.resources.metric_explain_fat_low
import io.github.lopezjavier.appfitness.composeapp.resources.metric_explain_fat_normal
import io.github.lopezjavier.appfitness.composeapp.resources.metric_explain_fat_very_high
import io.github.lopezjavier.appfitness.composeapp.resources.metric_explain_normal
import io.github.lopezjavier.appfitness.composeapp.resources.metric_explain_obese
import io.github.lopezjavier.appfitness.composeapp.resources.metric_explain_overweight
import io.github.lopezjavier.appfitness.composeapp.resources.metric_explain_underweight
import io.github.lopezjavier.appfitness.composeapp.resources.metric_explain_whr_high
import io.github.lopezjavier.appfitness.composeapp.resources.metric_explain_whr_low
import io.github.lopezjavier.appfitness.composeapp.resources.metric_status_fat_high
import io.github.lopezjavier.appfitness.composeapp.resources.metric_status_fat_low
import io.github.lopezjavier.appfitness.composeapp.resources.metric_status_fat_normal
import io.github.lopezjavier.appfitness.composeapp.resources.metric_status_fat_very_high
import io.github.lopezjavier.appfitness.composeapp.resources.metric_status_normal
import io.github.lopezjavier.appfitness.composeapp.resources.metric_status_obese
import io.github.lopezjavier.appfitness.composeapp.resources.metric_status_overweight
import io.github.lopezjavier.appfitness.composeapp.resources.metric_status_underweight
import io.github.lopezjavier.appfitness.composeapp.resources.metric_status_whr_high
import io.github.lopezjavier.appfitness.composeapp.resources.metric_status_whr_low
import io.github.lopezjavier.appfitness.composeapp.resources.metric_value_percent
import io.github.lopezjavier.appfitness.composeapp.resources.metric_whr_label
import io.github.lopezjavier.appfitness.designsystem.StatusTone
import io.github.lopezjavier.appfitness.domain.usecase.MetricInterpretation
import io.github.lopezjavier.appfitness.domain.usecase.MetricKind
import io.github.lopezjavier.appfitness.domain.usecase.MetricStatus
import io.github.lopezjavier.appfitness.domain.usecase.MetricTone
import io.github.lopezjavier.appfitness.domain.usecase.TargetDistanceResult
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TENTHS = 10
private const val MINUS = '−'

/** "faltan 4,2 kg", "objetivo alcanzado", "1,0 kg por encima del objetivo"... null without a distance. */
@Composable
internal fun distanceText(distance: TargetDistanceResult?): String? = when (distance) {
    null -> null
    is TargetDistanceResult.Remaining -> stringResource(Res.string.goal_distance_remaining, decimalText(distance.kg))
    TargetDistanceResult.Reached -> stringResource(Res.string.goal_distance_reached)
    is TargetDistanceResult.Above -> stringResource(Res.string.goal_distance_above, decimalText(distance.kg))
    is TargetDistanceResult.Below -> stringResource(Res.string.goal_distance_below, decimalText(distance.kg))
}

internal fun metricLabelRes(kind: MetricKind): StringResource = when (kind) {
    MetricKind.Bmi -> Res.string.metric_bmi_label
    MetricKind.BodyFat -> Res.string.metric_body_fat_label
    MetricKind.WaistToHip -> Res.string.metric_whr_label
}

/** The value as shown: BMI "23,4", body fat "19,5 %", waist-to-hip "0,86". */
@Composable
internal fun metricValueText(metric: MetricInterpretation): String = when (metric.metric) {
    MetricKind.Bmi -> decimalText(metric.value)
    MetricKind.BodyFat -> stringResource(Res.string.metric_value_percent, decimalText(metric.value))
    MetricKind.WaistToHip -> ratioText(metric.value)
}

internal fun statusTextRes(status: MetricStatus): StringResource = when (status) {
    MetricStatus.Underweight -> Res.string.metric_status_underweight
    MetricStatus.Normal -> Res.string.metric_status_normal
    MetricStatus.Overweight -> Res.string.metric_status_overweight
    MetricStatus.Obese -> Res.string.metric_status_obese
    MetricStatus.BodyFatLow -> Res.string.metric_status_fat_low
    MetricStatus.BodyFatNormal -> Res.string.metric_status_fat_normal
    MetricStatus.BodyFatHigh -> Res.string.metric_status_fat_high
    MetricStatus.BodyFatVeryHigh -> Res.string.metric_status_fat_very_high
    MetricStatus.WhrLowRisk -> Res.string.metric_status_whr_low
    MetricStatus.WhrHighRisk -> Res.string.metric_status_whr_high
}

internal fun explanationRes(status: MetricStatus): StringResource = when (status) {
    MetricStatus.Underweight -> Res.string.metric_explain_underweight
    MetricStatus.Normal -> Res.string.metric_explain_normal
    MetricStatus.Overweight -> Res.string.metric_explain_overweight
    MetricStatus.Obese -> Res.string.metric_explain_obese
    MetricStatus.BodyFatLow -> Res.string.metric_explain_fat_low
    MetricStatus.BodyFatNormal -> Res.string.metric_explain_fat_normal
    MetricStatus.BodyFatHigh -> Res.string.metric_explain_fat_high
    MetricStatus.BodyFatVeryHigh -> Res.string.metric_explain_fat_very_high
    MetricStatus.WhrLowRisk -> Res.string.metric_explain_whr_low
    MetricStatus.WhrHighRisk -> Res.string.metric_explain_whr_high
}

internal fun MetricTone.toStatusTone(): StatusTone = when (this) {
    MetricTone.Good -> StatusTone.Good
    MetricTone.Caution -> StatusTone.Caution
    MetricTone.Alert -> StatusTone.Alert
}

/** A delta with its sign and a decimal comma: "−1,4", "+0,6", "0,0" (true minus sign U+2212). */
internal fun signedTenths(value: Double): String {
    val tenths = (abs(value) * TENTHS).roundToInt()
    val sign = when {
        tenths == 0 -> ""
        value < 0 -> MINUS.toString()
        else -> "+"
    }
    return "$sign${tenths / TENTHS},${tenths % TENTHS}"
}

/** True when the delta rounds to 0,0. */
internal fun isZeroTenths(value: Double): Boolean = (abs(value) * TENTHS).roundToInt() == 0

/**
 * Share of the way from [startKg] to [targetKg] already covered by [currentKg], clamped to 0..1 (0 when moving away
 * from the target, 1 at or past it). Null without the three values or when start equals target.
 */
@Suppress("ComplexCondition") // Four plain null/equality guards.
internal fun goalProgress(startKg: Double?, currentKg: Double?, targetKg: Double?): Double? =
    if (startKg == null || currentKg == null || targetKg == null || startKg == targetKg) {
        null
    } else {
        ((startKg - currentKg) / (startKg - targetKg)).coerceIn(0.0, 1.0)
    }
