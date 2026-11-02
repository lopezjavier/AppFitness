package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.CoachIntake

/** What the user told the coach in "Para conocerte", shown in "Tus datos" and "Antes de empezar" (provisional). */
data class IntakeSummary(
    val trainingYears: Double? = null,
    val maintenanceKcal: Int? = null,
    val freeText: String? = null,
) {
    val isEmpty: Boolean get() = trainingYears == null && maintenanceKcal == null && freeText == null

    /** A stated value reads "Provisional" until real data takes over (US-68); an empty row reads "Sin indicar". */
    fun isProvisional(kind: IntakeRowKind): Boolean = when (kind) {
        IntakeRowKind.TrainingYears -> trainingYears != null
        IntakeRowKind.Maintenance -> maintenanceKcal != null
        IntakeRowKind.FreeText -> freeText != null
    }

    companion object {
        fun of(intake: CoachIntake?) = IntakeSummary(
            trainingYears = intake?.trainingYears,
            maintenanceKcal = intake?.knownMaintenanceKcal,
            freeText = intake?.freeText?.takeIf { it.isNotBlank() },
        )
    }
}

/**
 * The intake rows, each opening its own editor (R-55): years and maintenance open a sheet, the free text opens
 * "Para conocerte". A block reads only [TrainingYears] and [FreeText].
 */
enum class IntakeRowKind { TrainingYears, Maintenance, FreeText }
