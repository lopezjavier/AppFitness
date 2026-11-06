package io.github.lopezjavier.appfitness.domain.model

/**
 * The first cause the stall diagnosis found for a stalled main lift (07 section 3 and (7)), in check order. Figures are
 * already rounded by the server; the UI only formats them.
 */
sealed interface StallCause {
    /** Fewer than 2 lifting sessions per week; [planDays] is the plan's days per week (action variant for 1 day). */
    data class Sessions(val avg: Double, val planDays: Int) : StallCause

    /** Fewer than 10 weekly sets of [group]. */
    data class Sets(val avg: Double, val group: MuscleGroup) : StallCause

    /** The plan itself gives [group] fewer than 10 sets ("Mantener"). */
    data class GroupOnMaintain(val group: MuscleGroup) : StallCause

    /** Protein below 1.6 g/kg: [avgG] grams a day, [perKg] g/kg, against [targetG] grams. */
    data class Protein(val avgG: Int, val perKg: Double, val targetG: Int) : StallCause

    /** Average sleep below 7 h. */
    data class Sleep(val avgH: Double) : StallCause

    /** No check fired; [proteinGateMissing] is true when there were too few complete food days to judge protein. */
    data class NoClearCause(val proteinGateMissing: Boolean) : StallCause
}

/** A stalled main lift ([exerciseId]) and why. */
data class StallDiagnosis(val exerciseId: String, val cause: StallCause)
