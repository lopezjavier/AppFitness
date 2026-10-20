package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.macro_bar_state
import org.jetbrains.compose.resources.stringResource
import kotlin.math.floor

/** Display strings of a macro bar; unit symbols are not translatable copy. */
@Immutable
data class MacroBarText(val consumed: String, val target: String, val percent: String) {
    /** "142 g / 180 g · 79 %". */
    val full: String get() = "$consumed / $target · $percent"
}

/** Geometry and pure logic of [MacroProgressBar] (04 §2). */
object MacroProgressBarDefaults {
    val BarHeight: Dp = 8.dp
    val DotSize: Dp = Spacing.sm
    val Shape = AppShapes.full
    private const val PERCENT = 100L
    private const val TENTHS = 10L
    private const val HALF = 0.5

    /** `round(consumed / target x 100)`, half up, in integer math. A negative consumed counts as 0. */
    fun percent(consumedGrams: Int, targetGrams: Int): Int {
        require(targetGrams > 0) { "target must be positive but was $targetGrams" }
        val consumed = consumedGrams.coerceAtLeast(0).toLong()
        val target = targetGrams.toLong()
        return ((consumed * PERCENT * 2 + target) / (target * 2)).toInt()
    }

    /**
     * Half-up percent of a gram value with decimals (the data keeps 0.1 g). It is rounded to tenths first, so the
     * integer math stays exact: 100.9 / 200 -> 50.
     */
    fun percent(consumedGrams: Double, targetGrams: Int): Int {
        require(targetGrams > 0) { "target must be positive but was $targetGrams" }
        val tenths = floor(consumedGrams.coerceAtLeast(0.0) * TENTHS + HALF).toLong()
        val target = targetGrams.toLong()
        return ((tenths * PERCENT * 2 + TENTHS * target) / (TENTHS * target * 2)).toInt()
    }

    fun fillFraction(consumedGrams: Double, targetGrams: Int): Float {
        require(targetGrams > 0) { "target must be positive but was $targetGrams" }
        return (consumedGrams / targetGrams).toFloat().coerceIn(0f, 1f)
    }

    /** Whole grams (half up) for the consumed value; the percentage comes from the tenths. */
    fun text(consumedGrams: Double, targetGrams: Int): MacroBarText = MacroBarText(
        consumed = "${AppNumberFormat.integer(wholeGrams(consumedGrams))} g",
        target = "${AppNumberFormat.integer(targetGrams)} g",
        percent = "${AppNumberFormat.integer(percent(consumedGrams, targetGrams))} %",
    )

    internal fun wholeGrams(consumedGrams: Double): Int = floor(consumedGrams.coerceAtLeast(0.0) + HALF).toInt()

    /** Fill fraction capped to 0f..1f; the text still shows the real percentage. */
    fun fillFraction(consumedGrams: Int, targetGrams: Int): Float {
        require(targetGrams > 0) { "target must be positive but was $targetGrams" }
        return (consumedGrams.toFloat() / targetGrams).coerceIn(0f, 1f)
    }

    /** A negative consumed value is shown as 0 (like the percent and the fill). */
    fun text(consumedGrams: Int, targetGrams: Int): MacroBarText = MacroBarText(
        consumed = "${AppNumberFormat.integer(consumedGrams.coerceAtLeast(0))} g",
        target = "${AppNumberFormat.integer(targetGrams)} g",
        percent = "${AppNumberFormat.integer(percent(consumedGrams, targetGrams))} %",
    )

    fun trackColor(appColors: AppColors): Color = appColors.surfaceOverlay

    fun labelStyle(typography: AppTypography): TextStyle = typography.labelTechnical

    fun valueStyle(typography: AppTypography): TextStyle = typography.bodySmall
}

/**
 * Horizontal macro progress: dot and uppercase [label] on the left, "consumed / target . percent" on
 * the right, then an 8 dp bar in the macro colour. The bar is capped at 100%, the text is not. It is
 * one accessibility node carrying the caller's [modifier].
 */
@Composable
fun MacroProgressBar(
    label: String,
    macro: Macro,
    consumedGrams: Int,
    targetGrams: Int,
    modifier: Modifier = Modifier,
) = MacroProgressBar(label, macro, consumedGrams.toDouble(), targetGrams, modifier)

/** [MacroProgressBar] for a consumed value with decimals; it is shown as whole grams, half up. */
@Composable
fun MacroProgressBar(
    label: String,
    macro: Macro,
    consumedGrams: Double,
    targetGrams: Int,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val macroColor = MacroDefaults.color(macro, colors)
    val text = MacroProgressBarDefaults.text(consumedGrams, targetGrams)
    val fraction = MacroProgressBarDefaults.fillFraction(consumedGrams, targetGrams)
    val stateText =
        stringResource(
            Res.string.macro_bar_state,
            AppNumberFormat.integer(MacroProgressBarDefaults.wholeGrams(consumedGrams)),
            AppNumberFormat.integer(targetGrams),
            AppNumberFormat.integer(MacroProgressBarDefaults.percent(consumedGrams, targetGrams)),
        )
    Column(
        modifier =
        modifier.semantics(mergeDescendants = true) {
            contentDescription = label
            stateDescription = stateText
            progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
        },
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clearAndSetSemantics {}) {
            Box(Modifier.size(MacroProgressBarDefaults.DotSize).clip(AppShapes.full).background(macroColor))
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = label.uppercase(),
                style = MacroProgressBarDefaults.labelStyle(AppTheme.typography).copy(fontWeight = FontWeight.W700),
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text =
                buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.textPrimary)) { append(text.consumed) }
                    withStyle(SpanStyle(color = colors.textSecondary)) { append(" / ${text.target} ") }
                    withStyle(SpanStyle(color = macroColor, fontWeight = FontWeight.W700)) { append(text.percent) }
                },
                style = MacroProgressBarDefaults.valueStyle(AppTheme.typography),
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(MacroProgressBarDefaults.BarHeight)
                .clip(MacroProgressBarDefaults.Shape)
                .background(MacroProgressBarDefaults.trackColor(colors)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(MacroProgressBarDefaults.Shape)
                    .background(macroColor),
            )
        }
    }
}

@Preview
@Composable
private fun MacroProgressBarPreview() {
    AppTheme {
        Column(
            Modifier.background(AppTheme.colors.surfaceRaised).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            MacroProgressBar("Proteínas", Macro.Protein, 142, 180)
            MacroProgressBar("Carbohidratos", Macro.Carbs, 210, 270)
            MacroProgressBar("Grasas", Macro.Fat, 70, 67)
        }
    }
}
