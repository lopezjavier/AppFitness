package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.IntakeUpdate

/** The "Para conocerte" intake (US-81). */
interface CoachIntakeRepository {
    /** The stored intake, or null when the user never saved one. */
    suspend fun get(): Result<CoachIntake?>

    /** Validates server-side and stores only the edited value; returns the whole stored row. */
    suspend fun save(update: IntakeUpdate): Result<CoachIntake>

    /** Marks the one-time maintenance handover card as shown. */
    suspend fun acknowledgeMaintenanceHandover(): Result<Unit>
}
