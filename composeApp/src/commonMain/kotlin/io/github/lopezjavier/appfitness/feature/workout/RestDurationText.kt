package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.Composable
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.builder_rest_state
import io.github.lopezjavier.appfitness.composeapp.resources.rest_a11y_join
import io.github.lopezjavier.appfitness.composeapp.resources.rest_a11y_minutes
import io.github.lopezjavier.appfitness.composeapp.resources.rest_a11y_seconds
import io.github.lopezjavier.appfitness.composeapp.resources.rest_format_minutes
import io.github.lopezjavier.appfitness.composeapp.resources.rest_format_minutes_seconds
import io.github.lopezjavier.appfitness.composeapp.resources.rest_format_seconds
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** A rest as written: "45 s", "1 min", "1 min 10 s" (never "2 min 0 s" or "60 s"). */
@Composable
internal fun restDurationText(totalSeconds: Int): String {
    val (minutes, seconds) = formatRestDuration(totalSeconds)
    return when {
        minutes == 0 -> stringResource(Res.string.rest_format_seconds, seconds)
        seconds == 0 -> stringResource(Res.string.rest_format_minutes, minutes)
        else -> stringResource(Res.string.rest_format_minutes_seconds, minutes, seconds)
    }
}

/** The spoken state of a rest stepper: "Descanso: 1 minuto 10 segundos". */
@Composable
internal fun restStateDescription(totalSeconds: Int): String {
    val (minutes, seconds) = formatRestDuration(totalSeconds)
    val minutesText = pluralStringResource(Res.plurals.rest_a11y_minutes, minutes, minutes)
    val secondsText = pluralStringResource(Res.plurals.rest_a11y_seconds, seconds, seconds)
    val spoken =
        when {
            minutes == 0 -> secondsText
            seconds == 0 -> minutesText
            else -> stringResource(Res.string.rest_a11y_join, minutesText, secondsText)
        }
    return stringResource(Res.string.builder_rest_state, spoken)
}
