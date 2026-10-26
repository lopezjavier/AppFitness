package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.FoodEntryRow
import io.github.lopezjavier.appfitness.designsystem.FoodEntryRowModel
import io.github.lopezjavier.appfitness.designsystem.labelRes
import io.github.lopezjavier.appfitness.domain.model.CookingMethod
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import org.jetbrains.compose.resources.stringResource
import kotlin.math.floor

private const val HALF = 0.5
private const val TENTHS = 10L

/** Half-up whole number of a value that keeps 0.1 precision. */
internal fun wholeHalfUpInt(value: Double): Int = floor(value + HALF).toInt()

/** Half-up whole number of a value that keeps 0.1 precision, in es-ES grouping. */
internal fun wholeHalfUp(value: Double): String = AppNumberFormat.integer(wholeHalfUpInt(value))

/** "220" for a whole amount, "12,5" for one with a decimal (the data keeps 0.1). */
internal fun amountText(amount: Double): String {
    val tenths = floor(amount * TENTHS + HALF).toLong()
    val whole = AppNumberFormat.integer((tenths / TENTHS).toInt())
    val decimal = tenths % TENTHS
    return if (decimal == 0L) whole else "$whole,$decimal"
}

/** The row model of a logged food: whole-number kcal and macros, the amount line from [FoodEntryDetail]. */
@Composable
internal fun FoodLogEntry.toRowModel(): FoodEntryRowModel {
    val cookingLabel = if (cookingMethod == CookingMethod.Raw) null else stringResource(cookingMethod.labelRes())
    return FoodEntryRowModel(
        id = id,
        name = foodName,
        detail = FoodEntryDetail.format(portionLabel, amount, baseUnit, cookingLabel),
        kcal = wholeHalfUpInt(kcal),
        proteinG = wholeHalfUpInt(proteinG),
        carbsG = wholeHalfUpInt(carbsG),
        fatG = wholeHalfUpInt(fatG),
    )
}

/** A logged food of the diary: tap opens its sheet, swipe (or the TalkBack action) deletes it while online. */
@Composable
internal fun LoggedEntryRow(entry: FoodLogEntry, actions: EntryActions, modifier: Modifier = Modifier) {
    FoodEntryRow(
        model = entry.toRowModel(),
        modifier = modifier,
        onClick = { actions.onEdit(entry.id) },
        onDelete = if (actions.canDelete) ({ actions.onDelete(entry.id) }) else null,
        tagPrefix = "diary_entry",
    )
}
