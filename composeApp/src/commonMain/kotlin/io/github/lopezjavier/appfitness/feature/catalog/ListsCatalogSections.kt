package io.github.lopezjavier.appfitness.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_food_entry_detail
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_food_entry_name
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_measurement_edit
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_metric_label
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_metric_supporting
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_metric_value
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_progress_state
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_rows_add_object
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_rows_exercise_name
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_rows_exercise_supporting
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_rows_header
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_rows_list_headline
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_rows_list_supporting
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_rows_nav_label
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_rows_nav_value
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_rows_workout_cd
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_rows_workout_date
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_rows_workout_duration
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_rows_workout_name
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_rows_workout_sets
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_rows_workout_volume
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_food_entry
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_metric_card
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_rows
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_swipe
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_time_range
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_swipe_empty
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_swipe_hint
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_swipe_item
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_swipe_item_cd
import io.github.lopezjavier.appfitness.designsystem.AddItemButton
import io.github.lopezjavier.appfitness.designsystem.AppLinearProgress
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ExerciseRow
import io.github.lopezjavier.appfitness.designsystem.FoodEntryRow
import io.github.lopezjavier.appfitness.designsystem.FoodEntryRowModel
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.MeasurementRow
import io.github.lopezjavier.appfitness.designsystem.MetricCard
import io.github.lopezjavier.appfitness.designsystem.NavigationRow
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.SwipeToDeleteRow
import io.github.lopezjavier.appfitness.designsystem.TimeRange
import io.github.lopezjavier.appfitness.designsystem.TimeRangeSelector
import io.github.lopezjavier.appfitness.designsystem.WorkoutRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private const val SWIPE_ITEMS = 3
private const val CATALOG_PROGRESS = 0.6f

/** The sample row whose delete "fails": it comes back after [FAILED_DELETE_DELAY_MILLIS] with a Reject. */
private const val FAILING_ITEM = 2
private const val FAILED_DELETE_DELAY_MILLIS = 800L

private val DemoRanges = listOf(TimeRange.Week, TimeRange.Month, TimeRange.ThreeMonths, TimeRange.Year, TimeRange.All)

/** Catalog sections of the list and card components (T-120). */
internal fun LazyListScope.listsItems() {
    item { SwipeSection() }
    item { RowsSection() }
    item { FoodEntryRowSection() }
    item { MetricCardSection() }
    item { TimeRangeSection() }
}

/**
 * Swipe-to-delete sample: a local list that really removes a row; item 2 simulates a failed delete (it returns
 * with a Reject vibration). The list survives recreation.
 */
@Composable
internal fun SwipeSection() {
    var ids by rememberSaveable { mutableStateOf(List(SWIPE_ITEMS) { it + 1 }) }
    val scope = rememberCoroutineScope()
    val haptics = LocalAppHaptics.current
    CatalogSection(stringResource(Res.string.catalog_section_swipe), "catalog_section_swipe") {
        Text(
            text = stringResource(Res.string.catalog_swipe_hint),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
        )
        if (ids.isEmpty()) {
            Text(
                text = stringResource(Res.string.catalog_swipe_empty),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("catalog_swipe_empty"),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ids.forEach { number ->
                key(number) {
                    SwipeToDeleteRow(
                        deleteLabel = stringResource(Res.string.catalog_swipe_item_cd, number),
                        enabled = true,
                        onDelete = {
                            ids = ids - number
                            if (number == FAILING_ITEM) {
                                scope.launch {
                                    delay(FAILED_DELETE_DELAY_MILLIS)
                                    ids = (ids + number).sorted()
                                    haptics.reject()
                                }
                            }
                        },
                        backgroundTag = "catalog_swipe_background_$number",
                        modifier = Modifier.testTag("catalog_swipe_row_$number"),
                    ) {
                        AppListRow(
                            headline = stringResource(Res.string.catalog_swipe_item, number),
                            modifier = Modifier.padding(horizontal = Spacing.md),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowsSection() {
    CatalogSection(stringResource(Res.string.catalog_section_rows), "catalog_section_rows") {
        ListSectionHeader(
            title = stringResource(Res.string.catalog_rows_header),
            count = SWIPE_ITEMS,
            modifier = Modifier.testTag("catalog_section_header"),
        )
        AppListRow(
            headline = stringResource(Res.string.catalog_rows_list_headline),
            supporting = stringResource(Res.string.catalog_rows_list_supporting),
            onClick = {},
            modifier = Modifier.testTag("catalog_list_row"),
        )
        NavigationRow(
            label = stringResource(Res.string.catalog_rows_nav_label),
            value = stringResource(Res.string.catalog_rows_nav_value),
            onClick = {},
            modifier = Modifier.testTag("catalog_nav_row"),
        )
        ExerciseRow(
            name = stringResource(Res.string.catalog_rows_exercise_name),
            supporting = stringResource(Res.string.catalog_rows_exercise_supporting),
            onOpen = {},
            tagPrefix = "catalog_exercise_row",
        )
        WorkoutRow(
            name = stringResource(Res.string.catalog_rows_workout_name),
            dateText = stringResource(Res.string.catalog_rows_workout_date),
            durationText = stringResource(Res.string.catalog_rows_workout_duration),
            volumeText = stringResource(Res.string.catalog_rows_workout_volume),
            setsText = stringResource(Res.string.catalog_rows_workout_sets),
            onClick = {},
            contentDescription = stringResource(Res.string.catalog_rows_workout_cd),
            tag = "catalog_workout_row",
        )
        AddItemButton(
            objectLabel = stringResource(Res.string.catalog_rows_add_object),
            onClick = {},
            modifier = Modifier.testTag("catalog_add_item"),
        )
    }
}

@Composable
private fun MetricCardSection() {
    CatalogSection(stringResource(Res.string.catalog_section_metric_card), "catalog_section_metric_card") {
        MetricCard(
            label = stringResource(Res.string.catalog_metric_label),
            value = stringResource(Res.string.catalog_metric_value),
            modifier = Modifier.testTag("catalog_metric_card"),
            supporting = {
                Text(
                    text = stringResource(Res.string.catalog_metric_supporting),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                )
            },
            progress = {
                AppLinearProgress(
                    fraction = CATALOG_PROGRESS,
                    stateDescription = stringResource(Res.string.catalog_progress_state),
                    modifier = Modifier.testTag("catalog_linear_progress"),
                )
            },
        )
        val editLabel = stringResource(Res.string.catalog_measurement_edit)
        Column(Modifier.testTag("catalog_measurement_rows")) {
            MeasurementRow("84,5 cm", "12 oct", onClick = {}, clickLabel = editLabel)
            MeasurementRow("85,0 cm", "5 oct", onClick = {}, clickLabel = editLabel)
        }
    }
}

@Composable
private fun TimeRangeSection() {
    var selected by rememberSaveable { mutableStateOf(TimeRange.Month) }
    CatalogSection(stringResource(Res.string.catalog_section_time_range), "catalog_section_time_range") {
        TimeRangeSelector(
            selected = selected,
            onSelect = { selected = it },
            ranges = DemoRanges,
            tagPrefix = "catalog_time_range",
        )
    }
}

@Composable
private fun FoodEntryRowSection() {
    var present by rememberSaveable { mutableStateOf(true) }
    CatalogSection(stringResource(Res.string.catalog_section_food_entry), "catalog_section_food_entry") {
        if (present) {
            FoodEntryRow(
                model = FoodEntryRowModel(
                    id = "sample",
                    name = stringResource(Res.string.catalog_food_entry_name),
                    detail = stringResource(Res.string.catalog_food_entry_detail),
                    kcal = FOOD_SAMPLE_KCAL,
                    proteinG = FOOD_SAMPLE_PROTEIN,
                    carbsG = FOOD_SAMPLE_CARBS,
                    fatG = FOOD_SAMPLE_FAT,
                ),
                onClick = {},
                onDelete = { present = false },
                tagPrefix = "catalog_food_entry",
            )
        }
    }
}

private const val FOOD_SAMPLE_KCAL = 1240
private const val FOOD_SAMPLE_PROTEIN = 80
private const val FOOD_SAMPLE_CARBS = 150
private const val FOOD_SAMPLE_FAT = 40
