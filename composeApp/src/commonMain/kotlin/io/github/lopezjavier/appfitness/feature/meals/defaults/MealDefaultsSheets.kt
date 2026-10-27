package io.github.lopezjavier.appfitness.feature.meals.defaults

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.assign_default_day_error
import io.github.lopezjavier.appfitness.composeapp.resources.assign_default_title
import io.github.lopezjavier.appfitness.composeapp.resources.default_picker_empty
import io.github.lopezjavier.appfitness.composeapp.resources.default_picker_empty_hint
import io.github.lopezjavier.appfitness.composeapp.resources.default_picker_title
import io.github.lopezjavier.appfitness.composeapp.resources.template_items_count
import io.github.lopezjavier.appfitness.composeapp.resources.template_summary
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_assigned_to
import io.github.lopezjavier.appfitness.designsystem.AppEditSheet
import io.github.lopezjavier.appfitness.designsystem.AppPickerSheet
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.NUMERIC_FONT_FEATURES
import io.github.lopezjavier.appfitness.designsystem.SheetCommit
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WeekdaySelector
import io.github.lopezjavier.appfitness.feature.diary.wholeHalfUp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** [this] with tabular numerals, for counts and kcal. */
internal fun TextStyle.numeric(): TextStyle = copy(fontFeatureSettings = NUMERIC_FONT_FEATURES)

/** "N alimentos · X kcal", the same format as a comida row of the food search. */
@Composable
internal fun comidaSummary(foodCount: Int, kcal: Double): String = stringResource(
    Res.string.template_summary,
    pluralStringResource(Res.plurals.template_items_count, foodCount, foodCount),
    wholeHalfUp(kcal),
)

/** Picker sheet that lists the saved comidas; tapping one opens its assignment sheet. Closing it changes nothing. */
@Composable
internal fun DefaultComidaPickerSheet(
    slotId: String,
    comidas: List<DefaultComidaOptionUi>,
    hasSavedComidas: Boolean,
    onEvent: (MealDefaultsEvent) -> Unit,
) {
    AppPickerSheet(
        title = stringResource(Res.string.default_picker_title),
        onDismiss = { onEvent(MealDefaultsEvent.DismissPicker) },
        testTagPrefix = "default_picker_sheet",
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenMargin).padding(bottom = Spacing.xl)) {
            if (!hasSavedComidas) {
                EmptyState(
                    message = stringResource(Res.string.default_picker_empty_hint),
                    title = stringResource(Res.string.default_picker_empty),
                    modifier = Modifier.padding(vertical = Spacing.xl),
                    testTagPrefix = "default_picker_empty",
                )
            }
            comidas.forEach { PickerRow(slotId, it, onEvent) }
        }
    }
}

@Composable
private fun PickerRow(slotId: String, option: DefaultComidaOptionUi, onEvent: (MealDefaultsEvent) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
            .clickable(role = Role.Button) { onEvent(MealDefaultsEvent.OpenAssignment(slotId, option.templateId)) }
            .testTag("default_picker_row_${option.templateId}")
            .padding(vertical = Spacing.sm),
    ) {
        Text(option.name, style = AppTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
        Text(
            text = comidaSummary(option.foodCount, option.kcal),
            style = AppTheme.typography.bodySmall.numeric(),
            color = AppTheme.colors.textSecondary,
        )
    }
}

/**
 * Edit sheet to pick the weekdays of one comida in one meal. Conflicting days are disabled and named in visible
 * text below the selector; an empty selection shows an error and disables "Guardar" (it only applies to the screen's
 * draft, whose own "Guardar" persists). Dismissing it discards the sheet.
 */
@Composable
internal fun AssignDefaultSheet(sheet: AssignSheetUi, onEvent: (MealDefaultsEvent) -> Unit) {
    AppEditSheet(
        title = stringResource(Res.string.assign_default_title, sheet.mealLabel, sheet.templateName),
        commit = SheetCommit.Apply,
        onCommit = { onEvent(MealDefaultsEvent.ConfirmSheet) },
        onDismiss = { onEvent(MealDefaultsEvent.DismissSheet) },
        commitEnabled = sheet.canSave,
        testTagPrefix = "assign_default_sheet",
    ) {
        WeekdaySelector(
            selected = sheet.selectedDays,
            disabled = sheet.conflicts,
            onToggle = { onEvent(MealDefaultsEvent.ToggleDay(it)) },
            testTagPrefix = "assign_default",
        )
        ConflictNote(sheet.conflicts)
        if (!sheet.canSave) {
            Text(
                text = stringResource(Res.string.assign_default_day_error),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("assign_default_day_error"),
            )
        }
    }
}

/** One visible "Ya asignado a <comida>" line per other comida that owns some of the days. */
@Composable
private fun ConflictNote(conflicts: Map<Int, String>) {
    val names = conflicts.values.distinct()
    if (names.isEmpty()) return
    Column(Modifier.semantics(mergeDescendants = true) {}.testTag("assign_default_conflict_note")) {
        names.forEach {
            Text(
                text = stringResource(Res.string.weekday_assigned_to, it),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
        }
    }
}
