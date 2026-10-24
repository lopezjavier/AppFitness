package io.github.lopezjavier.appfitness.feature.food

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.feature.templates.FakeMealTemplateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * US-21 Comidas tab: tapping a row opens the details; swiping a row to the end deletes it at once; the reload.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One method per scenario.
class MealTemplatesManageTest {
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

    private fun viewModel(state: SavedStateHandle = SavedStateHandle()) = foodSearchModel(
        repository = repository,
        connectivity = connectivity,
        scanner = FakeBarcodeScanner(),
        state = state,
        templates = templates,
    )

    private fun TestScope.openComidas(model: FoodSearchViewModel) {
        model.onEvent(FoodSearchEvent.TabSelected(FoodSearchTab.Templates))
        advanceUntilIdle()
    }

    private fun FoodSearchViewModel.names(): List<String> {
        val results = uiState.value.templates
        assertTrue(results is TemplatesResults.Content)
        return results.rows.map { it.template.name }
    }

    @Test
    fun tappingARow_opensTheDetails() = runTest(dispatcher) {
        templates.templates += desayunoA
        val model = viewModel()
        openComidas(model)

        model.effects.test {
            model.onEvent(FoodSearchEvent.OpenTemplate("t1"))
            assertEquals(FoodSearchEffect.OpenTemplateDetails("t1"), awaitItem())
        }
    }

    @Test
    fun deleting_removesTheComidaAtOnce_andTheRowStaysGone() = runTest(dispatcher) {
        templates.templates += desayunoA
        templates.templates += desayunoA.copy(id = "t2", name = "Comida 2")
        val model = viewModel()
        openComidas(model)

        model.effects.test {
            model.onEvent(FoodSearchEvent.DeleteTemplate("t1"))
            advanceUntilIdle()
            expectNoEvents()
        }

        assertEquals(listOf("t2"), templates.templates.map { it.id })
        assertEquals(listOf("Comida 2"), model.names())
    }

    @Test
    fun deletingTheOnlyRow_showsTheEmptyState() = runTest(dispatcher) {
        templates.templates += desayunoA
        val model = viewModel()
        openComidas(model)

        model.onEvent(FoodSearchEvent.DeleteTemplate("t1"))
        advanceUntilIdle()

        assertEquals(TemplatesResults.Empty, model.uiState.value.templates)
        assertTrue(templates.templates.isEmpty())
    }

    @Test
    fun deleteFailure_bringsTheRowBack_andEmitsManageFailed() = runTest(dispatcher) {
        templates.templates += desayunoA
        templates.templates += desayunoA.copy(id = "t2", name = "Comida 2")
        templates.deleteFailure = AppError.Network
        val model = viewModel()
        openComidas(model)

        model.effects.test {
            model.onEvent(FoodSearchEvent.DeleteTemplate("t1"))
            advanceUntilIdle()
            assertEquals(FoodSearchEffect.TemplateManageFailed, awaitItem())
        }

        assertEquals(listOf("Comida 2", "Desayuno A"), model.names())
    }

    @Test
    fun deletingAnUnknownComida_deletesNothing() = runTest(dispatcher) {
        templates.templates += desayunoA
        val model = viewModel()
        openComidas(model)

        model.onEvent(FoodSearchEvent.DeleteTemplate("nope"))
        advanceUntilIdle()

        assertEquals(1, templates.templates.size)
    }

    @Test
    fun deletingOffline_isIgnored() = runTest(dispatcher) {
        templates.templates += desayunoA
        val model = viewModel()
        openComidas(model)
        connectivity.state.value = false
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(FoodSearchEvent.DeleteTemplate("t1"))
            advanceUntilIdle()
            expectNoEvents()
        }

        assertEquals(1, templates.templates.size)
    }

    @Test
    fun aViewModelRecreatedFromTheSameState_keepsTheComidasList() = runTest(dispatcher) {
        templates.templates += desayunoA
        val state = SavedStateHandle()
        openComidas(viewModel(state))

        val recreated = viewModel(state)
        advanceUntilIdle()

        assertEquals(listOf("Desayuno A"), recreated.names())
    }

    @Test
    fun refresh_reloadsTheList_afterTheEditorChangedIt() = runTest(dispatcher) {
        templates.templates += desayunoA
        val model = viewModel()
        openComidas(model)
        templates.templates[0] = desayunoA.copy(name = "Desayuno editado")

        model.onEvent(FoodSearchEvent.RefreshTemplates)
        advanceUntilIdle()

        assertEquals(listOf("Desayuno editado"), model.names())
    }
}
