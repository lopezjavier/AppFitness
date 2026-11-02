package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.CoachIntakeDraft
import io.github.lopezjavier.appfitness.domain.model.FreeTextKind
import io.github.lopezjavier.appfitness.domain.model.FreeTextLine
import kotlinx.datetime.LocalDate

/** The "Para conocerte" intake and the free-text block the coach reads (US-81, US-89). */
interface CoachIntakeRepository {
    /** The stored intake, or null when the user never saved one. */
    suspend fun get(): Result<CoachIntake?>

    /** Validates server-side and stores the single row; returns what was stored. */
    suspend fun save(intake: CoachIntake): Result<CoachIntake>

    /** One budgeted model call; keys already filled in [fields] are never overwritten (returned null). */
    suspend fun extract(freeText: String, fields: CoachIntakeDraft? = null): Result<CoachIntakeDraft>

    suspend fun freeTextLines(today: LocalDate): Result<List<FreeTextLine>>

    suspend fun setLineExcluded(kind: FreeTextKind, sourceId: String, excluded: Boolean): Result<Unit>

    /** Marks the one-time maintenance handover card as shown. */
    suspend fun acknowledgeMaintenanceHandover(): Result<Unit>
}
