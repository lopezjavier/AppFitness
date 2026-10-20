package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import kotlin.math.floor
import kotlin.math.roundToLong

/** Diary sums and progress figures (planning/04 §2). Sums run in integer tenths so 0.1 values never drift. */
object DiaryTotals {
    private const val TENTHS = 10.0
    private const val HALF = 0.5
    private const val PERCENT_PER_TENTH = 10L // consumed tenths * 100 % / 10

    fun sum(entries: List<FoodLogEntry>): MealTotals {
        var kcal = 0L
        var protein = 0L
        var carbs = 0L
        var fat = 0L
        for (entry in entries) {
            kcal += tenths(entry.kcal)
            protein += tenths(entry.proteinG)
            carbs += tenths(entry.carbsG)
            fat += tenths(entry.fatG)
        }
        return MealTotals(kcal / TENTHS, protein / TENTHS, carbs / TENTHS, fat / TENTHS)
    }

    /** Whole kcal left for the day, computed on the half-up rounded consumed value; negative when exceeded. */
    fun remainingKcal(targetKcal: Int, consumedKcal: Double): Int = targetKcal - roundHalfUp(consumedKcal)

    /** Half-up percent of a macro target; not capped (the bar caps it). Zero when the target is zero. */
    fun macroPercent(consumedG: Double, targetG: Int): Int {
        if (targetG <= 0) return 0
        val numerator = tenths(consumedG) * PERCENT_PER_TENTH
        return ((numerator * 2 + targetG) / (targetG * 2L)).toInt()
    }

    private fun tenths(value: Double): Long = (value * TENTHS).roundToLong()

    private fun roundHalfUp(value: Double): Int = floor(value + HALF).toInt()
}
