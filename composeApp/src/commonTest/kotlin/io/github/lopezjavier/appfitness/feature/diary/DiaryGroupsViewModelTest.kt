package io.github.lopezjavier.appfitness.feature.diary

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.MealRow
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.usecase.BuildDiaryDay
import io.github.lopezjavier.appfitness.feature.meals.defaults.FakeMealDefaultsRepository
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** US-55: comida groups in the Diario rows, their expanded state and the unchanged totals. */
@OptIn(ExperimentalCoroutinesApi::class)
class DiaryGroupsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate(2026, 10, 19)
    private val diary = FakeFoodDiaryRepository()
    private val savedState = SavedStateHandle()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(state: SavedStateHandle = savedState) = DiaryViewModel(
        FakeMealSlotRepository(),
        diary,
        FakeNutritionGoalsRepository(NutritionGoals(GoalStrategy.Maintenance, 0, 2400, MacroSplit(30, 45, 25))),
        BuildDiaryDay(),
        MutableClock(),
        FakeConnectivityObserver(),
        FakeMealDefaultsRepository(),
        state,
    )

    private fun DiaryViewModel.content() = assertIs<DiaryUiState.Content>(uiState.value).content

    private fun entry(id: String, kcal: Double, groupId: String? = null) = FoodLogEntry(
        id = id,
        logDate = today,
        mealSlotId = "breakfast",
        foodId = "f-$id",
        foodName = "Food $id",
        amount = 100.0,
        portionLabel = null,
        baseUnit = "g",
        kcal = kcal,
        proteinG = 1.0,
        carbsG = 2.0,
        fatG = 3.0,
        groupId = groupId,
        groupName = groupId?.let { "Avena con plátano" },
    )

    private fun seed() {
        diary.server[today] = listOf(entry("a", 150.0, "g1"), entry("b", 105.0, "g1"), entry("c", 80.0))
    }

    @Test
    fun rows_groupTheComidaEntriesAndKeepLooseOnesSingle() = runTest(dispatcher) {
        seed()
        val model = viewModel()
        advanceUntilIdle()

        val rows = model.content().meals.first { it.slot.id == "breakfast" }.rows

        assertEquals(2, rows.size)
        val group = assertIs<MealRow.Group>(rows[0])
        assertEquals(255.0, group.totals.kcal)
        assertIs<MealRow.Single>(rows[1])
    }

    @Test
    fun groupsAreCollapsedByDefault_andToggleGroupAddsAndRemovesTheId() = runTest(dispatcher) {
        seed()
        val model = viewModel()
        advanceUntilIdle()
        assertTrue(model.content().expandedGroupIds.isEmpty())

        model.onEvent(DiaryEvent.ToggleGroup("g1"))
        advanceUntilIdle()
        assertEquals(setOf("g1"), model.content().expandedGroupIds)

        model.onEvent(DiaryEvent.ToggleGroup("g1"))
        advanceUntilIdle()
        assertTrue(model.content().expandedGroupIds.isEmpty())
    }

    @Test
    fun expandedGroups_surviveARecreationFromTheSameSavedState() = runTest(dispatcher) {
        seed()
        viewModel().onEvent(DiaryEvent.ToggleGroup("g1"))

        val recreated = viewModel()
        advanceUntilIdle()

        assertEquals(setOf("g1"), recreated.content().expandedGroupIds)
    }

    @Test
    fun deletingOneIngredient_keepsTheGroupWithUpdatedTotals_andTheLastRemovesIt() = runTest(dispatcher) {
        seed()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(DiaryEvent.DeleteEntry("a"))
        advanceUntilIdle()
        val afterOne = model.content().meals.first { it.slot.id == "breakfast" }.rows
        assertEquals(105.0, assertIs<MealRow.Group>(afterOne[0]).totals.kcal)

        model.onEvent(DiaryEvent.DeleteEntry("b"))
        advanceUntilIdle()
        val afterLast = model.content().meals.first { it.slot.id == "breakfast" }.rows
        assertEquals(listOf("c"), afterLast.map { (it as MealRow.Single).entry.id })
    }

    @Test
    fun totals_equalTheFlatSums_whetherOrNotEntriesAreGrouped() = runTest(dispatcher) {
        seed()
        val model = viewModel()
        advanceUntilIdle()

        val content = model.content()
        assertEquals(335.0, content.dayTotals.kcal)
        assertEquals(335.0, content.meals.first { it.slot.id == "breakfast" }.totals.kcal)
    }
}
