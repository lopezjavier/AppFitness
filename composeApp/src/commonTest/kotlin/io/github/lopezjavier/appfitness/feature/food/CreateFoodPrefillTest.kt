package io.github.lopezjavier.appfitness.feature.food

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.NewFoodDraft
import io.github.lopezjavier.appfitness.domain.model.NewFoodDraftCodec
import io.github.lopezjavier.appfitness.domain.model.NewMicronutrients
import io.github.lopezjavier.appfitness.domain.model.NewPortion
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreateFoodPrefillTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeFoodRepository()
    private val connectivity = FakeConnectivityObserver()
    private val savedState = SavedStateHandle()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val dish = NewFoodDraft(
        name = "Lentejas con pollo",
        brand = null,
        baseUnit = "g",
        kcal = 180.0,
        protein = 6.5,
        carbs = 15.0,
        fat = 10.0,
        micronutrients = NewMicronutrients(mapOf(MicronutrientKind.Fiber to 2.5)),
        portions = listOf(NewPortion("1 ración", 250.0)),
    )

    private val partialLabel = NewFoodDraft(
        name = "",
        brand = null,
        baseUnit = "ml",
        kcal = null,
        protein = 3.0,
        carbs = null,
        fat = null,
        micronutrients = NewMicronutrients(),
        portions = emptyList(),
    )

    private fun viewModel(prefill: NewFoodDraft?, state: SavedStateHandle = savedState) = CreateFoodViewModel(
        barcode = null,
        name = null,
        foodRepository = repository,
        connectivity = connectivity,
        savedState = state,
        prefill = prefill?.let(NewFoodDraftCodec::encode),
    )

    private fun CreateFoodViewModel.type(field: CreateFoodField, text: String) =
        onEvent(CreateFoodEvent.FieldChanged(field, text))

    @Test
    fun prefill_fillsEveryFieldWithCommaDecimalsAndPortion() = runTest(dispatcher) {
        val state = viewModel(dish).uiState.value

        assertEquals("Lentejas con pollo", state.name)
        assertEquals("", state.brand)
        assertEquals("180", state.kcal)
        assertEquals("6,5", state.protein)
        assertEquals("15", state.carbs)
        assertEquals("10", state.fat)
        assertEquals("2,5", state.nutrients[MicronutrientKind.Fiber])
        assertEquals(listOf(PortionDraft("1 ración", "250")), state.portions)
        assertTrue(state.aiEstimateNote)
        assertTrue(state.canSave)
    }

    @Test
    fun partialDraft_leavesNullsBlank_andIsNotSavableNorShowsErrors() = runTest(dispatcher) {
        val state = viewModel(partialLabel).uiState.value

        assertEquals("", state.kcal)
        assertEquals("3", state.protein)
        assertEquals("", state.carbs)
        assertEquals("", state.fat)
        assertEquals("ml", state.baseUnit)
        assertTrue(state.portions.isEmpty())
        assertFalse(state.canSave)
        assertNull(state.errors.name)
        assertNull(state.errors.kcal)
    }

    @Test
    fun unknownUnit_fallsBackToGrams() = runTest(dispatcher) {
        assertEquals("g", viewModel(dish.copy(baseUnit = "oz")).uiState.value.baseUnit)
    }

    @Test
    fun invalidPrefillJson_opensAnEmptyForm() = runTest(dispatcher) {
        val model = CreateFoodViewModel(null, null, repository, connectivity, savedState, prefill = "{broken")

        assertEquals("", model.uiState.value.name)
        advanceUntilIdle()
        assertFalse(model.uiState.value.aiEstimateNote)
    }

    @Test
    fun noPrefill_showsNoNote() = runTest(dispatcher) {
        assertFalse(viewModel(null).uiState.value.aiEstimateNote)
    }

    @Test
    fun note_clearsOnNameEdit() = runTest(dispatcher) {
        val model = viewModel(dish)
        model.type(CreateFoodField.Name, "Lentejas")
        advanceUntilIdle()
        assertFalse(model.uiState.value.aiEstimateNote)
    }

    @Test
    fun note_clearsOnNutrientEdit() = runTest(dispatcher) {
        val model = viewModel(dish)
        model.onEvent(CreateFoodEvent.NutrientChanged(MicronutrientKind.Fiber, "3"))
        advanceUntilIdle()
        assertFalse(model.uiState.value.aiEstimateNote)
    }

    @Test
    fun note_clearsOnUnitChange() = runTest(dispatcher) {
        val model = viewModel(dish)
        model.onEvent(CreateFoodEvent.UnitSelected("ml"))
        advanceUntilIdle()
        assertFalse(model.uiState.value.aiEstimateNote)
    }

    @Test
    fun note_clearsOnAddRemoveAndEditOfPortions() = runTest(dispatcher) {
        listOf<(CreateFoodViewModel) -> Unit>(
            { it.onEvent(CreateFoodEvent.AddPortion) },
            { it.onEvent(CreateFoodEvent.RemovePortion(0)) },
            { it.onEvent(CreateFoodEvent.PortionLabelChanged(0, "Plato")) },
            { it.onEvent(CreateFoodEvent.PortionAmountChanged(0, "300")) },
        ).forEach { edit ->
            val model = viewModel(dish, SavedStateHandle())
            assertTrue(model.uiState.value.aiEstimateNote)
            edit(model)
            advanceUntilIdle()
            assertFalse(model.uiState.value.aiEstimateNote)
        }
    }

    @Test
    fun save_afterEdit_createsOnceWithEditedValuesAndEmitsSaved() = runTest(dispatcher) {
        val model = viewModel(dish)
        model.type(CreateFoodField.Name, "Lentejas sin chorizo")
        model.type(CreateFoodField.Kcal, "170")

        model.effects.test {
            model.onEvent(CreateFoodEvent.Save)
            advanceUntilIdle()
            assertEquals(CreateFoodEffect.Saved("new-1"), awaitItem())
        }
        val created = repository.created.single()
        assertEquals("Lentejas sin chorizo", created.name)
        assertEquals(170.0, created.nutrition.kcal)
        assertEquals(6.5, created.nutrition.proteinG)
        assertEquals(listOf(NewPortion("1 ración", 250.0)), created.portions)
        assertEquals(2.5, created.micronutrients.values[MicronutrientKind.Fiber])
    }

    @Test
    fun unit_isUnlockedForANewFood() = runTest(dispatcher) {
        assertFalse(viewModel(dish).uiState.value.unitLocked)
    }

    @Test
    fun recreatedBeforeAnyEdit_keepsTheNote() = runTest(dispatcher) {
        viewModel(dish)
        val restored = viewModel(dish)

        assertTrue(restored.uiState.value.aiEstimateNote)
        assertEquals("Lentejas con pollo", restored.uiState.value.name)
    }

    @Test
    fun recreatedAfterAnEdit_keepsEditsWithoutNote_andDoesNotReapplyPrefill() = runTest(dispatcher) {
        val first = viewModel(dish)
        first.type(CreateFoodField.Name, "Mi plato")
        first.type(CreateFoodField.Kcal, "99")

        val restored = viewModel(dish)

        assertEquals("Mi plato", restored.uiState.value.name)
        assertEquals("99", restored.uiState.value.kcal)
        assertFalse(restored.uiState.value.aiEstimateNote)
    }
}
