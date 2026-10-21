package io.github.lopezjavier.appfitness.feature.templates

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
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

/** US-21 comida details: the foods and quantities, rename and delete from the details. */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One method per scenario.
class TemplateDetailsViewModelTest {
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

    private fun viewModel(state: SavedStateHandle = SavedStateHandle()) = TemplateDetailsViewModel(
        templateId = "t1",
        mealTemplates = repository,
        scaleNutrition = ScaleNutrition(),
        validateTemplateName = ValidateTemplateName(),
        connectivity = connectivity,
        savedState = state,
    )

    private fun TestScope.contentOf(model: TemplateDetailsViewModel): TemplateDetailsUiState.Content {
        advanceUntilIdle()
        val state = model.uiState.value
        assertIs<TemplateDetailsUiState.Content>(state)
        return state
    }

    @Test
    fun loadingShowsTheFoodsWithTheirQuantities_andTheTotalKcal() = runTest(dispatcher) {
        repository.templates += desayunoA
        val content = contentOf(viewModel())

        assertEquals("Desayuno A", content.name)
        assertEquals(listOf("Huevos revueltos", "Batido de proteína"), content.rows.map { it.name })
        assertEquals(listOf("120", "300"), content.rows.map { it.amountText })
        assertEquals(listOf(null, "1 vaso"), content.rows.map { it.portionLabel })
        assertEquals(listOf(0, 1), content.rows.map { it.index })
        assertEquals(120.0 * 100.0 / 100.0 + 300.0 * 50.0 / 100.0, content.kcal, 0.001)
    }

    @Test
    fun theSameFoodTwice_isTwoRows() = runTest(dispatcher) {
        repository.templates +=
            desayunoA.copy(items = listOf(MealTemplateItem(eggs, 100.0, null), MealTemplateItem(eggs, 50.0, null)))

        val content = contentOf(viewModel())

        assertEquals(listOf("100", "50"), content.rows.map { it.amountText })
    }

    @Test
    fun aMissingComida_isAnError_andRetryLoadsItOnceItExists() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        assertIs<TemplateDetailsUiState.Error>(model.uiState.value)

        repository.templates += desayunoA
        model.onEvent(TemplateDetailsEvent.Retry)

        assertEquals("Desayuno A", contentOf(model).name)
    }

    @Test
    fun aFailedLoad_isAnError() = runTest(dispatcher) {
        repository.templates += desayunoA
        repository.listFailure = AppError.Network
        val model = viewModel()
        advanceUntilIdle()

        assertIs<TemplateDetailsUiState.Error>(model.uiState.value)
    }

    @Test
    fun refresh_picksUpTheChangesOfTheEditor_withoutShowingLoading() = runTest(dispatcher) {
        repository.templates += desayunoA
        val model = viewModel()
        advanceUntilIdle()
        repository.templates[0] = desayunoA.copy(items = listOf(MealTemplateItem(eggs, 200.0, null)))

        model.onEvent(TemplateDetailsEvent.Refresh)
        assertIs<TemplateDetailsUiState.Content>(model.uiState.value)
        advanceUntilIdle()

        assertEquals(listOf("200"), contentOf(model).rows.map { it.amountText })
    }

    @Test
    fun openRename_prefillsTheCurrentName() = runTest(dispatcher) {
        repository.templates += desayunoA
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TemplateDetailsEvent.OpenRename)

        assertEquals("Desayuno A", contentOf(model).rename?.name)
    }

    @Test
    fun renaming_savesTheNewName_closesTheSheet_andShowsTheNewName() = runTest(dispatcher) {
        repository.templates += desayunoA
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TemplateDetailsEvent.OpenRename)

        model.onEvent(TemplateDetailsEvent.RenameNameChanged("Desayuno B"))
        model.onEvent(TemplateDetailsEvent.SubmitRename)
        advanceUntilIdle()

        val content = contentOf(model)
        assertNull(content.rename)
        assertEquals("Desayuno B", content.name)
        assertEquals("Desayuno B", repository.templates.single().name)
    }

    @Test
    fun renamingToATakenName_keepsTheSheetOpen_withTheInlineError() = runTest(dispatcher) {
        repository.templates += desayunoA
        repository.templates += desayunoA.copy(id = "t2", name = "Comida 2")
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TemplateDetailsEvent.OpenRename)

        model.onEvent(TemplateDetailsEvent.RenameNameChanged("comida 2"))
        model.onEvent(TemplateDetailsEvent.SubmitRename)
        advanceUntilIdle()

        val rename = contentOf(model).rename
        assertEquals(TemplateNameError.Taken, rename?.nameError)
        assertFalse(rename?.canSubmit ?: true)
        assertEquals("Desayuno A", repository.templates.first { it.id == "t1" }.name)
    }

    @Test
    fun editingTheNameAfterATakenError_clearsTheError() = runTest(dispatcher) {
        repository.templates += desayunoA
        repository.templates += desayunoA.copy(id = "t2", name = "Comida 2")
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TemplateDetailsEvent.OpenRename)
        model.onEvent(TemplateDetailsEvent.RenameNameChanged("Comida 2"))
        model.onEvent(TemplateDetailsEvent.SubmitRename)
        advanceUntilIdle()

        model.onEvent(TemplateDetailsEvent.RenameNameChanged("Comida 3"))

        assertNull(contentOf(model).rename?.nameError)
    }

    @Test
    fun renamingToABlankName_isBlocked() = runTest(dispatcher) {
        repository.templates += desayunoA
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TemplateDetailsEvent.OpenRename)

        model.onEvent(TemplateDetailsEvent.RenameNameChanged("   "))
        model.onEvent(TemplateDetailsEvent.SubmitRename)
        advanceUntilIdle()

        assertFalse(contentOf(model).rename?.canSubmit ?: true)
        assertEquals("Desayuno A", repository.templates.single().name)
    }

    @Test
    fun renamingToATooLongName_showsTheInlineError_andIsBlocked() = runTest(dispatcher) {
        repository.templates += desayunoA
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TemplateDetailsEvent.OpenRename)

        model.onEvent(TemplateDetailsEvent.RenameNameChanged("x".repeat(61)))
        model.onEvent(TemplateDetailsEvent.SubmitRename)
        advanceUntilIdle()

        assertEquals(TemplateNameError.TooLong, contentOf(model).rename?.nameError)
        assertEquals("Desayuno A", repository.templates.single().name)
    }

    @Test
    fun offline_renameAndDeleteDoNothing() = runTest(dispatcher) {
        repository.templates += desayunoA
        val model = viewModel()
        advanceUntilIdle()
        connectivity.state.value = false
        advanceUntilIdle()

        model.onEvent(TemplateDetailsEvent.OpenRename)
        model.onEvent(TemplateDetailsEvent.Delete)

        val content = contentOf(model)
        assertTrue(content.isOffline)
        assertNull(content.rename)
        assertFalse(content.confirmDelete)
    }

    @Test
    fun renameFailure_emitsActionFailed_andKeepsTheSheet() = runTest(dispatcher) {
        repository.templates += desayunoA
        repository.renameFailure = AppError.Network
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TemplateDetailsEvent.OpenRename)
        model.onEvent(TemplateDetailsEvent.RenameNameChanged("Otra"))

        model.effects.test {
            model.onEvent(TemplateDetailsEvent.SubmitRename)
            advanceUntilIdle()
            assertEquals(TemplateDetailsEffect.ActionFailed(AppError.Network), awaitItem())
        }

        assertEquals("Otra", contentOf(model).rename?.name)
    }

    @Test
    fun deleteConfirmed_removesTheComida_andEmitsDeleted() = runTest(dispatcher) {
        repository.templates += desayunoA
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TemplateDetailsEvent.Delete)
        assertTrue(contentOf(model).confirmDelete)

        model.effects.test {
            model.onEvent(TemplateDetailsEvent.ConfirmDelete)
            advanceUntilIdle()
            assertEquals(TemplateDetailsEffect.Deleted, awaitItem())
        }

        assertTrue(repository.templates.isEmpty())
    }

    @Test
    fun deleteDismissed_keepsTheComida() = runTest(dispatcher) {
        repository.templates += desayunoA
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TemplateDetailsEvent.Delete)

        model.onEvent(TemplateDetailsEvent.DismissDelete)

        assertFalse(contentOf(model).confirmDelete)
        assertEquals(1, repository.templates.size)
    }

    @Test
    fun deleteFailure_emitsActionFailed_andKeepsTheComida() = runTest(dispatcher) {
        repository.templates += desayunoA
        repository.deleteFailure = AppError.Network
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TemplateDetailsEvent.Delete)

        model.effects.test {
            model.onEvent(TemplateDetailsEvent.ConfirmDelete)
            advanceUntilIdle()
            assertEquals(TemplateDetailsEffect.ActionFailed(AppError.Network), awaitItem())
        }

        val content = contentOf(model)
        assertFalse(content.confirmDelete)
        assertFalse(content.busy)
        assertEquals(1, repository.templates.size)
    }

    @Test
    fun theRenameDraft_survivesTheViewModelBeingRecreated() = runTest(dispatcher) {
        repository.templates += desayunoA
        val state = SavedStateHandle()
        val first = viewModel(state)
        advanceUntilIdle()
        first.onEvent(TemplateDetailsEvent.OpenRename)
        first.onEvent(TemplateDetailsEvent.RenameNameChanged("Mi comida"))

        val restored = viewModel(state)

        assertEquals("Mi comida", contentOf(restored).rename?.name)
    }

    @Test
    fun theTakenFlag_survivesTheViewModelBeingRecreated() = runTest(dispatcher) {
        repository.templates += desayunoA
        repository.templates += desayunoA.copy(id = "t2", name = "Comida 2")
        val state = SavedStateHandle()
        val first = viewModel(state)
        advanceUntilIdle()
        first.onEvent(TemplateDetailsEvent.OpenRename)
        first.onEvent(TemplateDetailsEvent.RenameNameChanged("Comida 2"))
        first.onEvent(TemplateDetailsEvent.SubmitRename)
        advanceUntilIdle()

        val restored = viewModel(state)

        assertEquals(TemplateNameError.Taken, contentOf(restored).rename?.nameError)
    }

    @Test
    fun theOpenDeleteDialog_survivesTheViewModelBeingRecreated() = runTest(dispatcher) {
        repository.templates += desayunoA
        val state = SavedStateHandle()
        val first = viewModel(state)
        advanceUntilIdle()
        first.onEvent(TemplateDetailsEvent.Delete)

        val restored = viewModel(state)

        assertTrue(contentOf(restored).confirmDelete)
    }
}
