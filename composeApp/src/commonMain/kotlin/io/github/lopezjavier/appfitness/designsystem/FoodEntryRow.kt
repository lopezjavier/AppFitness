package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_entry_delete_cd
import io.github.lopezjavier.appfitness.composeapp.resources.diary_entry_kcal
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_carbs
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_fat
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_protein
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Plain input of [FoodEntryRow]: the designsystem takes no domain type. Macros are whole grams. */
@Immutable
data class FoodEntryRowModel(
    val id: String,
    val name: String,
    val detail: String,
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
)

/** Tag helpers of [FoodEntryRow]: `<prefix>_<id>`, `_detail` and `_delete`. */
object FoodEntryRowDefaults {
    const val TAG_PREFIX = "food_entry"

    fun rowTag(prefix: String, id: String): String = "${prefix}_$id"

    fun detailTag(prefix: String, id: String): String = "${prefix}_detail_$id"

    fun deleteTag(prefix: String, id: String): String = "${prefix}_delete_$id"
}

/**
 * A logged food (R-16) on [AppListRow]: the name, [FoodEntryRowModel.detail] ("1 vaso (200 ml)") under it, and the
 * kcal with the P/C/G whole grams in the macro colours at the end. With [onClick] the row opens its sheet; with
 * [onDelete] it is wrapped in [SwipeToDeleteRow] (swipe or the TalkBack action "Eliminar <name>", R-20).
 * Tags: `<tagPrefix>_<id>` (outermost), `<tagPrefix>_detail_<id>` and `<tagPrefix>_delete_<id>` (swipe layer).
 */
@Composable
fun FoodEntryRow(
    model: FoodEntryRowModel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    tagPrefix: String = FoodEntryRowDefaults.TAG_PREFIX,
) {
    val tagged = modifier.testTag(FoodEntryRowDefaults.rowTag(tagPrefix, model.id))
    val row: @Composable () -> Unit = {
        AppListRow(
            headline = model.name,
            supporting = model.detail,
            supportingTestTag = FoodEntryRowDefaults.detailTag(tagPrefix, model.id),
            onClick = onClick,
            trailing = { EntryTotals(model) },
        )
    }
    if (onDelete == null) {
        Column(tagged) { row() }
    } else {
        SwipeToDeleteRow(
            deleteLabel = stringResource(Res.string.diary_entry_delete_cd, model.name),
            enabled = true,
            onDelete = onDelete,
            backgroundTag = FoodEntryRowDefaults.deleteTag(tagPrefix, model.id),
            modifier = tagged,
            containerColor = AppTheme.colors.surfaceRaised,
            content = row,
        )
    }
}

@Composable
private fun EntryTotals(model: FoodEntryRowModel) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = stringResource(Res.string.diary_entry_kcal, AppNumberFormat.integer(model.kcal)),
            style = AppTheme.typography.labelNumeric,
            color = AppTheme.colors.textPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MacroGrams(Res.string.diary_meal_protein, model.proteinG, Macro.Protein)
            MacroGrams(Res.string.diary_meal_carbs, model.carbsG, Macro.Carbs)
            MacroGrams(Res.string.diary_meal_fat, model.fatG, Macro.Fat)
        }
    }
}

@Composable
private fun MacroGrams(format: StringResource, grams: Int, macro: Macro) {
    Text(
        text = stringResource(format, AppNumberFormat.integer(grams)),
        style = AppTheme.typography.bodySmall,
        color = MacroDefaults.color(macro, AppTheme.colors),
    )
}
