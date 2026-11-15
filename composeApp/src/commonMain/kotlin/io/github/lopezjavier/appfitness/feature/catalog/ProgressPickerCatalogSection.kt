package io.github.lopezjavier.appfitness.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_progress_picker
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_status_week
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_week_item
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_week_item_current_cd
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_week_item_done_cd
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_week_item_planned_cd
import io.github.lopezjavier.appfitness.designsystem.SegmentedProgressPicker
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource

private const val SHORT_WEEKS = 6
private const val SHORT_CURRENT = 3
private const val LONG_WEEKS = 16
private const val LONG_CURRENT = 9

/** Catalog section of [SegmentedProgressPicker] (R-105): week tabs of a 6-week block (shared width) and a 16-week block (scrolling). */
internal fun LazyListScope.progressPickerItems() {
    item { ProgressPickerSection() }
}

@Composable
private fun ProgressPickerSection() {
    CatalogSection(stringResource(Res.string.catalog_section_progress_picker), "catalog_section_progress_picker") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.blockGap)) {
            PickerSample(SHORT_WEEKS, SHORT_CURRENT, "catalog_progress_picker_short")
            PickerSample(LONG_WEEKS, LONG_CURRENT, "catalog_progress_picker_long")
        }
    }
}

@Composable
private fun PickerSample(total: Int, current: Int, tag: String) {
    var selected by rememberSaveable { mutableIntStateOf(current) }
    val itemCd = (1..total).associateWith {
        stringResource(
            when {
                it < current -> Res.string.trainer_block_week_item_done_cd
                it == current -> Res.string.trainer_block_week_item_current_cd
                else -> Res.string.trainer_block_week_item_planned_cd
            },
            it,
        )
    }
    val labels = (1..total).associateWith { stringResource(Res.string.trainer_block_week_item, it) }
    SegmentedProgressPicker(
        total = total,
        done = current - 1,
        current = current,
        selected = selected,
        onSelect = { selected = it },
        label = { labels[it].orEmpty() },
        description = { itemCd[it].orEmpty() },
        stateDescription = stringResource(Res.string.trainer_block_status_week, current, total),
        modifier = Modifier.testTag(tag),
        testTag = tag,
    )
}
