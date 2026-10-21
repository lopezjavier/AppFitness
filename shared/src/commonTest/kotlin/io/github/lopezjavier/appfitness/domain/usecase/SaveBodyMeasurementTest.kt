package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.ManualWeightSave
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

private val MADRID = TimeZone.of("Europe/Madrid")

private class RecordingRepository : BodyMeasurementRepository {
    val saves = mutableListOf<ManualWeightSave>()

    override suspend fun getOnboardingWeight(): Result<BodyMeasurement?> = Result.success(null)

    override suspend fun saveOnboardingWeight(weightKg: Double, measuredAt: Instant): Result<Unit> =
        Result.success(Unit)

    override suspend fun getLatestWeight(): Result<BodyMeasurement?> = Result.success(null)

    override suspend fun getMeasurements(from: Instant, toExclusive: Instant): Result<List<BodyMeasurement>> =
        Result.success(emptyList())

    override suspend fun saveManualWeight(save: ManualWeightSave): Result<Unit> {
        saves += save
        return Result.success(Unit)
    }

    override suspend fun deleteMeasurement(id: String): Result<Unit> = Result.success(Unit)
}

private class FixedClock(private val at: Instant) : Clock {
    override fun today(): LocalDate = LocalDate(2026, 10, 14)

    override fun now(): Instant = at
}

class SaveBodyMeasurementTest {
    private val repository = RecordingRepository()

    private fun useCase(now: String) = SaveBodyMeasurement(repository, FixedClock(Instant.parse(now)), MADRID)

    @Test
    fun newSave_usesTheLocalDayBoundsOfNow() = runTest {
        // 12:30 Madrid on 2026-10-14 (UTC+2).
        val result = useCase("2026-10-14T10:30:00Z")(74.2, 18.5)

        assertTrue(result.isSuccess)
        assertEquals(
            ManualWeightSave(
                id = null,
                weightKg = 74.2,
                bodyFatPct = 18.5,
                measuredAt = Instant.parse("2026-10-14T10:30:00Z"),
                dayStart = Instant.parse("2026-10-13T22:00:00Z"),
                dayEnd = Instant.parse("2026-10-14T22:00:00Z"),
            ),
            repository.saves.single(),
        )
    }

    @Test
    fun justBeforeLocalMidnight_belongsToThatDay() = runTest {
        useCase("2026-10-14T21:59:00Z")(70.0, null)

        val save = repository.saves.single()
        assertEquals(Instant.parse("2026-10-13T22:00:00Z"), save.dayStart)
        assertEquals(Instant.parse("2026-10-14T22:00:00Z"), save.dayEnd)
    }

    @Test
    fun justAfterLocalMidnight_belongsToTheNextDay() = runTest {
        useCase("2026-10-14T22:00:00Z")(70.0, null)

        val save = repository.saves.single()
        assertEquals(Instant.parse("2026-10-14T22:00:00Z"), save.dayStart)
        assertEquals(Instant.parse("2026-10-15T22:00:00Z"), save.dayEnd)
    }

    @Test
    fun pastDayWithoutEntry_isMeasuredAtLocalNoon() = runTest {
        useCase("2026-10-14T10:30:00Z")(70.0, null, day = LocalDate(2026, 10, 10))

        val save = repository.saves.single()
        assertEquals(Instant.parse("2026-10-10T10:00:00Z"), save.measuredAt)
        assertEquals(Instant.parse("2026-10-09T22:00:00Z"), save.dayStart)
        assertEquals(Instant.parse("2026-10-10T22:00:00Z"), save.dayEnd)
    }

    @Test
    fun edit_keepsTheEntryDayAndTime() = runTest {
        val existing = BodyMeasurement("row-1", Instant.parse("2026-10-10T07:15:00Z"), 71.0, MeasurementSource.Manual)

        useCase("2026-10-14T10:30:00Z")(72.5, null, existing = existing)

        assertEquals(
            ManualWeightSave(
                id = "row-1",
                weightKg = 72.5,
                bodyFatPct = null,
                measuredAt = Instant.parse("2026-10-10T07:15:00Z"),
                dayStart = Instant.parse("2026-10-09T22:00:00Z"),
                dayEnd = Instant.parse("2026-10-10T22:00:00Z"),
            ),
            repository.saves.single(),
        )
    }

    @Test
    fun invalidInput_neverReachesTheRepository() = runTest {
        val case = useCase("2026-10-14T10:30:00Z")

        assertEquals(AppError.Validation, case(19.9, null).appErrorOrNull())
        assertEquals(AppError.Validation, case(401.0, null).appErrorOrNull())
        assertEquals(AppError.Validation, case(70.0, 80.0).appErrorOrNull())
        assertTrue(repository.saves.isEmpty())
    }
}
