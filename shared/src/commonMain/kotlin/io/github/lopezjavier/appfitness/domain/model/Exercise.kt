package io.github.lopezjavier.appfitness.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** DB values of `public.muscle_group`. */
@Serializable
enum class MuscleGroup {
    @SerialName("chest")
    Chest,

    @SerialName("back")
    Back,

    @SerialName("shoulders")
    Shoulders,

    @SerialName("biceps")
    Biceps,

    @SerialName("triceps")
    Triceps,

    @SerialName("forearms")
    Forearms,

    @SerialName("quads")
    Quads,

    @SerialName("hamstrings")
    Hamstrings,

    @SerialName("glutes")
    Glutes,

    @SerialName("calves")
    Calves,

    @SerialName("core")
    Core,

    @SerialName("full_body")
    FullBody,
}

/** DB values of `public.equipment`. */
@Serializable
enum class Equipment {
    @SerialName("barbell")
    Barbell,

    @SerialName("dumbbell")
    Dumbbell,

    @SerialName("machine")
    Machine,

    @SerialName("cable")
    Cable,

    @SerialName("bodyweight")
    Bodyweight,

    @SerialName("kettlebell")
    Kettlebell,

    @SerialName("band")
    Band,

    @SerialName("other")
    Other,
}

/** DB values of `public.exercise_metric_type`; each one maps to a [SetEntry] variant (see `SetEntryMapping.kt`). */
@Serializable
enum class ExerciseMetricType {
    @SerialName("weight_reps")
    WeightReps,

    @SerialName("reps_only")
    RepsOnly,

    @SerialName("duration")
    Duration,

    @SerialName("weight_duration")
    WeightDuration,

    @SerialName("distance_duration")
    DistanceDuration,

    @SerialName("bodyweight")
    Bodyweight,

    @SerialName("assisted")
    Assisted,

    @SerialName("weighted")
    Weighted,

    @SerialName("steps_duration")
    StepsDuration,

    @SerialName("floors_duration")
    FloorsDuration,
}

/**
 * Row of `public.exercises`. Catalog rows have a [key] and no [name] (the Spanish name lives in the client library,
 * ADR-38); custom rows have a [name] and no key.
 */
data class Exercise(
    val id: String,
    val key: String?,
    val name: String?,
    val muscleGroup: MuscleGroup,
    val secondaryMuscles: List<MuscleGroup>,
    val equipment: Equipment,
    val metricType: ExerciseMetricType,
    val isCustom: Boolean,
    /** The logged weight is per hand/side: volume counts it twice (1RM is never doubled, 04 §4.7). */
    val perHand: Boolean = false,
)

/** Client library entry of one catalog exercise (Spanish texts from the RepDB bundle). */
data class ExerciseInfo(
    val key: String,
    val name: String,
    val description: String,
    val instructions: List<String>,
    val tips: List<String>,
    /** False for the exercises that ship a single image (start pose only): no peak image, no crossfade. */
    val hasPeak: Boolean = true,
)

/** What the UI lists: an [exercise] with the [name] to show (never blank) and whether the library has media for it. */
data class CatalogExercise(val exercise: Exercise, val name: String, val hasMedia: Boolean) {
    val id: String get() = exercise.id
}

/** One exercise for the detail screen: [name] is blank when neither the library nor the row has one. */
data class ExerciseDetail(val exercise: Exercise, val name: String, val info: ExerciseInfo?)
