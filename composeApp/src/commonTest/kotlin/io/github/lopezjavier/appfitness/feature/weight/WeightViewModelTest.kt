package io.github.lopezjavier.appfitness.feature.weight

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.usecase.BodyMeasurementProblem
import io.github.lopezjavier.appfitness.domain.usecase.MeasurementsByDay
import io.github.lopezjavier.appfitness.domain.usecase.SaveBodyMeasurement
import io.github.lopezjavier.appfitness.domain.usecase.ValidateBodyMeasurement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One method per Gherkin scenario and rule.
class WeightViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val zone = TimeZone.UTC
    private val clock = object : Clock {
        override fun today(): LocalDate = LocalDate(2026, 10, 21)

        override fun now(): Instant = Instant.parse("2026-10-21T10:00:00Z")
    }
    private val connectivity = FakeConnectivityObserver()
    private val savedState = SavedStateHandle()
    private val yesterday = LocalDate(2026, 10, 20)
    private val today = LocalDate(2026, 10, 21)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun manual(id: String, at: String, kg: Double, fat: Double? = null) =
        BodyMeasurement(id, Instant.parse(at), kg, MeasurementSource.Manual, fat)

    private fun healthConnect(id: String, at: String, kg: Double) =
        BodyMeasurement(id, Instant.parse(at), kg, MeasurementSource.HealthConnect)

    private fun viewModel(repository: FakeWeightRepository, state: SavedStateHandle = savedState) = WeightViewModel(
        repository = repository,
        measurementsByDay = MeasurementsByDay(),
        saveBodyMeasurement = SaveBodyMeasurement(repository, clock, zone),
        validate = ValidateBodyMeasurement(),
        connectivity = connectivity,
        clock = clock,
        timeZone = zone,
        savedState = state,
    )

    // The state is combined on the test dispatcher, so let pending work run before reading it.
    private fun WeightViewModel.content(): WeightContent {
        dispatcher.scheduler.runCurrent()
        return assertIs<WeightUiState.Content>(uiState.value).content
    }

    private fun WeightViewModel.form() = assertNotNull(assertNotNull(content().sheet).form)

    private fun WeightViewModel.type(weight: String, fat: String = "") {
        onEvent(WeightEvent.SetWeight(weight))
        if (fat.isNotEmpty()) onEvent(WeightEvent.SetBodyFat(fat))
    }

    // Loading and states

    @Test
    fun opening_goesFromLoadingToTheCurrentMonthWithMarkedDaysAndTheCurrentWeight() = runTest(dispatcher) {
        val repository = FakeWeightRepository(
            manual("a", "2026-10-02T08:00:00Z", 75.0),
            manual("b", "2026-10-18T08:00:00Z", 74.2, fat = 15.1),
        )

        val model = viewModel(repository)
        assertEquals(WeightUiState.Loading, model.uiState.value)
        advanceUntilIdle()

        val content = model.content()
        assertEquals(YearMonth(2026, 10), content.month)
        assertEquals(setOf(LocalDate(2026, 10, 2), LocalDate(2026, 10, 18)), content.markedDays)
        assertEquals("b", content.current?.id)
        assertFalse(content.canGoNext)
        assertFalse(content.isEmpty)
        assertNull(content.sheet)
    }

    @Test
    fun currentWeight_isTheLatestRowOfAnySource() = runTest(dispatcher) {
        val repository = FakeWeightRepository(
            manual("m", "2026-10-18T08:00:00Z", 74.2),
            healthConnect("hc", "2026-10-19T07:00:00Z", 73.9),
        )

        val model = viewModel(repository)
        advanceUntilIdle()

        assertEquals("hc", model.content().current?.id)
    }

    @Test
    fun withoutMeasurements_theStateIsEmpty() = runTest(dispatcher) {
        val model = viewModel(FakeWeightRepository())
        advanceUntilIdle()

        assertTrue(model.content().isEmpty)
        assertTrue(model.content().markedDays.isEmpty())
    }

    @Test
    fun failedLoad_showsErrorAndRetryRecovers() = runTest(dispatcher) {
        val repository = FakeWeightRepository(manual("a", "2026-10-18T08:00:00Z", 74.2))
        repository.failReads = true
        val model = viewModel(repository)
        advanceUntilIdle()
        assertEquals(WeightUiState.Error, model.uiState.value)

        repository.failReads = false
        model.onEvent(WeightEvent.Retry)
        advanceUntilIdle()

        assertEquals("a", model.content().current?.id)
    }

    @Test
    fun refresh_picksUpAChangeMadeElsewhereWithoutShowingLoading() = runTest(dispatcher) {
        val repository = FakeWeightRepository(manual("a", "2026-10-18T08:00:00Z", 74.2))
        val model = viewModel(repository)
        advanceUntilIdle()

        repository.rows += manual("b", "2026-10-20T08:00:00Z", 73.0)
        model.onEvent(WeightEvent.Refresh)
        assertIs<WeightUiState.Content>(model.uiState.value)
        advanceUntilIdle()

        assertEquals("b", model.content().current?.id)
    }

    @Test
    fun failedRefresh_keepsWhatIsShown() = runTest(dispatcher) {
        val repository = FakeWeightRepository(manual("a", "2026-10-18T08:00:00Z", 74.2))
        val model = viewModel(repository)
        advanceUntilIdle()

        repository.failReads = true
        model.onEvent(WeightEvent.Refresh)
        advanceUntilIdle()

        assertEquals("a", model.content().current?.id)
    }

    // Month navigation

    @Test
    fun previousMonth_loadsThatMonthAndNextIsBlockedAtTheCurrentOne() = runTest(dispatcher) {
        val repository = FakeWeightRepository(
            manual("sep", "2026-09-15T08:00:00Z", 76.0),
            manual("oct", "2026-10-18T08:00:00Z", 74.2),
        )
        val model = viewModel(repository)
        advanceUntilIdle()

        model.onEvent(WeightEvent.NextMonth)
        advanceUntilIdle()
        assertEquals(YearMonth(2026, 10), model.content().month)

        model.onEvent(WeightEvent.PreviousMonth)
        advanceUntilIdle()

        val september = model.content()
        assertEquals(YearMonth(2026, 9), september.month)
        assertEquals(setOf(LocalDate(2026, 9, 15)), september.markedDays)
        assertTrue(september.canGoNext)
        assertEquals("oct", september.current?.id)

        model.onEvent(WeightEvent.NextMonth)
        advanceUntilIdle()
        assertEquals(YearMonth(2026, 10), model.content().month)
    }

    @Test
    fun visibleMonth_survivesRecreationFromTheSameSavedState() = runTest(dispatcher) {
        val repository = FakeWeightRepository(manual("sep", "2026-09-15T08:00:00Z", 76.0))
        viewModel(repository).also {
            advanceUntilIdle()
            it.onEvent(WeightEvent.PreviousMonth)
            advanceUntilIdle()
        }

        val recreated = viewModel(repository)
        advanceUntilIdle()

        assertEquals(YearMonth(2026, 9), recreated.content().month)
    }

    // Opening days and prefill

    @Test
    fun logToday_withoutATodayRow_opensCreateModePrefilledWithTheLatestWeightAndNow() = runTest(dispatcher) {
        val repository = FakeWeightRepository(manual("a", "2026-10-18T08:00:00Z", 74.2))
        val model = viewModel(repository)
        advanceUntilIdle()

        model.onEvent(WeightEvent.LogToday)
        advanceUntilIdle()

        val sheet = assertNotNull(model.content().sheet)
        assertEquals(today, sheet.day)
        assertTrue(sheet.isToday)
        val form = model.form()
        assertFalse(form.isEditing)
        assertEquals("74,2", form.weightText)
        assertEquals("", form.fatText)
        assertEquals(10, form.hour)
        assertEquals(0, form.minute)
        assertEquals(10 * 60, form.maxMinuteOfDay)
        assertTrue(form.canSave)
    }

    @Test
    fun logToday_withATodayRow_opensEditModeWithItsValues() = runTest(dispatcher) {
        val repository = FakeWeightRepository(manual("t", "2026-10-21T07:30:00Z", 73.9, fat = 15.1))
        val model = viewModel(repository)
        advanceUntilIdle()

        model.onEvent(WeightEvent.LogToday)
        advanceUntilIdle()

        val form = model.form()
        assertEquals("t", form.existingId)
        assertEquals("73,9", form.weightText)
        assertEquals("15,1", form.fatText)
        assertEquals(7, form.hour)
        assertEquals(30, form.minute)
    }

    @Test
    fun logToday_withNoMeasurementsAtAll_startsWithABlankWeightThatCannotBeSaved() = runTest(dispatcher) {
        val model = viewModel(FakeWeightRepository())
        advanceUntilIdle()

        model.onEvent(WeightEvent.LogToday)
        advanceUntilIdle()

        assertEquals("", model.form().weightText)
        assertFalse(model.form().canSave)
        assertNull(model.form().weightProblem)
    }

    @Test
    fun logToday_whileViewingAnotherMonth_returnsToTheCurrentMonth() = runTest(dispatcher) {
        val repository = FakeWeightRepository(manual("a", "2026-09-15T08:00:00Z", 76.0))
        val model = viewModel(repository)
        advanceUntilIdle()
        model.onEvent(WeightEvent.PreviousMonth)
        advanceUntilIdle()

        model.onEvent(WeightEvent.LogToday)
        advanceUntilIdle()

        assertEquals(YearMonth(2026, 10), model.content().month)
        assertEquals(today, model.content().sheet?.day)
    }

    @Test
    fun openingAnEmptyPastDay_prefillsTheLatestEarlierWeightAndNoon() = runTest(dispatcher) {
        val repository = FakeWeightRepository(
            manual("early", "2026-10-02T08:00:00Z", 75.0),
            manual("later", "2026-10-18T08:00:00Z", 74.2),
        )
        val model = viewModel(repository)
        advanceUntilIdle()

        model.onEvent(WeightEvent.OpenDay(LocalDate(2026, 10, 10)))
        advanceUntilIdle()

        val form = model.form()
        assertEquals("75,0", form.weightText)
        assertEquals(12, form.hour)
        assertEquals(0, form.minute)
        assertNull(form.maxMinuteOfDay)
        assertFalse(assertNotNull(model.content().sheet).isToday)
    }

    @Test
    fun openingAPastDayWithNothingEarlier_fallsBackToTheLatestOverall() = runTest(dispatcher) {
        val repository = FakeWeightRepository(manual("later", "2026-10-18T08:00:00Z", 74.2))
        val model = viewModel(repository)
        advanceUntilIdle()

        model.onEvent(WeightEvent.OpenDay(LocalDate(2026, 10, 3)))
        advanceUntilIdle()

        assertEquals("74,2", model.form().weightText)
    }

    @Test
    fun openingADayWithAManualRow_editsIt() = runTest(dispatcher) {
        val repository = FakeWeightRepository(manual("m", "2026-10-18T08:15:00Z", 74.2))
        val model = viewModel(repository)
        advanceUntilIdle()

        model.onEvent(WeightEvent.OpenDay(LocalDate(2026, 10, 18)))
        advanceUntilIdle()

        val form = model.form()
        assertEquals("m", form.existingId)
        assertEquals(8, form.hour)
        assertEquals(15, form.minute)
    }

    @Test
    fun futureDays_cannotBeOpened() = runTest(dispatcher) {
        val model = viewModel(FakeWeightRepository(manual("a", "2026-10-18T08:00:00Z", 74.2)))
        advanceUntilIdle()

        model.onEvent(WeightEvent.OpenDay(LocalDate(2026, 10, 22)))
        advanceUntilIdle()

        assertNull(model.content().sheet)
    }

    @Test
    fun closingTheSheet_discardsTheDraft() = runTest(dispatcher) {
        val repository = FakeWeightRepository(manual("a", "2026-10-18T08:00:00Z", 74.2))
        val model = viewModel(repository)
        advanceUntilIdle()
        model.onEvent(WeightEvent.LogToday)
        advanceUntilIdle()
        model.type("80,0")

        model.onEvent(WeightEvent.CloseSheet)
        model.onEvent(WeightEvent.LogToday)
        advanceUntilIdle()

        assertTrue(repository.saves.isEmpty())
        assertEquals("74,2", model.form().weightText)
    }

    // Saving

    @Test
    fun savingToday_storesAManualWeightForTodayAndUpdatesThePanel() = runTest(dispatcher) {
        val repository = FakeWeightRepository(manual("a", "2026-10-18T08:00:00Z", 74.2))
        val model = viewModel(repository)
        advanceUntilIdle()
        model.onEvent(WeightEvent.LogToday)
        advanceUntilIdle()

        model.type("73,9", "15,1")
        model.onEvent(WeightEvent.Save)
        advanceUntilIdle()

        val save = repository.saves.single()
        assertNull(save.id)
        assertEquals(73.9, save.weightKg)
        assertEquals(15.1, save.bodyFatPct)
        assertEquals(Instant.parse("2026-10-21T10:00:00Z"), save.measuredAt)
        val content = model.content()
        assertNull(content.sheet)
        assertEquals(73.9, content.current?.weightKg)
        assertTrue(today in content.markedDays)
    }

    @Test
    fun reopeningTodayAfterSaving_editsTheSameRowWithoutAddingASecond() = runTest(dispatcher) {
        val repository = FakeWeightRepository(manual("a", "2026-10-18T08:00:00Z", 74.2))
        val model = viewModel(repository)
        advanceUntilIdle()
        model.onEvent(WeightEvent.LogToday)
        advanceUntilIdle()
        model.type("73,9")
        model.onEvent(WeightEvent.Save)
        advanceUntilIdle()

        model.onEvent(WeightEvent.LogToday)
        advanceUntilIdle()
        assertEquals("73,9", model.form().weightText)
        val savedId = model.form().existingId
        assertNotNull(savedId)
        model.type("73,5")
        model.onEvent(WeightEvent.Save)
        advanceUntilIdle()

        assertEquals(savedId, repository.saves.last().id)
        assertEquals(1, repository.rows.count { it.measuredAt >= Instant.parse("2026-10-21T00:00:00Z") })
        assertEquals(73.5, model.content().current?.weightKg)
    }

    @Test
    fun savingAnEmptyPastDayAtAChosenTime_storesThatMomentAndMarksTheDay() = runTest(dispatcher) {
        val repository = FakeWeightRepository(manual("a", "2026-10-15T08:00:00Z", 74.2))
        val model = viewModel(repository)
        advanceUntilIdle()
        model.onEvent(WeightEvent.OpenDay(yesterday))
        advanceUntilIdle()

        model.type("74,0")
        model.onEvent(WeightEvent.SetTime(7, 30))
        model.onEvent(WeightEvent.Save)
        advanceUntilIdle()

        assertEquals(Instant.parse("2026-10-20T07:30:00Z"), repository.saves.single().measuredAt)
        assertTrue(yesterday in model.content().markedDays)
    }

    @Test
    fun editing_changesWeightFatAndTimeButKeepsTheDayAndId() = runTest(dispatcher) {
        val repository = FakeWeightRepository(manual("m", "2026-10-18T08:15:00Z", 74.2))
        val model = viewModel(repository)
        advanceUntilIdle()
        model.onEvent(WeightEvent.OpenDay(LocalDate(2026, 10, 18)))
        advanceUntilIdle()

        model.type("73,0", "14,0")
        model.onEvent(WeightEvent.SetTime(9, 5))
        model.onEvent(WeightEvent.Save)
        advanceUntilIdle()

        val save = repository.saves.single()
        assertEquals("m", save.id)
        assertEquals(Instant.parse("2026-10-18T09:05:00Z"), save.measuredAt)
        assertEquals(73.0, save.weightKg)
        assertEquals(14.0, save.bodyFatPct)
    }

    @Test
    fun failedSave_keepsTheSheetOpenWithTheDraftAndShowsTheError() = runTest(dispatcher) {
        val repository = FakeWeightRepository(manual("a", "2026-10-18T08:00:00Z", 74.2))
        val model = viewModel(repository)
        advanceUntilIdle()
        model.onEvent(WeightEvent.LogToday)
        advanceUntilIdle()
        model.type("73,9")
        repository.saveFailure = AppException(AppError.Unknown)

        model.onEvent(WeightEvent.Save)
        advanceUntilIdle()

        val form = model.form()
        assertEquals("73,9", form.weightText)
        assertEquals(WeightSaveError.Failed, form.saveError)
        assertFalse(form.isSaving)
        assertTrue(form.canSave)

        model.type("73,8")
        assertNull(model.form().saveError)

        repository.saveFailure = AppException(AppError.Network)
        model.onEvent(WeightEvent.Save)
        advanceUntilIdle()
        assertEquals(WeightSaveError.Offline, model.form().saveError)
    }

    // Validation

    @Test
    fun validationOutline_outOfRangeValuesDisableSaveAndReportTheProblem() = runTest(dispatcher) {
        val model = viewModel(FakeWeightRepository(manual("a", "2026-10-18T08:00:00Z", 74.2)))
        advanceUntilIdle()
        model.onEvent(WeightEvent.LogToday)
        advanceUntilIdle()

        model.type("19,9")
        assertEquals(BodyMeasurementProblem.TooLow, model.form().weightProblem)
        assertFalse(model.form().canSave)

        model.type("401")
        assertEquals(BodyMeasurementProblem.TooHigh, model.form().weightProblem)
        assertFalse(model.form().canSave)

        model.type("74,25")
        assertEquals(BodyMeasurementProblem.TooManyDecimals, model.form().weightProblem)

        model.type("74,2", "80")
        assertNull(model.form().weightProblem)
        assertEquals(BodyMeasurementProblem.TooHigh, model.form().fatProblem)
        assertFalse(model.form().canSave)
    }

    @Test
    fun boundaries_areAcceptedAndCommaOrPointBothParse() = runTest(dispatcher) {
        val model = viewModel(FakeWeightRepository(manual("a", "2026-10-18T08:00:00Z", 74.2)))
        advanceUntilIdle()
        model.onEvent(WeightEvent.LogToday)
        advanceUntilIdle()

        listOf("20" to "2", "400" to "75", "73.9" to "15,1", "73,9" to "").forEach { (weight, fat) ->
            model.onEvent(WeightEvent.SetWeight(weight))
            model.onEvent(WeightEvent.SetBodyFat(fat))
            assertTrue(model.form().canSave, "$weight / $fat")
        }
        model.onEvent(WeightEvent.SetBodyFat("1,9"))
        assertEquals(BodyMeasurementProblem.TooLow, model.form().fatProblem)
        model.onEvent(WeightEvent.SetBodyFat("abc"))
        assertEquals(BodyMeasurementProblem.NotANumber, model.form().fatProblem)
    }

    @Test
    fun timeOnToday_isClampedToNowAndPastDaysAreFree() = runTest(dispatcher) {
        val model = viewModel(FakeWeightRepository(manual("a", "2026-10-10T08:00:00Z", 74.2)))
        advanceUntilIdle()
        model.onEvent(WeightEvent.LogToday)
        advanceUntilIdle()

        model.onEvent(WeightEvent.SetTime(23, 59))
        assertEquals(10, model.form().hour)
        assertEquals(0, model.form().minute)

        model.onEvent(WeightEvent.OpenDay(yesterday))
        advanceUntilIdle()
        model.onEvent(WeightEvent.SetTime(23, 59))
        assertEquals(23, model.form().hour)
        assertEquals(59, model.form().minute)
    }

    // Deleting

    @Test
    fun deletingTheManualRow_isImmediateAndRecalculatesThePanelAndCalendar() = runTest(dispatcher) {
        val repository = FakeWeightRepository(
            manual("old", "2026-10-02T08:00:00Z", 75.0),
            manual("m", "2026-10-18T08:00:00Z", 74.2),
        )
        val model = viewModel(repository)
        advanceUntilIdle()
        model.onEvent(WeightEvent.OpenDay(LocalDate(2026, 10, 18)))
        advanceUntilIdle()

        model.onEvent(WeightEvent.DeleteManual)
        advanceUntilIdle()

        assertEquals(listOf("m"), repository.deletes)
        val content = model.content()
        assertNull(content.sheet)
        assertEquals("old", content.current?.id)
        assertEquals(setOf(LocalDate(2026, 10, 2)), content.markedDays)
    }

    @Test
    fun failedDelete_keepsTheSheetAndEmitsAnEffect() = runTest(dispatcher) {
        val repository = FakeWeightRepository(manual("m", "2026-10-18T08:00:00Z", 74.2))
        val model = viewModel(repository)
        advanceUntilIdle()
        model.onEvent(WeightEvent.OpenDay(LocalDate(2026, 10, 18)))
        advanceUntilIdle()
        repository.deleteFailure = AppException(AppError.Network)

        model.effects.test {
            model.onEvent(WeightEvent.DeleteManual)
            advanceUntilIdle()
            assertEquals(WeightEffect.DeleteFailed(offline = true), awaitItem())
        }
        assertNotNull(model.content().sheet)
    }

    // Health Connect rows

    @Test
    fun healthConnectRows_areListedWithoutAFormAndCanBeDeleted() = runTest(dispatcher) {
        val repository = FakeWeightRepository(
            healthConnect("hc1", "2026-10-19T07:00:00Z", 73.9),
            healthConnect("hc2", "2026-10-19T20:00:00Z", 74.3),
        )
        val model = viewModel(repository)
        advanceUntilIdle()

        model.onEvent(WeightEvent.OpenDay(LocalDate(2026, 10, 19)))
        advanceUntilIdle()
        val sheet = assertNotNull(model.content().sheet)
        assertEquals(listOf("hc2", "hc1"), sheet.healthConnect.map { it.id })
        assertNull(sheet.form)

        model.onEvent(WeightEvent.DeleteHealthConnect("hc1"))
        advanceUntilIdle()

        assertEquals(listOf("hc1"), repository.deletes)
        assertEquals(listOf("hc2"), model.content().sheet?.healthConnect?.map { it.id })
        assertEquals("hc2", model.content().current?.id)
    }

    @Test
    fun deletingTheLastHealthConnectRow_closesTheSheet() = runTest(dispatcher) {
        val repository = FakeWeightRepository(healthConnect("hc1", "2026-10-19T07:00:00Z", 73.9))
        val model = viewModel(repository)
        advanceUntilIdle()
        model.onEvent(WeightEvent.OpenDay(LocalDate(2026, 10, 19)))
        advanceUntilIdle()

        model.onEvent(WeightEvent.DeleteHealthConnect("hc1"))
        advanceUntilIdle()

        assertNull(model.content().sheet)
        assertTrue(model.content().isEmpty)
    }

    @Test
    fun aDayWithOnlyHealthConnectRows_canStartAManualWeight() = runTest(dispatcher) {
        val repository = FakeWeightRepository(healthConnect("hc1", "2026-10-19T07:00:00Z", 73.9))
        val model = viewModel(repository)
        advanceUntilIdle()
        model.onEvent(WeightEvent.OpenDay(LocalDate(2026, 10, 19)))
        advanceUntilIdle()

        model.onEvent(WeightEvent.StartManual)
        advanceUntilIdle()
        assertFalse(model.form().isEditing)
        assertEquals("73,9", model.form().weightText)
        model.type("73,5")
        model.onEvent(WeightEvent.Save)
        advanceUntilIdle()

        val save = repository.saves.single()
        assertNull(save.id)
        assertEquals(Instant.parse("2026-10-19T12:00:00Z"), save.measuredAt)
        assertNull(model.content().sheet)
    }

    // Offline

    @Test
    fun offline_disablesSaveAndDelete() = runTest(dispatcher) {
        val repository = FakeWeightRepository(manual("m", "2026-10-18T08:00:00Z", 74.2))
        val model = viewModel(repository)
        advanceUntilIdle()
        model.onEvent(WeightEvent.OpenDay(LocalDate(2026, 10, 18)))
        advanceUntilIdle()

        connectivity.state.value = false
        advanceUntilIdle()

        assertFalse(model.content().isOnline)
        assertFalse(model.form().canSave)
        assertFalse(model.form().canDelete)
        model.onEvent(WeightEvent.Save)
        model.onEvent(WeightEvent.DeleteManual)
        advanceUntilIdle()
        assertTrue(repository.saves.isEmpty())
        assertTrue(repository.deletes.isEmpty())
    }

    // Restoration

    @Test
    fun openDayAndHalfFilledDraft_surviveRecreationFromTheSameSavedState() = runTest(dispatcher) {
        val repository = FakeWeightRepository(manual("a", "2026-10-15T08:00:00Z", 74.2))
        viewModel(repository).also {
            advanceUntilIdle()
            it.onEvent(WeightEvent.OpenDay(yesterday))
            advanceUntilIdle()
            it.type("73,", "15")
            it.onEvent(WeightEvent.SetTime(7, 30))
        }

        val recreated = viewModel(repository)
        advanceUntilIdle()

        assertEquals(yesterday, recreated.content().sheet?.day)
        val form = recreated.form()
        assertEquals("73,", form.weightText)
        assertEquals("15", form.fatText)
        assertEquals(7, form.hour)
        assertEquals(30, form.minute)
        assertNull(form.existingId)
    }

    @Test
    fun aClosedSheet_isNotRestored() = runTest(dispatcher) {
        val repository = FakeWeightRepository(manual("a", "2026-10-15T08:00:00Z", 74.2))
        viewModel(repository).also {
            advanceUntilIdle()
            it.onEvent(WeightEvent.OpenDay(yesterday))
            advanceUntilIdle()
            it.onEvent(WeightEvent.CloseSheet)
        }

        val recreated = viewModel(repository)
        advanceUntilIdle()

        assertNull(recreated.content().sheet)
    }
}
