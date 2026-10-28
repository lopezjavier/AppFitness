package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.Discomfort
import io.github.lopezjavier.appfitness.domain.repository.DiscomfortRepository
import io.github.lopezjavier.appfitness.domain.rules.StrongPainTerms

/** Logs a discomfort: trims the note (empty = none), rejects more than [MAX_NOTE] characters, derives `severe`. */
class LogDiscomfort(private val repository: DiscomfortRepository) {
    suspend operator fun invoke(exerciseId: String, note: String?): Result<Discomfort> {
        val clean = note?.trim()?.takeIf { it.isNotEmpty() }
        if (clean != null && clean.length > MAX_NOTE) return Result.failure(AppException(AppError.Validation))
        return repository.log(exerciseId, clean, StrongPainTerms.matches(clean))
    }

    companion object {
        const val MAX_NOTE = 200
    }
}
