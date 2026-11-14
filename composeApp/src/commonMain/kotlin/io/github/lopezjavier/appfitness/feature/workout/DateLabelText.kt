package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.Composable
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.date_today
import io.github.lopezjavier.appfitness.composeapp.resources.date_yesterday
import io.github.lopezjavier.appfitness.composeapp.resources.duration_hours_minutes
import io.github.lopezjavier.appfitness.composeapp.resources.duration_minutes
import io.github.lopezjavier.appfitness.composeapp.resources.metric_unit_min
import io.github.lopezjavier.appfitness.composeapp.resources.month_short_1
import io.github.lopezjavier.appfitness.composeapp.resources.month_short_10
import io.github.lopezjavier.appfitness.composeapp.resources.month_short_11
import io.github.lopezjavier.appfitness.composeapp.resources.month_short_12
import io.github.lopezjavier.appfitness.composeapp.resources.month_short_2
import io.github.lopezjavier.appfitness.composeapp.resources.month_short_3
import io.github.lopezjavier.appfitness.composeapp.resources.month_short_4
import io.github.lopezjavier.appfitness.composeapp.resources.month_short_5
import io.github.lopezjavier.appfitness.composeapp.resources.month_short_6
import io.github.lopezjavier.appfitness.composeapp.resources.month_short_7
import io.github.lopezjavier.appfitness.composeapp.resources.month_short_8
import io.github.lopezjavier.appfitness.composeapp.resources.month_short_9
import io.github.lopezjavier.appfitness.composeapp.resources.summary_volume_value
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import kotlinx.datetime.Month
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private val MonthNames: Map<Month, StringResource> = mapOf(
    Month.JANUARY to Res.string.month_short_1,
    Month.FEBRUARY to Res.string.month_short_2,
    Month.MARCH to Res.string.month_short_3,
    Month.APRIL to Res.string.month_short_4,
    Month.MAY to Res.string.month_short_5,
    Month.JUNE to Res.string.month_short_6,
    Month.JULY to Res.string.month_short_7,
    Month.AUGUST to Res.string.month_short_8,
    Month.SEPTEMBER to Res.string.month_short_9,
    Month.OCTOBER to Res.string.month_short_10,
    Month.NOVEMBER to Res.string.month_short_11,
    Month.DECEMBER to Res.string.month_short_12,
)

/** Short Spanish month name ("oct"). */
@Composable
internal fun Month.shortLabel(): String = stringResource(MonthNames.getValue(this))

/** "Hoy", "Ayer" or "lun, 24 oct". */
@Composable
internal fun DateLabel.text(): String = when (this) {
    DateLabel.Today -> stringResource(Res.string.date_today)
    DateLabel.Yesterday -> stringResource(Res.string.date_yesterday)
    is DateLabel.Other -> AppDateFormat.weekdayDayMonth(dayOfWeek, day, month)
}

/** A volume as "2.120 kg" (whole kilos, es-ES grouping); null when there is none (duration and distance sets). */
@Composable
internal fun volumeText(volumeKg: Double): String? = if (volumeKg <= 0.0) {
    null
} else {
    stringResource(Res.string.summary_volume_value, AppNumberFormat.integer(volumeKg.roundToInt()))
}

private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60

/**
 * A duration for a `MetricTile`: the minutes as the value with "min" as the unit, or "1 h 5" with "min" past an hour,
 * so the figure stays short in a narrow tile.
 */
@Composable
internal fun durationMetric(durationSeconds: Long): Pair<String, String> {
    val minutes = (durationSeconds.coerceAtLeast(0) / SECONDS_PER_MINUTE).toInt()
    val unit = stringResource(Res.string.metric_unit_min)
    return if (minutes >= MINUTES_PER_HOUR) {
        "${minutes / MINUTES_PER_HOUR} h ${minutes % MINUTES_PER_HOUR}" to unit
    } else {
        minutes.toString() to unit
    }
}

/** A workout duration as "55 min" or "1 h 5 min" (whole minutes, rounded down; a stopwatch is only for the live timer). */
@Composable
internal fun durationText(durationSeconds: Long): String {
    val minutes = (durationSeconds.coerceAtLeast(0) / SECONDS_PER_MINUTE).toInt()
    return if (minutes >= MINUTES_PER_HOUR) {
        stringResource(Res.string.duration_hours_minutes, minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
    } else {
        stringResource(Res.string.duration_minutes, minutes)
    }
}
