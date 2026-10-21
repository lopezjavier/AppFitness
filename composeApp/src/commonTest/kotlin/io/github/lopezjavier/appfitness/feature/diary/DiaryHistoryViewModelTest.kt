package io.github.lopezjavier.appfitness.feature.diary

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.domain.model.DiaryDaySummary
import io.github.lopezjavier.appfitness.domain.model.DiaryGoals
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.port.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryHistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private var todayDate = LocalDate(2026, 10, 21)
    private val clock = object : Clock {
        override fun today(): LocalDate = todayDate

        override fun now(): Instant = Instant.parse("2026-10-21T10:00:00Z")
    }
    private val diary = FakeFoodDiaryRepository()
    private val goals = FakeNutritionGoalsRepository(
        NutritionGoals(GoalStrategy.Maintenance, 0, 2400, MacroSplit(30, 45, 25)),
    )
    private val savedState = SavedStateHandle()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(state: SavedStateHandle = savedState) = DiaryHistoryViewModel(diary, goals, clock, state)

    private fun DiaryHistoryViewModel.content() = assertIs<DiaryHistoryUiState.Content>(uiState.value).content

    private fun logged(date: LocalDate, kcal: Double, p: Double = 0.0, c: Double = 0.0, f: Double = 0.0) {
        diary.summaryDays += DiaryDaySummary(date, MealTotals(kcal, p, c, f))
    }

    private val oct18 = LocalDate(2026, 10, 18)

    @Test
    fun opening_goesFromLoadingToTheCurrentMonthWithMarkedDaysAndTodaySelected() = runTest(dispatcher) {
        logged(oct18, 1840.0, 142.0, 210.0, 48.0)
        logged(LocalDate(2026, 10, 2), 900.0)

        val model = viewModel()
        assertEquals(DiaryHistoryUiState.Loading, model.uiState.value)
        advanceUntilIdle()

        val content = model.content()
        assertEquals(YearMonth(2026, 10), content.month)
        assertEquals(setOf(oct18, LocalDate(2026, 10, 2)), content.markedDays)
        assertEquals(LocalDate(2026, 10, 21), content.selectedDay?.date)
        assertFalse(content.canGoNext)
        assertEquals(DiaryHistoryView.Day, content.view)
    }

    @Test
    fun selectingADay_showsItsTotalsAgainstTheTarget() = runTest(dispatcher) {
        logged(oct18, 1840.0, 142.0, 210.0, 48.0)
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(DiaryHistoryEvent.SelectDay(oct18))
        advanceUntilIdle()

        val day = model.content().selectedDay
        assertEquals(MealTotals(1840.0, 142.0, 210.0, 48.0), day?.totals)
        assertEquals(DiaryGoals(2400, 180, 270, 67), day?.goals)
        assertEquals(79, day?.proteinPercent)
        assertEquals(78, day?.carbsPercent)
        assertEquals(72, day?.fatPercent)
        assertEquals(560, day?.remainingKcal)
    }

    @Test
    fun selectingADayWithoutEntries_hasNoTotals() = runTest(dispatcher) {
        logged(oct18, 1840.0)
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(DiaryHistoryEvent.SelectDay(LocalDate(2026, 10, 10)))
        advanceUntilIdle()

        val day = model.content().selectedDay
        assertEquals(LocalDate(2026, 10, 10), day?.date)
        assertNull(day?.totals)
        assertFalse(day?.hasEntries ?: true)
        assertNull(day?.proteinPercent)
    }

    @Test
    fun withoutGoals_thereAreNoPercents() = runTest(dispatcher) {
        goals.row = null
        logged(oct18, 1840.0, 142.0)
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(DiaryHistoryEvent.SelectDay(oct18))
        advanceUntilIdle()

        val day = model.content().selectedDay
        assertEquals(1840.0, day?.totals?.kcal)
        assertNull(day?.goals)
        assertNull(day?.proteinPercent)
        assertNull(day?.remainingKcal)
    }

    @Test
    fun selectingADayOutsideTheMonthOrAfterToday_isIgnored() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(DiaryHistoryEvent.SelectDay(LocalDate(2026, 10, 22)))
        model.onEvent(DiaryHistoryEvent.SelectDay(LocalDate(2026, 9, 30)))
        advanceUntilIdle()

        assertEquals(LocalDate(2026, 10, 21), model.content().selectedDay?.date)
    }

    @Test
    fun noData_showsEmptySummaries() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        val content = model.content()
        assertEquals(emptySet(), content.markedDays)
        assertNull(content.week)
        assertNull(content.monthSummary)
        assertNull(content.selectedDay?.totals)
    }

    @Test
    fun weekAndMonthSummaries_averageOnlyTheLoggedDays() = runTest(dispatcher) {
        logged(LocalDate(2026, 10, 15), 1800.0)
        logged(LocalDate(2026, 10, 18), 2000.0)
        logged(LocalDate(2026, 10, 20), 2200.0)
        logged(LocalDate(2026, 10, 1), 1000.0)
        val model = viewModel()
        advanceUntilIdle()

        val content = model.content()
        assertEquals(3, content.week?.loggedDays)
        assertEquals(2000.0, content.week?.kcal)
        assertEquals(4, content.monthSummary?.loggedDays)
        assertEquals(7000.0, content.monthSummary?.totalKcal)
        assertEquals(1750.0, content.monthSummary?.avgKcalPerLoggedDay)
    }

    @Test
    fun weekCrossingAMonthBoundary_isFetchedSeparatelyFromTheViewedMonth() = runTest(dispatcher) {
        todayDate = LocalDate(2026, 11, 3)
        logged(LocalDate(2026, 10, 30), 2000.0)
        logged(LocalDate(2026, 11, 2), 1000.0)
        val model = viewModel()
        advanceUntilIdle()

        val content = model.content()
        assertEquals(YearMonth(2026, 11), content.month)
        assertEquals(2, content.week?.loggedDays)
        assertEquals(1500.0, content.week?.kcal)
        assertEquals(1, content.monthSummary?.loggedDays)
    }

    @Test
    fun previousMonth_reloadsThatMonthAndNextReturns() = runTest(dispatcher) {
        logged(LocalDate(2026, 9, 5), 1500.0)
        logged(oct18, 1840.0)
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(DiaryHistoryEvent.PreviousMonth)
        advanceUntilIdle()

        val september = model.content()
        assertEquals(YearMonth(2026, 9), september.month)
        assertEquals(setOf(LocalDate(2026, 9, 5)), september.markedDays)
        assertEquals(true, september.canGoNext)
        assertNull(september.selectedDay)
        assertEquals(LocalDate(2026, 9, 1) to LocalDate(2026, 9, 30), diary.summaryRequests.last())

        model.onEvent(DiaryHistoryEvent.NextMonth)
        advanceUntilIdle()

        assertEquals(YearMonth(2026, 10), model.content().month)
        assertEquals(LocalDate(2026, 10, 21), model.content().selectedDay?.date)
    }

    @Test
    fun nextMonth_isBlockedAtTheCurrentMonth() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        val requests = diary.summaryRequests.size

        model.onEvent(DiaryHistoryEvent.NextMonth)
        advanceUntilIdle()

        assertEquals(YearMonth(2026, 10), model.content().month)
        assertEquals(requests, diary.summaryRequests.size)
    }

    @Test
    fun selectView_switchesThePanel() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(DiaryHistoryEvent.SelectView(DiaryHistoryView.Month))
        advanceUntilIdle()

        assertEquals(DiaryHistoryView.Month, model.content().view)
    }

    @Test
    fun openDay_emitsTheSelectedDate() = runTest(dispatcher) {
        logged(oct18, 1840.0)
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(DiaryHistoryEvent.SelectDay(oct18))
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(DiaryHistoryEvent.OpenDay)
            assertEquals(DiaryHistoryEffect.OpenDiaryDay(oct18), awaitItem())
        }
    }

    @Test
    fun openDay_withoutASelectedDay_doesNothing() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(DiaryHistoryEvent.PreviousMonth)
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(DiaryHistoryEvent.OpenDay)
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun failure_showsErrorAndRetryRecovers() = runTest(dispatcher) {
        diary.failSummaries()
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(DiaryHistoryUiState.Error, model.uiState.value)

        diary.summariesResult = Result.success(Unit)
        diary.summariesGate = CompletableDeferred()
        model.onEvent(DiaryHistoryEvent.Retry)
        runCurrent()
        assertEquals(DiaryHistoryUiState.Loading, model.uiState.value)
        diary.summariesGate?.complete(Unit)
        advanceUntilIdle()

        assertIs<DiaryHistoryUiState.Content>(model.uiState.value)
    }

    @Test
    fun failureOnOneMonth_isRecoverableWithoutLosingTheMonth() = runTest(dispatcher) {
        logged(LocalDate(2026, 9, 5), 1500.0)
        val model = viewModel()
        advanceUntilIdle()

        diary.failSummaries()
        model.onEvent(DiaryHistoryEvent.PreviousMonth)
        advanceUntilIdle()
        assertEquals(DiaryHistoryUiState.Error, model.uiState.value)

        diary.summariesResult = Result.success(Unit)
        model.onEvent(DiaryHistoryEvent.Retry)
        advanceUntilIdle()

        assertEquals(YearMonth(2026, 9), model.content().month)
        assertEquals(setOf(LocalDate(2026, 9, 5)), model.content().markedDays)
    }

    @Test
    fun loading_isShownWhileTheServerIsAnswering() = runTest(dispatcher) {
        diary.summariesGate = CompletableDeferred()
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(DiaryHistoryUiState.Loading, model.uiState.value)

        diary.summariesGate?.complete(Unit)
        advanceUntilIdle()

        assertIs<DiaryHistoryUiState.Content>(model.uiState.value)
    }

    @Test
    fun recreatedFromTheSameSavedState_restoresMonthDayAndView() = runTest(dispatcher) {
        logged(LocalDate(2026, 9, 5), 1500.0)
        val first = viewModel()
        advanceUntilIdle()
        first.onEvent(DiaryHistoryEvent.PreviousMonth)
        advanceUntilIdle()
        first.onEvent(DiaryHistoryEvent.SelectDay(LocalDate(2026, 9, 5)))
        first.onEvent(DiaryHistoryEvent.SelectView(DiaryHistoryView.Week))
        advanceUntilIdle()

        val second = viewModel(savedState)
        advanceUntilIdle()

        val content = second.content()
        assertEquals(YearMonth(2026, 9), content.month)
        assertEquals(LocalDate(2026, 9, 5), content.selectedDay?.date)
        assertEquals(DiaryHistoryView.Week, content.view)
    }

    @Test
    fun refresh_picksUpChangesWithoutALoadingFlashAndKeepsTheSelection() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(DiaryHistoryEvent.SelectView(DiaryHistoryView.Week))
        model.onEvent(DiaryHistoryEvent.SelectDay(oct18))
        assertFalse(oct18 in model.content().markedDays)

        logged(oct18, 1840.0)
        diary.summariesGate = CompletableDeferred()
        model.onEvent(DiaryHistoryEvent.Refresh)
        runCurrent()
        assertIs<DiaryHistoryUiState.Content>(model.uiState.value)
        diary.summariesGate?.complete(Unit)
        advanceUntilIdle()

        val content = model.content()
        assertTrue(oct18 in content.markedDays)
        assertEquals(oct18, content.selectedDay?.date)
        assertEquals(DiaryHistoryView.Week, content.view)
        assertEquals(1, content.week?.loggedDays)
    }

    @Test
    fun refresh_failure_keepsWhatIsShown() = runTest(dispatcher) {
        logged(oct18, 1840.0)
        val model = viewModel()
        advanceUntilIdle()

        diary.failSummaries()
        model.onEvent(DiaryHistoryEvent.Refresh)
        advanceUntilIdle()

        assertTrue(oct18 in model.content().markedDays)
    }
}
