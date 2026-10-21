package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.ManualWeightSave
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
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
 * - Edit ([existing] set): the entry's day is locked; weight, fat and, when [time] is given, the time of day change.
 * - [time] (local) overrides the default time of a new save or an edit; the result must not be after now.
 */
class SaveBodyMeasurement(
    private val repository: BodyMeasurementRepository,
    private val clock: Clock,
    private val timeZone: TimeZone,
    private val validate: ValidateBodyMeasurement = ValidateBodyMeasurement(),
) {
    @Suppress("ReturnCount") // Two validation guards before the save.
    suspend operator fun invoke(
        weightKg: Double,
        bodyFatPct: Double?,
        day: LocalDate? = null,
        existing: BodyMeasurement? = null,
        time: LocalTime? = null,
    ): Result<Unit> {
        if (validate(weightKg, bodyFatPct) != BodyMeasurementCheck.Valid) {
            return Result.failure(AppException(AppError.Validation))
        }
        val now = clock.now()
        val today = now.toLocalDateTime(timeZone).date
        val targetDay = day ?: existing?.measuredAt?.toLocalDateTime(timeZone)?.date ?: today
        val measuredAt = when {
            time != null -> targetDay.atTime(time).toInstant(timeZone)
            existing != null -> existing.measuredAt
            targetDay == today -> now
            else -> targetDay.atTime(NOON_HOUR, 0).toInstant(timeZone)
        }
        // An untouched edit keeps its stored instant; anything else must not lie in the future.
        if ((existing == null || time != null) && measuredAt > now) {
            return Result.failure(AppException(AppError.Validation))
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
