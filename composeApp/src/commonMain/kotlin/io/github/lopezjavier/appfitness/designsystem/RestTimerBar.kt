package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.rest_timer_add
import io.github.lopezjavier.appfitness.composeapp.resources.rest_timer_add_cd
import io.github.lopezjavier.appfitness.composeapp.resources.rest_timer_cd
import io.github.lopezjavier.appfitness.composeapp.resources.rest_timer_cd_seconds
import io.github.lopezjavier.appfitness.composeapp.resources.rest_timer_skip
import io.github.lopezjavier.appfitness.composeapp.resources.rest_timer_skip_cd
import org.jetbrains.compose.resources.stringResource
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

/**
 * Stateless rest-timer bar: cyan countdown, "+30s" and "Omitir" actions and a thin progress line at the
 * bottom. The caller owns the time and removes the bar on skip or at zero. The caller's [modifier] lands
 * on the bar; with [testTagPrefix] the children are tagged `_countdown`, `_add` and `_skip`.
 */
@Composable
fun RestTimerBar(
    remaining: Duration,
    total: Duration,
    onAddTime: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String? = null,
) {
    val colors = RestTimerBarDefaults.colors(AppTheme.colors)
    val spoken = spokenCountdown(remaining)
    fun tag(part: String): Modifier = testTagPrefix?.let { Modifier.testTag("${it}_$part") } ?: Modifier
    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .heightIn(min = RestTimerBarDefaults.MinHeight)
            .clip(RestTimerBarDefaults.Shape)
            .background(colors.container),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = RestTimerBarDefaults.HorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = RestTimerBarDefaults.countdownText(remaining),
                style = RestTimerBarDefaults.countdownStyle(AppTheme.typography),
                color = colors.countdown,
                maxLines = 1,
                modifier = Modifier.weight(1f).then(tag("countdown")).semantics { contentDescription = spoken },
            )
            RestTimerAction(
                text = stringResource(Res.string.rest_timer_add),
                contentDescription = stringResource(Res.string.rest_timer_add_cd),
                contentColor = colors.addContent,
                onClick = onAddTime,
                modifier = tag("add"),
            )
            RestTimerAction(
                text = stringResource(Res.string.rest_timer_skip),
                contentDescription = stringResource(Res.string.rest_timer_skip_cd),
                contentColor = colors.skipContent,
                onClick = onSkip,
                modifier = tag("skip"),
            )
        }
        ProgressLine(
            progress = RestTimerBarDefaults.progress(remaining, total),
            colors = colors,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

@Composable
private fun spokenCountdown(remaining: Duration): String {
    val (minutes, seconds) = RestTimerBarDefaults.spokenParts(remaining)
    return if (RestTimerBarDefaults.speaksSecondsOnly(remaining)) {
        stringResource(Res.string.rest_timer_cd_seconds, seconds.toString())
    } else {
        stringResource(Res.string.rest_timer_cd, minutes.toString(), seconds.toString())
    }
}

/** Decorative progress line: the countdown already carries the information, so it has no semantics. */
@Composable
private fun ProgressLine(progress: Float, colors: RestTimerBarColors, modifier: Modifier = Modifier) {
    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .height(RestTimerBarDefaults.ProgressHeight)
            .background(colors.progressTrack)
            .clearAndSetSemantics {},
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(colors.progress))
    }
}

/** Compact action pill: 40 dp visual inside a 48 dp touch target (stock buttons are too tall for the bar). */
@Composable
private fun RestTimerAction(
    text: String,
    contentDescription: String,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RestTimerBarDefaults.colors(AppTheme.colors)
    Box(
        modifier =
        modifier
            .minimumInteractiveComponentSize()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
            Modifier
                .heightIn(min = RestTimerBarDefaults.ActionVisualHeight)
                .clip(RestTimerBarDefaults.ActionShape)
                .background(colors.actionContainer)
                .padding(horizontal = RestTimerBarDefaults.ActionHorizontalPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = RestTimerBarDefaults.actionStyle(AppTheme.typography),
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

@Preview
@Composable
private fun RestTimerBarPreview() {
    AppTheme {
        RestTimerBar(
            remaining = 72.seconds,
            total = 120.seconds,
            onAddTime = {},
            onSkip = {},
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}
