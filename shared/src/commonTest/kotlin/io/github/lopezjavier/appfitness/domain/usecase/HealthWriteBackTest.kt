package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.HealthAccess
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val NOW = Instant.parse("2026-10-22T09:00:00Z")
private val DAY_START = Instant.parse("2026-10-21T22:00:00Z")
private val DAY_END = Instant.parse("2026-10-22T22:00:00Z")

class HealthWriteBackTest {
    private val source = FakeHealthDataSource()
    private val repository = FakeMeasurementsRepository()
    private val clock = FixedTestClock(NOW)
    private val writeBack = HealthWriteBack(source, repository, clock)

    private fun row(hcRecordId: String?, weightKg: Double = 73.9, fat: Double? = 15.1) = BodyMeasurement(
        id = "row-1",
        measuredAt = NOW - 5.minutes,
        weightKg = weightKg,
        source = MeasurementSource.Manual,
        bodyFatPct = fat,
        hcRecordId = hcRecordId,
    )

    @Test
    fun newEntryWithPermission_isWrittenAndTheCopyMarkerStored() = runTest {
        writeBack.onSaved("row-1", 73.9, 15.1, NOW - 5.minutes, previous = null)

        assertEquals(
            listOf(WeighInWrite("row-1", 73.9, 15.1, NOW - 5.minutes, NOW.toEpochMilliseconds())),
            source.writes,
        )
        assertEquals(listOf<Pair<String, String?>>("row-1" to "row-1"), repository.hcRecordIds)
    }

    @Test
    fun withoutWritePermission_nothingIsWrittenOrStored() = runTest {
        source.access = HealthAccess(ALL_READ, canWrite = false)

        writeBack.onSaved("row-1", 73.9, 15.1, NOW, previous = null)

        assertTrue(source.writes.isEmpty())
        assertTrue(repository.hcRecordIds.isEmpty())
    }

    @Test
    fun editOfAnEntryWithACopy_updatesItWithAHigherVersion() = runTest {
        writeBack.onSaved("row-1", 73.9, 15.1, NOW, previous = null)
        clock.at = NOW + 1.minutes

        writeBack.onSaved("row-1", 73.5, 15.1, NOW, previous = row(hcRecordId = "row-1"))

        val (first, second) = source.writes
        assertEquals(73.5, second.weightKg)
        assertTrue(second.version > first.version)
        assertEquals(1, repository.hcRecordIds.size) // The marker is already set: not stored again.
    }

    @Test
    fun clearingTheFat_writesANullFatSoTheFatCopyIsRemoved() = runTest {
        writeBack.onSaved("row-1", 73.9, null, NOW, previous = row(hcRecordId = "row-1"))

        assertEquals(null, source.writes.single().bodyFatPct)
    }

    @Test
    fun editOfAnEntryWithoutACopy_isNotWritten() = runTest {
        writeBack.onSaved("row-1", 73.5, null, NOW, previous = row(hcRecordId = null))

        assertTrue(source.writes.isEmpty())
        assertTrue(repository.hcRecordIds.isEmpty())
    }

    @Test
    fun deletingAnEntryWithACopy_removesTheCopy() = runTest {
        writeBack.onDeleted(row(hcRecordId = "row-1"))

        assertEquals(listOf("row-1"), source.deletedCopies)
    }

    @Test
    fun deletingAnEntryWithoutACopy_touchesNothing() = runTest {
        writeBack.onDeleted(row(hcRecordId = null))

        assertTrue(source.deletedCopies.isEmpty())
    }

    @Test
    fun healthConnectFailures_neverReachTheCaller() = runTest {
        source.writeFailure = IllegalStateException("denied")
        source.deleteFailure = IllegalStateException("denied")

        writeBack.onSaved("row-1", 73.9, 15.1, NOW, previous = null)
        writeBack.onDeleted(row(hcRecordId = "row-1"))

        assertTrue(repository.hcRecordIds.isEmpty()) // A failed write stores no marker.
    }

    @Test
    fun aFailedMarkerStore_returnsNormally() = runTest {
        repository.hcRecordFailure = IllegalStateException("offline")

        writeBack.onSaved("row-1", 73.9, 15.1, NOW, previous = null)

        assertEquals(1, source.writes.size)
    }

    @Test
    fun preferencesWeight_writesTodaysManualEntryKeepingItsFat() = runTest {
        repository.dayRows = listOf(row(hcRecordId = null, weightKg = 73.5, fat = 15.1))

        writeBack.onTodaysWeightSaved(DAY_START, DAY_END)

        val write = source.writes.single()
        assertEquals(73.5, write.weightKg)
        assertEquals(15.1, write.bodyFatPct)
        assertEquals(listOf<Pair<String, String?>>("row-1" to "row-1"), repository.hcRecordIds)
    }

    @Test
    fun preferencesWeight_withoutPermission_writesNothing() = runTest {
        source.access = HealthAccess(ALL_READ, canWrite = false)
        repository.dayRows = listOf(row(hcRecordId = null))

        writeBack.onTodaysWeightSaved(DAY_START, DAY_END)

        assertTrue(source.writes.isEmpty())
    }
}
