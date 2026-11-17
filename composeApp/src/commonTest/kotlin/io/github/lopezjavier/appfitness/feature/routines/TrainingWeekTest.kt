package io.github.lopezjavier.appfitness.feature.routines

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrainingWeekTest {
    private val monday = LocalDate(2026, 11, 16)
    private val wednesday = LocalDate(2026, 11, 18)
    private val sunday = LocalDate(2026, 11, 22)

    @Test
    fun weekStart_isTheMondayOfTheWeek_forAnyDayOfIt() {
        assertEquals(monday, monday.weekStart())
        assertEquals(monday, wednesday.weekStart())
        assertEquals(monday, sunday.weekStart())
        assertEquals(LocalDate(2026, 11, 23), LocalDate(2026, 11, 23).weekStart())
    }

    @Test
    fun aWorkoutOnTheMondayOfTodaysWeek_countsInThisWeek() {
        val week = trainingWeekOf(mapOf(monday to 2), today = wednesday)

        assertEquals(setOf(monday), week.trainedDays)
        assertEquals(2, week.workouts)
        assertEquals(1, week.streakWeeks)
        assertEquals(wednesday, week.today)
    }

    @Test
    fun aWorkoutOnTheSundayBefore_belongsToTheLastWeek_notToThisOne() {
        val week = trainingWeekOf(mapOf(LocalDate(2026, 11, 15) to 1), today = monday)

        assertTrue(week.trainedDays.isEmpty())
        assertEquals(0, week.workouts)
        // The empty current week does not break the streak: last week still counts.
        assertEquals(1, week.streakWeeks)
    }

    @Test
    fun anEmptyCurrentWeek_keepsTheStreakOfTheWeeksBefore() {
        val counts = mapOf(LocalDate(2026, 11, 9) to 1, LocalDate(2026, 11, 2) to 3)

        val week = trainingWeekOf(counts, today = wednesday)

        assertEquals(0, week.workouts)
        assertEquals(2, week.streakWeeks)
    }

    @Test
    fun theCurrentWeekWithAWorkout_extendsTheStreak() {
        val counts = mapOf(wednesday to 1, LocalDate(2026, 11, 9) to 1)

        assertEquals(2, trainingWeekOf(counts, today = wednesday).streakWeeks)
    }

    @Test
    fun aWeekWithoutWorkouts_breaksTheStreak() {
        val counts = mapOf(wednesday to 1, LocalDate(2026, 11, 2) to 1)

        assertEquals(1, trainingWeekOf(counts, today = wednesday).streakWeeks)
    }

    @Test
    fun aFutureDay_isNeitherTrainedNorCounted() {
        val week = trainingWeekOf(mapOf(LocalDate(2026, 11, 20) to 1, wednesday to 1), today = wednesday)

        assertEquals(setOf(wednesday), week.trainedDays)
        assertEquals(1, week.workouts)
    }

    @Test
    fun noWorkoutsAtAll_isAnEmptyWeekWithNoStreak() {
        val week = trainingWeekOf(emptyMap(), today = wednesday)

        assertEquals(TrainingWeek(today = wednesday), week)
    }
}
