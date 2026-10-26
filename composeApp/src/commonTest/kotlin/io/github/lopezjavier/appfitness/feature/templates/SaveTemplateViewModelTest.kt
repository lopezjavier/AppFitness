package io.github.lopezjavier.appfitness.feature.templates

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.CookingMethod
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.usecase.BuildTemplateItems
import io.github.lopezjavier.appfitness.domain.usecase.ValidateTemplateName
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.food.food
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SaveTemplateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val day = LocalDate(2026, 10, 21)
    private val diary = FakeFoodDiaryRepository()
    private val repository = FakeMealTemplateRepository(
        mutableMapOf(
            "eggs" to food("eggs", "Huevos revueltos"),
            "shake" to food("shake", "Batido de proteína"),
            "rice" to food("rice", "Arroz"),
        ),
    )
    private val connectivity = FakeConnectivityObserver()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        diary.server[day] = listOf(
            entry("e1", "breakfast", "eggs", 120.0),
            entry("e2", "breakfast", "shake", 300.0),
            entry("e3", "lunch", "rice", 180.0),
        )
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun entry(id: String, slotId: String, foodId: String, amount: Double) = FoodLogEntry(
        id = id,
        logDate = day,
        mealSlotId = slotId,
        foodId = foodId,
        foodName = foodId,
        amount = amount,
        portionLabel = null,
        baseUnit = "g",
        kcal = 100.0,
        proteinG = 10.0,
        carbsG = 10.0,
        fatG = 5.0,
    )

    private fun viewModel(slotId: String = "breakfast", state: SavedStateHandle = SavedStateHandle()) =
        SaveTemplateViewModel(
            slotId = slotId,
            date = day,
            foodDiary = diary,
            buildTemplateItems = BuildTemplateItems(),
            validateTemplateName = ValidateTemplateName(),
            mealTemplates = repository,
            connectivity = connectivity,
            savedState = state,
        )

    private fun SaveTemplateViewModel.type(text: String) = onEvent(SaveTemplateEvent.NameChanged(text))

    private fun SaveTemplateViewModel.content(): SaveTemplateContent {
        val state = uiState.value
        assertIs<SaveTemplateUiState.Content>(state)
        return state.content
    }

    @Test
    fun saving_createsOneComidaWithTheSectionFoodsAndQuantitiesInOrder_andTheSlotAsSuggestion() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.type("Desayuno A")

        model.onEvent(SaveTemplateEvent.Submit)
        advanceUntilIdle()

        val saved = repository.templates.single()
        assertEquals("Desayuno A", saved.name)
        assertEquals("breakfast", saved.mealSlotId)
        assertEquals(listOf("eggs" to 120.0, "shake" to 300.0), saved.items.map { it.food.id to it.amount })
    }

    @Test
    fun saving_groupsExactlyTheEntriesOfTheSection() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.type("Mi desayuno")

        model.onEvent(SaveTemplateEvent.Submit)
        advanceUntilIdle()

        assertEquals(listOf(Triple(day, "breakfast", listOf("e1", "e2"))), repository.comidaSaves)
    }

    @Test
    fun saving_emitsSaved() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.type("Desayuno A")

        model.effects.test {
            model.onEvent(SaveTemplateEvent.Submit)
            advanceUntilIdle()
            assertEquals(SaveTemplateEffect.Saved, awaitItem())
        }
    }

    @Test
    fun sectionEntriesOfOtherMeals_areNotPartOfTheComida() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(2, model.content().itemCount)
    }

    @Test
    fun takenName_showsTheDuplicateError_andKeepsTheTypedName() = runTest(dispatcher) {
        repository.templates += MealTemplate("t0", "Desayuno A", null, emptyList())
        val model = viewModel()
        advanceUntilIdle()
        model.type("desayuno a ")

        model.onEvent(SaveTemplateEvent.Submit)
        advanceUntilIdle()

        val content = model.content()
        assertEquals(TemplateNameError.Taken, content.nameError)
        assertEquals("desayuno a ", content.name)
        assertFalse(content.canSave)
        assertEquals(1, repository.templates.size)
    }

    @Test
    fun editingTheNameAfterTheDuplicateError_clearsTheError() = runTest(dispatcher) {
        repository.templates += MealTemplate("t0", "Desayuno A", null, emptyList())
        val model = viewModel()
        advanceUntilIdle()
        model.type("Desayuno A")
        model.onEvent(SaveTemplateEvent.Submit)
        advanceUntilIdle()

        model.type("Desayuno B")
        advanceUntilIdle()

        assertNull(model.content().nameError)
        assertTrue(model.content().canSave)
    }

    @Test
    fun existingComidaNames_areExposedForTheNameSheet() = runTest(dispatcher) {
        repository.templates += MealTemplate("t0", "Desayuno A", null, emptyList())
        repository.templates += MealTemplate("t1", "Cena ligera", null, emptyList())
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(setOf("Desayuno A", "Cena ligera"), model.content().existingNames.toSet())
    }

    @Test
    fun aNameEqualToAnExistingOneIgnoringCaseAndSpaces_isDuplicateBeforeSubmitting() = runTest(dispatcher) {
        repository.templates += MealTemplate("t0", "Desayuno A", null, emptyList())
        val model = viewModel()
        advanceUntilIdle()

        model.type("  desayuno a")
        advanceUntilIdle()

        val content = model.content()
        assertEquals(TemplateNameError.Taken, content.nameError)
        assertFalse(content.canSave)
        assertEquals(0, repository.saveCalls)
    }

    @Test
    fun aDifferentName_isNotDuplicate_evenWhenOtherComidasExist() = runTest(dispatcher) {
        repository.templates += MealTemplate("t0", "Desayuno A", null, emptyList())
        val model = viewModel()
        advanceUntilIdle()

        model.type("Desayuno B")
        advanceUntilIdle()

        assertNull(model.content().nameError)
        assertTrue(model.content().canSave)
    }

    @Test
    fun whenTheComidaNamesCannotBeRead_savingStillWorks_andTheServerGuardsDuplicates() = runTest(dispatcher) {
        repository.listFailure = AppError.Network
        val model = viewModel()
        advanceUntilIdle()
        model.type("Desayuno A")
        advanceUntilIdle()

        assertTrue(model.content().existingNames.isEmpty())
        assertTrue(model.content().canSave)
    }

    @Test
    fun blankName_disablesSave() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.type("   ")
        advanceUntilIdle()

        assertFalse(model.content().canSave)
        assertNull(model.content().nameError)
    }

    @Test
    fun nameOfSixtyOneCharacters_showsTooLong_andDisablesSave() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.type("a".repeat(61))
        advanceUntilIdle()

        assertEquals(TemplateNameError.TooLong, model.content().nameError)
        assertFalse(model.content().canSave)
    }

    @Test
    fun offline_disablesSave() = runTest(dispatcher) {
        connectivity.state.value = false
        val model = viewModel()
        advanceUntilIdle()
        model.type("Desayuno A")

        assertTrue(model.content().isOffline)
        assertFalse(model.content().canSave)
    }

    @Test
    fun sectionWithoutEntries_cannotBeSaved() = runTest(dispatcher) {
        val model = viewModel(slotId = "dinner")
        advanceUntilIdle()
        model.type("Cena")

        assertEquals(0, model.content().itemCount)
        assertFalse(model.content().canSave)
    }

    @Test
    fun failedSave_emitsSaveFailed_andStaysOpenWithTheName() = runTest(dispatcher) {
        repository.saveFailure = AppError.Network
        val model = viewModel()
        advanceUntilIdle()
        model.type("Desayuno A")

        model.effects.test {
            model.onEvent(SaveTemplateEvent.Submit)
            advanceUntilIdle()
            assertIs<SaveTemplateEffect.SaveFailed>(awaitItem())
        }
        assertEquals("Desayuno A", model.content().name)
        assertNull(model.content().nameError)
    }

    private fun cookedEntries() {
        diary.server[day] = listOf(
            entry("e1", "breakfast", "eggs", 120.0).copy(cookingMethod = CookingMethod.Griddle),
            entry("e2", "breakfast", "shake", 300.0).copy(baseUnit = "ml"),
            entry("e3", "breakfast", "rice", 180.0),
        )
    }

    @Test
    fun items_listEachEntryWithItsOwnMethodAndWhetherItCanBeCooked() = runTest(dispatcher) {
        cookedEntries()
        val model = viewModel()
        advanceUntilIdle()

        val items = model.content().items
        assertEquals(listOf("e1", "e2", "e3"), items.map { it.entryId })
        assertEquals(listOf(CookingMethod.Griddle, CookingMethod.Raw, CookingMethod.Raw), items.map { it.method })
        assertEquals(listOf(true, false, true), items.map { it.canCook })
        assertEquals(listOf("g", "ml", "g"), items.map { it.baseUnit })
        assertEquals("eggs", items.first().foodName)
    }

    @Test
    fun methodChanged_updatesTheItemAndSurvivesRecreation() = runTest(dispatcher) {
        cookedEntries()
        val state = SavedStateHandle()
        val model = viewModel(state = state)
        advanceUntilIdle()

        model.onEvent(SaveTemplateEvent.MethodChanged("e3", CookingMethod.Fried))
        advanceUntilIdle()
        assertEquals(CookingMethod.Fried, model.content().items.last().method)

        val recreated = viewModel(state = state)
        advanceUntilIdle()
        assertEquals(CookingMethod.Fried, recreated.content().items.last().method)
        assertEquals(CookingMethod.Griddle, recreated.content().items.first().method)
    }

    @Test
    fun methodChanged_isIgnoredForDrinks() = runTest(dispatcher) {
        cookedEntries()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(SaveTemplateEvent.MethodChanged("e2", CookingMethod.Fried))
        advanceUntilIdle()

        assertEquals(CookingMethod.Raw, model.content().items[1].method)
    }

    @Test
    fun saving_carriesTheMethodsOfEachItem() = runTest(dispatcher) {
        cookedEntries()
        val model = viewModel()
        advanceUntilIdle()
        model.type("Desayuno A")
        model.onEvent(SaveTemplateEvent.MethodChanged("e3", CookingMethod.AirFryer))
        model.onEvent(SaveTemplateEvent.MethodChanged("e1", CookingMethod.Raw))

        model.onEvent(SaveTemplateEvent.Submit)
        advanceUntilIdle()

        val saved = repository.templates.single()
        assertEquals(
            listOf(CookingMethod.Raw, CookingMethod.Raw, CookingMethod.AirFryer),
            saved.items.map { it.cookingMethod },
        )
    }

    @Test
    fun loadingTheSection_failsWhenTheDayCannotBeRefreshed() = runTest(dispatcher) {
        diary.failRefreshOf(day)
        val model = viewModel()
        advanceUntilIdle()

        assertIs<SaveTemplateUiState.Error>(model.uiState.value)
    }
}
