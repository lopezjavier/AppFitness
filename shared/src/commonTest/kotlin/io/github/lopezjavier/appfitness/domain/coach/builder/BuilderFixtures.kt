package io.github.lopezjavier.appfitness.domain.coach.builder

import io.github.lopezjavier.appfitness.domain.model.CatalogExercise
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention

internal fun exercise(
    id: String,
    group: MuscleGroup,
    pattern: MovementPattern? = MovementPattern.Isolation,
    equipment: Equipment = Equipment.Machine,
    secondary: List<MuscleGroup> = emptyList(),
    name: String = id,
    custom: Boolean = false,
    metric: ExerciseMetricType = ExerciseMetricType.WeightReps,
    countsAsStrength: Boolean = true,
) = CatalogExercise(
    Exercise(
        id = id,
        key = if (custom) null else id,
        name = if (custom) name else null,
        muscleGroup = group,
        secondaryMuscles = secondary,
        equipment = equipment,
        metricType = metric,
        isCustom = custom,
        movementPattern = pattern,
        countsAsStrength = countsAsStrength,
    ),
    name,
    hasMedia = false,
)

private val mainPattern = mapOf(
    MuscleGroup.Chest to MovementPattern.HorizontalPush,
    MuscleGroup.Back to MovementPattern.HorizontalPull,
    MuscleGroup.Shoulders to MovementPattern.VerticalPush,
    MuscleGroup.Quads to MovementPattern.Squat,
    MuscleGroup.Hamstrings to MovementPattern.Hinge,
    MuscleGroup.Glutes to MovementPattern.Hinge,
)

/** Per H1 group: one main-lift exercise and one isolation exercise, ids `<group>-main` / `<group>-iso`. */
internal fun catalogue(): List<CatalogExercise> = SplitCatalog.ALL_GROUPS.flatMap { g ->
    val key = g.name.lowercase()
    listOf(
        exercise("$key-main", g, mainPattern.getValue(g), Equipment.Barbell),
        exercise("$key-iso", g, MovementPattern.Isolation, Equipment.Machine),
        exercise("$key-iso2", g, MovementPattern.Isolation, Equipment.Cable),
        exercise("$key-iso3", g, MovementPattern.Isolation, Equipment.Dumbbell),
        exercise("$key-iso4", g, MovementPattern.Isolation, Equipment.Dumbbell, name = "z-$key"),
        exercise("$key-iso5", g, MovementPattern.Isolation, Equipment.Dumbbell, name = "a-$key"),
    )
}

internal fun context(
    equipment: Set<Equipment> = emptySet(),
    history: Map<String, ExerciseHistory> = emptyMap(),
    favourites: Set<String> = emptySet(),
) = PickerContext(equipment, history, favourites)

internal fun startState(
    types: List<RoutineType>,
    intention: TrainingIntention? = TrainingIntention.Muscle,
    minutes: Int = 600,
    cardioTarget: Int = 150,
    cardioId: String? = "cardio-1",
): BuilderState = (
    BuilderState.start(
        types,
        intention,
        minutes,
        listOf(Equipment.Barbell),
        cardioTarget,
        cardioId,
    ) as BuilderStart.Ready
    ).state

/** Fills every required slot with the first eligible exercise of its group that the routine does not hold yet. */
internal fun fillAll(state: BuilderState, catalogue: List<CatalogExercise> = catalogue()): BuilderState {
    var s = state
    s.days.forEachIndexed { d, day ->
        day.slots.forEachIndexed { i, filled ->
            val used = s.days[d].slots.mapNotNull { it.pick?.exerciseId }.toSet()
            val entry = SlotEligibility.picker(filled.slot, catalogue, context(), used).first { !it.disabled }
            s = s.pick(d, i, Pick.of(entry.exercise.exercise, filled.slot, null))
        }
    }
    return s
}
