package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.IntakeUpdate
import io.github.lopezjavier.appfitness.domain.repository.CoachIntakeRepository

/** Stateful in-memory intake repository; each call can be scripted to fail and its arguments are recorded. */
internal class FakeCoachIntakeRepository(var stored: CoachIntake? = null) : CoachIntakeRepository {
    var getFailure: Throwable? = null
    var saveFailure: Throwable? = null
    var ackFailure: Throwable? = null

    /** Every update sent; like the server, each one changes only its own value. */
    val saveCalls = mutableListOf<IntakeUpdate>()
    var ackCalls = 0

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
        }
        stored = next
        return Result.success(next)
    }

    override suspend fun acknowledgeMaintenanceHandover(): Result<Unit> {
        ackCalls++
        return ackFailure?.let { Result.failure(it) } ?: Result.success(Unit)
    }
}
