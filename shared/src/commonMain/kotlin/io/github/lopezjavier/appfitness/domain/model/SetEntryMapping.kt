package io.github.lopezjavier.appfitness.domain.model

/** The `workout_sets` measurement columns a [SetEntry] can write. */
enum class SetColumn { WeightKg, Reps, DurationS, DistanceM, Steps, Floors }

/**
 * The `workout_sets` measurement columns of one set; null means "not applicable to the metric type". `weight_kg` and
 * `reps` are NOT NULL in the database, so the data layer writes 0 for them when null.
 */
data class SetColumns(
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationS: Int? = null,
    val distanceM: Double? = null,
    val steps: Int? = null,
    val floors: Int? = null,
) {
    /** The columns that carry a value. */
    fun written(): Set<SetColumn> = buildSet {
        if (weightKg != null) add(SetColumn.WeightKg)
        if (reps != null) add(SetColumn.Reps)
        if (durationS != null) add(SetColumn.DurationS)
        if (distanceM != null) add(SetColumn.DistanceM)
        if (steps != null) add(SetColumn.Steps)
        if (floors != null) add(SetColumn.Floors)
    }

    /** Builds the entry of [metricType]; missing applicable columns count as 0. */
    @Suppress("CyclomaticComplexMethod") // A flat dispatch over the metric types.
    fun toEntry(metricType: ExerciseMetricType): SetEntry {
        val weight = weightKg ?: 0.0
        val repCount = reps ?: 0
        val seconds = durationS ?: 0
        return when (metricType) {
            ExerciseMetricType.WeightReps -> SetEntry.WeightReps(weight, repCount)
            ExerciseMetricType.RepsOnly -> SetEntry.RepsOnly(repCount)
            ExerciseMetricType.Duration -> SetEntry.Duration(seconds)
            ExerciseMetricType.WeightDuration -> SetEntry.WeightDuration(weight, seconds)
            ExerciseMetricType.DistanceDuration -> SetEntry.DistanceDuration(distanceM ?: 0.0, seconds)
            ExerciseMetricType.Bodyweight -> SetEntry.Bodyweight(weight, repCount)
            ExerciseMetricType.Assisted -> SetEntry.Assisted(weight, repCount)
            ExerciseMetricType.Weighted -> SetEntry.Weighted(weight, repCount)
            ExerciseMetricType.StepsDuration -> SetEntry.StepsDuration(steps ?: 0, seconds)
            ExerciseMetricType.FloorsDuration -> SetEntry.FloorsDuration(floors ?: 0, seconds)
        }
    }
}

/** Columns written for this metric type; every other measurement column stays null. */
val ExerciseMetricType.columns: Set<SetColumn>
    get() = when (this) {
        ExerciseMetricType.WeightReps,
        ExerciseMetricType.Bodyweight,
        ExerciseMetricType.Assisted,
        ExerciseMetricType.Weighted,
        -> setOf(SetColumn.WeightKg, SetColumn.Reps)

        ExerciseMetricType.RepsOnly -> setOf(SetColumn.Reps)

        ExerciseMetricType.Duration -> setOf(SetColumn.DurationS)

        ExerciseMetricType.WeightDuration -> setOf(SetColumn.WeightKg, SetColumn.DurationS)

        ExerciseMetricType.DistanceDuration -> setOf(SetColumn.DistanceM, SetColumn.DurationS)

        ExerciseMetricType.StepsDuration -> setOf(SetColumn.Steps, SetColumn.DurationS)

        ExerciseMetricType.FloorsDuration -> setOf(SetColumn.Floors, SetColumn.DurationS)
    }

/** The metric type this entry belongs to. */
val SetEntry.metricType: ExerciseMetricType
    get() = when (this) {
        is SetEntry.WeightReps -> ExerciseMetricType.WeightReps
        is SetEntry.RepsOnly -> ExerciseMetricType.RepsOnly
        is SetEntry.Duration -> ExerciseMetricType.Duration
        is SetEntry.WeightDuration -> ExerciseMetricType.WeightDuration
        is SetEntry.DistanceDuration -> ExerciseMetricType.DistanceDuration
        is SetEntry.Bodyweight -> ExerciseMetricType.Bodyweight
        is SetEntry.Assisted -> ExerciseMetricType.Assisted
        is SetEntry.Weighted -> ExerciseMetricType.Weighted
        is SetEntry.StepsDuration -> ExerciseMetricType.StepsDuration
        is SetEntry.FloorsDuration -> ExerciseMetricType.FloorsDuration
    }

/** The `workout_sets` columns this entry writes. */
fun SetEntry.columns(): SetColumns = when (this) {
    is SetEntry.WeightReps -> SetColumns(weightKg = weightKg, reps = reps)
    is SetEntry.RepsOnly -> SetColumns(reps = reps)
    is SetEntry.Duration -> SetColumns(durationS = durationS)
    is SetEntry.WeightDuration -> SetColumns(weightKg = weightKg, durationS = durationS)
    is SetEntry.DistanceDuration -> SetColumns(distanceM = distanceM, durationS = durationS)
    is SetEntry.Bodyweight -> SetColumns(weightKg = addedKg, reps = reps)
    is SetEntry.Assisted -> SetColumns(weightKg = assistKg, reps = reps)
    is SetEntry.Weighted -> SetColumns(weightKg = addedKg, reps = reps)
    is SetEntry.StepsDuration -> SetColumns(steps = steps, durationS = durationS)
    is SetEntry.FloorsDuration -> SetColumns(floors = floors, durationS = durationS)
}

/** An all-zero entry of [metricType]. */
fun SetEntry.Companion.empty(metricType: ExerciseMetricType): SetEntry = SetColumns().toEntry(metricType)
