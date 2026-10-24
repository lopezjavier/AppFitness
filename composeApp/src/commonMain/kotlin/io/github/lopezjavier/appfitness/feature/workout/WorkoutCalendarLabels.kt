package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.Composable
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.calendar_count_cd
import io.github.lopezjavier.appfitness.composeapp.resources.calendar_workouts_count
import io.github.lopezjavier.appfitness.composeapp.resources.month_1
import io.github.lopezjavier.appfitness.composeapp.resources.month_10
import io.github.lopezjavier.appfitness.composeapp.resources.month_11
import io.github.lopezjavier.appfitness.composeapp.resources.month_12
import io.github.lopezjavier.appfitness.composeapp.resources.month_2
import io.github.lopezjavier.appfitness.composeapp.resources.month_3
import io.github.lopezjavier.appfitness.composeapp.resources.month_4
import io.github.lopezjavier.appfitness.composeapp.resources.month_5
import io.github.lopezjavier.appfitness.composeapp.resources.month_6
import io.github.lopezjavier.appfitness.composeapp.resources.month_7
import io.github.lopezjavier.appfitness.composeapp.resources.month_8
import io.github.lopezjavier.appfitness.composeapp.resources.month_9
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

private const val TIME_DIGITS = 2

/** The 12 month names, January first. */
private val MonthNames: List<StringResource> = listOf(
    Res.string.month_1,
    Res.string.month_2,
    Res.string.month_3,
    Res.string.month_4,
    Res.string.month_5,
    Res.string.month_6,
    Res.string.month_7,
    Res.string.month_8,
    Res.string.month_9,
    Res.string.month_10,
    Res.string.month_11,
    Res.string.month_12,
)

/** Capitalised name of month number [month] (1-12): "Marzo". */
@Composable
internal fun monthName(month: Int): String = stringResource(MonthNames[month - 1]).replaceFirstChar { it.titlecase() }

/** "5 entrenos". */
@Composable
internal fun workoutsText(count: Int): String = pluralStringResource(Res.plurals.calendar_workouts_count, count, count)

/** Spoken form of a tile or row: "Marzo, 5 entrenos". */
@Composable
internal fun countDescription(title: String, count: Int): String =
    stringResource(Res.string.calendar_count_cd, title, workoutsText(count))

/** "HH:mm" of [startedAt] in [zone]. */
internal fun timeText(startedAt: Instant, zone: TimeZone): String {
    val time = startedAt.toLocalDateTime(zone).time
    return "${time.hour.toString().padStart(TIME_DIGITS, '0')}:${time.minute.toString().padStart(TIME_DIGITS, '0')}"
}
