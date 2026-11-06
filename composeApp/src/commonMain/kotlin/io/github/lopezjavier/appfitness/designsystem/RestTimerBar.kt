package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.rest_timer_add
import io.github.lopezjavier.appfitness.composeapp.resources.rest_timer_add_cd
import io.github.lopezjavier.appfitness.composeapp.resources.rest_timer_cd
import io.github.lopezjavier.appfitness.composeapp.resources.rest_timer_cd_seconds
import io.github.lopezjavier.appfitness.composeapp.resources.rest_timer_paused
import io.github.lopezjavier.appfitness.composeapp.resources.rest_timer_paused_cd
import io.github.lopezjavier.appfitness.composeapp.resources.rest_timer_paused_cd_seconds
import io.github.lopezjavier.appfitness.composeapp.resources.rest_timer_skip
import io.github.lopezjavier.appfitness.composeapp.resources.rest_timer_skip_cd
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Stateless rest-timer bar: cyan countdown, "+30s" and "Omitir" actions and a thin progress line at the
 * bottom. The caller owns the time and removes the bar on skip or at zero. The caller's [modifier] lands
 * on the bar; with [testTagPrefix] the children are tagged `_countdown`, `_label`, `_add` and `_skip`. The optional
 * [label] (the "Descanso" tag) takes the spare width and ends in an ellipsis; the time never truncates.
 * The cardio run uses the same slot through [CardioTimerBar].
 */
@Composable
fun RestTimerBar(
    remaining: Duration,
    total: Duration,
    onAddTime: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String? = null,
    paused: Boolean = false,
    pausedTestTag: String? = null,
    label: String? = null,
) {
    val colors = RestTimerBarDefaults.colors(AppTheme.colors)
    val spoken = spokenCountdown(remaining, paused)
    TimerBarSurface(
        progress = RestTimerBarDefaults.progress(remaining, total),
        colors = colors,
        modifier = modifier,
    ) {
        Text(
            text = RestTimerBarDefaults.countdownText(remaining),
            style = RestTimerBarDefaults.countdownStyle(AppTheme.typography),
            color = colors.countdown,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.then(timerTag(testTagPrefix, "countdown")).semantics { contentDescription = spoken },
        )
        if (paused) PausedText(pausedTestTag?.let { Modifier.testTag(it) } ?: Modifier)
        TimerBarLabel(label, testTagPrefix, Modifier.weight(1f))
        RestTimerAction(
            text = stringResource(Res.string.rest_timer_add),
            contentDescription = stringResource(Res.string.rest_timer_add_cd),
            containerColor = colors.actionContainer,
            contentColor = colors.addContent,
            onClick = onAddTime,
            modifier = timerTag(testTagPrefix, "add"),
        )
        RestTimerAction(
            text = stringResource(Res.string.rest_timer_skip),
            contentDescription = stringResource(Res.string.rest_timer_skip_cd),
            containerColor = colors.actionContainer,
            contentColor = colors.skipContent,
            onClick = onSkip,
            modifier = timerTag(testTagPrefix, "skip"),
        )
    }
}

internal fun timerTag(prefix: String?, part: String): Modifier =
    prefix?.let { Modifier.testTag("${it}_$part") } ?: Modifier

/** The bar shell shared by the rest and the cardio variants: rounded overlay, content row and progress line. */
@Composable
internal fun TimerBarSurface(
    progress: Float,
    colors: RestTimerBarColors,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
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
            content = content,
        )
        ProgressLine(progress = progress, colors = colors, modifier = Modifier.align(Alignment.BottomStart))
    }
}

@Composable
internal fun PausedText(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.rest_timer_paused),
        style = AppTheme.typography.bodyMedium,
        color = AppTheme.colors.textSecondary,
        maxLines = 1,
        modifier = modifier,
    )
}

/** The tag of the bar: spare width, one line, ellipsis; without text it is a spacer that keeps the actions right. */
@Composable
internal fun TimerBarLabel(label: String?, testTagPrefix: String?, modifier: Modifier = Modifier) {
    if (label == null) {
        Spacer(modifier)
        return
    }
    Text(
        text = label,
        style = AppTheme.typography.bodySmall,
        color = AppTheme.colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.then(timerTag(testTagPrefix, "label")),
    )
}

@Composable
private fun spokenCountdown(remaining: Duration, paused: Boolean): String {
    val (minutes, seconds) = RestTimerBarDefaults.spokenParts(remaining)
    val secondsOnly = RestTimerBarDefaults.speaksSecondsOnly(remaining)
    return when {
        secondsOnly && paused -> stringResource(Res.string.rest_timer_paused_cd_seconds, seconds.toString())
        secondsOnly -> stringResource(Res.string.rest_timer_cd_seconds, seconds.toString())
        paused -> stringResource(Res.string.rest_timer_paused_cd, minutes.toString(), seconds.toString())
        else -> stringResource(Res.string.rest_timer_cd, minutes.toString(), seconds.toString())
    }
}

/** Decorative progress line: the countdown already carries the information, so it has no semantics. */
@Composable
internal fun ProgressLine(progress: Float, colors: RestTimerBarColors, modifier: Modifier = Modifier) {
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
internal fun RestTimerAction(
    text: String,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
        modifier
            .sizeIn(
                minWidth = RestTimerBarDefaults.ActionMinTouch,
                minHeight = RestTimerBarDefaults.ActionMinTouch,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
            Modifier
                .heightIn(min = RestTimerBarDefaults.ActionVisualHeight)
                .clip(RestTimerBarDefaults.ActionShape)
                .background(containerColor)
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
