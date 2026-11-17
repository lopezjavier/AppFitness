package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_meal_breakfast
import io.github.lopezjavier.appfitness.composeapp.resources.ic_meal_dinner
import io.github.lopezjavier.appfitness.composeapp.resources.ic_meal_lunch
import io.github.lopezjavier.appfitness.composeapp.resources.ic_meal_snack
import io.github.lopezjavier.appfitness.composeapp.resources.ic_restaurant_outlined
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.MealCode
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import org.jetbrains.compose.resources.DrawableResource

private val BREAKFAST_WORDS = listOf("desayun", "breakfast", "almuerzo")
private val LUNCH_WORDS = listOf("comida", "lunch", "mediodía", "mediodia")
private val SNACK_WORDS = listOf("snack", "merienda", "tentempié", "pre", "post", "aperitivo")
private val DINNER_WORDS = listOf("cena", "dinner", "noche")

/** The glyph of a meal: by its code, else by name heuristics (meals are user-configurable), else a generic plate. */
internal fun MealSlot.icon(): DrawableResource {
    val name = label.lowercase()
    val kind = code ?: when {
        BREAKFAST_WORDS.any { it in name } -> MealCode.Breakfast
        DINNER_WORDS.any { it in name } -> MealCode.Dinner
        SNACK_WORDS.any { it in name } -> MealCode.Snack
        LUNCH_WORDS.any { it in name } -> MealCode.Lunch
        else -> null
    }
    return when (kind) {
        MealCode.Breakfast -> Res.drawable.ic_meal_breakfast
        MealCode.Lunch -> Res.drawable.ic_meal_lunch
        MealCode.Snack -> Res.drawable.ic_meal_snack
        MealCode.Dinner -> Res.drawable.ic_meal_dinner
        null -> Res.drawable.ic_restaurant_outlined
    }
}

/** The accent of the meal at [index] (its position in the day): the colour of its badge, share bar and legend dot. */
@Composable
internal fun mealAccent(index: Int): Color {
    val colors = AppTheme.colors
    val palette = listOf(colors.energyAmber, colors.neonLime, colors.athleticCyan, colors.macroFat, colors.macroCarbs)
    return palette[index % palette.size]
}
