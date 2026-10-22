package io.github.lopezjavier.appfitness.feature.weight

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.CircumferenceEntry
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
        BodyCircumference(id, site, cm, Instant.parse(at), Instant.parse(at))

    private fun CircumferencesViewModel.type(site: CircumferenceSite, text: String) =
        onEvent(CircumferencesEvent.SetValue(site, text))

    private fun CircumferencesViewModel.value(site: CircumferenceSite) = sheet().values.getValue(site)

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
    fun openAdd_listsEverySiteBlankOnToday() = runTest(dispatcher) {
        val model = viewModel(FakeCircumferenceRepository())
        advanceUntilIdle()

        model.onEvent(CircumferencesEvent.OpenAdd)

        val sheet = model.sheet()
        assertEquals(CircumferenceSite.entries, sheet.values.keys.toList())
        assertTrue(sheet.values.values.all { it.isEmpty() })
        assertEquals(today, sheet.date)
        assertNull(sheet.helpSite)
        assertFalse(sheet.isEditing)
        assertFalse(sheet.canSave)
        assertFalse(sheet.canDelete)
    }

    @Test
    fun waistAndHip_areSavedTogetherInOneCallAndGiveTheRatio() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository()
        val model = viewModel(repository)
        advanceUntilIdle()

        model.onEvent(CircumferencesEvent.OpenAdd)
        model.type(CircumferenceSite.Waist, "84,5")
        model.type(CircumferenceSite.Hip, "98,0")
        model.onEvent(CircumferencesEvent.Save)
        advanceUntilIdle()

        assertEquals(
            listOf(
                listOf(
                    CircumferenceEntry(CircumferenceSite.Waist, 84.5),
                    CircumferenceEntry(CircumferenceSite.Hip, 98.0),
                ),
            ),
            repository.addCalls,
        )
        val content = model.content()
        assertNull(content.sheet)
        assertEquals(0.86, content.ratio)
        assertEquals(Instant.parse("2026-10-21T10:00:00Z"), repository.rows.first().measuredAt)
    }

    @Test
    fun onlyFilledSites_areSent() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository()
        val model = viewModel(repository)
        advanceUntilIdle()

        model.onEvent(CircumferencesEvent.OpenAdd)
        model.type(CircumferenceSite.Calf, "38")
        model.onEvent(CircumferencesEvent.Save)
        advanceUntilIdle()

        assertEquals(listOf(listOf(CircumferenceEntry(CircumferenceSite.Calf, 38.0))), repository.addCalls)
    }

    @Test
    fun sameDayRelog_latestSavedWinsInTheRatio() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository()
        val model = viewModel(repository)
        advanceUntilIdle()
        repeat(2) { round ->
            model.onEvent(CircumferencesEvent.OpenAdd)
            model.type(CircumferenceSite.Waist, if (round == 0) "90" else "80")
            model.type(CircumferenceSite.Hip, "100")
            model.onEvent(CircumferencesEvent.Save)
            advanceUntilIdle()
        }

        assertEquals(0.8, model.content().ratio)
        assertEquals(80.0, model.content().groups.single { it.site == CircumferenceSite.Waist }.rows.first().valueCm)
    }

    // Validation

    @Test
    fun invalidValues_blockSavingWithTheProblemOnThatSite() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository()
        val model = viewModel(repository)
        advanceUntilIdle()
        model.onEvent(CircumferencesEvent.OpenAdd)

        model.type(CircumferenceSite.Arm, "19,9")
        assertEquals(mapOf(CircumferenceSite.Arm to CircumferenceProblem.TooLow), model.sheet().problems)
        assertFalse(model.sheet().canSave)

        model.type(CircumferenceSite.Arm, "251")
        assertEquals(mapOf(CircumferenceSite.Arm to CircumferenceProblem.TooHigh), model.sheet().problems)
        assertFalse(model.sheet().canSave)

        model.type(CircumferenceSite.Arm, "abc")
        assertEquals(CircumferenceProblem.NotANumber, model.sheet().problems[CircumferenceSite.Arm])

        model.onEvent(CircumferencesEvent.Save)
        advanceUntilIdle()
        assertTrue(repository.rows.isEmpty())
    }

    @Test
    fun oneInvalidAndOneValid_cannotSave() = runTest(dispatcher) {
        val model = viewModel(FakeCircumferenceRepository())
        advanceUntilIdle()
        model.onEvent(CircumferencesEvent.OpenAdd)

        model.type(CircumferenceSite.Waist, "84,5")
        assertTrue(model.sheet().canSave)
        model.type(CircumferenceSite.Neck, "19,9")

        assertFalse(model.sheet().canSave)
        assertEquals(setOf(CircumferenceSite.Neck), model.sheet().problems.keys)
    }

    @Test
    fun boundaryValues_canBeSaved() = runTest(dispatcher) {
        val model = viewModel(FakeCircumferenceRepository())
        advanceUntilIdle()
        model.onEvent(CircumferencesEvent.OpenAdd)

        model.type(CircumferenceSite.Waist, "20")
        assertTrue(model.sheet().canSave)
        model.type(CircumferenceSite.Waist, "250")
        assertTrue(model.sheet().canSave)
        assertTrue(model.sheet().problems.isEmpty())
    }

    @Test
    fun allBlank_hasNoProblemButCannotSave() = runTest(dispatcher) {
        val model = viewModel(FakeCircumferenceRepository())
        advanceUntilIdle()
        model.onEvent(CircumferencesEvent.OpenAdd)
        model.type(CircumferenceSite.Waist, "   ")

        assertTrue(model.sheet().problems.isEmpty())
        assertFalse(model.sheet().canSave)
    }

    // Help

    @Test
    fun toggleHelp_opensOneSiteAtATimeAndClosesIt() = runTest(dispatcher) {
        val model = viewModel(FakeCircumferenceRepository())
        advanceUntilIdle()
        model.onEvent(CircumferencesEvent.OpenAdd)

        model.onEvent(CircumferencesEvent.ToggleHelp(CircumferenceSite.Waist))
        assertEquals(CircumferenceSite.Waist, model.sheet().helpSite)
        model.onEvent(CircumferencesEvent.ToggleHelp(CircumferenceSite.Hip))
        assertEquals(CircumferenceSite.Hip, model.sheet().helpSite)
        model.onEvent(CircumferencesEvent.ToggleHelp(CircumferenceSite.Hip))
        assertNull(model.sheet().helpSite)
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
        assertEquals("84,5", model.value(CircumferenceSite.Waist))
        model.type(CircumferenceSite.Waist, "90")
        model.onEvent(CircumferencesEvent.Save)
        advanceUntilIdle()

        val content = model.content()
        assertNull(content.sheet)
        assertEquals(0.92, content.ratio)
        assertEquals(90.0, content.groups.single { it.site == CircumferenceSite.Waist }.rows.single().valueCm)
        assertEquals(2, repository.rows.size)
    }

    @Test
    fun edit_locksTheSiteAndKeepsTheRowInstantOnItsDay() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository(row("w", CircumferenceSite.Waist, 84.5))
        val model = viewModel(repository)
        advanceUntilIdle()

        model.onEvent(CircumferencesEvent.OpenEdit("w"))
        model.type(CircumferenceSite.Hip, "99")

        assertEquals(CircumferenceSite.Waist, model.sheet().editSite)
        assertEquals(listOf(CircumferenceSite.Waist), model.sheet().values.keys.toList())
        assertTrue(model.sheet().canDelete)

        model.type(CircumferenceSite.Waist, "85")
        model.onEvent(CircumferencesEvent.Save)
        advanceUntilIdle()

        assertEquals(Instant.parse("2026-10-18T08:00:00Z"), repository.rows.single().measuredAt)
        assertTrue(repository.addCalls.isEmpty())
    }

    @Test
    fun edit_movedToAPastDay_isStoredAtNoon() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository(row("w", CircumferenceSite.Waist, 84.5))
        val model = viewModel(repository)
        advanceUntilIdle()
        model.onEvent(CircumferencesEvent.OpenEdit("w"))

        model.onEvent(CircumferencesEvent.SetDate(LocalDate(2026, 10, 15)))
        model.onEvent(CircumferencesEvent.Save)
        advanceUntilIdle()

        assertEquals(Instant.parse("2026-10-15T12:00:00Z"), repository.rows.single().measuredAt)
    }

    @Test
    fun edit_movedToToday_isStoredAtTheSaveInstant() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository(row("w", CircumferenceSite.Waist, 84.5))
        val model = viewModel(repository)
        advanceUntilIdle()
        model.onEvent(CircumferencesEvent.OpenEdit("w"))

        model.onEvent(CircumferencesEvent.SetDate(today))
        model.onEvent(CircumferencesEvent.Save)
        advanceUntilIdle()

        assertEquals(Instant.parse("2026-10-21T10:00:00Z"), repository.rows.single().measuredAt)
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
        model.type(CircumferenceSite.Waist, "90")
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
        model.type(CircumferenceSite.Waist, "84,5")
        repository.writeFailure = AppException(AppError.Unknown)

        model.onEvent(CircumferencesEvent.Save)
        advanceUntilIdle()

        val sheet = model.sheet()
        assertEquals(WeightSaveError.Failed, sheet.saveError)
        assertFalse(sheet.isSaving)
        assertEquals("84,5", model.value(CircumferenceSite.Waist))

        model.type(CircumferenceSite.Waist, "85")
        assertNull(model.sheet().saveError)
    }

    @Test
    fun networkFailureOnSave_isReportedAsOffline() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository()
        val model = viewModel(repository)
        advanceUntilIdle()
        model.onEvent(CircumferencesEvent.OpenAdd)
        model.type(CircumferenceSite.Waist, "84,5")
        repository.writeFailure = AppException(AppError.Network)

        model.onEvent(CircumferencesEvent.Save)
        advanceUntilIdle()

        assertEquals(WeightSaveError.Offline, model.sheet().saveError)
    }

    // Day

    @Test
    fun futureDate_isClampedToToday() = runTest(dispatcher) {
        val model = viewModel(FakeCircumferenceRepository())
        advanceUntilIdle()
        model.onEvent(CircumferencesEvent.OpenAdd)

        model.onEvent(CircumferencesEvent.SetDate(LocalDate(2026, 10, 24)))

        assertEquals(today, model.sheet().date)
        assertFalse(model.sheet().canGoNextDay)
    }

    @Test
    fun pastDay_isStoredAtNoonLocal() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository()
        val model = viewModel(repository)
        advanceUntilIdle()
        model.onEvent(CircumferencesEvent.OpenAdd)

        model.onEvent(CircumferencesEvent.SetDate(LocalDate(2026, 10, 19)))
        model.type(CircumferenceSite.Waist, "84,5")
        assertTrue(model.sheet().canGoNextDay)
        model.onEvent(CircumferencesEvent.Save)
        advanceUntilIdle()

        assertEquals(Instant.parse("2026-10-19T12:00:00Z"), repository.rows.single().measuredAt)
    }

    // Draft

    @Test
    fun closingTheSheet_discardsTheDraft() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository()
        val model = viewModel(repository)
        advanceUntilIdle()
        model.onEvent(CircumferencesEvent.OpenAdd)
        model.type(CircumferenceSite.Waist, "84,5")

        model.onEvent(CircumferencesEvent.CloseSheet)
        assertNull(model.content().sheet)
        assertTrue(repository.rows.isEmpty())

        model.onEvent(CircumferencesEvent.OpenAdd)
        assertEquals("", model.value(CircumferenceSite.Waist))
    }

    @Test
    fun addDraft_isRestoredFromTheSavedState() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository()
        val first = viewModel(repository)
        advanceUntilIdle()
        first.onEvent(CircumferencesEvent.OpenAdd)
        first.type(CircumferenceSite.Chest, "101,2")
        first.type(CircumferenceSite.Hip, "98,0")
        first.onEvent(CircumferencesEvent.SetDate(LocalDate(2026, 10, 19)))
        first.onEvent(CircumferencesEvent.ToggleHelp(CircumferenceSite.Waist))

        val second = viewModel(repository, savedState)
        advanceUntilIdle()

        val sheet = second.sheet()
        assertNull(sheet.existingId)
        assertEquals("101,2", sheet.values.getValue(CircumferenceSite.Chest))
        assertEquals("98,0", sheet.values.getValue(CircumferenceSite.Hip))
        assertEquals("", sheet.values.getValue(CircumferenceSite.Waist))
        assertEquals(CircumferenceSite.entries, sheet.values.keys.toList())
        assertEquals(LocalDate(2026, 10, 19), sheet.date)
        assertEquals(CircumferenceSite.Waist, sheet.helpSite)
    }

    @Test
    fun staleTimeKeysFromAnOlderVersion_areIgnored() = runTest(dispatcher) {
        val state = SavedStateHandle(
            mapOf(
                "circumference_sheet_open" to true,
                "circumference_draft_site" to "Chest",
                "circumference_draft_value" to "101,2",
                "circumference_draft_minute" to 465,
            ),
        )
        val model = viewModel(FakeCircumferenceRepository(), state)
        advanceUntilIdle()

        assertNull(model.content().sheet)
    }

    @Test
    fun editDraft_isRestoredFromTheSavedState() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository(row("w", CircumferenceSite.Waist, 84.5))
        val first = viewModel(repository)
        advanceUntilIdle()
        first.onEvent(CircumferencesEvent.OpenEdit("w"))
        first.type(CircumferenceSite.Waist, "85")

        val second = viewModel(repository, savedState)
        advanceUntilIdle()

        val sheet = second.sheet()
        assertEquals("w", sheet.existingId)
        assertEquals(CircumferenceSite.Waist, sheet.editSite)
        assertEquals(mapOf(CircumferenceSite.Waist to "85"), sheet.values)
    }

    @Test
    fun closedSheet_isNotRestored() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository()
        val first = viewModel(repository)
        advanceUntilIdle()
        first.onEvent(CircumferencesEvent.OpenAdd)
        first.type(CircumferenceSite.Waist, "84,5")
        first.onEvent(CircumferencesEvent.CloseSheet)

        val second = viewModel(repository, savedState)
        advanceUntilIdle()

        assertNull(second.content().sheet)
    }
}
