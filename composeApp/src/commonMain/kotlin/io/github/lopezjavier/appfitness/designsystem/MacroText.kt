package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One macro figure such as "P 24 g" (C-30): the letter in the macro colour, the number and unit in `textSecondary`,
 * so the colour tells the macro without the figure itself being hard to read. [format] is a string with the grams as
 * its only argument and the letter before the first space.
 */
@Composable
fun MacroText(format: StringResource, grams: String, macro: Macro, style: TextStyle, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val formatted = stringResource(format, grams)
    val letter = formatted.substringBefore(' ')
    val rest = formatted.removePrefix(letter)
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = MacroDefaults.color(macro, colors))) { append(letter) }
            withStyle(SpanStyle(color = colors.textSecondary)) { append(rest) }
        },
        style = style,
        modifier = modifier,
    )
}
