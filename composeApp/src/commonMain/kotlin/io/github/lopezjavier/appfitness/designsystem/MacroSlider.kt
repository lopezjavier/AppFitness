package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.macro_slider_state
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Colours of one [MacroSlider] state. */
@Immutable
data class MacroSliderColors(val thumb: Color, val activeTrack: Color, val inactiveTrack: Color, val value: Color)

/** Geometry, colours and pure logic of [MacroSlider] (04 §1.5). */
object MacroSliderDefaults {
    /** Touch target of the slider node. */
    val MinTouchHeight: Dp = 48.dp
    val TrackHeight: Dp = 8.dp
    val ThumbSize: Dp = 24.dp

    /** Vertical gap between stacked sliders, so adjacent touch zones never overlap. */
    val SliderGap: Dp = Spacing.lg
    val ValueRange: IntRange = 5..90

    /** Integer steps of 1%: the Material `steps` value is the number of stops between the ends. */
    fun steps(range: IntRange): Int = (range.last - range.first - 1).coerceAtLeast(0)

    /** Raw slider float to integer percent, rounded and coerced into [range]. */
    fun snap(raw: Float, range: IntRange): Int = raw.roundToInt().coerceIn(range.first, range.last)

    fun percentText(percent: Int): String = "${AppNumberFormat.integer(percent)} %"

    /** "180 g . 720 kcal" (grouped: "1.080 kcal"). */
    fun detailText(grams: Int, kcal: Int): String =
        "${AppNumberFormat.integer(grams)} g · ${AppNumberFormat.integer(kcal)} kcal"

    /** Disabled controls are exempt from WCAG contrast (ghostData). */
    fun colors(macro: Macro, enabled: Boolean, appColors: AppColors): MacroSliderColors {
        val accent = if (enabled) MacroDefaults.color(macro, appColors) else appColors.ghostData
        return MacroSliderColors(
            thumb = accent,
            activeTrack = accent,
            inactiveTrack = appColors.surfaceOverlay,
            value = accent,
        )
    }

    fun labelStyle(typography: AppTypography): TextStyle = typography.titleMedium

    fun valueStyle(typography: AppTypography): TextStyle = typography.labelNumeric

    fun detailStyle(typography: AppTypography): TextStyle = typography.bodySmall
}

/**
 * Independent macro-percentage slider in 1% steps (no auto-balance: the caller owns the state and
 * the grams/kcal, 04 §1.5). The caller's [modifier] lands on the Slider node, which is at least 48 dp
 * tall; its state description speaks percent, grams and kilocalories.
 */
@Composable
// The caller tag and semantics belong on the Slider node (E2E swipes, a11y), not on the root Column.
@Suppress("ModifierNotUsedAtRoot", "LongParameterList")
@OptIn(ExperimentalMaterial3Api::class) // Slider thumb/track slots are experimental in material3 1.4.
fun MacroSlider(
    label: String,
    macro: Macro,
    percent: Int,
    grams: Int,
    kcal: Int,
    onPercentChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: IntRange = MacroSliderDefaults.ValueRange,
) {
    require(percent in valueRange) { "percent $percent is outside $valueRange" }
    val colors = MacroSliderDefaults.colors(macro, enabled, AppTheme.colors)
    val spoken =
        stringResource(
            Res.string.macro_slider_state,
            AppNumberFormat.integer(percent),
            AppNumberFormat.integer(grams),
            AppNumberFormat.integer(kcal),
        )
    Column {
        MacroSliderHeader(label, percent, grams, kcal, colors.value)
        Slider(
            value = percent.toFloat(),
            onValueChange = { raw ->
                val snapped = MacroSliderDefaults.snap(raw, valueRange)
                if (snapped != percent) onPercentChange(snapped)
            },
            modifier =
            modifier
                .semantics {
                    contentDescription = label
                    stateDescription = spoken
                }
                .fillMaxWidth()
                .heightIn(min = MacroSliderDefaults.MinTouchHeight),
            enabled = enabled,
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = MacroSliderDefaults.steps(valueRange),
            thumb = { SliderThumb(colors.thumb) },
            track = { sliderState ->
                val span = (valueRange.last - valueRange.first).toFloat()
                SliderTrack(((sliderState.value - valueRange.first) / span).coerceIn(0f, 1f), colors)
            },
        )
    }
}

@Composable
private fun MacroSliderHeader(label: String, percent: Int, grams: Int, kcal: Int, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MacroSliderDefaults.labelStyle(AppTheme.typography),
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = MacroSliderDefaults.percentText(percent),
            style = MacroSliderDefaults.valueStyle(AppTheme.typography),
            color = valueColor,
        )
        Text(
            text = MacroSliderDefaults.detailText(grams, kcal),
            style = MacroSliderDefaults.detailStyle(AppTheme.typography),
            color = AppTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun SliderThumb(color: Color) {
    Box(Modifier.size(MacroSliderDefaults.ThumbSize).clip(CircleShape).background(color))
}

@Composable
private fun SliderTrack(fraction: Float, colors: MacroSliderColors) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(MacroSliderDefaults.TrackHeight)
            .clip(AppShapes.full)
            .background(colors.inactiveTrack),
    ) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(colors.activeTrack))
    }
}

@Preview
@Composable
private fun MacroSliderPreview() {
    AppTheme {
        Column(
            Modifier.background(AppTheme.colors.surfaceRaised).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MacroSliderDefaults.SliderGap),
        ) {
            MacroSlider("Proteínas", Macro.Protein, 30, 180, 720, {})
            MacroSlider("Carbohidratos", Macro.Carbs, 45, 270, 1080, {})
            MacroSlider("Grasas", Macro.Fat, 25, 67, 600, {}, enabled = false)
        }
    }
}
