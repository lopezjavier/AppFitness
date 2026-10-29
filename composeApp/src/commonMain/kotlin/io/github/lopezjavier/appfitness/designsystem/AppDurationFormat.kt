package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.Composable
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.stats_duration_h
import io.github.lopezjavier.appfitness.composeapp.resources.stats_duration_hm
import io.github.lopezjavier.appfitness.composeapp.resources.stats_duration_m
import org.jetbrains.compose.resources.stringResource

private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60

/**
 * "1 h 5 min" from one hour on, "1 h" on the hour (zero parts dropped), "45 min" below (shared by statistics,
 * Actividad and training preferences).
 */
@Composable
internal fun durationText(seconds: Long): String {
    val minutes = (seconds / SECONDS_PER_MINUTE).toInt()
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return when {
        hours == 0 -> stringResource(Res.string.stats_duration_m, rest)
        rest == 0 -> stringResource(Res.string.stats_duration_h, hours)
        else -> stringResource(Res.string.stats_duration_hm, hours, rest)
    }
}
