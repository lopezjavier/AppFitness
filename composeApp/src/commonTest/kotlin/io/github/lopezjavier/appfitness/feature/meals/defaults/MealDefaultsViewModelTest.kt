package io.github.lopezjavier.appfitness.feature.meals.defaults

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.CookingMethod
import io.github.lopezjavier.appfitness.domain.model.FoodPreferences
import io.github.lopezjavier.appfitness.domain.model.MealDefault
import io.github.lopezjavier.appfitness.domain.model.MealDefaultAssignment
import io.github.lopezjavier.appfitness.domain.model.NewTemplateItem
import io.github.lopezjavier.appfitness.domain.usecase.CookedItemNutrition
import io.github.lopezjavier.appfitness.domain.usecase.CookingImpact
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.feature.diary.FakeMealSlotRepository
import io.github.lopezjavier.appfitness.feature.food.FakeFoodPreferencesRepository
import io.github.lopezjavier.appfitness.feature.food.food
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MealDefaultsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val slots = FakeMealSlotRepository()
    private val templates = FakeMealTemplateRepository(
        mutableMapOf("eggs" to food("eggs", kcal = 100.0), "rice" to food("rice", kcal = 200.0)),
    )
    private val defaults = FakeMealDefaultsRepository()
    private val connectivity = FakeConnectivityObserver()
    private val preferences = FakeFoodPreferencesRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val cookingKcal =
        CookingImpact.addedKcal(CookingImpact.retainedG(CookingMethod.Griddle, FoodPreferences()))

    private suspend fun comida(name: String, vararg items: NewTemplateItem): String =
        templates.save(null, name, null, items.toList()).getOrThrow()

    private suspend fun eggs(name: String) = comida(name, NewTemplateItem("eggs", 100.0, null))

    private fun viewModel(state: SavedStateHandle = SavedStateHandle()) = MealDefaultsViewModel(
        slots,
        templates,
        defaults,
        preferences,
        CookedItemNutrition(ScaleNutrition()),
        connectivity,
        state,
    )

    private fun MealDefaultsViewModel.content() = assertIs<MealDefaultsUiState.Content>(uiState.value)

    private fun MealDefaultsViewModel.card(slotId: String) = content().meals.first { it.slotId == slotId }

    @Test
    fun loading_listsTheEnabledMealsInSavedOrder() = runTest(dispatcher) {
        val model = viewModel()
        assertEquals(MealDefaultsUiState.Loading, model.uiState.value)
        advanceUntilIdle()

        assertEquals(listOf("Desayuno", "Comida", "Merienda", "Cena"), model.content().meals.map { it.label })
        assertFalse(model.content().isDirty)
    }

    @Test
    fun addingAComida_preselectsEveryFreeDay() = runTest(dispatcher) {
        val a = eggs("Desayuno A")
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(MealDefaultsEvent.OpenPicker("breakfast"))
        assertEquals("breakfast", model.content().picker)
        model.onEvent(MealDefaultsEvent.OpenAssignment("breakfast", a))

        val sheet = assertNotNullSheet(model)
        assertEquals((1..7).toSet(), sheet.selectedDays)
        assertTrue(sheet.canSave)
        assertNull(model.content().picker)
    }

    @Test
    fun keepingMondayWednesdayFriday_savesThatPayload() = runTest(dispatcher) {
        val a = eggs("Desayuno A")
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(MealDefaultsEvent.OpenAssignment("breakfast", a))
        listOf(2, 4, 6, 7).forEach { model.onEvent(MealDefaultsEvent.ToggleDay(it)) }
        model.onEvent(MealDefaultsEvent.ConfirmSheet)
        assertEquals(setOf(1, 3, 5), model.card("breakfast").assignments.single().days)
        assertTrue(model.content().canSave)

        model.effects.test {
            model.onEvent(MealDefaultsEvent.Save)
            advanceUntilIdle()
            assertEquals(MealDefaultsEffect.Close, awaitItem())
        }
        assertEquals(listOf(listOf(MealDefaultAssignment("breakfast", a, setOf(1, 3, 5)))), defaults.saved)
    }

    @Test
    fun aSecondComidaOnOtherDays_namesTheConflictingOne() = runTest(dispatcher) {
        val a = eggs("Desayuno A")
        val b = eggs("Desayuno B")
        defaults.defaults += listOf(MealDefault("breakfast", a, 1))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(MealDefaultsEvent.OpenAssignment("breakfast", b))
        val sheet = assertNotNullSheet(model)

        assertEquals(mapOf(1 to "Desayuno A"), sheet.conflicts)
        assertEquals(setOf(2, 3, 4, 5, 6, 7), sheet.selectedDays)
        model.onEvent(MealDefaultsEvent.ToggleDay(1))
        assertEquals(setOf(2, 3, 4, 5, 6, 7), assertNotNullSheet(model).selectedDays)
    }

    @Test
    fun aComidaEditingItsOwnDays_keepsThem() = runTest(dispatcher) {
        val a = eggs("Desayuno A")
        defaults.defaults += listOf(1, 3, 5).map { MealDefault("breakfast", a, it) }
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(MealDefaultsEvent.OpenAssignment("breakfast", a))

        val sheet = assertNotNullSheet(model)
        assertEquals(setOf(1, 3, 5), sheet.selectedDays)
        assertTrue(sheet.conflicts.isEmpty())
    }

    @Test
    fun emptyingTheSelection_disablesTheSheetSave() = runTest(dispatcher) {
        val a = eggs("Desayuno A")
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(MealDefaultsEvent.OpenAssignment("breakfast", a))

        (1..7).forEach { model.onEvent(MealDefaultsEvent.ToggleDay(it)) }
        model.onEvent(MealDefaultsEvent.ConfirmSheet)

        assertFalse(assertNotNullSheet(model).canSave)
        assertTrue(model.card("breakfast").assignments.isEmpty())
    }

    @Test
    fun dismissingTheSheet_changesNothing() = runTest(dispatcher) {
        val a = eggs("Desayuno A")
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(MealDefaultsEvent.OpenAssignment("breakfast", a))

        model.onEvent(MealDefaultsEvent.DismissSheet)

        assertNull(model.content().sheet)
        assertFalse(model.content().isDirty)
        assertTrue(model.card("breakfast").assignments.isEmpty())
    }

    @Test
    fun withEverySevenDaysTaken_addIsDisabled() = runTest(dispatcher) {
        val a = eggs("Desayuno A")
        defaults.defaults += (1..7).map { MealDefault("breakfast", a, it) }
        val model = viewModel()
        advanceUntilIdle()

        assertFalse(model.card("breakfast").canAdd)
        assertTrue(model.card("lunch").canAdd)
    }

    @Test
    fun removingTheOnlyAssignmentAndSaving_sendsAnEmptyList() = runTest(dispatcher) {
        val a = eggs("Desayuno A")
        defaults.defaults += (1..7).map { MealDefault("breakfast", a, it) }
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(MealDefaultsEvent.Remove("breakfast", a))
        assertTrue(model.card("breakfast").assignments.isEmpty())
        assertTrue(model.card("breakfast").canAdd)
        model.onEvent(MealDefaultsEvent.Save)
        advanceUntilIdle()

        assertEquals(listOf(emptyList()), defaults.saved)
        assertTrue(defaults.defaults.isEmpty())
    }

    @Test
    fun withoutSavedComidas_theStateSaysSo() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        assertFalse(model.content().hasSavedComidas)
        assertTrue(model.content().comidas.isEmpty())
    }

    @Test
    fun thePicker_listsComidasWithCountAndKcalIncludingCooking() = runTest(dispatcher) {
        comida("Arroz con huevo", NewTemplateItem("rice", 100.0, null), NewTemplateItem("eggs", 100.0, null))
        comida("Huevos a la plancha", NewTemplateItem("eggs", 100.0, null, CookingMethod.Griddle))
        val model = viewModel()
        advanceUntilIdle()

        val options = model.content().comidas.associateBy { it.name }

        assertTrue(model.content().hasSavedComidas)
        assertEquals(2, options.getValue("Arroz con huevo").foodCount)
        assertEquals(300.0, options.getValue("Arroz con huevo").kcal)
        assertEquals(100.0 + cookingKcal, options.getValue("Huevos a la plancha").kcal)
    }

    @Test
    fun theAssignmentRow_showsTheCookedKcal() = runTest(dispatcher) {
        val id = comida("Huevos a la plancha", NewTemplateItem("eggs", 100.0, null, CookingMethod.Griddle))
        defaults.defaults += MealDefault("breakfast", id, 2)
        val model = viewModel()
        advanceUntilIdle()

        val row = model.card("breakfast").assignments.single()
        assertEquals(100.0 + cookingKcal, row.kcal)
        assertEquals(1, row.foodCount)
    }

    @Test
    fun aLoadFailure_showsTheErrorAndRetryRecovers() = runTest(dispatcher) {
        defaults.listFailure = AppError.Network
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(MealDefaultsUiState.LoadError, model.uiState.value)

        defaults.listFailure = null
        model.onEvent(MealDefaultsEvent.Retry)
        advanceUntilIdle()

        assertIs<MealDefaultsUiState.Content>(model.uiState.value)
    }

    @Test
    fun offline_savingIsDisabled() = runTest(dispatcher) {
        val a = eggs("Desayuno A")
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(MealDefaultsEvent.OpenAssignment("breakfast", a))
        model.onEvent(MealDefaultsEvent.ConfirmSheet)

        connectivity.state.value = false
        advanceUntilIdle()
        assertFalse(model.content().isOnline)
        assertFalse(model.content().canSave)
        model.onEvent(MealDefaultsEvent.Save)
        advanceUntilIdle()

        assertTrue(defaults.saved.isEmpty())
    }

    @Test
    fun backWithAnUnsavedDraft_asksAndConfirmingCloses() = runTest(dispatcher) {
        val a = eggs("Desayuno A")
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(MealDefaultsEvent.OpenAssignment("breakfast", a))
        model.onEvent(MealDefaultsEvent.ConfirmSheet)

        model.effects.test {
            model.onEvent(MealDefaultsEvent.Back)
            expectNoEvents()
            assertTrue(model.content().showDiscardConfirm)
            model.onEvent(MealDefaultsEvent.ConfirmDiscard)
            assertEquals(MealDefaultsEffect.Close, awaitItem())
        }
        assertTrue(defaults.saved.isEmpty())
    }

    @Test
    fun dismissingTheDiscardDialog_keepsTheDraft() = runTest(dispatcher) {
        val a = eggs("Desayuno A")
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(MealDefaultsEvent.OpenAssignment("breakfast", a))
        model.onEvent(MealDefaultsEvent.ConfirmSheet)
        model.onEvent(MealDefaultsEvent.Back)

        model.onEvent(MealDefaultsEvent.DismissDiscard)

        assertFalse(model.content().showDiscardConfirm)
        assertTrue(model.content().isDirty)
        assertEquals(1, model.card("breakfast").assignments.size)
    }

    @Test
    fun backWithoutChanges_closesAtOnce() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(MealDefaultsEvent.Back)
            assertEquals(MealDefaultsEffect.Close, awaitItem())
        }
    }

    @Test
    fun aFailedSave_keepsTheDraftAndReportsIt() = runTest(dispatcher) {
        val a = eggs("Desayuno A")
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(MealDefaultsEvent.OpenAssignment("breakfast", a))
        model.onEvent(MealDefaultsEvent.ConfirmSheet)
        defaults.saveFailure = AppError.Validation

        model.effects.test {
            model.onEvent(MealDefaultsEvent.Save)
            advanceUntilIdle()
            expectNoEvents()
        }

        assertEquals(MealDefaultsSaveError.Failed, model.content().saveError)
        assertTrue(model.content().isDirty)
        assertFalse(model.content().isSaving)
        assertEquals(1, model.card("breakfast").assignments.size)
    }

    @Test
    fun theDraftSheetAndPicker_surviveARecreatedViewModel() = runTest(dispatcher) {
        val a = eggs("Desayuno A")
        val state = SavedStateHandle()
        val first = viewModel(state)
        advanceUntilIdle()
        first.onEvent(MealDefaultsEvent.OpenAssignment("breakfast", a))
        listOf(2, 4, 6, 7).forEach { first.onEvent(MealDefaultsEvent.ToggleDay(it)) }
        first.onEvent(MealDefaultsEvent.ConfirmSheet)
        first.onEvent(MealDefaultsEvent.OpenAssignment("breakfast", a))
        first.onEvent(MealDefaultsEvent.ToggleDay(5))

        val second = viewModel(state)
        advanceUntilIdle()

        assertEquals(setOf(1, 3, 5), second.card("breakfast").assignments.single().days)
        assertEquals(setOf(1, 3), assertNotNullSheet(second).selectedDays)
        assertTrue(second.content().isDirty)
    }

    private fun assertNotNullSheet(model: MealDefaultsViewModel): AssignSheetUi =
        requireNotNull(model.content().sheet) { "the assignment sheet is not open" }
}
