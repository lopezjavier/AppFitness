package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.FreeTextKind
import io.github.lopezjavier.appfitness.domain.model.FreeTextLine
import io.github.lopezjavier.appfitness.domain.model.FreeTextStatus
import io.github.lopezjavier.appfitness.domain.model.IntakeUpdate
import io.github.lopezjavier.appfitness.domain.repository.CoachIntakeRepository
import kotlinx.datetime.LocalDate

/** Stateful in-memory intake repository; each call can be scripted to fail and its arguments are recorded. */
internal class FakeCoachIntakeRepository(var stored: CoachIntake? = null) : CoachIntakeRepository {
    var getFailure: Throwable? = null
    var saveFailure: Throwable? = null
    var linesFailure: Throwable? = null
    var toggleFailure: Throwable? = null
    var ackFailure: Throwable? = null

    /** Every update sent; like the server, each one changes only its own value. */
    val saveCalls = mutableListOf<IntakeUpdate>()
    val toggleCalls = mutableListOf<Triple<FreeTextKind, String, Boolean>>()
    var ackCalls = 0

    /** The lines the server holds; excluded ones are reported as [FreeTextStatus.Excluded]. */
    val lines = mutableListOf<FreeTextLine>()
    private val excluded = mutableSetOf<Pair<FreeTextKind, String>>()

    override suspend fun get(): Result<CoachIntake?> = getFailure?.let { Result.failure(it) } ?: Result.success(stored)

    override suspend fun save(update: IntakeUpdate): Result<CoachIntake> {
        saveCalls += update
        saveFailure?.let { return Result.failure(it) }
        val base = stored ?: CoachIntake()
        val next = when (update) {
            is IntakeUpdate.TrainingYears -> base.copy(trainingYears = update.years)

            is IntakeUpdate.KnownMaintenance -> base.copy(
                knownMaintenanceKcal = update.kcal,
                maintenanceHandoverPending = update.kcal != null && base.maintenanceHandoverPending,
            )

            is IntakeUpdate.FreeText -> base.copy(freeText = update.text)
        }
        stored = next
        return Result.success(next)
    }

    override suspend fun freeTextLines(today: LocalDate): Result<List<FreeTextLine>> {
        linesFailure?.let { return Result.failure(it) }
        return Result.success(
            lines.map { if ((it.kind to it.sourceId) in excluded) it.copy(status = FreeTextStatus.Excluded) else it },
        )
    }

    override suspend fun setLineExcluded(kind: FreeTextKind, sourceId: String, excluded: Boolean): Result<Unit> {
        toggleCalls += Triple(kind, sourceId, excluded)
        toggleFailure?.let { return Result.failure(it) }
        if (excluded) this.excluded += kind to sourceId else this.excluded -= kind to sourceId
        return Result.success(Unit)
    }

    override suspend fun acknowledgeMaintenanceHandover(): Result<Unit> {
        ackCalls++
        return ackFailure?.let { Result.failure(it) } ?: Result.success(Unit)
    }
}
