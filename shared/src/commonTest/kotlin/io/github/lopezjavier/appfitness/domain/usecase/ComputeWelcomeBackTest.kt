package io.github.lopezjavier.appfitness.domain.usecase

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComputeWelcomeBackTest {
    private val today = LocalDate(2026, 11, 20)
    private val compute = ComputeWelcomeBack()

    private fun daysAgo(n: Int) = LocalDate.fromEpochDays(today.toEpochDays() - n)

    private fun run(last: LocalDate? = daysAgo(14), saved: LocalDate? = daysAgo(40), hasBlock: Boolean = true) =
        compute(today, last, saved, hasBlock)

    @Test
    fun fourteenDays_shows() = assertTrue(run(last = daysAgo(14)))

    @Test
    fun thirtyDays_shows() = assertTrue(run(last = daysAgo(30), saved = daysAgo(60)))

    @Test
    fun thirteenDays_doesNotShow() = assertFalse(run(last = daysAgo(13)))

    @Test
    fun sixDays_doesNotShow() = assertFalse(run(last = daysAgo(6)))

    @Test
    fun sessionToday_doesNotShow() = assertFalse(run(last = today))

    @Test
    fun noCompletedSession_doesNotShow() = assertFalse(run(last = null))

    @Test
    fun noSavedBlock_doesNotShow() = assertFalse(run(hasBlock = false))

    @Test
    fun blockSavedBeforeTheLastSession_shows() = assertTrue(run(last = daysAgo(20), saved = daysAgo(25)))

    @Test
    fun blockSavedOnTheLastSessionDay_shows() = assertTrue(run(last = daysAgo(20), saved = daysAgo(20)))

    @Test
    fun blockSavedAfterTheLastSession_doesNotShow() = assertFalse(run(last = daysAgo(20), saved = daysAgo(19)))
}
