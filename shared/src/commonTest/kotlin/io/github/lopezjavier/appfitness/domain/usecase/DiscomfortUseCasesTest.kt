package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.Discomfort
import io.github.lopezjavier.appfitness.domain.model.DiscomfortLevel
import io.github.lopezjavier.appfitness.domain.repository.DiscomfortRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private class RecordingDiscomfortRepository : DiscomfortRepository {
    val logged = mutableListOf<Triple<String, DiscomfortLevel, String?>>()
    val resolved = mutableListOf<Pair<String, Instant>>()
    var open: List<Discomfort> = emptyList()

    override suspend fun log(exerciseId: String, level: DiscomfortLevel, note: String?): Result<Discomfort> {
        logged += Triple(exerciseId, level, note)
        return Result.success(Discomfort("d${logged.size}", exerciseId, Instant.fromEpochSeconds(0), level, note))
    }

    override suspend fun resolveOpen(exerciseId: String, at: Instant): Result<Unit> {
        resolved += exerciseId to at
        return Result.success(Unit)
    }

    override suspend fun unresolved(exerciseIds: Set<String>): Result<List<Discomfort>> = Result.success(open)
}

class LogDiscomfortTest {
    private val repository = RecordingDiscomfortRepository()
    private val logDiscomfort = LogDiscomfort(repository)

    private val level = DiscomfortLevel.Mild

    @Test
    fun levelAndTrimmedNoteReachTheRepository() = runTest {
        logDiscomfort("e1", DiscomfortLevel.PinchOrSnap, "  rodilla  ").getOrThrow()

        assertEquals(
            Triple<String, DiscomfortLevel, String?>("e1", DiscomfortLevel.PinchOrSnap, "rodilla"),
            repository.logged.single(),
        )
    }

    @Test
    fun noteIsTrimmed() = runTest {
        val stored = logDiscomfort("e1", level, "  rodilla  ").getOrThrow()

        assertEquals("rodilla", stored.note)
    }

    @Test
    fun emptyOrBlankNoteIsStoredAsNull() = runTest {
        assertNull(logDiscomfort("e1", level, "").getOrThrow().note)
        assertNull(logDiscomfort("e1", level, "   ").getOrThrow().note)
        assertNull(logDiscomfort("e1", level, null).getOrThrow().note)
    }

    @Test
    fun twoHundredCharactersAreAccepted() = runTest {
        assertTrue(logDiscomfort("e1", level, "a".repeat(200)).isSuccess)
    }

    @Test
    fun twoHundredAndOneCharactersAreAValidationErrorAndNothingIsStored() = runTest {
        val result = logDiscomfort("e1", level, "a".repeat(201))

        assertEquals(AppError.Validation, result.appErrorOrNull())
        assertTrue(repository.logged.isEmpty())
    }

    @Test
    fun trimmedLengthIsWhatCounts() = runTest {
        assertTrue(logDiscomfort("e1", level, " " + "a".repeat(200) + " ").isSuccess)
    }

    @Test
    fun theNoteNeverChangesTheLevel() = runTest {
        val stored = logDiscomfort("e1", DiscomfortLevel.Tension, "dolor fuerte en el codo").getOrThrow()

        assertEquals(DiscomfortLevel.Tension, stored.level)
        assertFalse(stored.severe)
    }

    @Test
    fun severeFollowsTheChosenLevel() = runTest {
        assertTrue(logDiscomfort("e1", DiscomfortLevel.SharpOrInjury, null).getOrThrow().severe)
        assertFalse(logDiscomfort("e1", DiscomfortLevel.PinchOrSnap, null).getOrThrow().severe)
    }
}

class ResolveDiscomfortTest {
    @Test
    fun resolvesTheExerciseAtTheCurrentInstant() = runTest {
        val repository = RecordingDiscomfortRepository()
        val now = Instant.parse("2026-11-02T08:00:00Z")

        ResolveDiscomfort(repository, FixedTestClock(now))("e1").getOrThrow()

        assertEquals(listOf("e1" to now), repository.resolved)
    }

    @Test
    fun aDeviceClockBehindTheServerIsClampedToTheNewestLoggedAt() = runTest {
        val serverLogged = Instant.parse("2026-11-02T08:00:05Z")
        val repository = RecordingDiscomfortRepository()
        repository.open = listOf(Discomfort("d1", "e1", serverLogged, DiscomfortLevel.Mild, null))

        ResolveDiscomfort(repository, FixedTestClock(Instant.parse("2026-11-02T08:00:00Z")))("e1").getOrThrow()

        assertEquals(listOf("e1" to serverLogged), repository.resolved)
    }
}
