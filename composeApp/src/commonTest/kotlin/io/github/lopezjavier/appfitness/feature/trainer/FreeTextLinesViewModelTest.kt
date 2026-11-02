package io.github.lopezjavier.appfitness.feature.trainer

import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.FreeTextKind
import io.github.lopezjavier.appfitness.domain.model.FreeTextLine
import io.github.lopezjavier.appfitness.domain.model.FreeTextStatus
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

private fun line(kind: FreeTextKind, id: String, day: Int, status: FreeTextStatus = FreeTextStatus.Sent) =
    FreeTextLine(kind, id, LocalDate(2026, 10, day), "texto $id", status)

@OptIn(ExperimentalCoroutinesApi::class)
class FreeTextLinesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeCoachIntakeRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = FreeTextLinesViewModel(repository, MutableClock())

    private fun FreeTextLinesViewModel.content() = assertIs<FreeTextLinesUiState.Content>(uiState.value)

    private fun FreeTextLinesViewModel.statusOf(id: String) =
        content().sections.flatMap { it.lines }.first { it.sourceId == id }.status

    @Test
    fun groupsByKind_inScreenOrder_newestFirst() = runTest(dispatcher) {
        repository.lines += listOf(
            line(FreeTextKind.Review, "r1", 3),
            line(FreeTextKind.Intake, "i1", 1),
            line(FreeTextKind.CheckIn, "c1", 5),
            line(FreeTextKind.CheckIn, "c2", 9),
            line(FreeTextKind.Discomfort, "d1", 2),
            line(FreeTextKind.Unknown, "u1", 4),
        )
        val model = viewModel()

        advanceUntilIdle()

        val sections = model.content().sections
        assertEquals(
            listOf(
                FreeTextKind.Intake,
                FreeTextKind.CheckIn,
                FreeTextKind.Discomfort,
                FreeTextKind.Review,
                FreeTextKind.Unknown,
            ),
            sections.map { it.kind },
        )
        assertEquals(listOf("c2", "c1"), sections[1].lines.map { it.sourceId })
        assertEquals("texto c2", sections[1].lines.first().text)
    }

    @Test
    fun statusIsMapped_sentExcludedAndOverLimit() = runTest(dispatcher) {
        repository.lines += listOf(
            line(FreeTextKind.CheckIn, "a", 1),
            line(FreeTextKind.CheckIn, "b", 2, FreeTextStatus.Excluded),
            line(FreeTextKind.CheckIn, "c", 3, FreeTextStatus.OverLimit),
        )
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(FreeTextStatus.Sent, model.statusOf("a"))
        assertEquals(FreeTextStatus.Excluded, model.statusOf("b"))
        assertEquals(FreeTextStatus.OverLimit, model.statusOf("c"))
    }

    @Test
    fun noLines_isEmpty() = runTest(dispatcher) {
        val model = viewModel()

        advanceUntilIdle()

        assertTrue(model.content().isEmpty)
    }

    @Test
    fun toggle_isOptimistic_andPersists() = runTest(dispatcher) {
        repository.lines += line(FreeTextKind.CheckIn, "a", 1)
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(FreeTextLinesEvent.Toggle(FreeTextKind.CheckIn, "a"))
        assertEquals(FreeTextStatus.Excluded, model.statusOf("a"))
        advanceUntilIdle()

        assertEquals(listOf(Triple(FreeTextKind.CheckIn, "a", true)), repository.toggleCalls)
        assertEquals(FreeTextStatus.Excluded, model.statusOf("a"))
    }

    @Test
    fun toggleOn_asksTheServerToInclude_andShowsItsStatus() = runTest(dispatcher) {
        repository.lines += line(FreeTextKind.CheckIn, "a", 1, FreeTextStatus.OverLimit)
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(FreeTextLinesEvent.Toggle(FreeTextKind.CheckIn, "a"))
        advanceUntilIdle()
        model.onEvent(FreeTextLinesEvent.Toggle(FreeTextKind.CheckIn, "a"))
        advanceUntilIdle()

        assertEquals(false, repository.toggleCalls.last().third)
        assertEquals(FreeTextStatus.OverLimit, model.statusOf("a"))
    }

    @Test
    fun toggleFailure_rollsBack_andReportsIt() = runTest(dispatcher) {
        repository.lines += line(FreeTextKind.CheckIn, "a", 1)
        repository.toggleFailure = AppException(AppError.Unknown)
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(FreeTextLinesEvent.Toggle(FreeTextKind.CheckIn, "a"))
            advanceUntilIdle()
            assertEquals(FreeTextLinesEffect.ToggleFailed(offline = false), awaitItem())
        }

        assertEquals(FreeTextStatus.Sent, model.statusOf("a"))
    }

    @Test
    fun toggleFailureOffline_flagsOffline() = runTest(dispatcher) {
        repository.lines += line(FreeTextKind.CheckIn, "a", 1)
        repository.toggleFailure = AppException(AppError.Network)
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(FreeTextLinesEvent.Toggle(FreeTextKind.CheckIn, "a"))
            advanceUntilIdle()
            assertEquals(FreeTextLinesEffect.ToggleFailed(offline = true), awaitItem())
        }
    }

    @Test
    fun reopening_showsThePersistedOffState() = runTest(dispatcher) {
        repository.lines += line(FreeTextKind.Review, "w", 1)
        val first = viewModel()
        advanceUntilIdle()
        first.onEvent(FreeTextLinesEvent.Toggle(FreeTextKind.Review, "w"))
        advanceUntilIdle()

        val reopened = viewModel()
        advanceUntilIdle()

        assertEquals(FreeTextStatus.Excluded, reopened.statusOf("w"))
    }

    @Test
    fun loadFailure_isOfflineOrError_andRetryRecovers() = runTest(dispatcher) {
        repository.linesFailure = AppException(AppError.Network)
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(FreeTextLinesUiState.Offline, model.uiState.value)

        repository.linesFailure = AppException(AppError.Unknown)
        model.onEvent(FreeTextLinesEvent.Retry)
        advanceUntilIdle()
        assertEquals(FreeTextLinesUiState.Error, model.uiState.value)

        repository.linesFailure = null
        model.onEvent(FreeTextLinesEvent.Retry)
        advanceUntilIdle()
        assertIs<FreeTextLinesUiState.Content>(model.uiState.value)
    }
}
