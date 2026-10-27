package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import org.jetbrains.compose.resources.vectorResource

/** Colours of [SelectableCard]. */
@Immutable
data class SelectableCardColors(val container: Color, val border: Color)

/** Shape, sizes and colours of [SelectableCard] (planning/05-design-system.md §6). */
object SelectableCardDefaults {
    val Shape = AppShapes.lg
    val MinHeight: Dp = 48.dp
    val UnselectedBorderWidth: Dp = AppBorder.thin
    val SelectedBorderWidth: Dp = AppBorder.focus
    val CheckSize: Dp = 24.dp

    fun colors(selected: Boolean, appColors: AppColors): SelectableCardColors = SelectableCardColors(
        container = appColors.surfaceRaised,
        border = if (selected) appColors.neonLime else appColors.borderSubtle,
    )

    fun borderWidth(selected: Boolean): Dp = if (selected) SelectedBorderWidth else UnselectedBorderWidth

    /** The description reads as body text: size and weight (not a dimmer colour) separate it from the title. */
    fun descriptionStyle(typography: AppTypography): TextStyle = typography.bodyMedium

    fun descriptionColor(appColors: AppColors): Color = appColors.textPrimary
}

/**
 * Radio-style card with a [title] and a [description]. The selected card has a lime border and a check icon
 * (a non-colour cue); the [description] is optional. The whole card is the target and is exposed as a radio
 * button; wrap a group of cards in [Modifier.selectableGroup] on the parent.
 */
@Composable
fun SelectableCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = "",
) {
    val colors = SelectableCardDefaults.colors(selected, AppTheme.colors)
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .heightIn(min = SelectableCardDefaults.MinHeight)
            .clip(SelectableCardDefaults.Shape)
            .background(colors.container)
            .border(SelectableCardDefaults.borderWidth(selected), colors.border, SelectableCardDefaults.Shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(Spacing.cardPadding),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = title, style = AppTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = SelectableCardDefaults.descriptionStyle(AppTheme.typography),
                    color = SelectableCardDefaults.descriptionColor(AppTheme.colors),
                )
            }
        }
        if (selected) {
            // Decorative: the selected state is conveyed by the selectable semantics.
            Icon(
                imageVector = vectorResource(Res.drawable.ic_check),
                contentDescription = null,
                tint = AppTheme.colors.neonLime,
                modifier = Modifier.size(SelectableCardDefaults.CheckSize),
            )
        }
    }
}

@Preview
@Composable
private fun SelectableCardPreview() {
    AppTheme {
        Column(
            Modifier.padding(Spacing.screenMargin).selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        ) {
            SelectableCard("Moderado", selected = true, onClick = {}, description = "Ejercicio 3–5 días por semana")
            SelectableCard("Alto", selected = false, onClick = {
            }, description = "Ejercicio intenso 6–7 días por semana")
        }
    }
}
