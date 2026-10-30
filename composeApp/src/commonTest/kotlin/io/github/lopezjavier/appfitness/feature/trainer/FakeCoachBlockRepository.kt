package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.BlockExercise
import io.github.lopezjavier.appfitness.domain.model.BlockSaved
import io.github.lopezjavier.appfitness.domain.model.BlockSession
import io.github.lopezjavier.appfitness.domain.model.BlockTraining
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.repository.CoachBlockRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.LocalDate

internal fun blockExercise(id: String = "ex-1", repMin: Int = 8, repMax: Int = 12) = BlockExercise(
    exerciseId = id,
    exerciseKey = "key-$id",
    role = "main",
    muscleGroup = "chest",
    sets = 3,
    repMin = repMin,
    repMax = repMax,
    restSeconds = 90,
    reason = "Trabaja el pecho con poca fatiga.",
)

internal fun blockTraining(days: Int = 3, chosen: Boolean = false) = BlockTraining(
    daysPerWeek = days,
    daysChosen = chosen,
    sessionMinutes = 60,
    repScheme = "hypertrophy",
    blockWeeks = 4,
    equipment = listOf("dumbbell", "bodyweight"),
    weeklySets = mapOf("chest" to 10, "back" to 12),
    sessionsPerMuscle = mapOf("chest" to 2, "back" to 2),
)

/** A [weeks]-week block repeating [sessionsPerWeek] workouts with two exercises each. */
internal fun coachBlock(
    planId: String = "plan-1",
    weeks: Int = 4,
    sessionsPerWeek: Int = 3,
    saved: Boolean = false,
    training: BlockTraining = blockTraining(sessionsPerWeek),
) = CoachBlock(
    planId = planId,
    training = training,
    sessions = (1..weeks).flatMap { week ->
        (0 until sessionsPerWeek).map { index ->
            BlockSession(
                week = week,
                sessionIndex = index,
                name = "Entreno ${index + 1}",
                focus = listOf("chest"),
                exercises = listOf(blockExercise("ex-1"), blockExercise("ex-2")),
                routineId = if (saved) "routine-$index" else null,
            )
        }
    },
    saved = saved,
)

/** Test double of [CoachBlockRepository]; every call is recorded and each answer is a settable result. */
internal class FakeCoachBlockRepository : CoachBlockRepository {
    var request: Result<CoachBlock> = Result.success(coachBlock())
    var current: Result<CoachBlock?> = Result.success(null)
    var saveResult: Result<BlockSaved> = Result.success(BlockSaved("folder-1", listOf("routine-0", "routine-1")))

    /** When set, [requestBlock] suspends until completed (a block being prepared). */
    var requestGate: CompletableDeferred<Unit>? = null

    /** When set, [saveBlock] suspends until completed. */
    var saveGate: CompletableDeferred<Unit>? = null

    val requests = mutableListOf<Pair<String, LocalDate>>()
    val reads = mutableListOf<String>()
    val saves = mutableListOf<String>()

    override suspend fun requestBlock(planId: String, today: LocalDate): Result<CoachBlock> {
        requests += planId to today
        requestGate?.await()
        return request
    }

    override suspend fun currentBlock(planId: String): Result<CoachBlock?> {
        reads += planId
        return current
    }

    override suspend fun saveBlock(planId: String): Result<BlockSaved> {
        saves += planId
        saveGate?.await()
        return saveResult
    }
}
