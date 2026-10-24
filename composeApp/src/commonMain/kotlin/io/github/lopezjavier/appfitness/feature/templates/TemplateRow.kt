package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.template_items_count
import io.github.lopezjavier.appfitness.composeapp.resources.template_summary
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.feature.diary.wholeHalfUp
import io.github.lopezjavier.appfitness.feature.food.TemplateSummary
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One comida: its name and "N alimentos · X kcal" (the total is neutral text, not a macro). The whole row is one tap
 * target that opens the read-only details of the comida ([onOpen]); adding and editing start from there (US-53).
 */
@Composable
internal fun TemplateRow(row: TemplateSummary, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val id = row.template.id
    val count = row.template.items.size
    val summary = stringResource(
        Res.string.template_summary,
        pluralStringResource(Res.plurals.template_items_count, count, count),
        wholeHalfUp(row.kcal),
    )
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
            .clickable(role = Role.Button, onClick = onOpen)
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
}
