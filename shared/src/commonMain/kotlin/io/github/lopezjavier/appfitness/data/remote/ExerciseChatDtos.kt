package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.ExerciseChatContext
import kotlinx.serialization.Serializable

/** Body of `POST exercise-chat`; the server rejects unknown keys, so these are exactly the allowed fields. */
@Serializable
internal data class ExerciseChatRequestDto(val exercise: ExerciseChatExerciseDto, val question: String) {
    companion object {
        fun from(context: ExerciseChatContext, question: String) = ExerciseChatRequestDto(
            ExerciseChatExerciseDto(context.name, context.muscles, context.instructions),
            question,
        )
    }
}

@Serializable
internal data class ExerciseChatExerciseDto(val name: String, val muscles: List<String>, val instructions: String)

@Serializable
internal data class ExerciseChatResponseDto(val answer: String)
