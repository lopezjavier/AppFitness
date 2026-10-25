package io.github.lopezjavier.appfitness.feature.food

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CookingMethod
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.domain.usecase.CookedItemNutrition
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.feature.templates.FakeMealTemplateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** US-21 / US-53: the Comidas tab of the food search lists the comidas; adding moved to the details screen. */
@OptIn(ExperimentalCoroutinesApi::class)
class MealTemplatesTabTest {
    private val dispatcher = StandardTestDispatcher()
    private val eggs = food("eggs", "Huevos revueltos", kcal = 100.0)
    private val shake = food("shake", "Batido de proteína", kcal = 50.0)
    private val repository = FakeFoodRepository()
    private val templates = FakeMealTemplateRepository(mutableMapOf("eggs" to eggs, "shake" to shake))
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

    private val preferences = FakeFoodPreferencesRepository()

    private fun viewModel(state: SavedStateHandle = SavedStateHandle()) = FoodSearchViewModel(
        foodRepository = repository,
        connectivity = connectivity,
        barcodeScanner = FakeBarcodeScanner(),
        mealTemplates = templates,
        foodPreferences = preferences,
        cookedItemNutrition = CookedItemNutrition(ScaleNutrition()),
        savedState = state,
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
    fun comidasTab_totalKcalIncludesTheCookingOil() = runTest(dispatcher) {
        templates.templates += desayunoA.copy(
            items = listOf(
                MealTemplateItem(eggs, 120.0, null, CookingMethod.Griddle),
                MealTemplateItem(shake, 300.0, "1 vaso"),
            ),
        )
        val model = viewModel()
        model.openTemplates()
        advanceUntilIdle()

        assertEquals(292.5, model.templateRows().single().kcal, 0.0)
    }

    @Test
    fun comidasTab_totalKcalOmitsTheOil_whenThePreferencesCannotBeRead() = runTest(dispatcher) {
        preferences.result = Result.failure(AppException(AppError.Network))
        templates.templates += desayunoA.copy(items = listOf(MealTemplateItem(eggs, 120.0, null, CookingMethod.Fried)))
        val model = viewModel()
        model.openTemplates()
        advanceUntilIdle()

        assertEquals(120.0, model.templateRows().single().kcal, 0.0)
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
