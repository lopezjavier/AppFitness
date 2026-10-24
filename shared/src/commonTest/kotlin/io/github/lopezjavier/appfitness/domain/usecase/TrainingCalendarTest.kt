package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.TrainedSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class TrainingCalendarTest {
    private fun session(id: String, iso: String) = TrainedSession(id, "W $id", Instant.parse(iso))

    @Test
    fun groupsByLocalDayAcrossMidnightInTheGivenZone() {
        val late = session("a", "2026-03-01T23:30:00Z")
        val madrid = TimeZone.of("Europe/Madrid")

        assertEquals(setOf(LocalDate(2026, 3, 1)), TrainingCalendar.daysOf(listOf(late), TimeZone.UTC).keys)
        assertEquals(setOf(LocalDate(2026, 3, 2)), TrainingCalendar.daysOf(listOf(late), madrid).keys)
    }

    @Test
    fun twoWorkoutsOnOneDayAreOrderedByStart() {
        val evening = session("evening", "2026-03-01T19:00:00Z")
        val morning = session("morning", "2026-03-01T08:00:00Z")

        val days = TrainingCalendar.daysOf(listOf(evening, morning), TimeZone.UTC)

        assertEquals(listOf("morning", "evening"), days.getValue(LocalDate(2026, 3, 1)).map { it.id })
    }

    @Test
    fun countsPerYearAndPerMonthAcrossYears() {
        val days = TrainingCalendar.daysOf(
            listOf(
                session("1", "2024-12-31T10:00:00Z"),
                session("2", "2025-01-02T10:00:00Z"),
                session("3", "2025-01-02T18:00:00Z"),
                session("4", "2025-03-10T10:00:00Z"),
            ),
            TimeZone.UTC,
        )

        assertEquals(mapOf(2024 to 1, 2025 to 3), TrainingCalendar.yearCounts(days))
        assertEquals(mapOf(1 to 2, 3 to 1), TrainingCalendar.monthCounts(days, 2025))
        assertEquals(emptyMap(), TrainingCalendar.monthCounts(days, 2023))
        assertEquals(
            setOf(YearMonth(2024, 12), YearMonth(2025, 1), YearMonth(2025, 3)),
            TrainingCalendar.monthsWithTraining(days),
        )
    }

    @Test
    fun noSessionsGiveEmptyResults() {
        val days = TrainingCalendar.daysOf(emptyList(), TimeZone.UTC)

        assertEquals(emptyMap(), days)
        assertEquals(emptyMap(), TrainingCalendar.yearCounts(days))
        assertEquals(emptySet(), TrainingCalendar.monthsWithTraining(days))
    }
}
