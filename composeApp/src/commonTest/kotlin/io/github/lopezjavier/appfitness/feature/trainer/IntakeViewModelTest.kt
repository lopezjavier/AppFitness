package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.IntakeUpdate
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IntakeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeCoachIntakeRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun IntakeViewModel.content() = assertIs<IntakeUiState.Content>(uiState.value)

    private suspend fun TestScope.loaded(saved: SavedStateHandle = SavedStateHandle()): IntakeViewModel {
        val model = IntakeViewModel(repository, saved)
        advanceUntilIdle()
        return model
    }

    @Test
    fun withoutStoredIntake_theTextIsEmptyAndNotDirty() = runTest(dispatcher) {
        val content = loaded().content()

        assertEquals("", content.text)
        assertFalse(content.dirty)
        assertFalse(content.canSave)
    }

    @Test
    fun withStoredText_theEditorShowsIt() = runTest(dispatcher) {
        repository.stored = CoachIntake(freeText = "Rodilla operada")

        val content = loaded().content()

        assertEquals("Rodilla operada", content.text)
        assertFalse(content.dirty)
    }

    @Test
    fun editing_marksDirty_andCutsAt500Characters() = runTest(dispatcher) {
        val model = loaded()

        model.onEvent(IntakeEvent.EditFreeText("a".repeat(600)))

        assertEquals(500, model.content().text.length)
        assertEquals(0, model.content().remainingChars)
        assertTrue(model.content().dirty)
        assertTrue(model.content().canSave)
    }

    @Test
    fun save_sendsOnlyTheText_andEmitsSaved() = runTest(dispatcher) {
        repository.stored = CoachIntake(trainingYears = 3.0, knownMaintenanceKcal = 2400)
        val model = loaded()
        model.onEvent(IntakeEvent.EditFreeText("Dolor de hombro"))

        model.effects.test {
            model.onEvent(IntakeEvent.Save)
            advanceUntilIdle()
            assertEquals(IntakeEffect.Saved, awaitItem())
        }

        assertEquals(listOf<IntakeUpdate>(IntakeUpdate.FreeText("Dolor de hombro")), repository.saveCalls)
        assertEquals(
            CoachIntake(trainingYears = 3.0, knownMaintenanceKcal = 2400, freeText = "Dolor de hombro"),
            repository.stored,
        )
        assertFalse(model.content().dirty)
    }

    @Test
    fun save_blankText_clearsTheValue() = runTest(dispatcher) {
        repository.stored = CoachIntake(freeText = "hola")
        val model = loaded()
        model.onEvent(IntakeEvent.EditFreeText("   "))

        model.onEvent(IntakeEvent.Save)
        advanceUntilIdle()

        assertEquals(listOf<IntakeUpdate>(IntakeUpdate.FreeText(null)), repository.saveCalls)
    }

    @Test
    fun saveFailure_keepsTheDraft_andShowsTheNotice() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(IntakeEvent.EditFreeText("Mi texto"))
        repository.saveFailure = AppException(AppError.Unknown)

        model.onEvent(IntakeEvent.Save)
        advanceUntilIdle()

        val content = model.content()
        assertEquals("Mi texto", content.text)
        assertEquals(IntakeNotice.SaveFailed, content.notice)
        assertTrue(content.canSave)
    }

    @Test
    fun saveOffline_isTheOfflineNotice() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(IntakeEvent.EditFreeText("Mi texto"))
        repository.saveFailure = AppException(AppError.Network)

        model.onEvent(IntakeEvent.Save)
        advanceUntilIdle()

        assertEquals(IntakeNotice.SaveOffline, model.content().notice)
    }

    @Test
    fun back_withoutChanges_closes() = runTest(dispatcher) {
        val model = loaded()

        model.effects.test {
            model.onEvent(IntakeEvent.Back)
            advanceUntilIdle()
            assertEquals(IntakeEffect.Close, awaitItem())
        }
    }

    @Test
    fun back_withChanges_asksBeforeDiscarding_thenCloses() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(IntakeEvent.EditFreeText("algo"))

        model.effects.test {
            model.onEvent(IntakeEvent.Back)
            advanceUntilIdle()
            expectNoEvents()
            assertTrue(model.content().discardDialog)
            model.onEvent(IntakeEvent.ConfirmDiscard)
            advanceUntilIdle()
            assertEquals(IntakeEffect.Close, awaitItem())
        }
    }

    @Test
    fun dismissingTheDiscardDialog_keepsTheDraft() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(IntakeEvent.EditFreeText("algo"))
        model.onEvent(IntakeEvent.Back)

        model.onEvent(IntakeEvent.DismissDiscard)

        assertFalse(model.content().discardDialog)
        assertEquals("algo", model.content().text)
    }

    @Test
    fun halfWrittenText_survivesRecreationFromTheSameSavedState() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        repository.stored = CoachIntake(freeText = "guardado")
        val first = loaded(saved)
        first.onEvent(IntakeEvent.EditFreeText("a medio escribir"))
        first.onEvent(IntakeEvent.Back)

        val second = loaded(saved)

        assertEquals("a medio escribir", second.content().text)
        assertEquals("guardado", second.content().baseline)
        assertTrue(second.content().dirty)
        assertTrue(second.content().discardDialog)
    }

    @Test
    fun loadFailure_isOfflineOrError_andRetryRecovers() = runTest(dispatcher) {
        repository.getFailure = AppException(AppError.Network)
        val model = loaded()
        assertEquals(IntakeUiState.Offline, model.uiState.value)

        repository.getFailure = null
        model.onEvent(IntakeEvent.Retry)
        advanceUntilIdle()

        assertIs<IntakeUiState.Content>(model.uiState.value)
    }

    @Test
    fun loadFailure_otherThanNetwork_isError() = runTest(dispatcher) {
        repository.getFailure = AppException(AppError.Unknown)

        assertEquals(IntakeUiState.Error, loaded().uiState.value)
    }
}
