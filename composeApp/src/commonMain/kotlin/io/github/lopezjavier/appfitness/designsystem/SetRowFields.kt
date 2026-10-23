package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.Immutable
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_added_weight
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_added_weight_cd
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_assist_weight
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_assist_weight_cd
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_distance
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_distance_cd
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_floors
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_floors_cd
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_reps
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_reps_cd
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_steps
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_steps_cd
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_time
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_time_cd
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_weight
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_weight_cd
import io.github.lopezjavier.appfitness.composeapp.resources.set_row_added_weight_cd
import io.github.lopezjavier.appfitness.composeapp.resources.set_row_assist_weight_cd
import io.github.lopezjavier.appfitness.composeapp.resources.set_row_distance_cd
import io.github.lopezjavier.appfitness.composeapp.resources.set_row_floors_cd
import io.github.lopezjavier.appfitness.composeapp.resources.set_row_reps_cd
import io.github.lopezjavier.appfitness.composeapp.resources.set_row_steps_cd
import io.github.lopezjavier.appfitness.composeapp.resources.set_row_weight_cd
import org.jetbrains.compose.resources.StringResource

/**
 * What one input of a [SetRow] measures. [tagPart] is the suffix of the field's test tag (`<prefix>_<tagPart>`);
 * [rowDescription] is the spoken label of the input of one set (null for [Duration]: the stopwatch speaks itself).
 */
enum class SetFieldKind(
    val tagPart: String,
    val header: StringResource,
    val headerDescription: StringResource,
    val rowDescription: StringResource?,
) {
    Weight("weight", Res.string.set_header_weight, Res.string.set_header_weight_cd, Res.string.set_row_weight_cd),
    AddedWeight(
        "added_weight",
        Res.string.set_header_added_weight,
        Res.string.set_header_added_weight_cd,
        Res.string.set_row_added_weight_cd,
    ),
    AssistWeight(
        "assist_weight",
        Res.string.set_header_assist_weight,
        Res.string.set_header_assist_weight_cd,
        Res.string.set_row_assist_weight_cd,
    ),
    Reps("reps", Res.string.set_header_reps, Res.string.set_header_reps_cd, Res.string.set_row_reps_cd),
    Distance(
        "distance",
        Res.string.set_header_distance,
        Res.string.set_header_distance_cd,
        Res.string.set_row_distance_cd,
    ),
    Duration("duration", Res.string.set_header_time, Res.string.set_header_time_cd, null),
    Steps("steps", Res.string.set_header_steps, Res.string.set_header_steps_cd, Res.string.set_row_steps_cd),
    Floors("floors", Res.string.set_header_floors, Res.string.set_header_floors_cd, Res.string.set_row_floors_cd),
    ;

    /** The three kinds that read and write the row's weight value. */
    val isWeight: Boolean get() = this == Weight || this == AddedWeight || this == AssistWeight

    /** Decimals a typed value may have: weights and kilometres take decimals, the rest are whole numbers. */
    val maxDecimals: Int
        get() = when (this) {
            Weight, AddedWeight, AssistWeight -> SetRowDefaults.WEIGHT_MAX_DECIMALS
            Distance -> SetRowDefaults.DISTANCE_MAX_DECIMALS
            else -> 0
        }

    val maxIntegerDigits: Int
        get() = when (this) {
            Weight, AddedWeight, AssistWeight -> SetRowDefaults.WEIGHT_MAX_INTEGER_DIGITS
            Distance -> SetRowDefaults.DISTANCE_MAX_INTEGER_DIGITS
            Steps -> SetRowDefaults.STEPS_MAX_INTEGER_DIGITS
            else -> SetRowDefaults.REPS_MAX_INTEGER_DIGITS
        }
}

/** One input of a set row: its [kind] and an optional ghost [hint] (weight fields: what the load means). */
@Immutable
data class SetFieldSpec(val kind: SetFieldKind, val hint: String? = null) {
    companion object {
        /** Kilograms and repetitions: what a [SetRow] shows unless told otherwise. */
        val WeightReps: List<SetFieldSpec> = listOf(SetFieldSpec(SetFieldKind.Weight), SetFieldSpec(SetFieldKind.Reps))
    }
}

/**
 * Callbacks of the inputs a [SetRow] has beyond weight and reps: [onFieldChange] for distance, steps and floors,
 * [onStopwatchToggle] and [onDurationEdit] (seconds typed by hand) for the stopwatch.
 */
@Immutable
data class SetRowFieldActions(
    val onFieldChange: (SetFieldKind, String) -> Unit = { _, _ -> },
    val onStopwatchToggle: () -> Unit = {},
    val onDurationEdit: (Int) -> Unit = {},
)

/** Column weights of the input cells of a set row. */
internal object SetRowFieldLayout {
    /** The classic KG | REPS split keeps its own widths; every other spec flexes inside the same total. */
    fun weights(fields: List<SetFieldSpec>, withRpe: Boolean): List<Float> {
        val total = SetRowDefaults.kgWeight(withRpe) + SetRowDefaults.repsWeight(withRpe)
        val kinds = fields.map { it.kind }
        return when {
            kinds == listOf(SetFieldKind.Weight, SetFieldKind.Reps) ->
                listOf(SetRowDefaults.kgWeight(withRpe), SetRowDefaults.repsWeight(withRpe))

            kinds.size == 1 -> listOf(total)

            // The stopwatch needs room for its time and toggle: the other field keeps a fixed share.
            SetFieldKind.Duration in kinds -> kinds.map {
                if (it ==
                    SetFieldKind.Duration
                ) {
                    total - SECONDARY
                } else {
                    SECONDARY
                }
            }

            else -> kinds.map { total / kinds.size }
        }
    }

    private const val SECONDARY = 2f
}
