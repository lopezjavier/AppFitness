package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeDiscomfortFollowUpRepository
import io.github.lopezjavier.appfitness.domain.model.DiscomfortNoteReason
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T-241 (US-108): the swap offer, its picker and the health note of the Trainer tab. */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One method per behaviour.
class TrainerDiscomfortViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeDiscomfortFollowUpRepository(
        followUps = listOf(followUp(showNote = true, noteReason = DiscomfortNoteReason.Days14)),
        candidates = swapCandidates(),
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) = TrainerViewModel(
        FakeCoachRepository(),
        MutableClock(),
        savedState = saved,
        discomfortFollowUps = repository,
        exerciseNames = discomfortNames(),
    )

    private fun TrainerViewModel.content() = assertIs<TrainerUiState.Content>(uiState.value)

    private fun TrainerViewModel.openPicker() {
        onEvent(TrainerEvent.OpenSwapPicker(FLAGGED_ID))
    }

    @Test
    fun offerAndNote_appearWithTheResolvedExerciseName() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        val item = model.content().discomfortFollowUps.single()
        assertEquals("Press banca", item.exerciseName)
        assertTrue(item.offerSwap)
        assertTrue(item.showNote)
        assertEquals(DiscomfortNoteReason.Days14, item.noteReason)
    }

    @Test
    fun noFollowUps_showNothing() = runTest(dispatcher) {
        repository.followUps = emptyList()
        val model = viewModel()
        advanceUntilIdle()

        assertTrue(model.content().discomfortFollowUps.isEmpty())
    }

    @Test
    fun aFailedRead_hidesTheFollowUps() = runTest(dispatcher) {
        repository.failing = true
        val model = viewModel()
        advanceUntilIdle()

        assertTrue(model.content().discomfortFollowUps.isEmpty())
    }

    @Test
    fun openingThePicker_loadsTheCandidatesInServerOrder() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.openPicker()
        advanceUntilIdle()

        val picker = assertIs<SwapPickerUi>(model.content().swapPicker)
        assertEquals(listOf("Fondos", "Aperturas"), picker.candidates.map { it.name })
        assertTrue(picker.candidates.first().isFavorite)
        assertEquals(false, picker.loading)
    }

    @Test
    fun noCandidates_leaveAnEmptyLoadedPicker() = runTest(dispatcher) {
        repository.candidates = emptyMap()
        val model = viewModel()
        advanceUntilIdle()

        model.openPicker()
        advanceUntilIdle()

        val picker = assertIs<SwapPickerUi>(model.content().swapPicker)
        assertTrue(picker.candidates.isEmpty())
        assertEquals(false, picker.loading)
    }

    @Test
    fun pickingACandidate_swapsOnce_closesThePicker_reloads_andEmitsSwapApplied() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.openPicker()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.PickSwap(CANDIDATE_FAVORITE_ID))
            model.onEvent(TrainerEvent.PickSwap(CANDIDATE_FAVORITE_ID))
            advanceUntilIdle()

            assertEquals(TrainerEffect.SwapApplied, awaitItem())
        }
        assertEquals(listOf(FLAGGED_ID to CANDIDATE_FAVORITE_ID), repository.swaps)
        assertNull(model.content().swapPicker)
        assertEquals(false, model.content().discomfortFollowUps.single().offerSwap)
    }

    @Test
    fun aFailedSwap_keepsThePickerOpen_andEmitsSwapFailed() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.openPicker()
        advanceUntilIdle()
        repository.failing = true

        model.effects.test {
            model.onEvent(TrainerEvent.PickSwap(CANDIDATE_OTHER_ID))
            advanceUntilIdle()

            assertEquals(TrainerEffect.SwapFailed(offline = true), awaitItem())
        }
        assertEquals(FLAGGED_ID, model.content().swapPicker?.exerciseId)
        assertNull(model.content().busyFollowUpExerciseId)
    }

    @Test
    fun offline_thePickerDoesNotOpen_andSwapFailedIsOffline() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        repository.failing = true

        model.effects.test {
            model.openPicker()
            advanceUntilIdle()

            assertEquals(TrainerEffect.SwapFailed(offline = true), awaitItem())
        }
        assertNull(model.content().swapPicker)
    }

    @Test
    fun decline_hidesTheOfferAtOnce_andKeepsTheNote() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.DeclineSwap(FLAGGED_ID))

        val item = model.content().discomfortFollowUps.single()
        assertEquals(false, item.offerSwap)
        assertTrue(item.showNote)
        advanceUntilIdle()
        assertEquals(listOf(FLAGGED_ID), repository.declined)
    }

    @Test
    fun aFailedDecline_bringsTheOfferBack_withAMessage() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        repository.failing = true

        model.effects.test {
            model.onEvent(TrainerEvent.DeclineSwap(FLAGGED_ID))
            advanceUntilIdle()

            assertEquals(TrainerEffect.FollowUpActionFailed(offline = true), awaitItem())
        }
        assertTrue(model.content().discomfortFollowUps.single().offerSwap)
    }

    @Test
    fun dismissingTheNote_hidesItAtOnce_andKeepsTheOffer() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.DismissDiscomfortNote(FLAGGED_ID))

        val item = model.content().discomfortFollowUps.single()
        assertEquals(false, item.showNote)
        assertTrue(item.offerSwap)
        advanceUntilIdle()
        assertEquals(listOf(FLAGGED_ID), repository.dismissed)
    }

    @Test
    fun aFailedDismiss_bringsTheNoteBack() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        repository.failing = true

        model.effects.test {
            model.onEvent(TrainerEvent.DismissDiscomfortNote(FLAGGED_ID))
            advanceUntilIdle()

            assertEquals(TrainerEffect.FollowUpActionFailed(offline = true), awaitItem())
        }
        assertTrue(model.content().discomfortFollowUps.single().showNote)
    }

    @Test
    fun refresh_dropsADiscomfortResolvedElsewhere() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        repository.followUps = emptyList()

        model.onEvent(TrainerEvent.Refresh)
        advanceUntilIdle()

        assertTrue(model.content().discomfortFollowUps.isEmpty())
    }

    @Test
    fun aViewModelRecreatedFromTheSameState_restoresTheOpenPicker() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.openPicker()
        advanceUntilIdle()

        val second = viewModel(saved)
        advanceUntilIdle()

        val picker = assertIs<SwapPickerUi>(second.content().swapPicker)
        assertEquals(FLAGGED_ID, picker.exerciseId)
        assertEquals(2, picker.candidates.size)
    }

    @Test
    fun aSavedPickerWhoseOfferIsGone_isForgotten() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.openPicker()
        advanceUntilIdle()
        repository.followUps = emptyList()

        val second = viewModel(saved)
        advanceUntilIdle()

        assertNull(second.content().swapPicker)
    }
}
