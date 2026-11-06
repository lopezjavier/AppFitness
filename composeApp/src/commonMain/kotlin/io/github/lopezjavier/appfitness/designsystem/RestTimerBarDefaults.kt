package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Remaining and total rest time; [total] only grows when the rest is extended. */
@Immutable
data class RestCountdown(val remaining: Duration, val total: Duration)

/** Colours of [RestTimerBar]. */
@Immutable
data class RestTimerBarColors(
    val container: Color,
    val countdown: Color,
    val progress: Color,
    val progressTrack: Color,
    val actionContainer: Color,
    val addContent: Color,
    val skipContent: Color,
)

object RestTimerBarDefaults {
    val MinHeight: Dp = 48.dp
    val ProgressHeight: Dp = Spacing.xs
    val Shape = AppShapes.md
    val HorizontalPadding: Dp = Spacing.md
    val ActionMinTouch: Dp = 48.dp
    val ActionVisualHeight: Dp = 40.dp
    val ActionShape = AppShapes.base
    val ActionHorizontalPadding: Dp = Spacing.md
    val Extension: Duration = 30.seconds
    private const val SECONDS_PER_MINUTE = 60
    private const val NANOS_PER_SECOND = 1_000_000_000L
    private const val MINUTE_PAD = 2

    /** Whole seconds, rounded up, so "00:01" stays until the time really reaches 0. */
    private fun ceilSeconds(remaining: Duration): Long {
        val nanos = remaining.inWholeNanoseconds
        return if (nanos <= 0L) 0L else (nanos + NANOS_PER_SECOND - 1) / NANOS_PER_SECOND
    }

    /** "mm:ss" with the seconds rounded up; 100 minutes or more are not padded; zero or less is "00:00". */
    fun countdownText(remaining: Duration): String {
        val (minutes, seconds) = spokenParts(remaining)
        return "${minutes.toString().padStart(MINUTE_PAD, '0')}:${seconds.toString().padStart(MINUTE_PAD, '0')}"
    }

    /** remaining / total, coerced to 0..1; a total of zero or less gives 0. */
    fun progress(remaining: Duration, total: Duration): Float =
        if (total <= Duration.ZERO) 0f else (remaining / total).toFloat().coerceIn(0f, 1f)

    /** "+30s": the remaining time (at least 0) plus [Extension]; the total grows if it is exceeded. */
    fun extend(countdown: RestCountdown): RestCountdown {
        val remaining = maxOf(countdown.remaining, Duration.ZERO) + Extension
        return RestCountdown(remaining, maxOf(countdown.total, remaining))
    }

    /** Whole minutes and seconds (rounded up) of the remaining time: 72 s is (1, 12). */
    fun spokenParts(remaining: Duration): Pair<Int, Int> {
        val total = ceilSeconds(remaining)
        return (total / SECONDS_PER_MINUTE).toInt() to (total % SECONDS_PER_MINUTE).toInt()
    }

    /** Under a minute the spoken text omits the minutes ("30 s", not "0 min 30 s"). */
    fun speaksSecondsOnly(remaining: Duration): Boolean = spokenParts(remaining).first == 0

    fun colors(appColors: AppColors): RestTimerBarColors = RestTimerBarColors(
        container = appColors.surfaceOverlay,
        countdown = appColors.athleticCyan,
        progress = appColors.athleticCyan,
        progressTrack = appColors.surfaceBase,
        actionContainer = appColors.surfaceRaised,
        addContent = appColors.textPrimary,
        skipContent = appColors.textSecondary,
    )

    fun countdownStyle(typography: AppTypography): TextStyle = typography.headlineMetric

    fun actionStyle(typography: AppTypography): TextStyle = typography.titleMedium
}
