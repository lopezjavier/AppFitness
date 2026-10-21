package io.github.lopezjavier.appfitness.feature.food

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.domain.usecase.AddTemplateToDiary
import io.github.lopezjavier.appfitness.domain.usecase.DefaultMealSlot
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.feature.diary.DefaultMealSlots
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.diary.FakeMealSlotRepository
import io.github.lopezjavier.appfitness.feature.templates.FakeMealTemplateRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** US-21 "Adding a comida to a meal": the Comidas tab of the food search lists the comidas and adds one to a meal. */
@OptIn(ExperimentalCoroutinesApi::class)
class MealTemplatesTabTest {
    private val dispatcher = StandardTestDispatcher()
    private val day = LocalDate(2026, 10, 19)
    private val eggs = food("eggs", "Huevos revueltos", kcal = 100.0)
    private val shake = food("shake", "Batido de proteína", kcal = 50.0)
    private val repository = FakeFoodRepository()
    private val templates = FakeMealTemplateRepository(mutableMapOf("eggs" to eggs, "shake" to shake))
    private val diary = FakeFoodDiaryRepository()
    private val connectivity = FakeConnectivityObserver()
    private val desayunoA = MealTemplate(
        id = "t1",
        name = "Desayuno A",
        mealSlotId = "breakfast",
        items = listOf(MealTemplateItem(eggs, 120.0, null), MealTemplateItem(shake, 300.0, "1 vaso")),
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(slotId: String? = "lunch", state: SavedStateHandle = SavedStateHandle()) =
        FoodSearchViewModel(
            foodRepository = repository,
            connectivity = connectivity,
            barcodeScanner = FakeBarcodeScanner(),
            slotId = slotId,
            date = day,
            mealTemplates = templates,
            addTemplateToDiary = AddTemplateToDiary(diary, ScaleNutrition()),
            scaleNutrition = ScaleNutrition(),
            mealSlots = FakeMealSlotRepository(),
            defaultMealSlot = DefaultMealSlot(),
            clock = MutableClock(),
            savedState = state,
            timeZone = TimeZone.UTC,
        )

    private fun FoodSearchViewModel.openTemplates() = onEvent(FoodSearchEvent.TabSelected(FoodSearchTab.Templates))

    private fun FoodSearchViewModel.templateRows(): List<TemplateSummary> {
        val results = uiState.value.templates
        assertIs<TemplatesResults.Content>(results)
        return results.rows
    }

    @Test
    fun comidasTab_listsEachComidaWithItsFoodCountAndTotalKcal() = runTest(dispatcher) {
        templates.templates += desayunoA
        val model = viewModel()
        model.openTemplates()
        advanceUntilIdle()

        val row = model.templateRows().single()
        assertEquals("Desayuno A", row.template.name)
        assertEquals(2, row.template.items.size)
        assertEquals(270.0, row.kcal, 0.0)
    }

    @Test
    fun comidasTab_isLoading_untilTheListArrives() = runTest(dispatcher) {
        val model = viewModel()
        model.openTemplates()

        assertEquals(TemplatesResults.Loading, model.uiState.value.templates)
    }

    @Test
    fun comidasTab_isEmpty_whenThereAreNoComidas() = runTest(dispatcher) {
        val model = viewModel()
        model.openTemplates()
        advanceUntilIdle()

        assertEquals(TemplatesResults.Empty, model.uiState.value.templates)
    }

    @Test
    fun comidasTab_isAnError_whenTheListFails() = runTest(dispatcher) {
        templates.listFailure = AppError.Network
        val model = viewModel()
        model.openTemplates()
        advanceUntilIdle()

        assertEquals(TemplatesResults.Error, model.uiState.value.templates)
    }

    @Test
    fun comidasTab_isAnError_offline_withoutAskingTheServer() = runTest(dispatcher) {
        templates.templates += desayunoA
        connectivity.state.value = false
        val model = viewModel()
        advanceUntilIdle()
        model.openTemplates()
        advanceUntilIdle()

        assertEquals(TemplatesResults.Error, model.uiState.value.templates)
    }

    @Test
    fun retry_reloadsTheComidas() = runTest(dispatcher) {
        templates.listFailure = AppError.Network
        val model = viewModel()
        model.openTemplates()
        advanceUntilIdle()
        templates.listFailure = null
        templates.templates += desayunoA

        model.onEvent(FoodSearchEvent.Retry)
        advanceUntilIdle()

        assertEquals("Desayuno A", model.templateRows().single().template.name)
    }

    @Test
    fun addingAComida_logsAllItsFoodsInOneRequest_toTheChosenMeal_andEmitsTemplateAdded() = runTest(dispatcher) {
        templates.templates += desayunoA
        val model = viewModel(slotId = "lunch")
        model.openTemplates()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(FoodSearchEvent.AddTemplate("t1"))
            advanceUntilIdle()
            assertEquals(FoodSearchEffect.TemplateAdded, awaitItem())
        }

        val batch = diary.addedBatches.single()
        assertEquals(listOf("eggs" to 120.0, "shake" to 300.0), batch.map { it.foodId to it.amount })
        assertTrue(batch.all { it.mealSlotId == "lunch" && it.date == day })
        assertEquals("1 vaso", batch.last().portionLabel)
    }

    @Test
    fun addingAComida_withoutAPreselectedMeal_usesTheDefaultMealSlot() = runTest(dispatcher) {
        templates.templates += desayunoA
        val model = viewModel(slotId = null)
        model.openTemplates()
        advanceUntilIdle()

        model.onEvent(FoodSearchEvent.AddTemplate("t1"))
        advanceUntilIdle()

        val expected = DefaultMealSlot()(LocalTime(10, 0), DefaultMealSlots)?.id
        assertEquals(expected, diary.addedBatches.single().first().mealSlotId)
    }

    @Test
    fun addingAComida_whenTheWriteFails_emitsTemplateFailed_andLogsNothing() = runTest(dispatcher) {
        templates.templates += desayunoA
        diary.failBatch()
        val model = viewModel()
        model.openTemplates()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(FoodSearchEvent.AddTemplate("t1"))
            advanceUntilIdle()
            assertEquals(FoodSearchEffect.TemplateFailed(AppError.Network), awaitItem())
        }
        assertTrue(diary.addedBatches.isEmpty())
    }

    @Test
    fun addingAComida_offline_logsNothing() = runTest(dispatcher) {
        templates.templates += desayunoA
        val model = viewModel()
        model.openTemplates()
        advanceUntilIdle()
        connectivity.state.value = false
        advanceUntilIdle()

        model.onEvent(FoodSearchEvent.AddTemplate("t1"))
        advanceUntilIdle()

        assertTrue(diary.addedBatches.isEmpty())
    }

    @Test
    fun theComidasTab_isRestoredAsTheSelectedTab_andLoadsItsList() = runTest(dispatcher) {
        templates.templates += desayunoA
        val state = SavedStateHandle()
        viewModel(state = state).openTemplates()

        val restored = viewModel(state = state)
        advanceUntilIdle()

        assertEquals(FoodSearchTab.Templates, restored.uiState.value.tab)
        assertEquals("Desayuno A", restored.templateRows().single().template.name)
    }
}
