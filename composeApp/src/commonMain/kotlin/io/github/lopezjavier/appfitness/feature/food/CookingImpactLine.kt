package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.cooking_impact_and
import io.github.lopezjavier.appfitness.composeapp.resources.cooking_impact_fat
import io.github.lopezjavier.appfitness.composeapp.resources.cooking_impact_kcal
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Macro
import io.github.lopezjavier.appfitness.designsystem.MacroDefaults
import io.github.lopezjavier.appfitness.feature.diary.amountText
import org.jetbrains.compose.resources.stringResource

/**
 * What the cooking oil adds to a food: "+22,5 kcal y +2,5 g de grasa". The kcal part is secondary text and the fat part
 * uses the fat macro colour. A polite live region, so a screen reader announces the change when the method changes.
 */
@Composable
fun CookingImpactLine(impact: CookingImpactPreview, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val kcal = stringResource(Res.string.cooking_impact_kcal, amountText(impact.addedKcal))
    val and = stringResource(Res.string.cooking_impact_and)
    val fat = stringResource(Res.string.cooking_impact_fat, amountText(impact.addedFatG))
    val fatColor = MacroDefaults.color(Macro.Fat, colors)
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = colors.textSecondary)) { append(kcal + and) }
            withStyle(SpanStyle(color = fatColor)) { append(fat) }
        },
        style = AppTheme.typography.labelNumeric,
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    )
}
