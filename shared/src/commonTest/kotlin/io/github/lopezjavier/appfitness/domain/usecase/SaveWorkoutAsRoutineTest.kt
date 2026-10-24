package io.github.lopezjavier.appfitness.domain.usecase

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class SaveWorkoutAsRoutineTest {
    @Test
    fun defaultName_isTheWorkoutNameTrimmedAndCutTo100() {
        assertEquals("Torso", SaveWorkoutAsRoutine.defaultName("  Torso "))
        assertEquals(100, SaveWorkoutAsRoutine.defaultName("a".repeat(150)).length)
    }

    @Test
    fun isValidName_needsBetween1And100CharactersAfterTrimming() {
        assertFalse(SaveWorkoutAsRoutine.isValidName("   "))
        assertTrue(SaveWorkoutAsRoutine.isValidName("a"))
        assertTrue(SaveWorkoutAsRoutine.isValidName("a".repeat(100)))
        assertFalse(SaveWorkoutAsRoutine.isValidName("a".repeat(101)))
    }

    @Test
    fun exceedsSetLimit_isTrueOnlyAboveTenSetsInAnExercise() {
        assertFalse(SaveWorkoutAsRoutine.exceedsSetLimit(listOf(3, 10)))
        assertTrue(SaveWorkoutAsRoutine.exceedsSetLimit(listOf(3, 11)))
    }
}

class WorkoutStartInstantTest {
    private val date = LocalDate(2026, 10, 20)

    @Test
    fun aPastDayStartsAtNoonLocalTime() {
        val start = WorkoutStartInstant.of(date, Instant.parse("2026-10-22T09:00:00Z"), TimeZone.of("Europe/Madrid"))

        assertEquals(Instant.parse("2026-10-20T10:00:00Z"), start)
    }

    @Test
    fun todayBeforeNoonIsCappedToNow() {
        val now = Instant.parse("2026-10-20T08:30:00Z")

        assertEquals(now, WorkoutStartInstant.of(date, now, TimeZone.UTC))
    }
}
