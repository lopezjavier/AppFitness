package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_more_vert
import io.github.lopezjavier.appfitness.composeapp.resources.template_add
import io.github.lopezjavier.appfitness.composeapp.resources.template_items_count
import io.github.lopezjavier.appfitness.composeapp.resources.template_menu_cd
import io.github.lopezjavier.appfitness.composeapp.resources.template_summary
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.feature.diary.wholeHalfUp
import io.github.lopezjavier.appfitness.feature.food.TemplateSummary
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One comida: its name, "N alimentos · X kcal" (the total is neutral text, not a macro), the "Añadir" button and the
 * overflow button ([onMenu]) with the options of the comida. "Añadir" is disabled offline ([enabled] false).
 */
@Composable
internal fun TemplateRow(
    row: TemplateSummary,
    enabled: Boolean,
    onAdd: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val id = row.template.id
    val count = row.template.items.size
    val summary = stringResource(
        Res.string.template_summary,
        pluralStringResource(Res.plurals.template_items_count, count, count),
        wholeHalfUp(row.kcal),
    )
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
            .testTag("food_search_template_$id"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = Spacing.sm)) {
            Text(
                text = row.template.name,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
            )
        }
        SecondaryButton(
            text = stringResource(Res.string.template_add),
            onClick = onAdd,
            enabled = enabled,
            modifier = Modifier.testTag("food_search_template_${id}_add"),
        )
        AppIconButton(
            contentDescription = stringResource(Res.string.template_menu_cd, row.template.name),
            icon = Res.drawable.ic_more_vert,
            testTag = "food_search_template_${id}_menu",
            onClick = onMenu,
        )
    }
}
