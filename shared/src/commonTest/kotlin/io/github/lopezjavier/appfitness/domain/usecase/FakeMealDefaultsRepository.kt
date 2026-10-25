package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.MealDefault
import io.github.lopezjavier.appfitness.domain.model.MealDefaultAssignment
import io.github.lopezjavier.appfitness.domain.repository.MealDefaultsRepository
import kotlinx.datetime.LocalDate

/**
 * In-memory [MealDefaultsRepository]: [save] replaces everything, [applyForDate] records the dates it was called
 * with and returns [applyResult]; each operation can be forced to fail with its `*Failure` property.
 */
class FakeMealDefaultsRepository(initial: List<MealDefault> = emptyList()) : MealDefaultsRepository {
    val defaults = initial.toMutableList()
    val appliedDates = mutableListOf<LocalDate>()
    var applyResult = 0
    var listFailure: AppError? = null
    var saveFailure: AppError? = null
    var applyFailure: AppError? = null

    private fun <T> failure(error: AppError): Result<T> = Result.failure(AppException(error))

    override suspend fun list(): Result<List<MealDefault>> =
        listFailure?.let { failure(it) } ?: Result.success(defaults.toList())

    override suspend fun save(assignments: List<MealDefaultAssignment>): Result<Unit> {
        saveFailure?.let { return failure(it) }
        defaults.clear()
        assignments.forEach { a ->
            a.weekdays.sorted().forEach { defaults += MealDefault(a.mealSlotId, a.templateId, it) }
        }
        return Result.success(Unit)
    }

    override suspend fun applyForDate(date: LocalDate): Result<Int> {
        applyFailure?.let { return failure(it) }
        appliedDates += date
        return Result.success(applyResult)
    }
}
