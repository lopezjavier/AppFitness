package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.BlockPause
import io.github.lopezjavier.appfitness.domain.model.CoachChangeStatus
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class TrainerBlockStatusTest {
    private fun week(n: Int, phase: WeekPhase, pct: Int = 0) = PlanWeek(
        week = n,
        startDate = LocalDate(2026, 1, 1),
        endDate = LocalDate(2026, 1, 7),
        phase = phase,
        deload = false,
        planned = 4,
        completed = pct / 25,
        adherencePct = pct,
        sessions = emptyList(),
    )

    private val running = listOf(
        week(1, WeekPhase.Past, 100),
        week(2, WeekPhase.Current, 75),
        week(3, WeekPhase.Upcoming),
        week(4, WeekPhase.Upcoming),
    )
    private val notPaused = BlockPause(paused = false, pausedWeeks = 0, blockEnd = null)

    @Test
    fun noBlockGivesNone() {
        assertEquals(BlockStatus.None, blockStatus(BlockSummary.None, emptyList(), null, false))
    }

    @Test
    fun proposedBlockGivesProposed() {
        assertEquals(BlockStatus.Proposed, blockStatus(BlockSummary.Proposed(coachBlock()), emptyList(), null, false))
    }

    @Test
    fun savedBeforeStartHasNoWeekAndNoAdherence() {
        val weeks = List(4) { week(it + 1, WeekPhase.Upcoming) }
        assertEquals(
            BlockStatus.Saved(week = null, total = 4, paused = false, reviewDue = false, adherence = null),
            blockStatus(BlockSummary.Saved(coachBlock(saved = true)), weeks, notPaused, false),
        )
    }

    @Test
    fun savedInWeekTwoShowsWeekAndItsAdherence() {
        assertEquals(
            BlockStatus.Saved(week = 2, total = 4, paused = false, reviewDue = false, adherence = 75),
            blockStatus(BlockSummary.Saved(coachBlock(saved = true)), running, notPaused, false),
        )
    }

    @Test
    fun pausedFlagIsCarried() {
        val status =
            blockStatus(BlockSummary.Saved(coachBlock(saved = true)), running, notPaused.copy(paused = true), false)
        assertEquals(true, (status as BlockStatus.Saved).paused)
    }

    @Test
    fun reviewDueIsCarried() {
        val weeks = List(4) { week(it + 1, WeekPhase.Past, 100) }
        assertEquals(
            BlockStatus.Saved(week = null, total = 4, paused = false, reviewDue = true, adherence = null),
            blockStatus(BlockSummary.Saved(coachBlock(saved = true)), weeks, notPaused, true),
        )
    }

    @Test
    fun lastWeekIsCurrent() {
        val weeks = List(3) { week(it + 1, WeekPhase.Past, 100) } + week(4, WeekPhase.Current, 50)
        val status = blockStatus(
            BlockSummary.Saved(coachBlock(saved = true)),
            weeks,
            notPaused,
            false,
        ) as BlockStatus.Saved
        assertEquals(4, status.week)
        assertEquals(50, status.adherence)
    }

    @Test
    fun emptyWeeksListKeepsSavedWithoutWeekOrTotal() {
        assertEquals(
            BlockStatus.Saved(week = null, total = 0, paused = false, reviewDue = false, adherence = null),
            blockStatus(BlockSummary.Saved(coachBlock(saved = true)), emptyList(), null, false),
        )
    }

    private val applied = coachChange("a", status = CoachChangeStatus.Applied).toRowUi()
    private val pending = coachChange("p", status = CoachChangeStatus.Pending).toRowUi()

    @Test
    fun rootKeepsPendingFlagsAndTuBloqueHoldsTheAppliedChanges() {
        val content = TrainerUiState.Content(coachChanges = listOf(pending, applied))
        assertEquals(listOf(pending), content.pendingCoachChanges)
        assertEquals(listOf(applied), content.appliedCoachChanges)
    }

    @Test
    fun contentStatusMatchesFunction() {
        val content = TrainerUiState.Content(block = BlockSummary.Proposed(coachBlock()), weeks = emptyList())
        assertEquals(BlockStatus.Proposed, content.blockStatus)
    }
}
