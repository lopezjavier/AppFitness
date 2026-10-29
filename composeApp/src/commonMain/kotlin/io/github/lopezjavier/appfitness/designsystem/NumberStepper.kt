@file:Suppress("MatchingDeclarationName")
// MatchingDeclarationName: the file holds NumberStepperDefaults and the NumberStepper composable.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_add
import io.github.lopezjavier.appfitness.composeapp.resources.ic_remove
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.vectorResource

object NumberStepperDefaults {
    val TouchTarget: Dp = 48.dp
    val VisualSize: Dp = 40.dp
    val IconSize: Dp = 24.dp
    val ValueMinWidth: Dp = 96.dp
    val ButtonShape = AppShapes.full

    fun iconColor(enabled: Boolean, appColors: AppColors): Color =
        if (enabled) appColors.textPrimary else appColors.ghostData

    fun buttonColor(appColors: AppColors): Color = appColors.surfaceOverlay
}

/**
 * Decrement / value / increment control. The value text is supplied formatted by the caller, so the unit and
 * sign stay a screen concern. Both buttons are 48 dp targets; a disabled end is dimmed and not clickable.
 * [stateDescription] is what a screen reader speaks for the whole control (for example "Ajuste: −400 kcal").
 * Tags: `<testTagPrefix>_decrement`, `_increment`, `_value`.
 */
@Suppress("LongParameterList") // Two buttons, each with a handler, enabled flag and description.
@Composable
fun NumberStepper(
    valueText: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    decrementEnabled: Boolean,
    incrementEnabled: Boolean,
    decrementContentDescription: String,
    incrementContentDescription: String,
    stateDescription: String,
    modifier: Modifier = Modifier,
    testTagPrefix: String? = null,
    valueColor: Color = AppTheme.colors.textPrimary,
) {
    Row(
        modifier = modifier.semantics { this.stateDescription = stateDescription },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(
            icon = Res.drawable.ic_remove,
            enabled = decrementEnabled,
            description = decrementContentDescription,
            onClick = onDecrement,
            tag = childTestTag(testTagPrefix, "decrement"),
        )
        Text(
            text = valueText,
            style = AppTheme.typography.labelNumeric,
            color = valueColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = NumberStepperDefaults.ValueMinWidth)
                .then(childTestTag(testTagPrefix, "value")?.let { Modifier.testTag(it) } ?: Modifier),
        )
        StepperButton(
            icon = Res.drawable.ic_add,
            enabled = incrementEnabled,
            description = incrementContentDescription,
            onClick = onIncrement,
            tag = childTestTag(testTagPrefix, "increment"),
        )
    }
}

@Composable
private fun StepperButton(
    icon: DrawableResource,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
    tag: String?,
) {
    Box(
        modifier = Modifier
            .size(NumberStepperDefaults.TouchTarget)
            .then(tag?.let { Modifier.testTag(it) } ?: Modifier)
            .clip(NumberStepperDefaults.ButtonShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(NumberStepperDefaults.VisualSize)
                .clip(NumberStepperDefaults.ButtonShape)
                .background(NumberStepperDefaults.buttonColor(AppTheme.colors)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                imageVector = vectorResource(icon),
                contentDescription = null,
                modifier = Modifier.size(NumberStepperDefaults.IconSize),
                colorFilter = ColorFilter.tint(NumberStepperDefaults.iconColor(enabled, AppTheme.colors)),
            )
        }
    }
}

@Preview
@Composable
private fun NumberStepperPreview() {
    AppTheme {
        Row(Modifier.padding(Spacing.lg)) {
            NumberStepper(
                valueText = "−400 kcal",
                onDecrement = {},
                onIncrement = {},
                decrementEnabled = true,
                incrementEnabled = false,
                decrementContentDescription = "Reducir ajuste 50 kcal",
                incrementContentDescription = "Aumentar ajuste 50 kcal",
                stateDescription = "Ajuste: −400 kcal",
            )
        }
    }
}
