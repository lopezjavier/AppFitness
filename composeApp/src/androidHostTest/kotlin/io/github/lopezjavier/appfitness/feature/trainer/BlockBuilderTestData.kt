package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.coach.builder.BuilderStart
import io.github.lopezjavier.appfitness.domain.coach.builder.BuilderState
import io.github.lopezjavier.appfitness.domain.coach.builder.Pick
import io.github.lopezjavier.appfitness.domain.coach.builder.PickerEntry
import io.github.lopezjavier.appfitness.domain.coach.builder.RoutineType
import io.github.lopezjavier.appfitness.domain.coach.builder.SplitCatalog
import io.github.lopezjavier.appfitness.domain.model.CatalogExercise
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow

/** Fixtures of the block builder host tests: a small catalogue and states built through the real core. */
internal object BlockBuilderTestData {
    private fun catalog(id: String, name: String, group: MuscleGroup, pattern: MovementPattern?) =
        CatalogExercise(exerciseRow(id, id, group, Equipment.Barbell, pattern = pattern), name, hasMedia = false)

    val bench = catalog("bench", "Press banca", MuscleGroup.Chest, MovementPattern.HorizontalPush)
    val fly = catalog("fly", "Aperturas", MuscleGroup.Chest, MovementPattern.Isolation)
    val bike = CatalogExercise(
        exerciseRow("bike", "bike", MuscleGroup.FullBody, metric = ExerciseMetricType.Duration),
        "Bicicleta estática",
        hasMedia = false,
    )
    val rower = CatalogExercise(
        exerciseRow("rower", "rower", MuscleGroup.FullBody, metric = ExerciseMetricType.Duration),
        "Remo en máquina",
        hasMedia = false,
    )
    val all = listOf(bench, fly, bike, rower)

    fun start(
        types: List<RoutineType> = listOf(RoutineType.Upper, RoutineType.Lower),
        intention: TrainingIntention? = TrainingIntention.Muscle,
        cardioTarget: Int = 150,
    ): BuilderState = (
        BuilderState.start(types, intention, 180, listOf(Equipment.Barbell), cardioTarget, "bike") as BuilderStart.Ready
        ).state

    /** The state with every required slot of every day picked with [bench]. */
    fun filled(state: BuilderState): BuilderState = state.days.indices.fold(state) { acc, day ->
        acc.days[day].slots.indices.fold(acc) { inner, slot ->
            inner.pick(day, slot, Pick(bench.id, bench.exercise.muscleGroup, bench.exercise.movementPattern, null))
        }
    }

    fun builderUi(
        state: BuilderState = start(),
        selectedDay: Int = 0,
        openSlot: SlotRef? = null,
        pickerEntries: List<PickerEntry> = emptyList(),
        saving: Boolean = false,
        saveError: BuilderSaveError? = null,
        discardDialog: Boolean = false,
        favouriteIds: Set<String> = emptySet(),
        validPatterns: List<MovementPattern> = emptyList(),
        createFailed: Boolean = false,
    ) = BlockBuilderUiState.Builder(
        state = state,
        selectedDay = selectedDay,
        openSlot = openSlot,
        pickerEntries = pickerEntries,
        cardioOptions = listOf(bike, rower),
        cardioExercise = bike,
        exercises = all.associateBy { it.id },
        saving = saving,
        saveError = saveError,
        discardDialog = discardDialog,
        favouriteIds = favouriteIds,
        validPatterns = validPatterns,
        createFailed = createFailed,
    )

    fun splits(days: Int = 4) = BlockBuilderUiState.Splits(days, SplitCatalog.splitsFor(days), 0)
}
