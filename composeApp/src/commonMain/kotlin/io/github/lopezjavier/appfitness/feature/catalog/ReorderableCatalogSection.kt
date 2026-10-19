package io.github.lopezjavier.appfitness.feature.catalog

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_reorderable_item
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_reorderable_reorder_cd
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_reorderable
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.DragHandle
import io.github.lopezjavier.appfitness.designsystem.ReorderableColumn
import io.github.lopezjavier.appfitness.domain.usecase.moveExercise
import org.jetbrains.compose.resources.stringResource

private const val SAMPLE_ITEMS = 3

/** Catalog section of [ReorderableColumn] and [DragHandle] (T-043): three cards reordered by long-press and drag. */
@Composable
internal fun ReorderableSection() {
    var order by rememberSaveable { mutableStateOf(List(SAMPLE_ITEMS) { it + 1 }) }
    CatalogSection(stringResource(Res.string.catalog_section_reorderable), "catalog_section_reorderable") {
        ReorderableColumn(
            items = order,
            key = { it },
            onMove = { from, to -> order = moveExercise(order, from, to) },
        ) { number, _, _ ->
            AppCard(Modifier.fillMaxWidth().testTag("catalog_reorderable_row_$number")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DragHandle(
                        contentDescription = stringResource(Res.string.catalog_reorderable_reorder_cd, number),
                        modifier = Modifier.dragHandle().testTag("catalog_reorderable_handle_$number"),
                    )
                    Text(
                        text = stringResource(Res.string.catalog_reorderable_item, number),
                        style = AppTheme.typography.bodyLarge,
                        color = AppTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
