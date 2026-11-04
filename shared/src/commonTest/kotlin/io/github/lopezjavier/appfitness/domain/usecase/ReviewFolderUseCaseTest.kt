package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.model.ExerciseAlternative
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.MyTraining
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
import io.github.lopezjavier.appfitness.domain.model.RoutineFolder
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import io.github.lopezjavier.appfitness.domain.repository.RoutineRepository
import io.github.lopezjavier.appfitness.domain.repository.TrainingPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewFolderUseCaseTest {
    private class Routines(val summaries: Result<List<RoutineSummary>>, val byId: Map<String, Routine>) :
        RoutineRepository {
        override suspend fun listActive() = summaries
        override suspend fun get(id: String) = Result.success(byId.getValue(id))
        override suspend fun save(routine: Routine): Result<String> = TODO()
        override suspend fun duplicate(id: String): Result<String> = TODO()
        override suspend fun updateFromSession(sessionId: String): Result<String> = TODO()
        override suspend fun createFromSession(sessionId: String, name: String): Result<String> = TODO()
        override suspend fun delete(id: String): Result<Unit> = TODO()
        override suspend fun listFolders(): Result<List<RoutineFolder>> = TODO()
        override suspend fun createFolder(name: String): Result<String> = TODO()
        override suspend fun renameFolder(id: String, name: String): Result<Unit> = TODO()
        override suspend fun deleteFolder(id: String): Result<Int> = TODO()
        override suspend fun moveToFolder(routineId: String, folderId: String?): Result<Unit> = TODO()
        override suspend fun saveOrder(folderId: String?, orderedIds: List<String>): Result<Unit> = TODO()
        override suspend fun setNext(routineId: String?): Result<Unit> = TODO()
    }

    private class Exercises(val all: Result<List<Exercise>>) : ExerciseRepository {
        override val changes: Flow<Unit> = emptyFlow()
        override suspend fun getAll() = all
        override suspend fun getById(id: String): Result<Exercise> = TODO()
        override suspend fun alternatives(exerciseId: String): Result<List<ExerciseAlternative>> = TODO()
        override suspend fun createCustom(
            name: String,
            muscleGroup: MuscleGroup,
            equipment: Equipment,
            metricType: ExerciseMetricType,
            perHand: Boolean,
            movementPattern: MovementPattern?,
        ): Result<Exercise> = TODO()
        override suspend fun updateCustom(
            id: String,
            name: String,
            muscleGroup: MuscleGroup,
            equipment: Equipment,
            perHand: Boolean,
            movementPattern: MovementPattern?,
        ): Result<Unit> = TODO()
        override suspend fun deleteCustom(id: String): Result<Unit> = TODO()
    }

    private class Prefs(val result: Result<TrainingPreferences>) : TrainingPreferencesRepository {
        override suspend fun get() = result
        override suspend fun saveMyTraining(training: MyTraining): Result<Unit> = TODO()
    }

    private fun row(id: String, group: MuscleGroup, pattern: MovementPattern? = null) = Exercise(
        id, id, null, group, emptyList(), Equipment.Barbell, ExerciseMetricType.WeightReps, false,
        movementPattern = pattern,
    )

    private fun routine(id: String, vararg planned: RoutineExercise) =
        Routine(id, "R$id", null, false, planned.toList())

    private fun planned(exerciseId: String, position: Int, sets: List<PlannedSet>) =
        RoutineExercise(exerciseId, position, null, sets, 90)

    private fun summary(id: String, folder: String?) = RoutineSummary(id, "R$id", 1, folder)

    private fun useCase(
        summaries: Result<List<RoutineSummary>>,
        routines: List<Routine>,
        exercises: Result<List<Exercise>>,
        prefs: Result<TrainingPreferences> = Result.success(TrainingPreferences()),
    ) = ReviewFolderUseCase(
        Routines(summaries, routines.associateBy { it.id!! }),
        Exercises(exercises),
        Prefs(prefs),
    )

    @Test
    fun reviewsOnlyTheFoldersRoutines_excludingWarmUpSets() = runTest {
        val sets = listOf(PlannedSet(null, 10, SetType.WarmUp), PlannedSet(null, 8), PlannedSet(null, 8))
        val useCase = useCase(
            Result.success(listOf(summary("1", "f"), summary("2", "other"))),
            listOf(routine("1", planned("bench", 0, sets)), routine("2", planned("bench", 0, sets))),
            Result.success(listOf(row("bench", MuscleGroup.Chest))),
        )

        val result = useCase("f").getOrThrow()

        assertEquals(1, result.routineCount)
        assertEquals(2.0, result.rows.filterIsInstance<GroupRow>().first { it.group == MuscleGroup.Chest }.sets)
    }

    @Test
    fun usesTheIntention_andTheRepRangeFallbackFromPlannedReps() = runTest {
        val squat = row("squat", MuscleGroup.Quads, MovementPattern.Squat)
        val sets = List(3) { PlannedSet(null, 12) }
        val useCase = useCase(
            Result.success(listOf(summary("1", "f"))),
            listOf(routine("1", planned("squat", 0, sets))),
            Result.success(listOf(squat)),
            Result.success(TrainingPreferences(intention = TrainingIntention.Strength)),
        )

        val result = useCase("f").getOrThrow()

        assertEquals(
            listOf(RepOffender("R1", "squat", 12, 12)),
            result.rows.filterIsInstance<StrengthRepsRow>().single().offenders,
        )
    }

    @Test
    fun missingIntention_orFailedPreferences_stillReviews() = runTest {
        val useCase = useCase(
            Result.success(listOf(summary("1", "f"))),
            listOf(routine("1")),
            Result.success(emptyList()),
            Result.failure(IllegalStateException("offline")),
        )

        val result = useCase("f").getOrThrow()

        assertTrue(result.rows.none { it is StrengthRepsRow })
    }

    @Test
    fun unknownExercise_isSkipped() = runTest {
        val useCase = useCase(
            Result.success(listOf(summary("1", "f"))),
            listOf(routine("1", planned("gone", 0, listOf(PlannedSet(null, 8))))),
            Result.success(emptyList()),
        )

        assertEquals(0, useCase("f").getOrThrow().liftingRoutines)
    }

    @Test
    fun loadFailure_isReturned() = runTest {
        val boom = IllegalStateException("boom")

        assertEquals(
            boom,
            useCase(Result.failure(boom), emptyList(), Result.success(emptyList()))("f").exceptionOrNull(),
        )
        assertEquals(
            boom,
            useCase(Result.success(emptyList()), emptyList(), Result.failure(boom))("f").exceptionOrNull(),
        )
    }
}
