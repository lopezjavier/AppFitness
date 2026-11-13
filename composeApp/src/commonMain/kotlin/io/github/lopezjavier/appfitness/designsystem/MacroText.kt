package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_carbs
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_fat
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_protein
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val SEPARATOR = " ·"

/**
 * One macro figure such as "P 24 g" (C-62): the letter in `textPrimary` W700, the figure and unit in the macro colour
 * W600. [format] is a string with the grams as its only argument and the letter before the first space. With [muted]
 * the whole figure is `textTertiary` (a meal with no entries, C-65). With [trailingSeparator] a " ·" in `textTertiary`
 * is attached to the figure, so a wrapped line never starts with a dot.
 */
@Composable
fun MacroText(
    format: StringResource,
    grams: String,
    macro: Macro,
    style: TextStyle,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    trailingSeparator: Boolean = false,
) {
    val colors = AppTheme.colors
    val formatted = stringResource(format, grams)
    val letter = formatted.substringBefore(' ')
    val rest = formatted.removePrefix(letter)
    val figureColor = if (muted) colors.textTertiary else MacroDefaults.color(macro, colors)
    val letterColor = if (muted) colors.textTertiary else colors.textPrimary
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = letterColor, fontWeight = FontWeight.W700)) { append(letter) }
            withStyle(SpanStyle(color = figureColor, fontWeight = FontWeight.W600)) { append(rest) }
            if (trailingSeparator) withStyle(SpanStyle(color = colors.textTertiary)) { append(SEPARATOR) }
        },
        style = style,
        modifier = modifier,
    )
}

/**
 * The three macros of a meal or group as one line, "P 48 g · C 71 g · G 7 g" (R-78, V-18, V-19). Whole items wrap
 * ("P 48 g ·" never splits), so the line never overlaps a neighbour. [muted] greys the whole line (C-65).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MacroLine(
    proteinG: String,
    carbsG: String,
    fatG: String,
    modifier: Modifier = Modifier,
    style: TextStyle = AppTheme.typography.bodySmall,
    muted: Boolean = false,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        MacroText(
            Res.string.diary_meal_protein,
            proteinG,
            Macro.Protein,
            style,
            muted = muted,
            trailingSeparator = true,
        )
        MacroText(Res.string.diary_meal_carbs, carbsG, Macro.Carbs, style, muted = muted, trailingSeparator = true)
        MacroText(Res.string.diary_meal_fat, fatG, Macro.Fat, style, muted = muted)
    }
}
