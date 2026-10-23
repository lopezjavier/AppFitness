package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.Composable
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.date_other
import io.github.lopezjavier.appfitness.composeapp.resources.date_today
import io.github.lopezjavier.appfitness.composeapp.resources.date_yesterday
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
import io.github.lopezjavier.appfitness.composeapp.resources.session_volume_none
import io.github.lopezjavier.appfitness.composeapp.resources.summary_volume_value
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_short_1
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_short_2
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_short_3
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_short_4
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_short_5
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_short_6
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_short_7
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private val WeekdayNames: Map<DayOfWeek, StringResource> = mapOf(
    DayOfWeek.MONDAY to Res.string.weekday_short_1,
    DayOfWeek.TUESDAY to Res.string.weekday_short_2,
    DayOfWeek.WEDNESDAY to Res.string.weekday_short_3,
    DayOfWeek.THURSDAY to Res.string.weekday_short_4,
    DayOfWeek.FRIDAY to Res.string.weekday_short_5,
    DayOfWeek.SATURDAY to Res.string.weekday_short_6,
    DayOfWeek.SUNDAY to Res.string.weekday_short_7,
)

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

    is DateLabel.Other -> stringResource(
        Res.string.date_other,
        stringResource(WeekdayNames.getValue(dayOfWeek)),
        day,
        stringResource(MonthNames.getValue(month)),
    )
}

/** A volume as "2.120 kg" (whole kilos, es-ES grouping); "—" when there is none (duration and distance sets). */
@Composable
internal fun volumeText(volumeKg: Double): String = if (volumeKg <= 0.0) {
    stringResource(Res.string.session_volume_none)
} else {
    stringResource(Res.string.summary_volume_value, AppNumberFormat.integer(volumeKg.roundToInt()))
}
