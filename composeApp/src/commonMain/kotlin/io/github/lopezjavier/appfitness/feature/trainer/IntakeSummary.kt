package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.InjuredArea
import io.github.lopezjavier.appfitness.domain.model.Situation

/** What the user told the coach in "Para conocerte", shown in "Tus datos" and "Antes de empezar" (provisional). */
data class IntakeSummary(
    val situation: Situation? = null,
    val trainingYears: Double? = null,
    val injuredArea: InjuredArea? = null,
    val maintenanceKcal: Int? = null,
) {
    val isEmpty: Boolean
        get() = situation == null && trainingYears == null && injuredArea == null && maintenanceKcal == null

    companion object {
        fun of(intake: CoachIntake?) = IntakeSummary(
            situation = intake?.situation,
            trainingYears = intake?.trainingYears,
            injuredArea = intake?.injuredArea,
            maintenanceKcal = intake?.knownMaintenanceKcal,
        )
    }
}

/** The three intake rows of "Antes de empezar" (plan mode); each opens "Para conocerte" (`PlanFixTarget.Intake`). */
enum class IntakeRowKind { Situation, TrainingAge, InjuredArea }
