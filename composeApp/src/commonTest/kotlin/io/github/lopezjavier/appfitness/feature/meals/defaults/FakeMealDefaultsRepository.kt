package io.github.lopezjavier.appfitness.feature.meals.defaults

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.MealDefault
import io.github.lopezjavier.appfitness.domain.model.MealDefaultAssignment
import io.github.lopezjavier.appfitness.domain.repository.MealDefaultsRepository
import kotlinx.datetime.LocalDate

/**
 * In-memory [MealDefaultsRepository]: [save] replaces everything and records each payload in [saved];
 * [applyForDate] records the dates it was called with in [appliedDates] and returns [applyResult]. Each operation can
 * be forced to fail with its `*Failure` property. [onApply] runs on every apply call (to record call order).
 */
internal class FakeMealDefaultsRepository(initial: List<MealDefault> = emptyList()) : MealDefaultsRepository {
    val defaults = initial.toMutableList()
    val saved = mutableListOf<List<MealDefaultAssignment>>()
    val appliedDates = mutableListOf<LocalDate>()
    var applyResult = 0
    var listFailure: AppError? = null
    var saveFailure: AppError? = null
    var applyFailure: AppError? = null
    var onApply: (LocalDate) -> Unit = {}

    private fun <T> failure(error: AppError): Result<T> = Result.failure(AppException(error))

    override suspend fun list(): Result<List<MealDefault>> =
        listFailure?.let { failure(it) } ?: Result.success(defaults.toList())

    override suspend fun save(assignments: List<MealDefaultAssignment>): Result<Unit> {
        saveFailure?.let { return failure(it) }
        saved.add(assignments)
        defaults.clear()
        assignments.forEach { a ->
            a.weekdays.sorted().forEach { defaults += MealDefault(a.mealSlotId, a.templateId, it) }
        }
        return Result.success(Unit)
    }

    override suspend fun applyForDate(date: LocalDate): Result<Int> {
        onApply(date)
        applyFailure?.let { return failure(it) }
        appliedDates += date
        return Result.success(applyResult)
    }
}
