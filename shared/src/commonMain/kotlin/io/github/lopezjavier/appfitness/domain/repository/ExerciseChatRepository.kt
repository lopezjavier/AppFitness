package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.ExerciseChatContext

/** Asks the `exercise-chat` Edge Function about an exercise. A failure carries an `AppException`. */
interface ExerciseChatRepository {
    /** Each question stands alone: only [exercise] and [question] are sent. Nothing is stored. */
    suspend fun ask(exercise: ExerciseChatContext, question: String): Result<String>
}
