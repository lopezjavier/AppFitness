package io.github.lopezjavier.appfitness.feature.templates

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.domain.model.Micronutrient
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.MicronutrientUnit
import io.github.lopezjavier.appfitness.domain.model.Micronutrients
import io.github.lopezjavier.appfitness.domain.usecase.QuantityCheck
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.domain.usecase.ValidateQuantity
import io.github.lopezjavier.appfitness.domain.usecase.ValidateTemplateName
import io.github.lopezjavier.appfitness.feature.food.FakeFoodRepository
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** US-21 comida details as the editor (ADR-45): the draft, its totals, "Guardar" and the immediate delete. */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One method per scenario.
class TemplateDetailsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val eggs = food("eggs", "Huevos revueltos", kcal = 100.0)
    private val shake = food("shake", "Batido de proteína", kcal = 50.0)
    private val repository = FakeMealTemplateRepository(mutableMapOf("eggs" to eggs, "shake" to shake))
    private val foods = FakeFoodRepository()
    private val connectivity = FakeConnectivityObserver()
    private val desayunoA = MealTemplate(
        id = "t1",
        name = "Desayuno A",
        mealSlotId = "breakfast",
        items = listOf(MealTemplateItem(eggs, 120.0, null), MealTemplateItem(shake, 300.0, "1 vaso")),
    )
    private val eggsTwice = MealTemplate(
        id = "t1",
        name = "Doble huevo",
        mealSlotId = null,
        items = listOf(MealTemplateItem(eggs, 100.0, "1 ración"), MealTemplateItem(eggs, 50.0, "1 ración")),
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(state: SavedStateHandle = SavedStateHandle()) = TemplateDetailsViewModel(
        templateId = "t1",
        mealTemplates = repository,
        scaleNutrition = ScaleNutrition(),
        validateQuantity = ValidateQuantity(),
        validateTemplateName = ValidateTemplateName(),
        foodRepository = foods,
        connectivity = connectivity,
        savedState = state,
    )

    private fun TestScope.contentOf(model: TemplateDetailsViewModel): TemplateDetailsUiState.Content {
        advanceUntilIdle()
        val state = model.uiState.value
        assertIs<TemplateDetailsUiState.Content>(state)
        return state
    }

    private fun TestScope.loaded(template: MealTemplate = desayunoA): TemplateDetailsViewModel {
        repository.templates += template
        return viewModel().also { advanceUntilIdle() }
    }

    // --- Loading and totals ---

    @Test
    fun loading_showsTheFoodsWithTheirNutrition_andTheTotals() = runTest(dispatcher) {
        val content = contentOf(loaded())

        assertEquals("Desayuno A", content.name)
        assertEquals(listOf("Huevos revueltos", "Batido de proteína"), content.rows.map { it.name })
        assertEquals(listOf("120", "300"), content.rows.map { it.amountText })
        assertEquals(listOf(null, "1 vaso"), content.rows.map { it.portionLabel })
        assertEquals(listOf(0, 1), content.rows.map { it.index })
        assertEquals(listOf(120.0, 150.0), content.rows.map { it.kcal })
        assertEquals(listOf(12.0, 30.0), content.rows.map { it.proteinG })
        assertEquals(270.0, content.totalKcal, 0.001)
        assertEquals(42.0, content.totalProteinG, 0.001)
        assertEquals(84.0, content.totalCarbsG, 0.001)
        assertEquals(21.0, content.totalFatG, 0.001)
        assertFalse(content.isDirty)
        assertFalse(content.canSave)
    }

    @Test
    fun changingAGramsAmount_updatesTheRow_andTheTotals() = runTest(dispatcher) {
        val model = loaded(desayunoA.copy(items = listOf(MealTemplateItem(food("pasta", kcal = 130.0), 100.0, null))))
        repository.foods["pasta"] = food("pasta", kcal = 130.0)

        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "180"))

        val content = contentOf(model)
        assertEquals(234.0, content.rows.single().kcal, 0.001)
        assertEquals(234.0, content.totalKcal, 0.001)
    }

    @Test
    fun totals_sumTheEditedAmountsOfTwoFoods() = runTest(dispatcher) {
        val model = loaded()

        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "200"))
        model.onEvent(TemplateDetailsEvent.AmountChanged(1, "100"))

        assertEquals(200.0 + 50.0, contentOf(model).totalKcal, 0.001)
    }

    @Test
    fun removingAFood_dropsItsRow_andItsShareOfTheTotals() = runTest(dispatcher) {
        val model = loaded()

        model.onEvent(TemplateDetailsEvent.RemoveFood(1))

        val content = contentOf(model)
        assertEquals(listOf(0), content.rows.map { it.index })
        assertEquals(120.0, content.totalKcal, 0.001)
        assertEquals(12.0, content.totalProteinG, 0.001)
        assertTrue(content.isDirty)
    }

    @Test
    fun anInvalidAmount_setsTheError_countsAsZero_andDisablesSave() = runTest(dispatcher) {
        val model = loaded()

        listOf("" to QuantityCheck.TooLow, "0" to QuantityCheck.TooLow, "abc" to QuantityCheck.TooLow).forEach {
            model.onEvent(TemplateDetailsEvent.AmountChanged(0, it.first))
            val content = contentOf(model)
            assertEquals(it.second, content.rows.first().quantityError, "amount '${it.first}'")
            assertEquals(0.0, content.rows.first().kcal, 0.0)
            assertEquals(150.0, content.totalKcal, 0.001)
            assertFalse(content.canSave)
        }
        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "5001"))
        assertEquals(QuantityCheck.TooHigh, contentOf(model).rows.first().quantityError)
    }

    @Test
    fun anEditedAmount_dropsThePortionLabel_ofThatFoodOnly() = runTest(dispatcher) {
        val model = loaded()

        model.onEvent(TemplateDetailsEvent.AmountChanged(1, "250"))

        assertEquals(listOf(null, null), contentOf(model).rows.map { it.portionLabel })
        model.onEvent(TemplateDetailsEvent.AmountChanged(1, "300"))
        assertEquals("1 vaso", contentOf(model).rows[1].portionLabel)
    }

    @Test
    fun theLastFood_cannotBeRemoved() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.RemoveFood(0))

        model.onEvent(TemplateDetailsEvent.RemoveFood(1))

        val content = contentOf(model)
        assertEquals(listOf("shake"), content.rows.map { it.foodId })
        assertFalse(content.rows.single().removable)
    }

    @Test
    fun theSameFoodTwice_isTwoRows_withTheirOwnAmounts() = runTest(dispatcher) {
        val model = loaded(eggsTwice)

        model.onEvent(TemplateDetailsEvent.AmountChanged(1, "75"))

        val rows = contentOf(model).rows
        assertEquals(listOf(0, 1), rows.map { it.index })
        assertEquals(listOf("100", "75"), rows.map { it.amountText })
        model.onEvent(TemplateDetailsEvent.RemoveFood(0))
        model.onEvent(TemplateDetailsEvent.RemoveFood(1))
        assertEquals(listOf(1), contentOf(model).rows.map { it.index })
    }

    // --- Load states ---

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
    fun retryAndRefresh_doNotDiscardTheDraft() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "150"))
        model.onEvent(TemplateDetailsEvent.RemoveFood(1))
        model.onEvent(TemplateDetailsEvent.OpenRename)
        model.onEvent(TemplateDetailsEvent.RenameNameChanged("Mi desayuno"))
        model.onEvent(TemplateDetailsEvent.SubmitRename)

        model.onEvent(TemplateDetailsEvent.Refresh)
        advanceUntilIdle()
        model.onEvent(TemplateDetailsEvent.Retry)
        val content = contentOf(model)

        assertEquals(listOf("150"), content.rows.map { it.amountText })
        assertEquals("Mi desayuno", content.name)
    }

    // --- canSave ---

    @Test
    fun canSave_isTrueOnlyForAValidChangedDraft_online() = runTest(dispatcher) {
        val model = loaded()
        assertFalse(contentOf(model).canSave)

        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "150"))
        assertTrue(contentOf(model).canSave)

        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "120"))
        assertFalse(contentOf(model).isDirty)
        assertFalse(contentOf(model).canSave)
    }

    @Test
    fun offline_disablesSave_andIgnoresTheSaveClick() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "150"))

        connectivity.state.value = false
        advanceUntilIdle()
        model.onEvent(TemplateDetailsEvent.SaveClicked)

        val content = contentOf(model)
        assertTrue(content.isOffline)
        assertFalse(content.canSave)
        assertEquals(DetailsSheet.None, content.sheet)
    }

    // --- Rename (draft only) and save ---

    @Test
    fun rename_isDraftOnly_untilGuardar() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.OpenRename)
        assertEquals("Desayuno A", contentOf(model).rename?.name)

        model.onEvent(TemplateDetailsEvent.RenameNameChanged("Desayuno B"))
        model.onEvent(TemplateDetailsEvent.SubmitRename)

        val content = contentOf(model)
        assertEquals("Desayuno B", content.name)
        assertEquals(DetailsSheet.None, content.sheet)
        assertTrue(content.canSave)
        assertEquals("Desayuno A", repository.templates.single().name)
    }

    @Test
    fun rename_worksOffline_becauseItOnlyChangesTheDraft() = runTest(dispatcher) {
        val model = loaded()
        connectivity.state.value = false
        advanceUntilIdle()

        model.onEvent(TemplateDetailsEvent.OpenRename)
        model.onEvent(TemplateDetailsEvent.RenameNameChanged("Otra"))
        model.onEvent(TemplateDetailsEvent.SubmitRename)

        assertEquals("Otra", contentOf(model).name)
    }

    @Test
    fun aBlankOrTooLongRename_isBlocked() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.OpenRename)

        model.onEvent(TemplateDetailsEvent.RenameNameChanged("   "))
        model.onEvent(TemplateDetailsEvent.SubmitRename)
        assertEquals(DetailsSheet.Rename, contentOf(model).sheet)
        assertEquals("Desayuno A", contentOf(model).name)

        model.onEvent(TemplateDetailsEvent.RenameNameChanged("x".repeat(61)))
        model.onEvent(TemplateDetailsEvent.SubmitRename)
        assertEquals(TemplateNameError.TooLong, contentOf(model).rename?.nameError)
        assertEquals("Desayuno A", contentOf(model).name)
    }

    @Test
    fun overwrite_savesTheDraftName_slotAndRemainingItems() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.OpenRename)
        model.onEvent(TemplateDetailsEvent.RenameNameChanged("Desayuno B"))
        model.onEvent(TemplateDetailsEvent.SubmitRename)
        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "200"))
        model.onEvent(TemplateDetailsEvent.RemoveFood(1))

        model.effects.test {
            model.onEvent(TemplateDetailsEvent.SaveClicked)
            assertEquals(DetailsSheet.Choose, contentOf(model).sheet)
            model.onEvent(TemplateDetailsEvent.Overwrite)
            advanceUntilIdle()
            assertEquals(TemplateDetailsEffect.Saved, awaitItem())
        }

        val saved = repository.templates.single()
        assertEquals("t1", saved.id)
        assertEquals("Desayuno B", saved.name)
        assertEquals("breakfast", saved.mealSlotId)
        assertEquals(listOf("eggs"), saved.items.map { it.food.id })
        assertEquals(200.0, saved.items.single().amount, 0.0)
        assertNull(saved.items.single().portionLabel)
    }

    @Test
    fun overwrite_keepsThePortionLabel_ofAnUntouchedAmount() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "150"))

        model.onEvent(TemplateDetailsEvent.SaveClicked)
        model.onEvent(TemplateDetailsEvent.Overwrite)
        advanceUntilIdle()

        val items = repository.templates.single().items
        assertEquals(150.0, items[0].amount, 0.0)
        assertEquals("1 vaso", items[1].portionLabel)
    }

    @Test
    fun overwrite_withARenamedTakenName_showsTheInlineError_andKeepsTheRenameSheetOpen() = runTest(dispatcher) {
        repository.templates += desayunoA.copy(id = "t2", name = "Comida 2")
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.OpenRename)
        model.onEvent(TemplateDetailsEvent.RenameNameChanged("comida 2"))
        model.onEvent(TemplateDetailsEvent.SubmitRename)

        model.onEvent(TemplateDetailsEvent.SaveClicked)
        model.onEvent(TemplateDetailsEvent.Overwrite)
        advanceUntilIdle()

        val content = contentOf(model)
        assertEquals(DetailsSheet.Rename, content.sheet)
        assertEquals(TemplateNameError.Taken, content.rename?.nameError)
        assertFalse(content.rename?.canSubmit ?: true)
        assertEquals("Desayuno A", repository.templates.first { it.id == "t1" }.name)
        model.onEvent(TemplateDetailsEvent.RenameNameChanged("Comida 3"))
        assertNull(contentOf(model).rename?.nameError)
    }

    @Test
    fun saveAsNew_withANewName_createsASecondComida_andLeavesTheOriginalUntouched() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "200"))

        model.effects.test {
            model.onEvent(TemplateDetailsEvent.SaveClicked)
            model.onEvent(TemplateDetailsEvent.SaveAsNew)
            model.onEvent(TemplateDetailsEvent.NewNameChanged("Desayuno B"))
            model.onEvent(TemplateDetailsEvent.SubmitNewName)
            advanceUntilIdle()
            assertEquals(TemplateDetailsEffect.Saved, awaitItem())
        }

        assertEquals(listOf("Desayuno A", "Desayuno B"), repository.templates.map { it.name })
        assertEquals(120.0, repository.templates.first { it.name == "Desayuno A" }.items.first().amount, 0.0)
        assertEquals(200.0, repository.templates.first { it.name == "Desayuno B" }.items.first().amount, 0.0)
    }

    @Test
    fun saveAsNew_withATakenName_showsTheDuplicateError_andSavesNothing() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "200"))
        model.onEvent(TemplateDetailsEvent.SaveClicked)
        model.onEvent(TemplateDetailsEvent.SaveAsNew)
        model.onEvent(TemplateDetailsEvent.NewNameChanged("desayuno a"))

        model.onEvent(TemplateDetailsEvent.SubmitNewName)
        advanceUntilIdle()

        val content = contentOf(model)
        assertEquals(DetailsSheet.NewName, content.sheet)
        assertEquals(TemplateNameError.Taken, content.newName?.nameError)
        assertEquals(1, repository.templates.size)
    }

    @Test
    fun aSaveFailure_emitsActionFailed_andKeepsTheDraft() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "200"))
        repository.saveFailure = AppError.Network

        model.effects.test {
            model.onEvent(TemplateDetailsEvent.SaveClicked)
            model.onEvent(TemplateDetailsEvent.Overwrite)
            advanceUntilIdle()
            assertEquals(TemplateDetailsEffect.ActionFailed(AppError.Network), awaitItem())
        }

        val content = contentOf(model)
        assertEquals("200", content.rows.first().amountText)
        assertTrue(content.canSave)
        assertFalse(content.busy)
        assertEquals(120.0, repository.templates.single().items.first().amount, 0.0)
    }

    // --- Delete ---

    @Test
    fun delete_removesTheComidaAtOnce_withoutAConfirmState_andEmitsDeleted() = runTest(dispatcher) {
        val model = loaded()

        model.effects.test {
            model.onEvent(TemplateDetailsEvent.Delete)
            advanceUntilIdle()
            assertEquals(TemplateDetailsEffect.Deleted, awaitItem())
        }

        assertTrue(repository.templates.isEmpty())
    }

    @Test
    fun offline_deleteDoesNothing() = runTest(dispatcher) {
        val model = loaded()
        connectivity.state.value = false
        advanceUntilIdle()

        model.onEvent(TemplateDetailsEvent.Delete)
        advanceUntilIdle()

        assertEquals(1, repository.templates.size)
    }

    @Test
    fun deleteFailure_emitsActionFailed_andKeepsTheComida() = runTest(dispatcher) {
        val model = loaded()
        repository.deleteFailure = AppError.Network

        model.effects.test {
            model.onEvent(TemplateDetailsEvent.Delete)
            advanceUntilIdle()
            assertEquals(TemplateDetailsEffect.ActionFailed(AppError.Network), awaitItem())
        }

        assertFalse(contentOf(model).busy)
        assertEquals(1, repository.templates.size)
    }

    // --- Food sheet ---

    @Test
    fun openingAFood_showsItsSheet_withTheNutritionOfItsRow_andItsMicronutrients() = runTest(dispatcher) {
        val fiber = Micronutrients(listOf(Micronutrient(MicronutrientKind.Fiber, 2.0, MicronutrientUnit.G)))
        foods.micronutrientResults["eggs"] = Result.success(fiber)
        val model = loaded()

        model.onEvent(TemplateDetailsEvent.OpenFood(0))

        val open = assertNotNull(contentOf(model).openFood)
        assertEquals(0, contentOf(model).openFoodIndex)
        assertEquals("eggs", open.food.id)
        assertEquals(120.0, open.row.kcal, 0.001)
        assertEquals(fiber, open.micronutrients)
    }

    @Test
    fun aFoodWithoutMicronutrients_hasNone_andTheSheetStillOpens() = runTest(dispatcher) {
        val model = loaded()

        model.onEvent(TemplateDetailsEvent.OpenFood(1))

        val open = assertNotNull(contentOf(model).openFood)
        assertNull(open.micronutrients)
    }

    @Test
    fun removingTheOpenFood_closesItsSheet_andDismissClosesIt() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.OpenFood(1))
        model.onEvent(TemplateDetailsEvent.RemoveFood(1))
        assertNull(contentOf(model).openFood)

        model.onEvent(TemplateDetailsEvent.OpenFood(0))
        model.onEvent(TemplateDetailsEvent.DismissFood)
        assertNull(contentOf(model).openFoodIndex)
    }

    @Test
    fun anAmountTypedInTheFoodSheet_goesStraightIntoTheDraft_withNothingSaved() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.OpenFood(0))

        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "250"))

        val content = contentOf(model)
        assertEquals(250.0, content.openFood?.row?.kcal)
        assertEquals(120.0, repository.templates.single().items.first().amount, 0.0)
    }

    // --- Restoration (ADR-40) ---

    @Test
    fun theDraft_survivesTheViewModelBeingRecreated() = runTest(dispatcher) {
        repository.templates += desayunoA
        val state = SavedStateHandle()
        val first = viewModel(state)
        advanceUntilIdle()
        first.onEvent(TemplateDetailsEvent.AmountChanged(0, "150"))
        first.onEvent(TemplateDetailsEvent.RemoveFood(1))
        first.onEvent(TemplateDetailsEvent.OpenRename)
        first.onEvent(TemplateDetailsEvent.RenameNameChanged("Mi comida"))
        first.onEvent(TemplateDetailsEvent.SubmitRename)
        first.onEvent(TemplateDetailsEvent.OpenFood(0))

        val content = contentOf(viewModel(state))

        assertEquals(listOf("eggs"), content.rows.map { it.foodId })
        assertEquals("150", content.rows.single().amountText)
        assertEquals("Mi comida", content.name)
        assertEquals(0, content.openFoodIndex)
    }

    @Test
    fun theOpenRenameSheet_withItsTypedTextAndTakenFlag_survivesRecreation() = runTest(dispatcher) {
        repository.templates += desayunoA
        val state = SavedStateHandle()
        val first = viewModel(state)
        advanceUntilIdle()
        first.onEvent(TemplateDetailsEvent.OpenRename)
        first.onEvent(TemplateDetailsEvent.RenameNameChanged("Typed"))

        val content = contentOf(viewModel(state))

        assertEquals(DetailsSheet.Rename, content.sheet)
        assertEquals("Typed", content.rename?.name)
    }

    @Test
    fun theOpenChoiceAndTheTypedNewName_surviveRecreation() = runTest(dispatcher) {
        repository.templates += desayunoA
        val state = SavedStateHandle()
        val first = viewModel(state)
        advanceUntilIdle()
        first.onEvent(TemplateDetailsEvent.AmountChanged(0, "150"))
        first.onEvent(TemplateDetailsEvent.SaveClicked)
        assertEquals(DetailsSheet.Choose, contentOf(viewModel(state)).sheet)

        first.onEvent(TemplateDetailsEvent.SaveAsNew)
        first.onEvent(TemplateDetailsEvent.NewNameChanged("Mi comida"))
        val content = contentOf(viewModel(state))

        assertEquals(DetailsSheet.NewName, content.sheet)
        assertEquals("Mi comida", content.newName?.name)
        assertTrue(content.canSave)
    }

    @Test
    fun theSameFoodTwice_draftIsRestoredByIndex() = runTest(dispatcher) {
        repository.templates += eggsTwice
        val state = SavedStateHandle()
        val first = viewModel(state)
        advanceUntilIdle()
        first.onEvent(TemplateDetailsEvent.AmountChanged(1, "80"))
        first.onEvent(TemplateDetailsEvent.RemoveFood(0))

        val rows = contentOf(viewModel(state)).rows

        assertEquals(listOf(1), rows.map { it.index })
        assertEquals("80", rows.single().amountText)
    }

    @Test
    fun aTakenNameError_survivesRecreation() = runTest(dispatcher) {
        repository.templates += desayunoA.copy(id = "t2", name = "Comida 2")
        val state = SavedStateHandle()
        repository.templates += desayunoA
        val first = viewModel(state)
        advanceUntilIdle()
        first.onEvent(TemplateDetailsEvent.OpenRename)
        first.onEvent(TemplateDetailsEvent.RenameNameChanged("Comida 2"))
        first.onEvent(TemplateDetailsEvent.SubmitRename)
        first.onEvent(TemplateDetailsEvent.SaveClicked)
        first.onEvent(TemplateDetailsEvent.Overwrite)
        advanceUntilIdle()

        val content = contentOf(viewModel(state))

        assertEquals(TemplateNameError.Taken, content.rename?.nameError)
    }
}
