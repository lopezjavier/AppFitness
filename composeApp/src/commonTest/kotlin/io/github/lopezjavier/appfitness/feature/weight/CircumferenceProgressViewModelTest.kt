package io.github.lopezjavier.appfitness.feature.weight

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.usecase.CircumferenceChange
import io.github.lopezjavier.appfitness.domain.usecase.SummarizeCircumferences
import io.github.lopezjavier.appfitness.domain.usecase.ValidateCircumference
import io.github.lopezjavier.appfitness.domain.usecase.WaistHipRatio
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The per-site progress of [CircumferencesViewModel] (T-324): `CircumferencesContent.sites` and the site screen. */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One method per Gherkin scenario and rule.
class CircumferenceProgressViewModelTest {
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

    private fun viewModel(repository: FakeCircumferenceRepository, state: SavedStateHandle = savedState) =
        CircumferencesViewModel(
            repository = repository,
            validate = ValidateCircumference(),
            waistHipRatio = WaistHipRatio(),
            summarize = SummarizeCircumferences(),
            connectivity = connectivity,
            clock = clock,
            timeZone = TimeZone.UTC,
            savedState = state,
        )

    private fun CircumferencesViewModel.content(): CircumferencesContent {
        dispatcher.scheduler.runCurrent()
        return assertIs<CircumferencesUiState.Content>(uiState.value).content
    }

    private fun CircumferencesViewModel.site(site: CircumferenceSite = CircumferenceSite.Waist) =
        siteState(site).also { dispatcher.scheduler.runCurrent() }.value

    // Per-site progress on Peso

    @Test
    fun sites_comeFromTheLoadedRows() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository(
            row("w1", CircumferenceSite.Waist, 86.5, "2026-09-20T08:00:00Z"),
            row("w2", CircumferenceSite.Waist, 84.5, "2026-10-18T08:00:00Z"),
            row("n1", CircumferenceSite.Neck, 38.0),
        )
        val model = viewModel(repository)
        advanceUntilIdle()

        val sites = model.content().sites
        assertEquals(listOf(CircumferenceSite.Neck, CircumferenceSite.Waist), sites.map { it.site })
        val waist = sites.last()
        assertEquals(84.5, waist.latestCm)
        assertEquals(CircumferenceChange.Compared(-2.0, 86.5, LocalDate(2026, 9, 20), 3.0), waist.change)
        assertEquals(CircumferenceChange.NoPair, sites.first().change)
    }

    @Test
    fun save_updatesTheLatestValueAndTheChange() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository(row("w1", CircumferenceSite.Waist, 86.5, "2026-09-20T08:00:00Z"))
        val model = viewModel(repository)
        advanceUntilIdle()
        assertEquals(CircumferenceChange.NoPair, model.content().sites.single().change)

        model.onEvent(CircumferencesEvent.OpenAdd)
        model.type(CircumferenceSite.Waist, "84,5")
        model.onEvent(CircumferencesEvent.Save)
        advanceUntilIdle()

        val waist = model.content().sites.single()
        assertEquals(84.5, waist.latestCm)
        assertEquals(today, waist.latestDate)
        assertEquals(-2.0, assertIs<CircumferenceChange.Compared>(waist.change).deltaCm)
    }

    @Test
    fun optimisticDeleteOfASitesOnlyRow_removesTheSiteAtOnce() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository(
            row("w", CircumferenceSite.Waist, 84.5),
            row("h", CircumferenceSite.Hip, 98.0),
        )
        repository.deleteGate = CompletableDeferred()
        val model = viewModel(repository)
        advanceUntilIdle()

        model.onEvent(CircumferencesEvent.Delete("h"))
        advanceUntilIdle()

        assertTrue(repository.deletes.isEmpty())
        assertEquals(listOf(CircumferenceSite.Waist), model.content().sites.map { it.site })
    }

    @Test
    fun edit_recomputesTheSites() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository(
            row("w1", CircumferenceSite.Waist, 86.5, "2026-09-20T08:00:00Z"),
            row("w2", CircumferenceSite.Waist, 84.5),
        )
        val model = viewModel(repository)
        advanceUntilIdle()

        model.onEvent(CircumferencesEvent.OpenEdit("w2"))
        model.type(CircumferenceSite.Waist, "86")
        model.onEvent(CircumferencesEvent.Save)
        advanceUntilIdle()

        val waist = model.content().sites.single()
        assertEquals(86.0, waist.latestCm)
        val change = assertIs<CircumferenceChange.Compared>(waist.change)
        assertEquals(-0.5, change.deltaCm)
        assertEquals(true, change.withinMargin)
    }

    // Site screen

    @Test
    fun siteScreen_showsTheSummaryAndTheSiteRowsNewestFirst() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository(
            row("w1", CircumferenceSite.Waist, 86.5, "2026-09-20T08:00:00Z"),
            row("w2", CircumferenceSite.Waist, 84.5),
            row("h", CircumferenceSite.Hip, 98.0),
        )
        val model = viewModel(repository)
        assertEquals(CircumferenceSiteUiState.Loading, model.siteState(CircumferenceSite.Waist).value)
        advanceUntilIdle()

        val content = assertIs<CircumferenceSiteUiState.Content>(model.site())
        assertEquals(CircumferenceSite.Waist, content.summary.site)
        assertEquals(84.5, content.summary.latestCm)
        assertEquals(listOf("w2", "w1"), content.rows.map { it.id })
        assertTrue(content.isOnline)
        assertNull(content.sheet)
    }

    @Test
    fun siteScreen_sameSite_isOneFlowForTheLifeOfTheViewModel() = runTest(dispatcher) {
        val model = viewModel(FakeCircumferenceRepository(row("w", CircumferenceSite.Waist, 84.5)))

        assertSame(model.siteState(CircumferenceSite.Waist), model.siteState(CircumferenceSite.Waist))
    }

    @Test
    fun siteScreen_failedRead_isErrorAndRetryRecovers() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository(row("w", CircumferenceSite.Waist, 84.5))
        repository.failReads = true
        val model = viewModel(repository)
        advanceUntilIdle()
        assertEquals(CircumferenceSiteUiState.Error, model.site())

        repository.failReads = false
        model.onEvent(CircumferencesEvent.Retry)
        advanceUntilIdle()

        assertIs<CircumferenceSiteUiState.Content>(model.site())
    }

    @Test
    fun siteScreen_whoseFirstLoadHasNoRowsForTheSite_isGone() = runTest(dispatcher) {
        val model = viewModel(FakeCircumferenceRepository(row("h", CircumferenceSite.Hip, 98.0)))
        advanceUntilIdle()

        assertEquals(CircumferenceSiteUiState.Gone, model.site(CircumferenceSite.Waist))
    }

    @Test
    fun siteScreen_deletingTheLastValue_keepsTheLastSummaryWithNoRowsAndThenEmitsDeleted() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository(row("w", CircumferenceSite.Waist, 84.5))
        repository.deleteGate = CompletableDeferred()
        val model = viewModel(repository)
        advanceUntilIdle()
        val shown = assertIs<CircumferenceSiteUiState.Content>(model.site())

        model.effects.test {
            model.onEvent(CircumferencesEvent.Delete("w"))
            advanceUntilIdle()
            val pending = assertIs<CircumferenceSiteUiState.Content>(model.site())
            assertEquals(shown.summary, pending.summary)
            assertTrue(pending.rows.isEmpty())

            repository.deleteGate?.complete(Unit)
            advanceUntilIdle()
            assertEquals(CircumferencesEffect.Deleted, awaitItem())
        }
        val confirmed = assertIs<CircumferenceSiteUiState.Content>(model.site())
        assertEquals(shown.summary, confirmed.summary)
        assertTrue(confirmed.rows.isEmpty())
    }

    @Test
    fun siteScreen_failedDeleteOfTheLastValue_bringsTheRowBack() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository(row("w", CircumferenceSite.Waist, 84.5))
        repository.deleteGate = CompletableDeferred()
        repository.writeFailure = AppException(AppError.Unknown)
        val model = viewModel(repository)
        advanceUntilIdle()

        model.onEvent(CircumferencesEvent.Delete("w"))
        advanceUntilIdle()
        repository.deleteGate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("w"), assertIs<CircumferenceSiteUiState.Content>(model.site()).rows.map { it.id })
    }

    @Test
    fun siteScreen_deletingOneOfSeveralValues_recomputesTheSummary() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository(
            row("w1", CircumferenceSite.Waist, 86.5, "2026-09-20T08:00:00Z"),
            row("w2", CircumferenceSite.Waist, 84.5),
        )
        val model = viewModel(repository)
        advanceUntilIdle()

        model.onEvent(CircumferencesEvent.Delete("w2"))
        advanceUntilIdle()

        val content = assertIs<CircumferenceSiteUiState.Content>(model.site())
        assertEquals(86.5, content.summary.latestCm)
        assertEquals(CircumferenceChange.NoPair, content.summary.change)
        assertEquals(listOf("w1"), content.rows.map { it.id })
    }

    @Test
    fun siteScreen_editDraft_isRestoredFromTheSavedState() = runTest(dispatcher) {
        val repository = FakeCircumferenceRepository(row("w", CircumferenceSite.Waist, 84.5))
        val first = viewModel(repository)
        advanceUntilIdle()
        first.onEvent(CircumferencesEvent.OpenEdit("w"))
        first.type(CircumferenceSite.Waist, "85")

        val second = viewModel(repository, savedState)
        advanceUntilIdle()

        val sheet = assertNotNull(assertIs<CircumferenceSiteUiState.Content>(second.site()).sheet)
        assertEquals("w", sheet.existingId)
        assertEquals(mapOf(CircumferenceSite.Waist to "85"), sheet.values)
    }

    @Test
    fun siteScreen_offline_isReported() = runTest(dispatcher) {
        val model = viewModel(FakeCircumferenceRepository(row("w", CircumferenceSite.Waist, 84.5)))
        connectivity.state.value = false
        advanceUntilIdle()

        assertFalse(assertIs<CircumferenceSiteUiState.Content>(model.site()).isOnline)
    }
}
