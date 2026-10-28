package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.Discomfort
import io.github.lopezjavier.appfitness.domain.repository.DiscomfortRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private class RecordingDiscomfortRepository : DiscomfortRepository {
    val logged = mutableListOf<Triple<String, String?, Boolean>>()
    val resolved = mutableListOf<Pair<String, Instant>>()
    var open: List<Discomfort> = emptyList()

    override suspend fun log(exerciseId: String, note: String?, severe: Boolean): Result<Discomfort> {
        logged += Triple(exerciseId, note, severe)
        return Result.success(Discomfort("d${logged.size}", exerciseId, Instant.fromEpochSeconds(0), note, severe))
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

    @Test
    fun noteIsTrimmed() = runTest {
        val stored = logDiscomfort("e1", "  rodilla  ").getOrThrow()

        assertEquals("rodilla", stored.note)
    }

    @Test
    fun emptyOrBlankNoteIsStoredAsNull() = runTest {
        assertNull(logDiscomfort("e1", "").getOrThrow().note)
        assertNull(logDiscomfort("e1", "   ").getOrThrow().note)
        assertNull(logDiscomfort("e1", null).getOrThrow().note)
    }

    @Test
    fun twoHundredCharactersAreAccepted() = runTest {
        assertTrue(logDiscomfort("e1", "a".repeat(200)).isSuccess)
    }

    @Test
    fun twoHundredAndOneCharactersAreAValidationErrorAndNothingIsStored() = runTest {
        val result = logDiscomfort("e1", "a".repeat(201))

        assertEquals(AppError.Validation, result.appErrorOrNull())
        assertTrue(repository.logged.isEmpty())
    }

    @Test
    fun trimmedLengthIsWhatCounts() = runTest {
        assertTrue(logDiscomfort("e1", " " + "a".repeat(200) + " ").isSuccess)
    }

    @Test
    fun severeIsDerivedFromTheTerms() = runTest {
        assertTrue(logDiscomfort("e1", "dolor fuerte en el codo").getOrThrow().severe)
        assertFalse(logDiscomfort("e1", "dolor").getOrThrow().severe)
        assertFalse(logDiscomfort("e1", null).getOrThrow().severe)
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
        repository.open = listOf(Discomfort("d1", "e1", serverLogged, null, false))

        ResolveDiscomfort(repository, FixedTestClock(Instant.parse("2026-11-02T08:00:00Z")))("e1").getOrThrow()

        assertEquals(listOf("e1" to serverLogged), repository.resolved)
    }
}
