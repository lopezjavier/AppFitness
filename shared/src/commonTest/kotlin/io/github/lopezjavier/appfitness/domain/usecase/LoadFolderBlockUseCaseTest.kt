package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.model.ExerciseAlternative
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.MyTraining
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.RepRange
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoadFolderBlockUseCaseTest {
    private class Routines(
        val summaries: Result<List<RoutineSummary>>,
        val byId: Map<String, Routine>,
        val folders: List<RoutineFolder>,
    ) : RoutineRepository {
        var writes = 0
        override suspend fun listActive() = summaries
        override suspend fun get(id: String) = Result.success(byId.getValue(id))
        override suspend fun listFolders() = Result.success(folders)
        override suspend fun save(routine: Routine): Result<String> = TODO().also { writes++ }
        override suspend fun duplicate(id: String): Result<String> = TODO().also { writes++ }
        override suspend fun updateFromSession(sessionId: String): Result<String> = TODO().also { writes++ }
        override suspend fun createFromSession(sessionId: String, name: String): Result<String> =
            TODO().also { writes++ }
        override suspend fun delete(id: String): Result<Unit> = TODO().also { writes++ }
        override suspend fun createFolder(name: String): Result<String> = TODO().also { writes++ }
        override suspend fun renameFolder(id: String, name: String): Result<Unit> = TODO().also { writes++ }
        override suspend fun deleteFolder(id: String): Result<Int> = TODO().also { writes++ }
        override suspend fun moveToFolder(routineId: String, folderId: String?): Result<Unit> = TODO().also { writes++ }
        override suspend fun saveOrder(folderId: String?, orderedIds: List<String>): Result<Unit> =
            TODO().also { writes++ }
        override suspend fun setNext(routineId: String?): Result<Unit> = TODO().also { writes++ }
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

    private val prefs = TrainingPreferences(
        intention = TrainingIntention.Strength,
        equipment = setOf(Equipment.Barbell),
        daysPerWeek = 2,
        sessionMinutes = 60,
    )

    private fun row(id: String, group: MuscleGroup, pattern: MovementPattern? = null) = Exercise(
        id, id, null, group, emptyList(), Equipment.Barbell, ExerciseMetricType.WeightReps, false,
        movementPattern = pattern,
    )

    private fun planned(id: String, sets: List<PlannedSet>, range: RepRange? = null, rpe: Double? = null) =
        RoutineExercise(id, 0, null, sets, 150, rpe, repRange = range)

    private fun routine(id: String, vararg ex: RoutineExercise) = Routine(id, "R$id", null, false, ex.toList())

    private fun useCase(
        routines: Routines,
        exercises: Result<List<Exercise>> = Result.success(
            listOf(row("squat", MuscleGroup.Quads, MovementPattern.Squat)),
        ),
        preferences: Result<TrainingPreferences> = Result.success(prefs),
    ) = LoadFolderBlockUseCase(routines, Exercises(exercises), Prefs(preferences))

    private fun repo(vararg routines: Routine, folders: List<RoutineFolder> = listOf(RoutineFolder("f", "Fuerza"))) =
        Routines(
            Result.success(
                routines.map {
                    RoutineSummary(it.id!!, it.name, 1, "f")
                } + RoutineSummary("x", "Other", 1, "g"),
            ),
            routines.associateBy { it.id!! } + ("x" to routine("x")),
            folders,
        )

    @Test
    fun mapsTheFolder_dropsWarmUps_andKeepsTheFoldersValues() = runTest {
        val sets = listOf(PlannedSet(40.0, 10, SetType.WarmUp), PlannedSet(100.0, 5), PlannedSet(100.0, 5))
        val source = useCase(repo(routine("1", planned("squat", sets, RepRange(4, 6), 8.0)))).invoke("f").getOrThrow()

        val e = source.draft.routines.single().exercises.single()
        assertEquals("Fuerza", source.folderName)
        assertEquals(60, source.sessionMinutes)
        assertEquals(2, source.draft.daysPerWeek)
        assertEquals(TrainingIntention.Strength, source.draft.intention)
        assertEquals(setOf(Equipment.Barbell), source.draft.equipment)
        assertEquals(2, e.sets)
        assertEquals(4, e.repMin)
        assertEquals(6, e.repMax)
        assertEquals(8.0, e.rpe)
        assertEquals(150, e.restSeconds)
        assertEquals(100.0, e.plannedWeightKg)
    }

    @Test
    fun withoutARepRange_theRepsFollowThePlannedSets() = runTest {
        val sets = List(3) { PlannedSet(null, 12) }
        val e = useCase(repo(routine("1", planned("squat", sets)))).invoke("f").getOrThrow()
            .draft.routines.single().exercises.single()

        assertEquals(12, e.repMin)
        assertEquals(12, e.repMax)
        assertNull(e.plannedWeightKg)
    }

    @Test
    fun unknownExercise_isSkipped() = runTest {
        val source = useCase(repo(routine("1", planned("gone", listOf(PlannedSet(null, 8))))))("f").getOrThrow()
        assertTrue(source.draft.routines.single().exercises.isEmpty())
    }

    @Test
    fun existingFolderNames_listEveryFolder() = runTest {
        val folders = listOf(RoutineFolder("f", "Fuerza"), RoutineFolder("g", "Fuerza · bloque"))
        val source = useCase(repo(routine("1"), folders = folders))("f").getOrThrow()
        assertEquals(setOf("Fuerza", "Fuerza · bloque"), source.existingFolderNames)
    }

    @Test
    fun missingDaysOrMinutes_failsWithIllegalState() = runTest {
        val noDays = useCase(repo(routine("1")), preferences = Result.success(prefs.copy(daysPerWeek = null)))("f")
        val noMinutes =
            useCase(repo(routine("1")), preferences = Result.success(prefs.copy(sessionMinutes = null)))("f")
        assertTrue(noDays.exceptionOrNull() is IllegalStateException)
        assertTrue(noMinutes.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun failedReads_propagate_andNothingIsWritten() = runTest {
        val boom = IllegalStateException("boom")
        val failing = Routines(Result.failure(boom), emptyMap(), emptyList())
        assertEquals(boom, useCase(failing)("f").exceptionOrNull())
        val ok = repo(routine("1"))
        assertEquals(boom, useCase(ok, exercises = Result.failure(boom))("f").exceptionOrNull())
        assertEquals(boom, useCase(ok, preferences = Result.failure(boom))("f").exceptionOrNull())
        assertEquals(0, ok.writes)
    }
}
