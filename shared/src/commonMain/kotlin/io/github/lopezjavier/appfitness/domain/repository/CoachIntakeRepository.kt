package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.FreeTextKind
import io.github.lopezjavier.appfitness.domain.model.FreeTextLine
import io.github.lopezjavier.appfitness.domain.model.IntakeUpdate
import kotlinx.datetime.LocalDate

/** The "Para conocerte" intake and the free-text block the coach reads (US-81, US-89). */
interface CoachIntakeRepository {
    /** The stored intake, or null when the user never saved one. */
    suspend fun get(): Result<CoachIntake?>

    /** Validates server-side and stores only the edited value; returns the whole stored row. */
    suspend fun save(update: IntakeUpdate): Result<CoachIntake>

    suspend fun freeTextLines(today: LocalDate): Result<List<FreeTextLine>>

    suspend fun setLineExcluded(kind: FreeTextKind, sourceId: String, excluded: Boolean): Result<Unit>

    /** Marks the one-time maintenance handover card as shown. */
    suspend fun acknowledgeMaintenanceHandover(): Result<Unit>
}
