package io.github.lopezjavier.appfitness.feature.analytics.stats

import androidx.compose.runtime.Composable
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.stats_duration_hm
import io.github.lopezjavier.appfitness.composeapp.resources.stats_duration_m
import io.github.lopezjavier.appfitness.composeapp.resources.stats_sessions_one
import io.github.lopezjavier.appfitness.composeapp.resources.stats_sessions_other
import io.github.lopezjavier.appfitness.composeapp.resources.stats_sets_one
import io.github.lopezjavier.appfitness.composeapp.resources.stats_sets_other
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val TENTHS = 10
private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60

/** Sets with a decimal comma only when needed: 3.0 gives "3" and 1.5 gives "1,5" (secondary muscles count 0.5). */
internal fun formatSetsNumber(sets: Double): String {
    val tenths = (sets * TENTHS).roundToInt()
    val whole = AppNumberFormat.integer(tenths / TENTHS)
    return if (tenths % TENTHS == 0) whole else "$whole,${tenths % TENTHS}"
}

/** "1 serie", "3 series", "1,5 series", "0 series". */
@Composable
internal fun setsText(sets: Double): String {
    val one = (sets * TENTHS).roundToInt() == TENTHS
    return stringResource(if (one) Res.string.stats_sets_one else Res.string.stats_sets_other, formatSetsNumber(sets))
}

/** "1 sesión", "3 sesiones". */
@Composable
internal fun sessionsText(sessions: Int): String =
    stringResource(if (sessions == 1) Res.string.stats_sessions_one else Res.string.stats_sessions_other, sessions)

/** "1 h 5 min" from one hour on, "45 min" below. */
@Composable
internal fun durationText(seconds: Long): String {
    val minutes = (seconds / SECONDS_PER_MINUTE).toInt()
    return if (minutes >= MINUTES_PER_HOUR) {
        stringResource(Res.string.stats_duration_hm, minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
    } else {
        stringResource(Res.string.stats_duration_m, minutes)
    }
}
