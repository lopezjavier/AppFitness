package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_tone_caution
import io.github.lopezjavier.appfitness.composeapp.resources.ic_tone_favorable
import io.github.lopezjavier.appfitness.composeapp.resources.ic_tone_unfavorable
import io.github.lopezjavier.appfitness.composeapp.resources.ic_trending_down
import io.github.lopezjavier.appfitness.composeapp.resources.ic_trending_flat
import io.github.lopezjavier.appfitness.composeapp.resources.ic_trending_up
import io.github.lopezjavier.appfitness.composeapp.resources.trend_tone_caution
import io.github.lopezjavier.appfitness.composeapp.resources.trend_tone_favorable
import io.github.lopezjavier.appfitness.composeapp.resources.trend_tone_unfavorable
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

/**
 * Whether a trend is good for the user's goal. The colour is about the goal, not the sign (04 §3.3);
 * the goal-to-tone rule belongs to the E4 `WeeklyRate` use case.
 */
enum class TrendTone { Favorable, Unfavorable, Caution, Neutral }

/** Arrow of the pill; it follows the sign of the rate, independently of the tone. */
enum class TrendDirection { Up, Down, Flat, None }

object TrendPillDefaults {
    val Shape = PillDefaults.Shape
    val IconSize: Dp = 16.dp
    val MinHeight: Dp = PillDefaults.MinHeight
    val ItemSpacing: Dp = PillDefaults.ItemSpacing

    fun contentColor(tone: TrendTone, appColors: AppColors): Color = when (tone) {
        TrendTone.Favorable -> appColors.electricGreen
        TrendTone.Unfavorable -> appColors.error
        TrendTone.Caution -> appColors.energyAmber
        TrendTone.Neutral -> appColors.textSecondary
    }

    fun containerColor(appColors: AppColors): Color = PillDefaults.containerColor(appColors)

    /** Sign of the signed rate: > 0 Up, < 0 Down, 0 Flat, null (not enough data) None. */
    fun direction(rate: Double?): TrendDirection = when {
        rate == null -> TrendDirection.None
        rate > 0.0 -> TrendDirection.Up
        rate < 0.0 -> TrendDirection.Down
        else -> TrendDirection.Flat
    }

    /** Tone glyph with a different shape per tone, so the tone survives greyscale (WCAG 1.4.1). */
    fun toneIcon(tone: TrendTone): DrawableResource? = when (tone) {
        TrendTone.Favorable -> Res.drawable.ic_tone_favorable
        TrendTone.Caution -> Res.drawable.ic_tone_caution
        TrendTone.Unfavorable -> Res.drawable.ic_tone_unfavorable
        TrendTone.Neutral -> null
    }

    fun directionIcon(direction: TrendDirection): DrawableResource? = when (direction) {
        TrendDirection.Up -> Res.drawable.ic_trending_up
        TrendDirection.Down -> Res.drawable.ic_trending_down
        TrendDirection.Flat -> Res.drawable.ic_trending_flat
        TrendDirection.None -> null
    }

    fun textStyle(typography: AppTypography): TextStyle = typography.bodySmall.copy(fontWeight = FontWeight.W600)
}

/**
 * Read-only weekly-trend pill: tone glyph, trend arrow and the caller-formatted [text]. The tone is
 * spoken through a state description. The caller's [modifier] lands on the merged node.
 */
@Composable
fun TrendPill(
    text: String,
    tone: TrendTone,
    modifier: Modifier = Modifier,
    direction: TrendDirection = TrendDirection.None,
) {
    val color = TrendPillDefaults.contentColor(tone, AppTheme.colors)
    val toneDescription =
        when (tone) {
            TrendTone.Favorable -> stringResource(Res.string.trend_tone_favorable)
            TrendTone.Unfavorable -> stringResource(Res.string.trend_tone_unfavorable)
            TrendTone.Caution -> stringResource(Res.string.trend_tone_caution)
            TrendTone.Neutral -> null
        }
    PillContainer(
        modifier = modifier.semantics(mergeDescendants = true) { toneDescription?.let { stateDescription = it } },
    ) {
        TrendPillDefaults.toneIcon(tone)?.let { PillIcon(it, color) }
        TrendPillDefaults.directionIcon(direction)?.let { PillIcon(it, color) }
        Text(text = text, style = TrendPillDefaults.textStyle(AppTheme.typography), color = color)
    }
}

@Composable
private fun PillIcon(icon: DrawableResource, tint: Color) {
    Image(
        imageVector = vectorResource(icon),
        contentDescription = null,
        modifier = Modifier.size(TrendPillDefaults.IconSize),
        colorFilter = ColorFilter.tint(tint),
    )
}

@Preview
@Composable
private fun TrendPillPreview() {
    AppTheme {
        Row(Modifier.padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TrendPill("−0,45 kg", TrendTone.Favorable, direction = TrendDirection.Down)
            TrendPill("+0,30 kg", TrendTone.Unfavorable, direction = TrendDirection.Up)
            TrendPill("Sin datos", TrendTone.Neutral)
        }
    }
}
