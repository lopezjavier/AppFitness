package io.github.lopezjavier.appfitness.feature.diary

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.DiaryGoals
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.usecase.BuildDiaryDay
import io.github.lopezjavier.appfitness.feature.workout.DateLabel
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate(2026, 10, 19)
    private val yesterday = LocalDate(2026, 10, 18)
    private val slots = FakeMealSlotRepository()
    private val diary = FakeFoodDiaryRepository()
    private val goals = FakeNutritionGoalsRepository(
        NutritionGoals(GoalStrategy.Maintenance, 0, 2400, MacroSplit(30, 45, 25)),
    )
    private val savedState = SavedStateHandle()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(state: SavedStateHandle = savedState) =
        DiaryViewModel(slots, diary, goals, BuildDiaryDay(), MutableClock(), state)

    private fun DiaryViewModel.content() = assertIs<DiaryUiState.Content>(uiState.value).content

    private fun entry(
        id: String,
        slotId: String,
        kcal: Double,
        protein: Double = 0.0,
        carbs: Double = 0.0,
        fat: Double = 0.0,
        date: LocalDate = today,
    ) = FoodLogEntry(id, date, slotId, "f-$id", "Food $id", 100.0, null, "g", kcal, protein, carbs, fat)

    /** The 04 §2 example: 1.840 kcal, 142 / 210 / 48 g, spread over two meals. */
    private fun seedPlanExample() {
        diary.server[today] = listOf(
            entry("a", "breakfast", 700.5, 50.2, 80.0, 15.1),
            entry("b", "lunch", 1139.5, 91.8, 130.0, 32.9),
        )
    }

    @Test
    fun startsLoading_thenShowsTheDefaultSectionsInOrderForToday() = runTest(dispatcher) {
        val model = viewModel()
        assertEquals(DiaryUiState.Loading, model.uiState.value)

        advanceUntilIdle()

        val content = model.content()
        assertEquals(listOf("Desayuno", "Comida", "Merienda", "Cena"), content.meals.map { it.slot.label })
        assertEquals(DateLabel.Today, content.label)
        assertEquals(today, content.date)
    }

    @Test
    fun dayProgress_exposesTotalsRemainingAndGramTargets() = runTest(dispatcher) {
        seedPlanExample()
        val model = viewModel()
        advanceUntilIdle()

        val content = model.content()
        assertEquals(1840.0, content.dayTotals.kcal)
        assertEquals(142.0, content.dayTotals.proteinG)
        assertEquals(210.0, content.dayTotals.carbsG)
        assertEquals(48.0, content.dayTotals.fatG)
        assertEquals(DiaryGoalsState.Set(DiaryGoals(2400, 180, 270, 67), remainingKcal = 560), content.goals)
        assertEquals(700.5, content.meals.first { it.slot.id == "breakfast" }.totals.kcal)
    }

    @Test
    fun calorieExcess_hasNegativeRemaining() = runTest(dispatcher) {
        diary.server[today] = listOf(entry("a", "dinner", 2550.0))
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(-150, (model.content().goals as DiaryGoalsState.Set).remainingKcal)
    }

    @Test
    fun emptySection_showsTheDaysRemainingAsHint() = runTest(dispatcher) {
        seedPlanExample()
        val model = viewModel()
        advanceUntilIdle()

        val meals = model.content().meals.associateBy { it.slot.id }
        assertTrue(meals.getValue("snack").isEmpty)
        assertTrue(meals.getValue("snack").showRemainingHint)
        assertEquals(560, meals.getValue("snack").remainingKcal)
        assertFalse(meals.getValue("breakfast").isEmpty)
        assertFalse(meals.getValue("breakfast").showRemainingHint)
    }

    @Test
    fun emptySection_hasNoHintWhenNothingRemains() = runTest(dispatcher) {
        diary.server[today] = listOf(entry("a", "dinner", 2400.0), entry("b", "lunch", 150.0))
        val model = viewModel()
        advanceUntilIdle()

        assertTrue(model.content().meals.none { it.showRemainingHint })
    }

    @Test
    fun emptySection_hasNoHintWithoutGoals() = runTest(dispatcher) {
        goals.row = null
        val model = viewModel()
        advanceUntilIdle()

        val content = model.content()
        assertEquals(DiaryGoalsState.NotSet, content.goals)
        assertTrue(content.meals.all { it.isEmpty && !it.showRemainingHint && it.remainingKcal == null })
    }

    @Test
    fun previousDay_showsYesterdayWithItsEntries() = runTest(dispatcher) {
        diary.server[yesterday] = listOf(entry("y", "lunch", 800.0, date = yesterday))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(DiaryEvent.PreviousDay)
        advanceUntilIdle()

        val content = model.content()
        assertEquals(DateLabel.Yesterday, content.label)
        assertEquals(800.0, content.dayTotals.kcal)
        assertEquals(listOf("y"), content.meals.flatMap { it.entries }.map { it.id })
    }

    @Test
    fun changingTheDay_showsLoadingUntilTheNewDayArrives() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        diary.gate = CompletableDeferred()

        model.onEvent(DiaryEvent.PreviousDay)
        runCurrent()

        assertEquals(DiaryUiState.Loading, model.uiState.value)
        diary.gate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(DateLabel.Yesterday, model.content().label)
    }

    @Test
    fun nextDay_isBlockedOnTodayAndAllowedFromYesterday() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        assertFalse(model.content().canGoNext)

        model.onEvent(DiaryEvent.NextDay)
        advanceUntilIdle()
        assertEquals(today, model.content().date)

        model.onEvent(DiaryEvent.PreviousDay)
        advanceUntilIdle()
        assertTrue(model.content().canGoNext)

        model.onEvent(DiaryEvent.NextDay)
        advanceUntilIdle()
        assertEquals(today, model.content().date)
        assertEquals(DateLabel.Today, model.content().label)
    }

    @Test
    fun otherDays_areLabelledWithWeekdayDayAndMonth() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(DiaryEvent.PreviousDay)
        model.onEvent(DiaryEvent.PreviousDay)
        advanceUntilIdle()

        assertEquals(LocalDate(2026, 10, 17), model.content().date)
        assertIs<DateLabel.Other>(model.content().label)
    }

    @Test
    fun withoutGoals_openGoalsEmitsOpenPreferences() = runTest(dispatcher) {
        goals.row = null
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(DiaryEvent.OpenGoals)
            assertEquals(DiaryEffect.OpenPreferences, awaitItem())
        }
    }

    @Test
    fun addToMeal_emitsTheSlotAndTheVisibleDay() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(DiaryEvent.PreviousDay)
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(DiaryEvent.AddToMeal("dinner"))
            assertEquals(DiaryEffect.AddToMeal("dinner", yesterday), awaitItem())
        }
    }

    @Test
    fun toggleSection_collapsesAndExpands() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(DiaryEvent.ToggleSection("lunch"))
        advanceUntilIdle()
        assertEquals(setOf("lunch"), model.content().collapsedSlotIds)

        model.onEvent(DiaryEvent.ToggleSection("lunch"))
        advanceUntilIdle()
        assertEquals(emptySet(), model.content().collapsedSlotIds)
    }

    @Test
    fun failedLoad_showsErrorAndRetryRecovers() = runTest(dispatcher) {
        slots.result = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(DiaryUiState.Error, model.uiState.value)

        slots.result = Result.success(DefaultMealSlots)
        model.onEvent(DiaryEvent.Retry)
        advanceUntilIdle()

        assertEquals(4, model.content().meals.size)
    }

    @Test
    fun failedEntries_showErrorAndRetryReloadsTheDay() = runTest(dispatcher) {
        diary.failRefresh()
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(DiaryUiState.Error, model.uiState.value)

        diary.refreshResult = Result.success(Unit)
        model.onEvent(DiaryEvent.Retry)
        advanceUntilIdle()

        assertEquals(today, model.content().date)
    }

    @Test
    fun refresh_picksUpNewGoalsWithoutALoadingFlash() = runTest(dispatcher) {
        goals.row = null
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(DiaryGoalsState.NotSet, model.content().goals)

        model.uiState.test {
            assertIs<DiaryUiState.Content>(awaitItem())
            goals.row = NutritionGoals(GoalStrategy.Maintenance, 0, 2400, MacroSplit(30, 45, 25))
            model.onEvent(DiaryEvent.Refresh)
            advanceUntilIdle()
            val next = awaitItem()
            assertIs<DiaryGoalsState.Set>(assertIs<DiaryUiState.Content>(next).content.goals)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun failedRefresh_keepsTheContent() = runTest(dispatcher) {
        seedPlanExample()
        val model = viewModel()
        advanceUntilIdle()

        diary.failRefresh()
        slots.result = Result.failure(AppException(AppError.Network))
        model.onEvent(DiaryEvent.Refresh)
        advanceUntilIdle()

        assertEquals(1840.0, model.content().dayTotals.kcal)
    }

    @Test
    fun repositoryUpdate_reemitsTheListAndTheTotals() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(0.0, model.content().dayTotals.kcal)

        diary.push(today, listOf(entry("n", "snack", 250.0)))
        advanceUntilIdle()

        val content = model.content()
        assertEquals(250.0, content.dayTotals.kcal)
        assertEquals(listOf("n"), content.meals.first { it.slot.id == "snack" }.entries.map { it.id })
        assertEquals(2150, (content.goals as DiaryGoalsState.Set).remainingKcal)
    }

    @Test
    fun recreatedViewModel_keepsTheDayAndTheCollapsedSections() = runTest(dispatcher) {
        val first = viewModel()
        advanceUntilIdle()
        first.onEvent(DiaryEvent.PreviousDay)
        first.onEvent(DiaryEvent.ToggleSection("snack"))
        first.onEvent(DiaryEvent.ToggleSection("dinner"))
        advanceUntilIdle()

        val second = viewModel(savedState)
        advanceUntilIdle()

        val content = second.content()
        assertEquals(DateLabel.Yesterday, content.label)
        assertEquals(setOf("snack", "dinner"), content.collapsedSlotIds)
    }
}
