package io.github.lopezjavier.appfitness.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row of `exercise_favorites` (RLS limits the read to the caller). */
@Serializable
internal data class FavoriteExerciseDto(@SerialName("exercise_id") val exerciseId: String)

/** Insert body of `exercise_favorites` (the user comes from the column default). */
@Serializable
internal data class FavoriteExerciseInsertDto(@SerialName("exercise_id") val exerciseId: String)
