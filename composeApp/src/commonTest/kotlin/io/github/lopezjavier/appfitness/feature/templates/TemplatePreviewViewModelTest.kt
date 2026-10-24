package io.github.lopezjavier.appfitness.feature.templates

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.domain.model.NewTemplateItem
import io.github.lopezjavier.appfitness.domain.usecase.AddTemplateToDiary
import io.github.lopezjavier.appfitness.domain.usecase.DefaultMealSlot
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.feature.diary.DefaultMealSlots
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.diary.FakeMealSlotRepository
import io.github.lopezjavier.appfitness.feature.food.food
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** US-53: the read-only details of a comida, its "Añadir" and its "Editar". */
@OptIn(ExperimentalCoroutinesApi::class)
class TemplatePreviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val day = LocalDate(2026, 10, 19)
    private val rice = food("rice", "Arroz", kcal = 200.0)
    private val milk = food("milk", "Leche", kcal = 100.0, baseUnit = "ml")
    private val templates = FakeMealTemplateRepository(mutableMapOf("rice" to rice, "milk" to milk))
    private val diary = FakeFoodDiaryRepository()
    private val connectivity = FakeConnectivityObserver()
    private val comida = MealTemplate(
        id = "t1",
        name = "Comida A",
        mealSlotId = "lunch",
        items = listOf(MealTemplateItem(rice, 100.0, null), MealTemplateItem(milk, 50.0, "medio vaso")),
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        slotId: String? = "lunch",
        templateId: String = "t1",
        slots: FakeMealSlotRepository = FakeMealSlotRepository(),
    ) = TemplatePreviewViewModel(
        templateId = templateId,
        slotId = slotId,
        date = day,
        mealTemplates = templates,
        scaleNutrition = ScaleNutrition(),
        addTemplateToDiary = AddTemplateToDiary(diary, ScaleNutrition()),
        mealSlots = slots,
        defaultMealSlot = DefaultMealSlot(),
        clock = MutableClock(),
        connectivity = connectivity,
        savedState = SavedStateHandle(),
        timeZone = TimeZone.UTC,
    )

    private fun TemplatePreviewViewModel.content(): TemplatePreviewUiState.Content {
        val state = uiState.value
        assertIs<TemplatePreviewUiState.Content>(state)
        return state
    }

    @Test
    fun isLoading_untilTheComidaArrives() = runTest(dispatcher) {
        templates.templates += comida

        assertEquals(TemplatePreviewUiState.Loading, viewModel().uiState.value)
    }

    @Test
    fun loadsTheRowsAndTheTotalsOfTheComida() = runTest(dispatcher) {
        templates.templates += comida
        val model = viewModel()
        advanceUntilIdle()

        val content = model.content()
        assertEquals("Comida A", content.name)
        assertEquals(listOf("Arroz", "Leche"), content.rows.map { it.name })
        assertEquals(listOf("g", "ml"), content.rows.map { it.unit })
        assertEquals(listOf(100.0, 50.0), content.rows.map { it.amount })
        assertEquals(listOf(null, "medio vaso"), content.rows.map { it.portionLabel })
        assertEquals(listOf(200.0, 50.0), content.rows.map { it.kcal })
        assertEquals(250.0, content.totalKcal, 0.0)
    }

    @Test
    fun opening_addsNothingToTheDiary() = runTest(dispatcher) {
        templates.templates += comida
        viewModel()
        advanceUntilIdle()

        assertTrue(diary.addedBatches.isEmpty())
    }

    @Test
    fun aComidaThatDoesNotExist_isAnError() = runTest(dispatcher) {
        val model = viewModel(templateId = "gone")
        advanceUntilIdle()

        assertEquals(TemplatePreviewUiState.Error, model.uiState.value)
    }

    @Test
    fun aFailingList_isAnError_andRetryRecovers() = runTest(dispatcher) {
        templates.templates += comida
        templates.listFailure = AppError.Network
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(TemplatePreviewUiState.Error, model.uiState.value)

        templates.listFailure = null
        model.onEvent(TemplatePreviewEvent.Retry)
        advanceUntilIdle()

        assertEquals("Comida A", model.content().name)
    }

    @Test
    fun add_logsEveryFoodInOneRequest_toTheRouteMealAndDay_andEmitsAdded() = runTest(dispatcher) {
        templates.templates += comida
        val model = viewModel(slotId = "dinner")
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TemplatePreviewEvent.Add)
            advanceUntilIdle()
            assertEquals(TemplatePreviewEffect.Added, awaitItem())
        }

        val batch = diary.addedBatches.single()
        assertEquals(listOf("rice" to 100.0, "milk" to 50.0), batch.map { it.foodId to it.amount })
        assertTrue(batch.all { it.mealSlotId == "dinner" && it.date == day })
    }

    @Test
    fun add_withoutAPreselectedMeal_usesTheMealOfTheTimeOfDay() = runTest(dispatcher) {
        templates.templates += comida
        val model = viewModel(slotId = null)
        advanceUntilIdle()

        model.onEvent(TemplatePreviewEvent.Add)
        advanceUntilIdle()

        val expected = DefaultMealSlot()(LocalTime(10, 0), DefaultMealSlots)?.id
        assertEquals(expected, diary.addedBatches.single().first().mealSlotId)
    }

    @Test
    fun add_withNoEnabledMeal_failsAndLogsNothing() = runTest(dispatcher) {
        templates.templates += comida
        val model = viewModel(slotId = null, slots = FakeMealSlotRepository(Result.success(emptyList())))
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TemplatePreviewEvent.Add)
            advanceUntilIdle()
            assertEquals(TemplatePreviewEffect.Failed(null), awaitItem())
        }
        assertTrue(diary.addedBatches.isEmpty())
        assertTrue(model.content().addFailed)
    }

    @Test
    fun add_whenTheWriteFails_emitsFailed_logsNothing_andAllowsRetry() = runTest(dispatcher) {
        templates.templates += comida
        diary.failBatch()
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TemplatePreviewEvent.Add)
            advanceUntilIdle()
            assertEquals(TemplatePreviewEffect.Failed(AppError.Network), awaitItem())
        }
        assertTrue(diary.addedBatches.isEmpty())
        assertTrue(model.content().addFailed)
        assertFalse(model.content().adding)
    }

    @Test
    fun add_offline_doesNothing_andIsNotAllowed() = runTest(dispatcher) {
        templates.templates += comida
        val model = viewModel()
        advanceUntilIdle()
        connectivity.state.value = false
        advanceUntilIdle()

        model.onEvent(TemplatePreviewEvent.Add)
        advanceUntilIdle()

        assertTrue(model.content().isOffline)
        assertFalse(model.content().canAdd)
        assertTrue(diary.addedBatches.isEmpty())
    }

    @Test
    fun add_ofAnEmptyComida_isNotAllowed() = runTest(dispatcher) {
        templates.templates += comida.copy(items = emptyList())
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TemplatePreviewEvent.Add)
        advanceUntilIdle()

        assertFalse(model.content().canAdd)
        assertTrue(diary.addedBatches.isEmpty())
    }

    @Test
    fun add_doubleTap_addsOnce() = runTest(dispatcher) {
        templates.templates += comida
        val gate = CompletableDeferred<Unit>()
        diary.batchGate = gate
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TemplatePreviewEvent.Add)
        model.onEvent(TemplatePreviewEvent.Add)
        advanceUntilIdle()
        assertTrue(model.content().adding)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, diary.addedBatches.size)
    }

    @Test
    fun edit_emitsOpenEditor_andAddsNothing() = runTest(dispatcher) {
        templates.templates += comida
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TemplatePreviewEvent.Edit)
            assertEquals(TemplatePreviewEffect.OpenEditor, awaitItem())
        }
        assertTrue(diary.addedBatches.isEmpty())
    }

    @Test
    fun refresh_picksUpAnEditedComida_withoutShowingTheLoadingState() = runTest(dispatcher) {
        templates.templates += comida
        val model = viewModel()
        advanceUntilIdle()
        templates.save("t1", "Comida B", "lunch", listOf(NewTemplateItem("rice", 200.0, null)))

        model.onEvent(TemplatePreviewEvent.Refresh)

        assertEquals("Comida A", model.content().name)
        advanceUntilIdle()
        assertEquals("Comida B", model.content().name)
        assertEquals(400.0, model.content().totalKcal, 0.0)
    }

    @Test
    fun refresh_thatFails_keepsTheContentAlreadyShown() = runTest(dispatcher) {
        templates.templates += comida
        val model = viewModel()
        advanceUntilIdle()
        templates.listFailure = AppError.Network

        model.onEvent(TemplatePreviewEvent.Refresh)
        advanceUntilIdle()

        assertEquals("Comida A", model.content().name)
    }

    @Test
    fun refresh_afterTheComidaWasDeleted_isAnError() = runTest(dispatcher) {
        templates.templates += comida
        val model = viewModel()
        advanceUntilIdle()
        templates.templates.clear()

        model.onEvent(TemplatePreviewEvent.Refresh)
        advanceUntilIdle()

        assertEquals(TemplatePreviewUiState.Error, model.uiState.value)
    }
}
