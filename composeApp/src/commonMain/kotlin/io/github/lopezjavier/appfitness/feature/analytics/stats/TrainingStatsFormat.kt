package io.github.lopezjavier.appfitness.feature.analytics.stats

import androidx.compose.runtime.Composable
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.stats_sessions_one
import io.github.lopezjavier.appfitness.composeapp.resources.stats_sessions_other
import io.github.lopezjavier.appfitness.composeapp.resources.stats_sets_one
import io.github.lopezjavier.appfitness.composeapp.resources.stats_sets_other
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val TENTHS = 10

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
