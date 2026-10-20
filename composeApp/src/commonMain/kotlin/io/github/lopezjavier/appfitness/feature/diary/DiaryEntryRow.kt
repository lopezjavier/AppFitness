package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_entry_amount
import io.github.lopezjavier.appfitness.composeapp.resources.diary_entry_edit_label
import io.github.lopezjavier.appfitness.composeapp.resources.diary_entry_kcal
import io.github.lopezjavier.appfitness.composeapp.resources.diary_entry_not_selected
import io.github.lopezjavier.appfitness.composeapp.resources.diary_entry_selected
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_carbs
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_fat
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_protein
import io.github.lopezjavier.appfitness.composeapp.resources.diary_select_entry_cd
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Macro
import io.github.lopezjavier.appfitness.designsystem.MacroDefaults
import io.github.lopezjavier.appfitness.designsystem.SetCompletionCheckbox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.DiaryViewMode
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.floor

private const val NAME_MAX_LINES = 2
private const val HALF = 0.5
private const val TENTHS = 10L

/** Half-up whole number of a value that keeps 0.1 precision, in es-ES grouping. */
internal fun wholeHalfUp(value: Double): String = AppNumberFormat.integer(floor(value + HALF).toInt())

/** "220" for a whole amount, "12,5" for one with a decimal (the data keeps 0.1). */
internal fun amountText(amount: Double): String {
    val tenths = floor(amount * TENTHS + HALF).toLong()
    val whole = AppNumberFormat.integer((tenths / TENTHS).toInt())
    val decimal = tenths % TENTHS
    return if (decimal == 0L) whole else "$whole,$decimal"
}

/** Amount of an entry as shown: its portion ("2 × huevo") or the amount with its base unit ("220 g"). */
@Composable
private fun entryDetail(entry: FoodLogEntry): String = entry.portionLabel
    ?: stringResource(Res.string.diary_entry_amount, amountText(entry.amount), entry.baseUnit.orEmpty()).trim()

/**
 * A logged food in one of the two entry renderings of [viewMode] (the Summary mode composes no entries): Detail shows
 * the name, the amount, the kcal and the P/C/F grams; List a single line with the name, amount and kcal. Tapping the
 * row calls [onEdit]; with [canDelete] a swipe from the end (or the "Eliminar" accessibility action) calls [onDelete].
 * A long press calls [onLongPress] (starts the selection). In selection mode ([selecting]) the row shows a round
 * checkbox ([selected]) and a tap calls [onToggle] instead of editing.
 */
@Composable
@Suppress("LongParameterList") // The row's state plus one callback per gesture.
internal fun DiaryEntryRow(
    entry: FoodLogEntry,
    viewMode: DiaryViewMode,
    canDelete: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLongPress: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SwipeableEntryRow(entry, canDelete, onDelete, modifier.testTag("diary_entry_${entry.id}")) {
        val editLabel = stringResource(Res.string.diary_entry_edit_label)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minTouchTarget)
                .combinedClickable(
                    onClickLabel = editLabel,
                    role = Role.Button,
                    onLongClick = onLongPress,
                    onClick = if (selecting) onToggle else onEdit,
                )
                .padding(vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (selecting) EntryCheckbox(entry, selected, onToggle)
            if (viewMode == DiaryViewMode.List) ListEntryContent(entry) else DetailEntryContent(entry)
        }
    }
}

@Composable
private fun EntryCheckbox(entry: FoodLogEntry, selected: Boolean, onToggle: () -> Unit) {
    val state = stringResource(
        if (selected) Res.string.diary_entry_selected else Res.string.diary_entry_not_selected,
    )
    SetCompletionCheckbox(
        checked = selected,
        onCheckedChange = { onToggle() },
        contentDescription = stringResource(Res.string.diary_select_entry_cd, entry.foodName),
        modifier = Modifier.semantics { stateDescription = state }.testTag("diary_entry_select_${entry.id}"),
    )
}

@Composable
private fun RowScope.DetailEntryContent(entry: FoodLogEntry) {
    Column(Modifier.weight(1f)) {
        Text(
            text = entry.foodName,
            style = AppTheme.typography.bodyLarge,
            color = AppTheme.colors.textPrimary,
            maxLines = NAME_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        Text(text = entryDetail(entry), style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            MacroGrams(Res.string.diary_meal_protein, entry.proteinG, Macro.Protein)
            MacroGrams(Res.string.diary_meal_carbs, entry.carbsG, Macro.Carbs)
            MacroGrams(Res.string.diary_meal_fat, entry.fatG, Macro.Fat)
        }
    }
    KcalLabel(entry)
}

@Composable
private fun RowScope.ListEntryContent(entry: FoodLogEntry) {
    Text(
        text = entry.foodName,
        style = AppTheme.typography.bodyLarge,
        color = AppTheme.colors.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
    Text(text = entryDetail(entry), style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
    KcalLabel(entry)
}

@Composable
private fun KcalLabel(entry: FoodLogEntry) {
    Text(
        text = stringResource(Res.string.diary_entry_kcal, wholeHalfUp(entry.kcal)),
        style = AppTheme.typography.labelNumeric,
        color = AppTheme.colors.textPrimary,
    )
}

@Composable
private fun MacroGrams(format: StringResource, grams: Double, macro: Macro) {
    Text(
        text = stringResource(format, amountText(grams)),
        style = AppTheme.typography.bodySmall,
        color = MacroDefaults.color(macro, AppTheme.colors),
    )
}
