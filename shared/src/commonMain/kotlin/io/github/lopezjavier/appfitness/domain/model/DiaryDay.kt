package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate

/** Energy and macros of a set of entries; values keep the 0.1 precision of the snapshots. */
data class MealTotals(val kcal: Double, val proteinG: Double, val carbsG: Double, val fatG: Double) {
    companion object {
        val ZERO = MealTotals(0.0, 0.0, 0.0, 0.0)
    }
}

/** Daily targets: calories and the gram target of each macro (as computed by `CalculateMacroTargets`). */
data class DiaryGoals(val targetKcal: Int, val proteinG: Int, val carbsG: Int, val fatG: Int)

/** The totals of one day that has diary entries (US-24); days without entries have no summary. */
data class DiaryDaySummary(val date: LocalDate, val totals: MealTotals)

/** An enabled meal with the entries logged in it. */
data class MealSection(val slot: MealSlot, val entries: List<FoodLogEntry>, val totals: MealTotals)

/** A row of a meal in the Diario: a loose entry or a collapsible comida group (ADR-68). */
sealed interface MealRow {
    data class Single(val entry: FoodLogEntry) : MealRow

    /** The entries of one applied comida within a meal; [totals] is their sum. */
    data class Group(val groupId: String, val name: String, val entries: List<FoodLogEntry>, val totals: MealTotals) :
        MealRow
}

/**
 * A day of the diary. [totals] counts every entry of the day, also those of a slot without a section. [goals] is
 * null when the user has not set any.
 */
data class DiaryDay(val date: LocalDate, val goals: DiaryGoals?, val meals: List<MealSection>, val totals: MealTotals)
