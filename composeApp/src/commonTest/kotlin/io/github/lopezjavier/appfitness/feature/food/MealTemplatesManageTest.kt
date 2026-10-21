package io.github.lopezjavier.appfitness.feature.food

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.feature.templates.FakeMealTemplateRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * US-21 "Renaming and deleting a comida" from the Comidas tab: the row menu, rename, delete and the editor hand-off.
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
        slotId = "lunch",
        templates = templates,
        clock = MutableClock(),
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
    fun renameOpensTheSheet_prefilledWithTheCurrentName() = runTest(dispatcher) {
        templates.templates += desayunoA
        val model = viewModel()
        openComidas(model)

        model.onEvent(FoodSearchEvent.RenameTemplate("t1"))
        advanceUntilIdle()

        val rename = model.uiState.value.manage.rename
        assertEquals("t1", rename?.templateId)
        assertEquals("Desayuno A", rename?.name)
    }

    @Test
    fun renamingSavesTheNewName_closesTheSheet_andUpdatesTheList() = runTest(dispatcher) {
        templates.templates += desayunoA
        val model = viewModel()
        openComidas(model)
        model.onEvent(FoodSearchEvent.RenameTemplate("t1"))

        model.onEvent(FoodSearchEvent.RenameNameChanged("Desayuno B"))
        model.onEvent(FoodSearchEvent.SubmitRename)
        advanceUntilIdle()

        assertNull(model.uiState.value.manage.rename)
        assertEquals(listOf("Desayuno B"), model.names())
    }

    @Test
    fun renamingToATakenName_keepsTheSheetOpen_withTheDuplicateError() = runTest(dispatcher) {
        templates.templates += desayunoA
        templates.templates += desayunoA.copy(id = "t2", name = "Comida 2")
        val model = viewModel()
        openComidas(model)
        model.onEvent(FoodSearchEvent.RenameTemplate("t1"))

        model.onEvent(FoodSearchEvent.RenameNameChanged("comida 2"))
        model.onEvent(FoodSearchEvent.SubmitRename)
        advanceUntilIdle()

        assertTrue(model.uiState.value.manage.rename?.nameTaken == true)
        assertEquals(listOf("Comida 2", "Desayuno A"), model.names())
    }

    @Test
    fun renamingToABlankName_doesNothing() = runTest(dispatcher) {
        templates.templates += desayunoA
        val model = viewModel()
        openComidas(model)
        model.onEvent(FoodSearchEvent.RenameTemplate("t1"))

        model.onEvent(FoodSearchEvent.RenameNameChanged("   "))
        model.onEvent(FoodSearchEvent.SubmitRename)
        advanceUntilIdle()

        assertTrue(model.uiState.value.manage.rename != null)
        assertEquals(listOf("Desayuno A"), model.names())
    }

    @Test
    fun renamingOffline_doesNothing() = runTest(dispatcher) {
        templates.templates += desayunoA
        val model = viewModel()
        openComidas(model)
        model.onEvent(FoodSearchEvent.RenameTemplate("t1"))
        connectivity.state.value = false
        advanceUntilIdle()

        model.onEvent(FoodSearchEvent.RenameNameChanged("Otra"))
        model.onEvent(FoodSearchEvent.SubmitRename)
        advanceUntilIdle()

        assertTrue(model.uiState.value.manage.rename != null)
        assertEquals("Desayuno A", templates.templates.single().name)
    }

    @Test
    fun deleteConfirmed_removesTheComida_andClosesTheDialog() = runTest(dispatcher) {
        templates.templates += desayunoA
        val model = viewModel()
        openComidas(model)

        model.onEvent(FoodSearchEvent.DeleteTemplate("t1"))
        advanceUntilIdle()
        assertEquals("t1", model.uiState.value.manage.deleteFor)
        model.onEvent(FoodSearchEvent.ConfirmDeleteTemplate)
        advanceUntilIdle()

        assertNull(model.uiState.value.manage.deleteFor)
        assertEquals(TemplatesResults.Empty, model.uiState.value.templates)
    }

    @Test
    fun deleteDismissed_keepsTheComida() = runTest(dispatcher) {
        templates.templates += desayunoA
        val model = viewModel()
        openComidas(model)
        model.onEvent(FoodSearchEvent.DeleteTemplate("t1"))

        model.onEvent(FoodSearchEvent.DismissDeleteTemplate)
        advanceUntilIdle()

        assertNull(model.uiState.value.manage.deleteFor)
        assertEquals(listOf("Desayuno A"), model.names())
    }

    @Test
    fun deleteFailure_emitsTemplateManageFailed_andKeepsTheComida() = runTest(dispatcher) {
        templates.templates += desayunoA
        templates.deleteFailure = AppError.Network
        val model = viewModel()
        openComidas(model)
        model.onEvent(FoodSearchEvent.DeleteTemplate("t1"))

        model.effects.test {
            model.onEvent(FoodSearchEvent.ConfirmDeleteTemplate)
            advanceUntilIdle()
            assertEquals(FoodSearchEffect.TemplateManageFailed, awaitItem())
        }

        assertEquals(listOf("Desayuno A"), model.names())
    }

    @Test
    fun editRequested_closesTheMenu_andOpensTheEditor() = runTest(dispatcher) {
        templates.templates += desayunoA
        val model = viewModel()
        openComidas(model)
        model.onEvent(FoodSearchEvent.OpenTemplateMenu("t1"))
        advanceUntilIdle()
        assertEquals("t1", model.uiState.value.manage.menuFor)

        model.effects.test {
            model.onEvent(FoodSearchEvent.EditTemplate("t1"))
            assertEquals(FoodSearchEffect.OpenTemplateEditor("t1"), awaitItem())
        }
        advanceUntilIdle()

        assertNull(model.uiState.value.manage.menuFor)
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

    @Test
    fun theRenameDraft_survivesTheViewModelBeingRecreated() = runTest(dispatcher) {
        templates.templates += desayunoA
        val state = SavedStateHandle()
        val first = viewModel(state)
        openComidas(first)
        first.onEvent(FoodSearchEvent.RenameTemplate("t1"))
        first.onEvent(FoodSearchEvent.RenameNameChanged("Mi comida"))

        val restored = viewModel(state)
        advanceUntilIdle()

        assertEquals("Mi comida", restored.uiState.value.manage.rename?.name)
    }
}
