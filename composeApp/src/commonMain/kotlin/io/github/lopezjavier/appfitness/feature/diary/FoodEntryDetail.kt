package io.github.lopezjavier.appfitness.feature.diary

import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import kotlin.math.floor

private const val HALF = 0.5
private const val COOKING_SEPARATOR = " · "

/** The supporting line of a logged food: its portion and/or amount, then the cooking method when it is not raw. */
object FoodEntryDetail {
    /**
     * "1 vaso (200 ml)" for a portion with the base amount, "220 g" for the raw base unit, then " · [cookingLabel]"
     * when the entry is cooked. A portion without a base unit reads as the portion alone.
     */
    fun format(portionLabel: String?, amount: Double, baseUnit: String?, cookingLabel: String?): String {
        val base = listOf(amountText(amount), baseUnit.orEmpty()).filter { it.isNotEmpty() }.joinToString(" ")
        val quantity = when {
            portionLabel == null -> base
            baseUnit.isNullOrEmpty() -> portionLabel
            else -> "$portionLabel ($base)"
        }
        return if (cookingLabel == null) quantity else "$quantity$COOKING_SEPARATOR$cookingLabel"
    }
}

/** The day at a glance for the summary strip: whole numbers, half-up. [targetKcal] is null without goals. */
data class DiarySummary(val kcal: Int, val targetKcal: Int?, val proteinG: Int, val carbsG: Int, val fatG: Int) {
    /** "1.240 / 2.100 kcal · P 80 · C 150 · G 40", or "1.240 kcal · P 80 · C 150 · G 40" without goals. */
    val text: String
        get() {
            val consumed = AppNumberFormat.integer(kcal)
            val energy = targetKcal?.let { "$consumed / ${AppNumberFormat.integer(it)} kcal" } ?: "$consumed kcal"
            return "$energy · P $proteinG · C $carbsG · G $fatG"
        }

    companion object {
        fun of(totals: MealTotals, targetKcal: Int?) = DiarySummary(
            kcal = half(totals.kcal),
            targetKcal = targetKcal,
            proteinG = half(totals.proteinG),
            carbsG = half(totals.carbsG),
            fatG = half(totals.fatG),
        )

        private fun half(value: Double): Int = floor(value + HALF).toInt()
    }
}
