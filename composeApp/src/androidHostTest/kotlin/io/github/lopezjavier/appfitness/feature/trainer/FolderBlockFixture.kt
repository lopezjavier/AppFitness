package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.coach.builder.BuilderInputs
import io.github.lopezjavier.appfitness.domain.coach.builder.SplitCatalog
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.RepRange
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
import io.github.lopezjavier.appfitness.domain.model.RoutineFolder
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import io.github.lopezjavier.appfitness.domain.usecase.BuildBlockUseCase
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.domain.usecase.LoadFolderBlockUseCase
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.FakeFavoriteExercisesRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock

/**
 * A folder "Fuerza" of two routines with every group once per routine at 5 sets (100 %), over the fakes the host tests
 * of "Desde una carpeta" share. The options turn it into the folders of the Gherkin scenarios.
 */
internal class FolderBlockFixture {
    val blocks = FakeCoachBlockRepository()
    val routines = FakeRoutineRepository()
    private val prefs = FakeTrainingPreferencesRepository()
    private val groups = SplitCatalog.ALL_GROUPS

    private val mainPattern = mapOf(
        MuscleGroup.Chest to MovementPattern.HorizontalPush,
        MuscleGroup.Back to MovementPattern.HorizontalPull,
        MuscleGroup.Shoulders to MovementPattern.VerticalPush,
        MuscleGroup.Quads to MovementPattern.Squat,
        MuscleGroup.Hamstrings to MovementPattern.Hinge,
        MuscleGroup.Glutes to MovementPattern.Hinge,
    )

    private fun rows(): List<Exercise> = groups.flatMap { g ->
        val key = g.name.lowercase()
        listOf(exerciseRow("$key-main", "$key-main", g, Equipment.Barbell, pattern = mainPattern.getValue(g))) +
            (1..4).map {
                exerciseRow("$key-iso$it", "$key-iso$it", g, Equipment.Machine, pattern = MovementPattern.Isolation)
            }
    } + listOf(
        exerciseRow("stretch", "stretch", MuscleGroup.Back, Equipment.Machine, pattern = MovementPattern.Isolation)
            .copy(countsAsStrength = false),
        exerciseRow(
            "bike",
            "bike",
            MuscleGroup.FullBody,
            metric = ExerciseMetricType.Duration,
            pattern = MovementPattern.Cardio,
        ),
    )

    private val exerciseRepository = FakeExerciseRepository(rows())
    private val library = FakeExerciseLibrary(
        rows().associate { it.key!! to exerciseInfo(it.key!!, "Nombre ${it.key}") },
    )

    val favourites = FakeFavoriteExercisesRepository()

    init {
        blocks.builderInputs = Result.success(BuilderInputs(emptyMap(), listOf("bike"), "bike"))
    }

    private fun planned(id: String, sets: Int = 5, range: RepRange? = RepRange(8, 12)) = RoutineExercise(
        exerciseId = id,
        position = 0,
        supersetGroup = null,
        plannedSets = List(sets) { PlannedSet(null, 10) },
        restSeconds = 120,
        targetRpe = 8.0,
        repRange = range,
    )

    private fun routine(id: String, ex: List<RoutineExercise>) =
        Routine(id, "Rutina $id", null, false, ex.mapIndexed { i, e -> e.copy(position = i) })

    /** Index of the Chest exercise inside a routine built by [folder]. */
    val chestIndex: Int get() = groups.indexOf(MuscleGroup.Chest)

    /**
     * Builds the folder. [skip] drops one group from both routines, [days] is "Días por semana", [routineCount] the
     * lifting routines, [mainChest] puts a "chest-main" at 8-12 reps in routine 1, [overSets] gives the first routine's
     * "chest-iso1" 11 sets, [stretchRoutine] adds a routine with no lifting exercise and [existingBlock] a folder that
     * already uses the default name.
     */
    @Suppress("LongParameterList") // One flag per scenario.
    fun folder(
        skip: MuscleGroup? = null,
        intention: TrainingIntention = TrainingIntention.Muscle,
        days: Int = 2,
        routineCount: Int = 2,
        mainChest: Boolean = false,
        overSets: Boolean = false,
        stretchRoutine: Boolean = false,
        existingBlock: Boolean = false,
    ) {
        prefs.result = Result.success(
            TrainingPreferences(intention = intention, daysPerWeek = days, sessionMinutes = 180),
        )
        for (n in 1..routineCount) {
            val ex = groups.filter { it != skip }.map { g ->
                val key = g.name.lowercase()
                when {
                    mainChest && n == 1 && g == MuscleGroup.Chest -> planned("chest-main")
                    overSets && n == 1 && g == MuscleGroup.Chest -> planned("chest-iso1", sets = 11)
                    else -> planned("$key-iso$n")
                }
            }
            routines.routines += routine("$n", ex)
            routines.folderOf["$n"] = "f"
        }
        if (stretchRoutine) {
            routines.routines += routine("s", listOf(planned("stretch")))
            routines.folderOf["s"] = "f"
        }
        routines.folders += RoutineFolder("f", "Fuerza")
        if (existingBlock) routines.folders += RoutineFolder("g", "Fuerza · bloque")
    }

    fun newModel(saved: SavedStateHandle = SavedStateHandle()) = FolderBlockViewModel(
        planId = "plan-1",
        folderId = "f",
        buildBlock = BuildBlockUseCase(blocks, LoadFolderBlockUseCase(routines, exerciseRepository, prefs)),
        catalog = LoadExerciseCatalog(exerciseRepository, library),
        favourites = favourites,
        plans = FakeCoachPlanRepository(),
        clock = MutableClock(),
        savedState = saved,
    )
}
