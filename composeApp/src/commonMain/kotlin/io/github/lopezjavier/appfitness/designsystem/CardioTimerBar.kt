@file:Suppress("MatchingDeclarationName") // The defaults object sits beside the component, like the others.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.cardio_timer_cd_open
import io.github.lopezjavier.appfitness.composeapp.resources.cardio_timer_cd_over
import io.github.lopezjavier.appfitness.composeapp.resources.cardio_timer_cd_paused
import io.github.lopezjavier.appfitness.composeapp.resources.cardio_timer_cd_target
import io.github.lopezjavier.appfitness.composeapp.resources.cardio_timer_cd_target_seconds
import io.github.lopezjavier.appfitness.composeapp.resources.cardio_timer_pause
import io.github.lopezjavier.appfitness.composeapp.resources.cardio_timer_pause_cd
import io.github.lopezjavier.appfitness.composeapp.resources.cardio_timer_resume
import io.github.lopezjavier.appfitness.composeapp.resources.cardio_timer_resume_cd
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object CardioTimerBarDefaults {
    private const val SECONDS_PER_MINUTE = 60
    private const val MINUTE_PAD = 2

    /** Cardio tone (R-58): lime on the bar, amber once the target is passed; the track and pills are the rest's. */
    fun cardioColors(appColors: AppColors, overTarget: Boolean): RestTimerBarColors {
        val tone = if (overTarget) appColors.energyAmber else appColors.neonLime
        return RestTimerBarDefaults.colors(appColors).copy(countdown = tone, progress = tone)
    }

    /** Count-up "mm:ss" (seconds truncated); past the [target] it is "+mm:ss" of the excess. */
    fun elapsedText(elapsed: Duration, target: Duration?): String {
        val over = isOverTarget(elapsed, target)
        val shown = if (over && target != null) elapsed - target else elapsed
        val total = maxOf(shown.inWholeSeconds, 0L)
        val text = "${(total / SECONDS_PER_MINUTE).toString().padStart(MINUTE_PAD, '0')}:" +
            (total % SECONDS_PER_MINUTE).toString().padStart(MINUTE_PAD, '0')
        return if (over) "+$text" else text
    }

    /** True once the elapsed time has passed the target (reaching it exactly is not over). */
    fun isOverTarget(elapsed: Duration, target: Duration?): Boolean =
        target != null && target > Duration.ZERO && elapsed > target

    /** elapsed / target coerced to 0..1 (full when over); without a target the line stays empty. */
    fun cardioProgress(elapsed: Duration, target: Duration?): Float =
        if (target == null || target <= Duration.ZERO) 0f else (elapsed / target).toFloat().coerceIn(0f, 1f)

    /** Whole minutes and seconds (truncated) of a count-up time: 725 s is (12, 5). */
    fun elapsedParts(elapsed: Duration): Pair<Int, Int> {
        val total = maxOf(elapsed.inWholeSeconds, 0L)
        return (total / SECONDS_PER_MINUTE).toInt() to (total % SECONDS_PER_MINUTE).toInt()
    }
}

/**
 * The cardio variant of the shared timer bar (R-58): the elapsed time counts up in lime (amber and "+mm:ss" once the
 * [target] is passed), the [label] is the exercise name and the only action is the pause/resume pill; finishing is the
 * LISTO tick of the set. Tags (with [testTagPrefix]): `_elapsed`, `_label`, `_toggle` and `_paused`.
 */
@Composable
fun CardioTimerBar(
    label: String,
    elapsed: Duration,
    target: Duration?,
    paused: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String? = null,
) {
    val over = CardioTimerBarDefaults.isOverTarget(elapsed, target)
    val colors = CardioTimerBarDefaults.cardioColors(AppTheme.colors, over)
    val spoken = spokenCardio(label, elapsed, target, paused)
    TimerBarSurface(
        progress = CardioTimerBarDefaults.cardioProgress(elapsed, target),
        colors = colors,
        modifier = modifier,
    ) {
        Text(
            text = CardioTimerBarDefaults.elapsedText(elapsed, target),
            style = RestTimerBarDefaults.countdownStyle(AppTheme.typography),
            color = colors.countdown,
            maxLines = 1,
            softWrap = false,
            modifier = timerTag(testTagPrefix, "elapsed").semantics { contentDescription = spoken },
        )
        if (paused) PausedText(timerTag(testTagPrefix, "paused"))
        TimerBarLabel(label, testTagPrefix, Modifier.weight(1f))
        RestTimerAction(
            text = stringResource(if (paused) Res.string.cardio_timer_resume else Res.string.cardio_timer_pause),
            contentDescription = stringResource(
                if (paused) Res.string.cardio_timer_resume_cd else Res.string.cardio_timer_pause_cd,
            ),
            containerColor = colors.actionContainer,
            contentColor = colors.addContent,
            onClick = onToggle,
            modifier = timerTag(testTagPrefix, "toggle"),
        )
    }
}

@Composable
private fun spokenCardio(label: String, elapsed: Duration, target: Duration?, paused: Boolean): String {
    val over = CardioTimerBarDefaults.isOverTarget(elapsed, target)
    val shown = if (over && target != null) elapsed - target else elapsed
    val (minutes, seconds) = CardioTimerBarDefaults.elapsedParts(shown)
    val text = when {
        over -> stringResource(Res.string.cardio_timer_cd_over, label, minutes.toString(), seconds.toString())

        target == null -> stringResource(Res.string.cardio_timer_cd_open, label, minutes.toString(), seconds.toString())

        minutes == 0 -> stringResource(
            Res.string.cardio_timer_cd_target_seconds,
            label,
            seconds.toString(),
            target.inWholeMinutes.toString(),
        )

        else -> stringResource(
            Res.string.cardio_timer_cd_target,
            label,
            minutes.toString(),
            seconds.toString(),
            target.inWholeMinutes.toString(),
        )
    }
    return if (paused) stringResource(Res.string.cardio_timer_cd_paused, text) else text
}

@Preview
@Composable
private fun CardioTimerBarPreview() {
    AppTheme {
        CardioTimerBar(
            label = "Bicicleta Estática",
            elapsed = 12.minutes + 5.seconds,
            target = 40.minutes,
            paused = false,
            onToggle = {},
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}
