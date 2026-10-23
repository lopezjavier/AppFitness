package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import io.github.lopezjavier.appfitness.composeapp.resources.rpe_picker_option_cd
import io.github.lopezjavier.appfitness.domain.usecase.RpeScale
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val ROW_TINT_ALPHA = 0.16f

/**
 * One option of the RPE scale: a leading marker bar in the ramp colour (05 §2.5), the number in `textPrimary`
 * (colour is never the only cue) and a check when [selected]. A tap reports through [onClick].
 */
@Composable
internal fun RpeOptionRow(value: Double, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val ramp = colors.rpeScale(value)
    val text = RpeScale.text(value)
    val description = stringResource(Res.string.rpe_picker_option_cd, text)
    val shape = AppShapes.md
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(ramp.copy(alpha = ROW_TINT_ALPHA).compositeOver(colors.surfaceOverlay))
            .then(if (selected) Modifier.border(AppBorder.focus, ramp, shape) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description }
            .testTag(RpePickerDefaults.optionTag(value)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(Spacing.sm).fillMaxHeight().background(ramp))
        Text(
            text = text,
            style = AppTheme.typography.titleMedium.copy(
                fontFeatureSettings = NUMERIC_FONT_FEATURES,
                fontWeight = if (selected) FontWeight.SemiBold else null,
            ),
            color = colors.textPrimary,
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.lg),
        )
        if (selected) {
            // Decorative: the selected state is conveyed by the selectable semantics.
            Icon(
                painter = painterResource(Res.drawable.ic_check),
                contentDescription = null,
                tint = ramp,
                modifier = Modifier.padding(end = Spacing.lg).size(Spacing.xl),
            )
        }
    }
}
