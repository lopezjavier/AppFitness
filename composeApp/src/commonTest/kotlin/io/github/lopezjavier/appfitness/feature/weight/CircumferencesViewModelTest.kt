package io.github.lopezjavier.appfitness.feature.weight

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.usecase.CircumferenceProblem
import io.github.lopezjavier.appfitness.domain.usecase.ValidateCircumference
import io.github.lopezjavier.appfitness.domain.usecase.WaistHipRatio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
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
class CircumferencesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = object : Clock {
        override fun today(): LocalDate = LocalDate(2026, 10, 21)

        override fun now(): Instant = Instant.parse("2026-10-21T10:00:00Z")
    }
    private val connectivity = FakeConnectivityObserver()
    private val savedState = SavedStateHandle()
    private val today = LocalDate(2026, 10, 21)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun row(id: String, site: CircumferenceSite, cm: Double, at: String = "2026-10-18T08:00:00Z") =
        BodyCircumference(id, site, cm, Instant.parse(at))

    private fun viewModel(repository: FakeCircumferenceRepository, state: SavedStateHandle = savedState) =
        CircumferencesViewModel(
            repository = repository,
            validate = ValidateCircumference(),
            waistHipRatio = WaistHipRatio(),
            connectivity = connectivity,
            clock = clock,
            timeZone = TimeZone.UTC,
            savedState = state,
        )

    private fun CircumferencesViewModel.content(): CircumferencesContent {
        dispatcher.scheduler.runCurrent()
        return assertIs<CircumferencesUiState.Content>(uiState.value).content
    }

    private fun CircumferencesViewModel.sheet() = assertNotNull(content().sheet)

    // Loading and states

    @Test
    fun opening_goesFromLoadingToGroupsBySiteNewestFirst() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository(
            row("w1", CircumferenceSite.Waist, 86.0, "2026-10-01T08:00:00Z"),
            row("w2", CircumferenceSite.Waist, 84.5, "2026-10-18T08:00:00Z"),
            row("n1", CircumferenceSite.Neck, 38.0),
        )

        val model = viewModel(repository)
        assertEquals(CircumferencesUiState.Loading, model.uiState.value)
        advanceUntilIdle()

        val content = model.content()
        assertEquals(listOf(CircumferenceSite.Neck, CircumferenceSite.Waist), content.groups.map { it.site })
        assertEquals(listOf("w2", "w1"), content.groups.last().rows.map { it.id })
        assertFalse(content.isEmpty)
        assertNull(content.sheet)
    }

    @Test
    fun noRows_isEmptyWithoutRatio() = runTest(dispatcher) {
        val model = viewModel(FakeCircumferenceRepository())
        advanceUntilIdle()

        assertTrue(model.content().isEmpty)
        assertNull(model.content().ratio)
    }

    @Test
    fun failedRead_isErrorAndRetryRecovers() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository(row("w", CircumferenceSite.Waist, 84.5))
        repository.failReads = true
        val model = viewModel(repository)
        advanceUntilIdle()
        assertEquals(CircumferencesUiState.Error, model.uiState.value)

        repository.failReads = false
        model.onEvent(CircumferencesEvent.Retry)
        advanceUntilIdle()

        assertEquals(1, model.content().groups.size)
    }

    @Test
    fun silentRefreshFailure_keepsTheRowsShown() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository(row("w", CircumferenceSite.Waist, 84.5))
        val model = viewModel(repository)
        advanceUntilIdle()

        repository.failReads = true
        model.onEvent(CircumferencesEvent.Refresh)
        advanceUntilIdle()

        assertEquals(1, model.content().groups.size)
    }

    // Ratio

    @Test
    fun ratio_needsWaistAndHip() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository(row("w", CircumferenceSite.Waist, 84.5))
        val model = viewModel(repository)
        advanceUntilIdle()
        assertNull(model.content().ratio)

        repository.rows += row("h", CircumferenceSite.Hip, 98.0)
        model.onEvent(CircumferencesEvent.Refresh)
        advanceUntilIdle()

        assertEquals(0.86, model.content().ratio)
    }

    // Add

    @Test
    fun openAdd_defaultsToWaistAndNow() = runTest(dispatcher) {
        val model = viewModel(FakeCircumferenceRepository())
        advanceUntilIdle()

        model.onEvent(CircumferencesEvent.OpenAdd)

        val sheet = model.sheet()
        assertEquals(CircumferenceSite.Waist, sheet.site)
        assertEquals(today, sheet.date)
        assertEquals(10, sheet.hour)
        assertEquals(0, sheet.minute)
        assertFalse(sheet.isEditing)
        assertFalse(sheet.canSave)
        assertFalse(sheet.canDelete)
    }

    @Test
    fun logWaist_createsARowShownInTheWaistHistory() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository()
        val model = viewModel(repository)
        advanceUntilIdle()

        model.onEvent(CircumferencesEvent.OpenAdd)
        model.onEvent(CircumferencesEvent.SetValue("84,5"))
        model.onEvent(CircumferencesEvent.Save)
        advanceUntilIdle()

        val content = model.content()
        assertNull(content.sheet)
        val waist = content.groups.single { it.site == CircumferenceSite.Waist }
        assertEquals(84.5, waist.rows.single().valueCm)
        assertEquals(Instant.parse("2026-10-21T10:00:00Z"), waist.rows.single().measuredAt)
    }

    @Test
    fun selectSite_changesTheSiteOfANewMeasurement() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository()
        val model = viewModel(repository)
        advanceUntilIdle()

        model.onEvent(CircumferencesEvent.OpenAdd)
        model.onEvent(CircumferencesEvent.SelectSite(CircumferenceSite.Calf))
        model.onEvent(CircumferencesEvent.SetValue("38"))
        model.onEvent(CircumferencesEvent.Save)
        advanceUntilIdle()

        assertEquals(CircumferenceSite.Calf, repository.rows.single().site)
    }

    // Validation

    @Test
    fun invalidValues_blockSavingWithTheProblem() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository()
        val model = viewModel(repository)
        advanceUntilIdle()
        model.onEvent(CircumferencesEvent.OpenAdd)

        model.onEvent(CircumferencesEvent.SetValue("19,9"))
        assertEquals(CircumferenceProblem.TooLow, model.sheet().valueProblem)
        assertFalse(model.sheet().canSave)

        model.onEvent(CircumferencesEvent.SetValue("251"))
        assertEquals(CircumferenceProblem.TooHigh, model.sheet().valueProblem)
        assertFalse(model.sheet().canSave)

        model.onEvent(CircumferencesEvent.SetValue("abc"))
        assertEquals(CircumferenceProblem.NotANumber, model.sheet().valueProblem)

        model.onEvent(CircumferencesEvent.Save)
        advanceUntilIdle()
        assertTrue(repository.rows.isEmpty())
    }

    @Test
    fun boundaryValues_canBeSaved() = runTest(dispatcher) {
        val model = viewModel(FakeCircumferenceRepository())
        advanceUntilIdle()
        model.onEvent(CircumferencesEvent.OpenAdd)

        model.onEvent(CircumferencesEvent.SetValue("20"))
        assertTrue(model.sheet().canSave)
        model.onEvent(CircumferencesEvent.SetValue("250"))
        assertTrue(model.sheet().canSave)
        assertNull(model.sheet().valueProblem)
    }

    @Test
    fun blankValue_hasNoProblemButCannotSave() = runTest(dispatcher) {
        val model = viewModel(FakeCircumferenceRepository())
        advanceUntilIdle()
        model.onEvent(CircumferencesEvent.OpenAdd)

        assertNull(model.sheet().valueProblem)
        assertFalse(model.sheet().canSave)
    }

    // Edit and delete

    @Test
    fun edit_recalculatesTheRatioAndHistory() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository(
            row("w", CircumferenceSite.Waist, 84.5),
            row("h", CircumferenceSite.Hip, 98.0),
        )
        val model = viewModel(repository)
        advanceUntilIdle()

        model.onEvent(CircumferencesEvent.OpenEdit("w"))
        assertEquals("84,5", model.sheet().valueText)
        model.onEvent(CircumferencesEvent.SetValue("90"))
        model.onEvent(CircumferencesEvent.Save)
        advanceUntilIdle()

        val content = model.content()
        assertNull(content.sheet)
        assertEquals(0.92, content.ratio)
        assertEquals(90.0, content.groups.single { it.site == CircumferenceSite.Waist }.rows.single().valueCm)
        assertEquals(2, repository.rows.size)
    }

    @Test
    fun edit_locksTheSite() = runTest(dispatcher) {
        val model = viewModel(FakeCircumferenceRepository(row("w", CircumferenceSite.Waist, 84.5)))
        advanceUntilIdle()

        model.onEvent(CircumferencesEvent.OpenEdit("w"))
        model.onEvent(CircumferencesEvent.SelectSite(CircumferenceSite.Hip))

        assertEquals(CircumferenceSite.Waist, model.sheet().site)
        assertTrue(model.sheet().isEditing)
        assertTrue(model.sheet().canDelete)
    }

    @Test
    fun delete_removesTheRowAtOnceAndRecalculatesTheRatio() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository(
            row("w", CircumferenceSite.Waist, 84.5),
            row("h", CircumferenceSite.Hip, 98.0),
        )
        val model = viewModel(repository)
        advanceUntilIdle()
        assertEquals(0.86, model.content().ratio)

        model.onEvent(CircumferencesEvent.Delete("h"))
        advanceUntilIdle()

        assertEquals(listOf("h"), repository.deletes)
        assertNull(model.content().ratio)
        assertEquals(listOf(CircumferenceSite.Waist), model.content().groups.map { it.site })
    }

    @Test
    fun deleteFromTheEditSheet_closesIt() = runTest(dispatcher) {
        val model = viewModel(FakeCircumferenceRepository(row("w", CircumferenceSite.Waist, 84.5)))
        advanceUntilIdle()
        model.onEvent(CircumferencesEvent.OpenEdit("w"))

        model.onEvent(CircumferencesEvent.Delete("w"))
        advanceUntilIdle()

        assertNull(model.content().sheet)
        assertTrue(model.content().isEmpty)
    }

    @Test
    fun failedDelete_emitsAnEffectAndKeepsTheRow() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository(row("w", CircumferenceSite.Waist, 84.5))
        val model = viewModel(repository)
        advanceUntilIdle()
        repository.writeFailure = AppException(AppError.Network)

        model.effects.test {
            model.onEvent(CircumferencesEvent.Delete("w"))
            advanceUntilIdle()
            assertEquals(CircumferencesEffect.DeleteFailed(offline = true), awaitItem())
        }
        assertEquals(1, model.content().groups.size)
    }

    // Offline and failures

    @Test
    fun offline_blocksSaveAndDelete() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository(row("w", CircumferenceSite.Waist, 84.5))
        val model = viewModel(repository)
        advanceUntilIdle()
        model.onEvent(CircumferencesEvent.OpenEdit("w"))
        connectivity.state.value = false
        advanceUntilIdle()

        assertFalse(model.content().isOnline)
        assertFalse(model.sheet().canSave)
        assertFalse(model.sheet().canDelete)
        model.onEvent(CircumferencesEvent.SetValue("90"))
        model.onEvent(CircumferencesEvent.Save)
        model.onEvent(CircumferencesEvent.Delete("w"))
        advanceUntilIdle()

        assertEquals(84.5, repository.rows.single().valueCm)
        assertTrue(repository.deletes.isEmpty())
    }

    @Test
    fun failedSave_keepsTheSheetWithAnError() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository()
        val model = viewModel(repository)
        advanceUntilIdle()
        model.onEvent(CircumferencesEvent.OpenAdd)
        model.onEvent(CircumferencesEvent.SetValue("84,5"))
        repository.writeFailure = AppException(AppError.Unknown)

        model.onEvent(CircumferencesEvent.Save)
        advanceUntilIdle()

        val sheet = model.sheet()
        assertEquals(WeightSaveError.Failed, sheet.saveError)
        assertFalse(sheet.isSaving)
        assertEquals("84,5", sheet.valueText)

        model.onEvent(CircumferencesEvent.SetValue("85"))
        assertNull(model.sheet().saveError)
    }

    @Test
    fun networkFailureOnSave_isReportedAsOffline() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository()
        val model = viewModel(repository)
        advanceUntilIdle()
        model.onEvent(CircumferencesEvent.OpenAdd)
        model.onEvent(CircumferencesEvent.SetValue("84,5"))
        repository.writeFailure = AppException(AppError.Network)

        model.onEvent(CircumferencesEvent.Save)
        advanceUntilIdle()

        assertEquals(WeightSaveError.Offline, model.sheet().saveError)
    }

    // Date and time

    @Test
    fun futureDateAndTime_areClamped() = runTest(dispatcher) {
        val model = viewModel(FakeCircumferenceRepository())
        advanceUntilIdle()
        model.onEvent(CircumferencesEvent.OpenAdd)

        model.onEvent(CircumferencesEvent.SetDate(LocalDate(2026, 10, 24)))
        assertEquals(today, model.sheet().date)
        assertFalse(model.sheet().canGoNextDay)

        model.onEvent(CircumferencesEvent.SetTime(23, 30))
        assertEquals(10, model.sheet().hour)
        assertEquals(0, model.sheet().minute)
    }

    @Test
    fun pastDay_allowsAnyTimeAndKeepsItInTheSavedInstant() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository()
        val model = viewModel(repository)
        advanceUntilIdle()
        model.onEvent(CircumferencesEvent.OpenAdd)

        model.onEvent(CircumferencesEvent.SetDate(LocalDate(2026, 10, 19)))
        model.onEvent(CircumferencesEvent.SetTime(23, 30))
        model.onEvent(CircumferencesEvent.SetValue("84,5"))
        assertTrue(model.sheet().canGoNextDay)
        assertNull(model.sheet().maxMinuteOfDay)
        model.onEvent(CircumferencesEvent.Save)
        advanceUntilIdle()

        assertEquals(Instant.parse("2026-10-19T23:30:00Z"), repository.rows.single().measuredAt)
    }

    @Test
    fun movingToTodayAfterALateTime_clampsTheTimeToNow() = runTest(dispatcher) {
        val model = viewModel(FakeCircumferenceRepository())
        advanceUntilIdle()
        model.onEvent(CircumferencesEvent.OpenAdd)
        model.onEvent(CircumferencesEvent.SetDate(LocalDate(2026, 10, 20)))
        model.onEvent(CircumferencesEvent.SetTime(23, 0))

        model.onEvent(CircumferencesEvent.SetDate(today))

        assertEquals(10, model.sheet().hour)
        assertEquals(0, model.sheet().minute)
    }

    // Draft

    @Test
    fun closingTheSheet_discardsTheDraft() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository()
        val model = viewModel(repository)
        advanceUntilIdle()
        model.onEvent(CircumferencesEvent.OpenAdd)
        model.onEvent(CircumferencesEvent.SetValue("84,5"))

        model.onEvent(CircumferencesEvent.CloseSheet)
        assertNull(model.content().sheet)
        assertTrue(repository.rows.isEmpty())

        model.onEvent(CircumferencesEvent.OpenAdd)
        assertEquals("", model.sheet().valueText)
    }

    @Test
    fun addDraft_isRestoredFromTheSavedState() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository()
        val first = viewModel(repository)
        advanceUntilIdle()
        first.onEvent(CircumferencesEvent.OpenAdd)
        first.onEvent(CircumferencesEvent.SelectSite(CircumferenceSite.Chest))
        first.onEvent(CircumferencesEvent.SetValue("101,2"))
        first.onEvent(CircumferencesEvent.SetDate(LocalDate(2026, 10, 19)))
        first.onEvent(CircumferencesEvent.SetTime(7, 45))

        val second = viewModel(repository, savedState)
        advanceUntilIdle()

        val sheet = second.sheet()
        assertNull(sheet.existingId)
        assertEquals(CircumferenceSite.Chest, sheet.site)
        assertEquals("101,2", sheet.valueText)
        assertEquals(LocalDate(2026, 10, 19), sheet.date)
        assertEquals(7, sheet.hour)
        assertEquals(45, sheet.minute)
    }

    @Test
    fun editDraft_isRestoredFromTheSavedState() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository(row("w", CircumferenceSite.Waist, 84.5))
        val first = viewModel(repository)
        advanceUntilIdle()
        first.onEvent(CircumferencesEvent.OpenEdit("w"))
        first.onEvent(CircumferencesEvent.SetValue("85"))

        val second = viewModel(repository, savedState)
        advanceUntilIdle()

        val sheet = second.sheet()
        assertEquals("w", sheet.existingId)
        assertEquals(CircumferenceSite.Waist, sheet.site)
        assertEquals("85", sheet.valueText)
    }

    @Test
    fun closedSheet_isNotRestored() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository()
        val first = viewModel(repository)
        advanceUntilIdle()
        first.onEvent(CircumferencesEvent.OpenAdd)
        first.onEvent(CircumferencesEvent.SetValue("84,5"))
        first.onEvent(CircumferencesEvent.CloseSheet)

        val second = viewModel(repository, savedState)
        advanceUntilIdle()

        assertNull(second.content().sheet)
    }
}
