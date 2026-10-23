package io.github.lopezjavier.appfitness.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row of `public.exercise_options` as read (only the exercises with the flag on are selected). */
@Serializable
internal data class ExerciseOptionsDto(@SerialName("exercise_id") val exerciseId: String) {
    companion object {
        const val COLUMNS = "exercise_id"
    }
}

/** Arguments of the `set_exercise_double_dumbbell` RPC. */
@Serializable
internal data class SetDoubleDumbbellParams(
    @SerialName("p_exercise_id") val exerciseId: String,
    @SerialName("p_enabled") val enabled: Boolean,
)
