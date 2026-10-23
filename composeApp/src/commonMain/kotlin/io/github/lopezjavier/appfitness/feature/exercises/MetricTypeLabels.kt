package io.github.lopezjavier.appfitness.feature.exercises

import androidx.compose.runtime.Composable
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.metric_type_assisted
import io.github.lopezjavier.appfitness.composeapp.resources.metric_type_assisted_hint
import io.github.lopezjavier.appfitness.composeapp.resources.metric_type_bodyweight
import io.github.lopezjavier.appfitness.composeapp.resources.metric_type_bodyweight_hint
import io.github.lopezjavier.appfitness.composeapp.resources.metric_type_distance_duration
import io.github.lopezjavier.appfitness.composeapp.resources.metric_type_distance_duration_hint
import io.github.lopezjavier.appfitness.composeapp.resources.metric_type_duration
import io.github.lopezjavier.appfitness.composeapp.resources.metric_type_duration_hint
import io.github.lopezjavier.appfitness.composeapp.resources.metric_type_floors_duration
import io.github.lopezjavier.appfitness.composeapp.resources.metric_type_floors_duration_hint
import io.github.lopezjavier.appfitness.composeapp.resources.metric_type_reps_only
import io.github.lopezjavier.appfitness.composeapp.resources.metric_type_reps_only_hint
import io.github.lopezjavier.appfitness.composeapp.resources.metric_type_steps_duration
import io.github.lopezjavier.appfitness.composeapp.resources.metric_type_steps_duration_hint
import io.github.lopezjavier.appfitness.composeapp.resources.metric_type_weight_duration
import io.github.lopezjavier.appfitness.composeapp.resources.metric_type_weight_duration_hint
import io.github.lopezjavier.appfitness.composeapp.resources.metric_type_weight_reps
import io.github.lopezjavier.appfitness.composeapp.resources.metric_type_weight_reps_hint
import io.github.lopezjavier.appfitness.composeapp.resources.metric_type_weighted
import io.github.lopezjavier.appfitness.composeapp.resources.metric_type_weighted_hint
import io.github.lopezjavier.appfitness.composeapp.resources.weight_hint_added
import io.github.lopezjavier.appfitness.composeapp.resources.weight_hint_assisted
import io.github.lopezjavier.appfitness.composeapp.resources.weight_hint_band
import io.github.lopezjavier.appfitness.composeapp.resources.weight_hint_barbell
import io.github.lopezjavier.appfitness.composeapp.resources.weight_hint_bodyweight
import io.github.lopezjavier.appfitness.composeapp.resources.weight_hint_cable
import io.github.lopezjavier.appfitness.composeapp.resources.weight_hint_dumbbell
import io.github.lopezjavier.appfitness.composeapp.resources.weight_hint_kettlebell
import io.github.lopezjavier.appfitness.composeapp.resources.weight_hint_machine
import io.github.lopezjavier.appfitness.composeapp.resources.weight_hint_other
import io.github.lopezjavier.appfitness.designsystem.SetFieldKind
import io.github.lopezjavier.appfitness.designsystem.SetFieldSpec
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The Spanish name of this metric type: the one wording of the create form and every screen that names it. */
internal val ExerciseMetricType.label: StringResource
    get() = when (this) {
        ExerciseMetricType.WeightReps -> Res.string.metric_type_weight_reps
        ExerciseMetricType.RepsOnly -> Res.string.metric_type_reps_only
        ExerciseMetricType.Duration -> Res.string.metric_type_duration
        ExerciseMetricType.WeightDuration -> Res.string.metric_type_weight_duration
        ExerciseMetricType.DistanceDuration -> Res.string.metric_type_distance_duration
        ExerciseMetricType.Bodyweight -> Res.string.metric_type_bodyweight
        ExerciseMetricType.Assisted -> Res.string.metric_type_assisted
        ExerciseMetricType.Weighted -> Res.string.metric_type_weighted
        ExerciseMetricType.StepsDuration -> Res.string.metric_type_steps_duration
        ExerciseMetricType.FloorsDuration -> Res.string.metric_type_floors_duration
    }

/** One line that explains what the metric type logs. */
internal val ExerciseMetricType.description: StringResource
    get() = when (this) {
        ExerciseMetricType.WeightReps -> Res.string.metric_type_weight_reps_hint
        ExerciseMetricType.RepsOnly -> Res.string.metric_type_reps_only_hint
        ExerciseMetricType.Duration -> Res.string.metric_type_duration_hint
        ExerciseMetricType.WeightDuration -> Res.string.metric_type_weight_duration_hint
        ExerciseMetricType.DistanceDuration -> Res.string.metric_type_distance_duration_hint
        ExerciseMetricType.Bodyweight -> Res.string.metric_type_bodyweight_hint
        ExerciseMetricType.Assisted -> Res.string.metric_type_assisted_hint
        ExerciseMetricType.Weighted -> Res.string.metric_type_weighted_hint
        ExerciseMetricType.StepsDuration -> Res.string.metric_type_steps_duration_hint
        ExerciseMetricType.FloorsDuration -> Res.string.metric_type_floors_duration_hint
    }

/** What the weight of an exercise on this equipment means ("barra + discos" for a barbell). */
internal val Equipment.weightHint: StringResource
    get() = when (this) {
        Equipment.Barbell -> Res.string.weight_hint_barbell
        Equipment.Dumbbell -> Res.string.weight_hint_dumbbell
        Equipment.Machine -> Res.string.weight_hint_machine
        Equipment.Cable -> Res.string.weight_hint_cable
        Equipment.Bodyweight -> Res.string.weight_hint_bodyweight
        Equipment.Kettlebell -> Res.string.weight_hint_kettlebell
        Equipment.Band -> Res.string.weight_hint_band
        Equipment.Other -> Res.string.weight_hint_other
    }

/**
 * The input fields of a set of [metricType]: KG | REPS, only REPS, TIEMPO (stopwatch), KG | TIEMPO, KM | TIEMPO,
 * +KG | REPS, -KG | REPS, PASOS | TIEMPO or PISOS | TIEMPO. The weight field hints what the load is: the equipment's
 * meaning, "peso de ayuda" for assisted and "carga extra" for bodyweight and weighted.
 */
@Composable
internal fun setFieldSpecs(metricType: ExerciseMetricType, equipment: Equipment?): List<SetFieldSpec> {
    val equipmentHint = equipment?.let { stringResource(it.weightHint) }
    val assistHint = stringResource(Res.string.weight_hint_assisted)
    val addedHint = stringResource(Res.string.weight_hint_added)
    return when (metricType) {
        ExerciseMetricType.WeightReps -> listOf(SetFieldSpec(SetFieldKind.Weight, equipmentHint), reps())

        ExerciseMetricType.RepsOnly -> listOf(reps())

        ExerciseMetricType.Duration -> listOf(time())

        ExerciseMetricType.WeightDuration -> listOf(SetFieldSpec(SetFieldKind.Weight, equipmentHint), time())

        ExerciseMetricType.DistanceDuration -> listOf(SetFieldSpec(SetFieldKind.Distance), time())

        ExerciseMetricType.Bodyweight,
        ExerciseMetricType.Weighted,
        -> listOf(SetFieldSpec(SetFieldKind.AddedWeight, addedHint), reps())

        ExerciseMetricType.Assisted -> listOf(SetFieldSpec(SetFieldKind.AssistWeight, assistHint), reps())

        ExerciseMetricType.StepsDuration -> listOf(SetFieldSpec(SetFieldKind.Steps), time())

        ExerciseMetricType.FloorsDuration -> listOf(SetFieldSpec(SetFieldKind.Floors), time())
    }
}

private fun reps() = SetFieldSpec(SetFieldKind.Reps)

private fun time() = SetFieldSpec(SetFieldKind.Duration)
