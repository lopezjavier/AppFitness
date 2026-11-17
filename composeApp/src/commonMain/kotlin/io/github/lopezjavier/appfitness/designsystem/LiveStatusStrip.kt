@file:Suppress("MatchingDeclarationName") // The file holds the components and their Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Geometry of [LiveStatusStrip]. */
object LiveStatusStripDefaults {
    val DotSize: Dp = 8.dp
    const val PULSE_MILLIS = 900
    const val PULSE_MIN = 0.35f
}

/**
 * The status line of a running session: a lit pill with a dot ("EN VIVO", or "EN PAUSA" frozen) and the sets figure
 * ("12 de 20 series") on the end. The dot pulses only when decorative motion is on. The pill text and the figure are
 * given by the caller. The optional [elapsedText] is the session stopwatch; [elapsedSemantics] gives it a spoken
 * description for assistive technology (null keeps the timer decorative).
 */
@Composable
fun LiveStatusStrip(
    statusText: String,
    progressText: String,
    paused: Boolean,
    modifier: Modifier = Modifier,
    elapsedText: String? = null,
    elapsedSemantics: (SemanticsPropertyReceiver.() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val accent = if (paused) colors.energyAmber else colors.neonLime
    val pulse = if (LocalAnimationsEnabled.current && !paused) {
        rememberInfiniteTransition(label = "live_pulse").animateFloat(
            initialValue = 1f,
            targetValue = LiveStatusStripDefaults.PULSE_MIN,
            animationSpec = infiniteRepeatable(
                tween(LiveStatusStripDefaults.PULSE_MILLIS, easing = LinearEasing),
                RepeatMode.Reverse,
            ),
            label = "live_pulse_alpha",
        ).value
    } else {
        1f
    }
    Row(
        modifier.fillMaxWidth().padding(horizontal = Spacing.screenMargin, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (elapsedText == null) Arrangement.SpaceBetween else Arrangement.Start,
    ) {
        Row(
            Modifier
                .clip(AppShapes.full)
                .background(AppPaint.tintSurface(accent))
                .border(AppBorder.thin, AppPaint.accentBorder(accent), AppShapes.full)
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(
                Modifier.size(LiveStatusStripDefaults.DotSize)
                    .drawBehind {
                        drawRadialGlow(accent, Offset(size.width / 2, size.height / 2), size.width * 2f, 0.5f * pulse)
                        drawCircle(accent.copy(alpha = pulse))
                    }
                    .clearAndSetSemantics {},
            )
            Spacer(Modifier.size(Spacing.sm))
            Text(statusText, style = AppTheme.typography.labelTechnical, color = accent)
        }
        if (elapsedText != null) {
            Text(
                text = elapsedText,
                style = AppTheme.typography.headlineMetric,
                color = colors.textPrimary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm).then(timerSemantics(elapsedSemantics)),
            )
        }
        Text(
            text = progressText,
            style = AppTheme.typography.labelNumeric,
            color = colors.textSecondary,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** The stopwatch is decorative unless the caller gives it a spoken description. */
private fun timerSemantics(properties: (SemanticsPropertyReceiver.() -> Unit)?): Modifier =
    if (properties == null) Modifier.clearAndSetSemantics {} else Modifier.semantics(properties = properties)
