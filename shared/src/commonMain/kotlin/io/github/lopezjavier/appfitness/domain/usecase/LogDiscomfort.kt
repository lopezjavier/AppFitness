package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.Discomfort
import io.github.lopezjavier.appfitness.domain.model.DiscomfortLevel
import io.github.lopezjavier.appfitness.domain.model.NoteText
import io.github.lopezjavier.appfitness.domain.repository.DiscomfortRepository

/**
 * Logs a discomfort at a [DiscomfortLevel]: trims the note (empty = none), rejects more than [MAX_NOTE] characters
 * and never interprets the text.
 */
class LogDiscomfort(private val repository: DiscomfortRepository) {
    suspend operator fun invoke(exerciseId: String, level: DiscomfortLevel, note: String?): Result<Discomfort> {
        val clean = NoteText.normalise(note)
        if (clean != null && !NoteText.isValid(clean)) return Result.failure(AppException(AppError.Validation))
        return repository.log(exerciseId, level, clean)
    }

    companion object {
        const val MAX_NOTE = NoteText.MAX
    }
}
