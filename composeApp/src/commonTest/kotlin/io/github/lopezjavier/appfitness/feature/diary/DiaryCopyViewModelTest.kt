package io.github.lopezjavier.appfitness.feature.diary

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.usecase.BuildDiaryDay
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
class DiaryCopyViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate(2026, 10, 19)
    private val yesterday = LocalDate(2026, 10, 18)
    private val tomorrow = LocalDate(2026, 10, 20)
    private val slots = FakeMealSlotRepository()
    private val diary = FakeFoodDiaryRepository()
    private val goals = FakeNutritionGoalsRepository(
        NutritionGoals(GoalStrategy.Maintenance, 0, 2400, MacroSplit(30, 45, 25)),
    )
    private val connectivity = FakeConnectivityObserver()
    private val savedState = SavedStateHandle()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(state: SavedStateHandle = savedState) = DiaryViewModel(
        slots,
        diary,
        goals,
        BuildDiaryDay(),
        MutableClock(),
        FakeDiaryPreferences(),
        connectivity,
        state,
    )

    private fun DiaryViewModel.content() = assertIs<DiaryUiState.Content>(uiState.value).content

    private fun entry(id: String, slotId: String, kcal: Double, date: LocalDate = today) =
        FoodLogEntry(id, date, slotId, "f-$id", "Food $id", 120.0, "1 ración", "g", kcal, 10.0, 20.0, 5.0)

    private fun seedLunchOfThree() {
        diary.server[today] = listOf(
            entry("a", "lunch", 300.0),
            entry("b", "lunch", 200.0),
            entry("c", "lunch", 100.0),
            entry("d", "breakfast", 50.0),
        )
    }

    @Test
    fun enterSelection_selectsThatEntryAndToggleAddsAndRemoves() = runTest(dispatcher) {
        seedLunchOfThree()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(DiaryEvent.EnterSelection("a"))
        model.onEvent(DiaryEvent.ToggleSelected("b"))
        advanceUntilIdle()
        assertEquals(setOf("a", "b"), model.content().selectedIds)
        assertTrue(model.content().isSelecting)

        model.onEvent(DiaryEvent.ToggleSelected("a"))
        advanceUntilIdle()
        assertEquals(setOf("b"), model.content().selectedIds)
    }

    @Test
    fun clearSelection_leavesSelectionMode() = runTest(dispatcher) {
        seedLunchOfThree()
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(DiaryEvent.EnterSelection("a"))

        model.onEvent(DiaryEvent.ClearSelection)
        advanceUntilIdle()

        assertFalse(model.content().isSelecting)
    }

    @Test
    fun selectMeal_selectsEveryEntryOfThatMeal() = runTest(dispatcher) {
        seedLunchOfThree()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(DiaryEvent.SelectMeal("lunch"))
        advanceUntilIdle()

        assertEquals(setOf("a", "b", "c"), model.content().selectedIds)
    }

    @Test
    fun selection_survivesARecreatedViewModel() = runTest(dispatcher) {
        seedLunchOfThree()
        val first = viewModel()
        advanceUntilIdle()
        first.onEvent(DiaryEvent.EnterSelection("a"))
        first.onEvent(DiaryEvent.ToggleSelected("c"))
        advanceUntilIdle()

        val second = viewModel(savedState)
        advanceUntilIdle()

        assertEquals(setOf("a", "c"), second.content().selectedIds)
    }

    @Test
    fun selection_dropsEntriesThatNoLongerExist() = runTest(dispatcher) {
        seedLunchOfThree()
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(DiaryEvent.SelectMeal("lunch"))
        advanceUntilIdle()

        diary.deleteEntry("b")
        advanceUntilIdle()

        assertEquals(setOf("a", "c"), model.content().selectedIds)
    }

    @Test
    fun changingDay_clearsTheSelection() = runTest(dispatcher) {
        seedLunchOfThree()
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(DiaryEvent.EnterSelection("a"))

        model.onEvent(DiaryEvent.PreviousDay)
        advanceUntilIdle()

        assertFalse(model.content().isSelecting)
    }

    @Test
    fun copySelected_opensTheCopySheetWithTheSelectedEntries() = runTest(dispatcher) {
        seedLunchOfThree()
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(DiaryEvent.EnterSelection("a"))
        model.onEvent(DiaryEvent.ToggleSelected("b"))
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(DiaryEvent.CopySelected)
            val effect = assertIs<DiaryEffect.OpenCopy>(awaitItem())
            assertEquals(setOf("a", "b"), effect.entryIds.toSet())
            assertEquals(today, effect.date)
        }
    }

    @Test
    fun copyMeal_opensTheCopySheetWithEveryEntryOfTheMeal() = runTest(dispatcher) {
        seedLunchOfThree()
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(DiaryEvent.CopyMeal("lunch"))
            assertEquals(DiaryEffect.OpenCopy(listOf("a", "b", "c"), today), awaitItem())
        }
    }

    @Test
    fun confirmCopy_toAnotherMeal_isOneRequestKeepingAmountsAndSnapshots() = runTest(dispatcher) {
        seedLunchOfThree()
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(DiaryEvent.EnterSelection("a"))
        model.onEvent(DiaryEvent.ToggleSelected("b"))

        model.effects.test {
            model.onEvent(DiaryEvent.ConfirmCopy(listOf("a", "b"), "dinner", today))
            advanceUntilIdle()
            assertEquals(DiaryEffect.ShowUndo(UndoKind.Copy, CopyTarget("dinner", today)), awaitItem())
        }

        assertEquals(1, diary.addedBatches.size)
        val batch = diary.addedBatches.single()
        assertEquals(listOf("f-a", "f-b"), batch.map { it.foodId })
        assertEquals(setOf("dinner"), batch.map { it.mealSlotId }.toSet())
        assertEquals(listOf(120.0, 120.0), batch.map { it.amount })
        assertEquals(listOf("1 ración", "1 ración"), batch.map { it.portionLabel })
        assertEquals(listOf(300.0, 200.0), batch.map { it.snapshot.kcal })
        val content = model.content()
        assertEquals(2, content.meals.first { it.slot.id == "dinner" }.entries.size)
        assertEquals(650.0 + 500.0, content.dayTotals.kcal)
        assertFalse(content.isSelecting)
    }

    @Test
    fun confirmCopy_toAnotherDay_isOneRequestAndLeavesTheVisibleDayUnchanged() = runTest(dispatcher) {
        seedLunchOfThree()
        val model = viewModel()
        advanceUntilIdle()
        val totals = model.content().dayTotals.kcal

        model.onEvent(DiaryEvent.ConfirmCopy(listOf("a", "b", "c"), "lunch", tomorrow))
        advanceUntilIdle()

        assertEquals(1, diary.addedBatches.size)
        assertEquals(setOf(tomorrow), diary.addedBatches.single().map { it.date }.toSet())
        assertEquals(today, model.content().date)
        assertEquals(totals, model.content().dayTotals.kcal)
    }

    @Test
    fun confirmCopy_failure_reportsTheErrorAndOffersNoUndo() = runTest(dispatcher) {
        seedLunchOfThree()
        diary.failBatch()
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(DiaryEvent.ConfirmCopy(listOf("a"), "dinner", today))
            advanceUntilIdle()
            assertEquals(DiaryEffect.WriteFailed(AppError.Network), awaitItem())
        }
        model.onEvent(DiaryEvent.UndoCopy)
        advanceUntilIdle()

        assertTrue(diary.deletedBatches.isEmpty())
    }

    @Test
    fun confirmCopy_offline_isRefusedWithoutARequest() = runTest(dispatcher) {
        seedLunchOfThree()
        connectivity.state.value = false
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(DiaryEvent.ConfirmCopy(listOf("a"), "dinner", today))
            advanceUntilIdle()
            assertEquals(DiaryEffect.WriteFailed(AppError.Network), awaitItem())
        }

        assertTrue(diary.addedBatches.isEmpty())
    }

    @Test
    fun undoCopy_deletesTheCreatedEntriesInOneRequest() = runTest(dispatcher) {
        seedLunchOfThree()
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(DiaryEvent.ConfirmCopy(listOf("a", "b"), "dinner", today))
        advanceUntilIdle()

        model.onEvent(DiaryEvent.UndoCopy)
        advanceUntilIdle()

        assertEquals(listOf(listOf("batch-1-0", "batch-1-1")), diary.deletedBatches)
        assertTrue(model.content().meals.first { it.slot.id == "dinner" }.isEmpty)
    }

    @Test
    fun undoCopy_twice_deletesOnlyOnce() = runTest(dispatcher) {
        seedLunchOfThree()
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(DiaryEvent.ConfirmCopy(listOf("a"), "dinner", today))
        advanceUntilIdle()

        model.onEvent(DiaryEvent.UndoCopy)
        model.onEvent(DiaryEvent.UndoCopy)
        advanceUntilIdle()

        assertEquals(1, diary.deletedBatches.size)
    }

    @Test
    fun canRepeatYesterday_isTrueOnlyForEmptyMealsWhoseYesterdayHadFoods() = runTest(dispatcher) {
        diary.server[yesterday] = listOf(entry("y1", "lunch", 400.0, yesterday))
        diary.server[today] = listOf(entry("t1", "breakfast", 100.0))
        val model = viewModel()
        advanceUntilIdle()

        val repeat = model.content().canRepeatYesterday
        assertEquals(true, repeat["lunch"])
        assertEquals(false, repeat["dinner"])
        assertEquals(false, repeat["breakfast"])
    }

    @Test
    fun canRepeatYesterday_isFalseWhenYesterdayCannotBeLoaded() = runTest(dispatcher) {
        diary.server[yesterday] = listOf(entry("y1", "lunch", 400.0, yesterday))
        diary.failRefreshOf(yesterday)
        val model = viewModel()
        advanceUntilIdle()

        assertFalse(model.content().canRepeatYesterday["lunch"] == true)
    }

    @Test
    fun repeatYesterday_logsYesterdaysMealOnTheVisibleDayInOneRequest() = runTest(dispatcher) {
        diary.server[yesterday] = listOf(
            entry("y1", "lunch", 400.0, yesterday),
            entry("y2", "lunch", 150.0, yesterday),
            entry("y3", "dinner", 500.0, yesterday),
        )
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(DiaryEvent.RepeatYesterday("lunch"))
            advanceUntilIdle()
            assertEquals(DiaryEffect.ShowUndo(UndoKind.Repeat, CopyTarget("lunch", today)), awaitItem())
        }

        assertEquals(1, diary.addedBatches.size)
        assertEquals(listOf("f-y1", "f-y2"), diary.addedBatches.single().map { it.foodId })
        assertEquals(setOf(today), diary.addedBatches.single().map { it.date }.toSet())
        assertEquals(550.0, model.content().dayTotals.kcal)
        assertEquals(false, model.content().canRepeatYesterday["lunch"])
    }

    @Test
    fun repeatYesterday_thenUndo_deletesTheReturnedIds() = runTest(dispatcher) {
        diary.server[yesterday] = listOf(entry("y1", "lunch", 400.0, yesterday))
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(DiaryEvent.RepeatYesterday("lunch"))
        advanceUntilIdle()

        model.onEvent(DiaryEvent.UndoCopy)
        advanceUntilIdle()

        assertEquals(listOf(listOf("batch-1-0")), diary.deletedBatches)
        assertEquals(0.0, model.content().dayTotals.kcal)
    }

    @Test
    fun repeatYesterday_withNothingYesterday_doesNothing() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(DiaryEvent.RepeatYesterday("lunch"))
        advanceUntilIdle()

        assertTrue(diary.addedBatches.isEmpty())
    }

    @Test
    fun totals_areRecomputedAfterEachCacheEmission() = runTest(dispatcher) {
        seedLunchOfThree()
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(650.0, model.content().dayTotals.kcal)

        model.onEvent(DiaryEvent.ConfirmCopy(listOf("a"), "snack", today))
        advanceUntilIdle()
        assertEquals(950.0, model.content().dayTotals.kcal)

        model.onEvent(DiaryEvent.UndoCopy)
        advanceUntilIdle()
        assertEquals(650.0, model.content().dayTotals.kcal)
    }
}
