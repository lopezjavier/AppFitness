package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** T-300 (US-136): the session of the current week that gets the play button. */
class PlanWeekNextPendingSessionTest {
    private fun session(index: Int, name: String, done: Boolean, routineId: String? = "r-$index") =
        PlanWeekSession(sessionIndex = index, name = name, routineId = routineId, done = done)

    private fun week(phase: WeekPhase, sessions: List<PlanWeekSession>) = PlanWeek(
        week = 3,
        startDate = LocalDate(2026, 11, 9),
        endDate = LocalDate(2026, 11, 15),
        phase = phase,
        deload = false,
        planned = sessions.size,
        completed = sessions.count { it.done },
        adherencePct = 0,
        sessions = sessions,
    )

    @Test
    fun currentWeek_returnsTheFirstPendingSession() {
        val week = week(
            WeekPhase.Current,
            listOf(
                session(0, "Torso A", done = true),
                session(1, "Pierna A", done = true),
                session(2, "Torso B", done = true),
                session(3, "Pierna B", done = false),
            ),
        )

        assertEquals("Pierna B", week.nextPendingSession()?.name)
    }

    @Test
    fun currentWeek_lowestSessionIndexWinsWhenTheListIsUnordered() {
        val week = week(
            WeekPhase.Current,
            listOf(
                session(3, "Pierna B", done = false),
                session(1, "Pierna A", done = false),
                session(0, "Torso A", done = true),
            ),
        )

        assertEquals("Pierna A", week.nextPendingSession()?.name)
    }

    @Test
    fun currentWeek_allDone_isNull() {
        val week = week(WeekPhase.Current, listOf(session(0, "Torso A", done = true), session(1, "Pierna A", true)))

        assertNull(week.nextPendingSession())
    }

    @Test
    fun currentWeek_noSessions_isNull() {
        assertNull(week(WeekPhase.Current, emptyList()).nextPendingSession())
    }

    @Test
    fun pastWeek_withPendingSessions_isNull() {
        val week = week(WeekPhase.Past, listOf(session(0, "Torso A", done = false)))

        assertNull(week.nextPendingSession())
    }

    @Test
    fun upcomingWeek_isNull() {
        val week = week(WeekPhase.Upcoming, listOf(session(0, "Torso A", done = false)))

        assertNull(week.nextPendingSession())
    }

    @Test
    fun pendingSessionWithoutRoutine_isReturnedAsIs() {
        val week = week(
            WeekPhase.Current,
            listOf(session(0, "Torso A", done = false, routineId = null), session(1, "Pierna A", done = false)),
        )

        val next = week.nextPendingSession()

        assertEquals("Torso A", next?.name)
        assertNull(next?.routineId)
    }
}
