package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import io.github.lopezjavier.appfitness.composeapp.resources.template_edit_cd
import io.github.lopezjavier.appfitness.composeapp.resources.template_items_count
import io.github.lopezjavier.appfitness.composeapp.resources.template_summary
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.feature.diary.wholeHalfUp
import io.github.lopezjavier.appfitness.feature.food.TemplateSummary
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One comida: its name and "N alimentos · X kcal" (the total is neutral text, not a macro). Tapping the row adds it
 * ([onAdd], disabled offline: [enabled] false) and the trailing pencil opens its editor ([onOpen]). This departs from
 * the "row opens details" rule on purpose (US-47).
 */
@Composable
internal fun TemplateRow(
    row: TemplateSummary,
    enabled: Boolean,
    onAdd: () -> Unit,
    onOpen: () -> Unit,
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
            .heightIn(min = Spacing.minTouchTarget),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .clickable(enabled = enabled, role = Role.Button, onClick = onAdd)
                .testTag("food_search_template_$id")
                .padding(vertical = Spacing.sm),
        ) {
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
        AppIconButton(
            contentDescription = stringResource(Res.string.template_edit_cd, row.template.name),
            icon = Res.drawable.ic_edit,
            testTag = "food_search_template_${id}_edit",
            onClick = onOpen,
        )
    }
}
