package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.food_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.food_offline_message
import io.github.lopezjavier.appfitness.composeapp.resources.food_templates_empty
import io.github.lopezjavier.appfitness.composeapp.resources.food_templates_empty_hint
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.composeapp.resources.template_add_failed
import io.github.lopezjavier.appfitness.composeapp.resources.template_manage_failed
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.feature.templates.SwipeableTemplateRow
import io.github.lopezjavier.appfitness.feature.templates.TemplateRow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val SKELETON_ROWS = 4
private val SkeletonRowHeight = Spacing.xl * 2

/**
 * The "Comidas" tab under the search box: the saved meals with their totals, or the loading, empty and error states.
 * [addFailed] and [manageFailed] show the generic failures above the list; the "Añadir" buttons are disabled offline.
 */
@Composable
internal fun TemplatesPane(
    results: TemplatesResults,
    isOffline: Boolean,
    addFailed: Boolean,
    manageFailed: Boolean,
    onEvent: (FoodSearchEvent) -> Unit,
) {
    when (results) {
        TemplatesResults.Loading -> TemplatesLoading()

        is TemplatesResults.Content -> TemplatesList(results.rows, isOffline, addFailed, manageFailed, onEvent)

        TemplatesResults.Empty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                message = stringResource(Res.string.food_templates_empty_hint),
                title = stringResource(Res.string.food_templates_empty),
                testTagPrefix = "food_search_templates_empty",
            )
        }

        TemplatesResults.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ErrorState(
                message = stringResource(
                    if (isOffline) Res.string.food_offline_message else Res.string.food_error_message,
                ),
                action = AppStateAction(stringResource(Res.string.state_retry)) { onEvent(FoodSearchEvent.Retry) },
                testTagPrefix = "food_search_templates_retry",
            )
        }
    }
}

@Composable
private fun FailureText(message: StringResource, tag: String) {
    Text(
        text = stringResource(message),
        style = AppTheme.typography.bodySmall,
        color = AppTheme.colors.error,
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun TemplatesLoading() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.screenMargin).testTag("food_search_templates_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        repeat(SKELETON_ROWS) { SkeletonBox(Modifier.fillMaxWidth().heightIn(min = SkeletonRowHeight)) }
    }
}

@Composable
private fun TemplatesList(
    rows: List<TemplateSummary>,
    isOffline: Boolean,
    addFailed: Boolean,
    manageFailed: Boolean,
    onEvent: (FoodSearchEvent) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("food_search_templates_list"),
        contentPadding = PaddingValues(horizontal = Spacing.screenMargin, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (addFailed) {
            item { FailureText(Res.string.template_add_failed, "food_search_templates_add_failed") }
        }
        if (manageFailed) {
            item { FailureText(Res.string.template_manage_failed, "food_search_templates_manage_failed") }
        }
        items(rows, key = { it.template.id }) { row ->
            SwipeableTemplateRow(
                templateId = row.template.id,
                name = row.template.name,
                enabled = !isOffline,
                onDelete = { onEvent(FoodSearchEvent.SwipeDeleteTemplate(row.template.id)) },
            ) {
                TemplateRow(
                    row = row,
                    enabled = !isOffline,
                    onAdd = { onEvent(FoodSearchEvent.AddTemplate(row.template.id)) },
                    onOpen = { onEvent(FoodSearchEvent.OpenTemplate(row.template.id)) },
                )
            }
        }
    }
}
