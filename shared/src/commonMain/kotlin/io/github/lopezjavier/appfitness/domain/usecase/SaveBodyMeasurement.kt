package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.ManualWeightSave
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Validates and stores one manual weight (+ optional body fat) for a local day.
 *
 * - New save (no [existing], no [day]): today, measured now.
 * - Past day without an entry ([day] set): measured at local noon of that day.
 * - Edit ([existing] set): the entry's day and time never change, so the day is locked; only weight and fat do.
 */
class SaveBodyMeasurement(
    private val repository: BodyMeasurementRepository,
    private val clock: Clock,
    private val timeZone: TimeZone,
    private val validate: ValidateBodyMeasurement = ValidateBodyMeasurement(),
) {
    suspend operator fun invoke(
        weightKg: Double,
        bodyFatPct: Double?,
        day: LocalDate? = null,
        existing: BodyMeasurement? = null,
    ): Result<Unit> {
        if (validate(weightKg, bodyFatPct) != BodyMeasurementCheck.Valid) {
            return Result.failure(AppException(AppError.Validation))
        }
        val now = clock.now()
        val today = now.toLocalDateTime(timeZone).date
        val targetDay = day ?: existing?.measuredAt?.toLocalDateTime(timeZone)?.date ?: today
        val measuredAt = when {
            existing != null -> existing.measuredAt
            targetDay == today -> now
            else -> targetDay.atTime(NOON_HOUR, 0).toInstant(timeZone)
        }
        return repository.saveManualWeight(
            ManualWeightSave(
                id = existing?.id,
                weightKg = weightKg,
                bodyFatPct = bodyFatPct,
                measuredAt = measuredAt,
                dayStart = targetDay.atStartOfDayIn(timeZone),
                dayEnd = targetDay.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone),
            ),
        )
    }

    private companion object {
        const val NOON_HOUR = 12
    }
}
