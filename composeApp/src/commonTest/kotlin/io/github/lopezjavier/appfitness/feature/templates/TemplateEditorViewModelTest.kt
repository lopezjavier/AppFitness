package io.github.lopezjavier.appfitness.feature.templates

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.domain.usecase.QuantityCheck
import io.github.lopezjavier.appfitness.domain.usecase.ValidateQuantity
import io.github.lopezjavier.appfitness.domain.usecase.ValidateTemplateName
import io.github.lopezjavier.appfitness.feature.food.food
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** US-21 "Editing a comida": quantities, removals, "Sobrescribir" and "Guardar como nueva" from the editor. */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One method per scenario.
class TemplateEditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val eggs = food("eggs", "Huevos revueltos", kcal = 100.0)
    private val shake = food("shake", "Batido de proteína", kcal = 50.0)
    private val repository = FakeMealTemplateRepository(mutableMapOf("eggs" to eggs, "shake" to shake))
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

    private fun viewModel(state: SavedStateHandle = SavedStateHandle()) = TemplateEditorViewModel(
        templateId = "t1",
        mealTemplates = repository,
        validateQuantity = ValidateQuantity(),
        validateTemplateName = ValidateTemplateName(),
        connectivity = connectivity,
        savedState = state,
    )

    private fun TestScope.contentOf(model: TemplateEditorViewModel): TemplateEditorContent {
        advanceUntilIdle()
        val state = model.uiState.value
        assertIs<TemplateEditorUiState.Content>(state)
        return state.content
    }

    private fun TemplateEditorViewModel.saveAs(name: String) {
        onEvent(TemplateEditorEvent.SaveClicked)
        onEvent(TemplateEditorEvent.SaveAsNew)
        onEvent(TemplateEditorEvent.NewNameChanged(name))
    }

    @Test
    fun changingAnAmount_clearsThePortionLabel_ofThatFoodOnly() = runTest(dispatcher) {
        repository.templates += desayunoA
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TemplateEditorEvent.AmountChanged("eggs", "150"))
        model.onEvent(TemplateEditorEvent.SaveClicked)
        model.onEvent(TemplateEditorEvent.Overwrite)
        advanceUntilIdle()

        val items = repository.templates.single().items
        assertEquals(150.0, items.first { it.food.id == "eggs" }.amount, 0.0)
        assertNull(items.first { it.food.id == "eggs" }.portionLabel)
        assertEquals("1 vaso", items.first { it.food.id == "shake" }.portionLabel)
    }

    @Test
    fun removingAFood_savesTheComidaWithoutIt() = runTest(dispatcher) {
        repository.templates += desayunoA
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TemplateEditorEvent.RemoveFood("shake"))
        model.onEvent(TemplateEditorEvent.SaveClicked)
        model.onEvent(TemplateEditorEvent.Overwrite)
        advanceUntilIdle()

        assertEquals(listOf("eggs"), repository.templates.single().items.map { it.food.id })
    }

    @Test
    fun theLastFood_cannotBeRemoved() = runTest(dispatcher) {
        repository.templates += desayunoA
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TemplateEditorEvent.RemoveFood("eggs"))

        model.onEvent(TemplateEditorEvent.RemoveFood("shake"))

        val content = contentOf(model)
        assertEquals(listOf("shake"), content.rows.map { it.foodId })
        assertFalse(content.rows.single().removable)
    }

    @Test
    fun overwrite_keepsTheName_andReplacesTheItems() = runTest(dispatcher) {
        repository.templates += desayunoA
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TemplateEditorEvent.RemoveFood("shake"))

        model.effects.test {
            model.onEvent(TemplateEditorEvent.SaveClicked)
            model.onEvent(TemplateEditorEvent.Overwrite)
            advanceUntilIdle()
            assertEquals(TemplateEditorEffect.Saved, awaitItem())
        }

        assertEquals("Desayuno A", repository.templates.single().name)
        assertEquals(1, repository.templates.single().items.size)
    }

    @Test
    fun invalidAmounts_zeroAndAboveTheLimit_disableSave() = runTest(dispatcher) {
        repository.templates += desayunoA
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TemplateEditorEvent.AmountChanged("eggs", "0"))
        assertFalse(contentOf(model).canSave)
        model.onEvent(TemplateEditorEvent.AmountChanged("eggs", "5001"))
        assertFalse(contentOf(model).canSave)
        assertEquals(QuantityCheck.TooHigh, contentOf(model).rows.first { it.foodId == "eggs" }.quantityError)
    }

    @Test
    fun offline_disablesSave() = runTest(dispatcher) {
        repository.templates += desayunoA
        val model = viewModel()
        advanceUntilIdle()

        connectivity.state.value = false
        advanceUntilIdle()

        assertFalse(contentOf(model).canSave)
    }

    @Test
    fun saveAsNew_withANewName_createsASecondComida_andLeavesTheOriginalUntouched() = runTest(dispatcher) {
        repository.templates += desayunoA
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TemplateEditorEvent.AmountChanged("eggs", "200"))

        model.saveAs("Desayuno B")
        model.onEvent(TemplateEditorEvent.SubmitNewName)
        advanceUntilIdle()

        assertEquals(listOf("Desayuno A", "Desayuno B"), repository.templates.map { it.name })
        assertEquals(120.0, repository.templates.first { it.name == "Desayuno A" }.items.first().amount, 0.0)
        assertEquals(200.0, repository.templates.first { it.name == "Desayuno B" }.items.first().amount, 0.0)
    }

    @Test
    fun saveAsNew_withATakenName_showsTheDuplicateError_andSavesNothing() = runTest(dispatcher) {
        repository.templates += desayunoA
        val model = viewModel()
        advanceUntilIdle()

        model.saveAs("desayuno a")
        model.onEvent(TemplateEditorEvent.SubmitNewName)
        advanceUntilIdle()

        assertEquals(TemplateNameError.Taken, contentOf(model).newNameError)
        assertEquals(1, repository.templates.size)
    }

    @Test
    fun aFailedLoad_isAnError_andRetryLoadsTheComida() = runTest(dispatcher) {
        repository.listFailure = AppError.Network
        repository.templates += desayunoA
        val model = viewModel()
        advanceUntilIdle()
        assertIs<TemplateEditorUiState.Error>(model.uiState.value)

        repository.listFailure = null
        model.onEvent(TemplateEditorEvent.Retry)
        advanceUntilIdle()

        assertEquals("Desayuno A", contentOf(model).name)
    }

    @Test
    fun theDraft_survivesTheViewModelBeingRecreated() = runTest(dispatcher) {
        repository.templates += desayunoA
        val state = SavedStateHandle()
        val first = viewModel(state)
        advanceUntilIdle()
        first.onEvent(TemplateEditorEvent.AmountChanged("eggs", "150"))
        first.onEvent(TemplateEditorEvent.RemoveFood("shake"))

        val restored = viewModel(state)
        advanceUntilIdle()

        val content = contentOf(restored)
        assertEquals(listOf("eggs"), content.rows.map { it.foodId })
        assertEquals("150", content.rows.single().amountText)
    }

    @Test
    fun theDraft_keepsTheOpenChoiceAndTheTypedNewName() = runTest(dispatcher) {
        repository.templates += desayunoA
        val state = SavedStateHandle()
        val first = viewModel(state)
        advanceUntilIdle()
        first.saveAs("Mi comida")

        val restored = viewModel(state)
        advanceUntilIdle()

        assertEquals(EditorSheet.NewName, contentOf(restored).sheet)
        assertEquals("Mi comida", contentOf(restored).newName)
        assertTrue(contentOf(restored).canSubmitNewName)
    }
}
