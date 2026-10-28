package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.rpe_picker_option_cd
import io.github.lopezjavier.appfitness.domain.usecase.RpeScale
import org.jetbrains.compose.resources.stringResource

/**
 * One option of the RPE scale: a [ScaleOptionRow] in the ramp colour (05 §2.5) showing the number. A tap reports
 * through [onClick].
 */
@Composable
internal fun RpeOptionRow(value: Double, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val text = RpeScale.text(value)
    ScaleOptionRow(
        label = text,
        color = AppTheme.colors.rpeScale(value),
        selected = selected,
        onClick = onClick,
        testTag = RpePickerDefaults.optionTag(value),
        contentDescription = stringResource(Res.string.rpe_picker_option_cd, text),
        modifier = modifier,
        textStyle = AppTheme.typography.titleMedium.copy(fontFeatureSettings = NUMERIC_FONT_FEATURES),
    )
}
