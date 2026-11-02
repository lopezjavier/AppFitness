package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.CoachIntakeDraft
import io.github.lopezjavier.appfitness.domain.model.FreeTextKind
import io.github.lopezjavier.appfitness.domain.model.FreeTextLine
import io.github.lopezjavier.appfitness.domain.model.FreeTextStatus
import io.github.lopezjavier.appfitness.domain.repository.CoachIntakeRepository
import io.github.lopezjavier.appfitness.domain.repository.IntakePromptPreference
import kotlinx.datetime.LocalDate

/** Stateful in-memory intake repository; each call can be scripted to fail and its arguments are recorded. */
internal class FakeCoachIntakeRepository(var stored: CoachIntake? = null) : CoachIntakeRepository {
    var getFailure: Throwable? = null
    var saveFailure: Throwable? = null
    var extractFailure: Throwable? = null
    var linesFailure: Throwable? = null
    var toggleFailure: Throwable? = null
    var ackFailure: Throwable? = null

    /** What the model "finds" in the text; keys already present in the sent fields come back null, like the server. */
    var extractResult = CoachIntakeDraft()

    val saveCalls = mutableListOf<CoachIntake>()
    val extractCalls = mutableListOf<Pair<String, CoachIntakeDraft?>>()
    val toggleCalls = mutableListOf<Triple<FreeTextKind, String, Boolean>>()
    var ackCalls = 0

    /** The lines the server holds; excluded ones are reported as [FreeTextStatus.Excluded]. */
    val lines = mutableListOf<FreeTextLine>()
    private val excluded = mutableSetOf<Pair<FreeTextKind, String>>()

    override suspend fun get(): Result<CoachIntake?> = getFailure?.let { Result.failure(it) } ?: Result.success(stored)

    override suspend fun save(intake: CoachIntake): Result<CoachIntake> {
        saveCalls += intake
        saveFailure?.let { return Result.failure(it) }
        stored = intake
        return Result.success(intake)
    }

    override suspend fun extract(freeText: String, fields: CoachIntakeDraft?): Result<CoachIntakeDraft> {
        extractCalls += freeText to fields
        extractFailure?.let { return Result.failure(it) }
        return Result.success(extractResult)
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

internal class FakeIntakePromptPreference(var asked: Boolean = false) : IntakePromptPreference {
    override suspend fun isAsked(): Boolean = asked

    override suspend fun markAsked() {
        asked = true
    }
}
