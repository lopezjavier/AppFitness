package io.github.lopezjavier.appfitness.feature.diary

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.domain.model.DiaryDaySummary
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.usecase.BuildDiaryDay
import io.github.lopezjavier.appfitness.feature.meals.defaults.FakeMealDefaultsRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
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

/** The day-at-a-glance summary, `isToday`, "Hoy" and the day picker's marked days of [DiaryViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class DiaryDayPickerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate(2026, 10, 19)
    private val yesterday = LocalDate(2026, 10, 18)
    private val diary = FakeFoodDiaryRepository()
    private val goals = FakeNutritionGoalsRepository(
        NutritionGoals(GoalStrategy.Maintenance, 0, 2400, MacroSplit(30, 45, 25)),
    )
    private val savedState = SavedStateHandle()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = DiaryViewModel(
        FakeMealSlotRepository(),
        diary,
        goals,
        BuildDiaryDay(),
        MutableClock(),
        FakeConnectivityObserver(),
        FakeMealDefaultsRepository(),
        savedState,
    )

    private fun DiaryViewModel.content() = assertIs<DiaryUiState.Content>(uiState.value).content

    private fun entry(id: String, kcal: Double, protein: Double = 0.0, carbs: Double = 0.0, fat: Double = 0.0) =
        FoodLogEntry(id, today, "lunch", "f-$id", "Food $id", 100.0, null, "g", kcal, protein, carbs, fat)

    private fun summaryOf(day: LocalDate) = DiaryDaySummary(day, MealTotals(100.0, 0.0, 0.0, 0.0))

    @Test
    fun summary_roundsHalfUpAndTakesTheTargetFromTheGoals() = runTest(dispatcher) {
        diary.server[today] = listOf(entry("a", 1839.6, 79.5, 149.4, 40.05))
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(DiarySummary(1840, 2400, 80, 149, 40), model.content().summary)
    }

    @Test
    fun summary_withoutGoals_hasNoTarget() = runTest(dispatcher) {
        goals.row = null
        diary.server[today] = listOf(entry("a", 500.0))
        val model = viewModel()
        advanceUntilIdle()

        assertNull(model.content().summary.targetKcal)
        assertEquals(500, model.content().summary.kcal)
    }

    @Test
    fun isToday_isTrueOnTodayAndFalseAfterGoingToYesterday() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        assertTrue(model.content().isToday)

        model.onEvent(DiaryEvent.GoToDay(yesterday))
        advanceUntilIdle()

        assertFalse(model.content().isToday)
    }

    @Test
    fun goToToday_returnsToTodayAndPersistsTheDate() = runTest(dispatcher) {
        savedState["diary_date"] = yesterday.toString()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(DiaryEvent.GoToToday)
        advanceUntilIdle()

        assertEquals(today, model.content().date)
        assertEquals(today.toString(), savedState.get<String>("diary_date"))
    }

    @Test
    fun pickerMonthShown_publishesTheMarkedDaysOfThatMonthOnly() = runTest(dispatcher) {
        diary.summaryDays += summaryOf(LocalDate(2026, 10, 3))
        diary.summaryDays += summaryOf(LocalDate(2026, 9, 30))
        val model = viewModel()
        advanceUntilIdle()
        assertTrue(model.content().pickerMarkedDays.isEmpty())

        model.onEvent(DiaryEvent.PickerMonthShown(YearMonth(2026, 10)))
        advanceUntilIdle()

        assertEquals(setOf(LocalDate(2026, 10, 3)), model.content().pickerMarkedDays)
        assertEquals(listOf(LocalDate(2026, 10, 1) to LocalDate(2026, 10, 31)), diary.summaryRequests)
    }

    @Test
    fun pickerMonthShown_whenLoadingFails_leavesNoMarksAndNoErrorState() = runTest(dispatcher) {
        diary.summaryDays += summaryOf(LocalDate(2026, 10, 3))
        diary.failSummaries()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(DiaryEvent.PickerMonthShown(YearMonth(2026, 10)))
        advanceUntilIdle()

        assertTrue(model.content().pickerMarkedDays.isEmpty())
    }

    @Test
    fun pickerMonthShown_aSecondMonthCancelsTheFirstLoad() = runTest(dispatcher) {
        diary.summaryDays += summaryOf(LocalDate(2026, 9, 5))
        diary.summaryDays += summaryOf(LocalDate(2026, 10, 3))
        val model = viewModel()
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        diary.summariesGate = gate

        model.onEvent(DiaryEvent.PickerMonthShown(YearMonth(2026, 9)))
        runCurrent()
        diary.summariesGate = null
        model.onEvent(DiaryEvent.PickerMonthShown(YearMonth(2026, 10)))
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(setOf(LocalDate(2026, 10, 3)), model.content().pickerMarkedDays)
    }

    @Test
    fun aDayJustLogged_isMarkedWhenTheVisibleDaysEntriesChange() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(DiaryEvent.PickerMonthShown(YearMonth(2026, 10)))
        advanceUntilIdle()
        assertTrue(model.content().pickerMarkedDays.isEmpty())

        diary.summaryDays += summaryOf(today)
        diary.push(today, listOf(entry("a", 300.0)))
        advanceUntilIdle()

        assertEquals(setOf(today), model.content().pickerMarkedDays)
    }
}
