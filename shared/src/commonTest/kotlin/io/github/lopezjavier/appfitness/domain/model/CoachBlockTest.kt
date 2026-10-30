package io.github.lopezjavier.appfitness.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CoachBlockTest {
    private val training = BlockTraining(3, false, 60, "hypertrophy", 4, emptyList(), emptyMap(), emptyMap())

    private fun session(week: Int, index: Int) =
        BlockSession(week, index, "W$week-$index", emptyList(), emptyList(), null)

    @Test
    fun weeks_groupsByWeekAscendingAndOrdersSessionsByIndex() {
        val block = CoachBlock(
            "p1",
            training,
            listOf(session(2, 1), session(1, 1), session(2, 0), session(1, 0)),
            saved = false,
        )

        val weeks = block.weeks()

        assertEquals(listOf(1, 2), weeks.map { it.week })
        assertEquals(listOf(listOf(0, 1), listOf(0, 1)), weeks.map { week -> week.sessions.map { it.sessionIndex } })
    }

    @Test
    fun weeks_isEmptyWithoutSessions() {
        assertEquals(emptyList(), CoachBlock("p1", training, emptyList(), saved = false).weeks())
    }
}
