package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BlockEdit
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseAlternative
import io.github.lopezjavier.appfitness.domain.model.MissingField
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
class BlockPreviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val blocks = FakeCoachBlockRepository()
    private val exercises = FakeExerciseRepository(listOf(exerciseRow("ex-1", "bench"), exerciseRow("ex-2", "row")))
    private val library = FakeExerciseLibrary(
        mapOf("bench" to exerciseInfo("bench", "Press banca"), "row" to exerciseInfo("row", "Remo")),
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) =
        BlockPreviewViewModel("plan-1", blocks, exercises, library, MutableClock(), saved)

    private fun BlockPreviewViewModel.preview() = assertIs<BlockPreviewUiState.Preview>(uiState.value)

    private fun BlockPreviewViewModel.failure() = assertIs<BlockPreviewUiState.Failed>(uiState.value).kind

    @Test
    fun withoutAStoredBlock_theScreenStartsIdle_andDoesNotRequest() = runTest(dispatcher) {
        val model = viewModel()
        assertEquals(BlockPreviewUiState.Loading, model.uiState.value)

        advanceUntilIdle()

        assertEquals(BlockPreviewUiState.Idle, model.uiState.value)
        assertTrue(blocks.requests.isEmpty())
    }

    @Test
    fun aStoredBlock_isShownWithoutRequestingAgain() = runTest(dispatcher) {
        blocks.current = Result.success(coachBlock())
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(coachBlock(), model.preview().block)
        assertEquals(1, model.preview().selectedWeek)
        assertTrue(blocks.requests.isEmpty())
    }

    @Test
    fun aRequestFlagWithNothingStored_isInterrupted_andNeverRequestsByItself() = runTest(dispatcher) {
        val saved = SavedStateHandle(mapOf("block_requesting" to true))
        val model = viewModel(saved)

        advanceUntilIdle()

        assertEquals(PlanFailureKind.Interrupted, model.failure())
        assertTrue(blocks.requests.isEmpty())
        assertNull(saved.get<Boolean>("block_requesting"))
    }

    @Test
    fun aRequestFlagWithAStoredBlock_showsTheBlock() = runTest(dispatcher) {
        blocks.current = Result.success(coachBlock())
        val model = viewModel(SavedStateHandle(mapOf("block_requesting" to true)))

        advanceUntilIdle()

        assertEquals(coachBlock(), model.preview().block)
    }

    @Test
    fun request_showsProgressThenThePreview_forTheOpenPlanAndUtcToday() = runTest(dispatcher) {
        blocks.requestGate = CompletableDeferred()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(BlockPreviewEvent.Request)
        advanceUntilIdle()
        assertEquals(BlockPreviewUiState.Requesting, model.uiState.value)

        blocks.requestGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(coachBlock(), model.preview().block)
        assertEquals(listOf("plan-1" to LocalDate(2026, 10, 19)), blocks.requests)
    }

    @Test
    fun requestWhileRequesting_isIgnored() = runTest(dispatcher) {
        blocks.requestGate = CompletableDeferred()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(BlockPreviewEvent.Request)
        advanceUntilIdle()
        model.onEvent(BlockPreviewEvent.Request)
        blocks.requestGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, blocks.requests.size)
    }

    @Test
    fun requestFlag_isSetWhileInFlight_andClearedAfterwards() = runTest(dispatcher) {
        blocks.requestGate = CompletableDeferred()
        val saved = SavedStateHandle()
        val model = viewModel(saved)
        advanceUntilIdle()

        model.onEvent(BlockPreviewEvent.Request)
        advanceUntilIdle()
        assertEquals(true, saved.get<Boolean>("block_requesting"))

        blocks.requestGate!!.complete(Unit)
        advanceUntilIdle()
        assertNull(saved.get<Boolean>("block_requesting"))
    }

    private fun failingRequest(failure: Throwable): BlockPreviewViewModel {
        blocks.request = Result.failure(failure)
        return viewModel()
    }

    @Test
    fun failures_mapToTheirKinds() = runTest(dispatcher) {
        val cases = listOf(
            CoachException(CoachFailure.LimitReached(CoachFailure.Scope.User)) to
                PlanFailureKind.Limit(CoachFailure.Scope.User),
            CoachException(CoachFailure.LimitReached(CoachFailure.Scope.Global)) to
                PlanFailureKind.Limit(CoachFailure.Scope.Global),
            CoachException(CoachFailure.NoCredit) to PlanFailureKind.NoCredit,
            CoachException(CoachFailure.Timeout) to PlanFailureKind.Timeout,
            CoachException(CoachFailure.Invalid) to PlanFailureKind.Invalid,
            CoachException(CoachFailure.Missing(setOf(MissingField.Intention, MissingField.Place))) to
                PlanFailureKind.Missing(setOf(MissingField.Intention, MissingField.Place)),
            CoachException(CoachFailure.Infeasible) to PlanFailureKind.Infeasible,
            CoachException(CoachFailure.Other) to PlanFailureKind.Other,
            AppException(AppError.Network) to PlanFailureKind.Offline,
        )
        for ((failure, kind) in cases) {
            val model = failingRequest(failure)
            advanceUntilIdle()
            model.onEvent(BlockPreviewEvent.Request)
            advanceUntilIdle()

            assertEquals(kind, model.failure())
        }
    }

    @Test
    fun readingTheStoredBlockFails_showsTheFailureWithoutRequesting() = runTest(dispatcher) {
        blocks.current = Result.failure(AppException(AppError.Network))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(PlanFailureKind.Offline, model.failure())
        assertTrue(blocks.requests.isEmpty())
    }

    @Test
    fun retry_showsABlockStoredMeanwhile_withoutAskingAgain() = runTest(dispatcher) {
        blocks.request = Result.failure(CoachException(CoachFailure.Timeout))
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(BlockPreviewEvent.Request)
        advanceUntilIdle()
        blocks.current = Result.success(coachBlock())

        model.onEvent(BlockPreviewEvent.Retry)
        advanceUntilIdle()

        assertEquals(coachBlock(), model.preview().block)
        assertEquals(1, blocks.requests.size)
    }

    @Test
    fun retry_withNothingStored_asksAgain() = runTest(dispatcher) {
        blocks.request = Result.failure(CoachException(CoachFailure.Timeout))
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(BlockPreviewEvent.Request)
        advanceUntilIdle()
        blocks.request = Result.success(coachBlock())

        model.onEvent(BlockPreviewEvent.Retry)
        advanceUntilIdle()

        assertEquals(coachBlock(), model.preview().block)
        assertEquals(2, blocks.requests.size)
    }

    @Test
    fun exerciseNames_resolveFromTheCatalog_andAMissingIdIsAbsent() = runTest(dispatcher) {
        blocks.current = Result.success(
            coachBlock(sessionsPerWeek = 1).let { block ->
                block.copy(
                    sessions = block.sessions.map {
                        it.copy(exercises = listOf(blockExercise("ex-1"), blockExercise("gone")))
                    },
                )
            },
        )
        val model = viewModel()

        advanceUntilIdle()

        val names = model.preview().exerciseNames
        assertEquals("Press banca", names["ex-1"])
        assertNull(names["gone"])
    }

    @Test
    fun anUnreadableCatalog_stillShowsTheBlock() = runTest(dispatcher) {
        exercises.failure = AppError.Network
        blocks.current = Result.success(coachBlock())
        val model = viewModel()

        advanceUntilIdle()

        assertTrue(model.preview().exerciseNames.isEmpty())
    }

    @Test
    fun selectWeek_changesTheTab_andIsRemembered() = runTest(dispatcher) {
        blocks.current = Result.success(coachBlock())
        val saved = SavedStateHandle()
        val model = viewModel(saved)
        advanceUntilIdle()

        model.onEvent(BlockPreviewEvent.SelectWeek(3))

        assertEquals(3, model.preview().selectedWeek)
        assertEquals(3, saved.get<Int>("block_week"))
        assertTrue(model.preview().sessions.all { it.week == 3 })
    }

    @Test
    fun selectWeek_outsideTheBlock_isIgnored() = runTest(dispatcher) {
        blocks.current = Result.success(coachBlock())
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(BlockPreviewEvent.SelectWeek(9))

        assertEquals(1, model.preview().selectedWeek)
    }

    @Test
    fun aRecreatedModel_restoresTheWeekAndTheOpenReasonSheet() = runTest(dispatcher) {
        blocks.current = Result.success(coachBlock())
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.onEvent(BlockPreviewEvent.SelectWeek(2))
        first.onEvent(BlockPreviewEvent.OpenReason(blockExerciseKey(2, 1, 0)))

        val second = viewModel(saved)
        advanceUntilIdle()

        assertEquals(2, second.preview().selectedWeek)
        assertEquals(blockExerciseKey(2, 1, 0), second.preview().reasonKey)
        assertEquals("ex-1", second.preview().reasonExercise?.exerciseId)
    }

    @Test
    fun aRestoredReasonKeyOutsideTheBlock_isDropped() = runTest(dispatcher) {
        blocks.current = Result.success(coachBlock())
        val saved = SavedStateHandle(mapOf("block_reason_exercise" to "9/9/9"))
        val model = viewModel(saved)

        advanceUntilIdle()

        assertNull(model.preview().reasonKey)
        assertNull(saved.get<String>("block_reason_exercise"))
    }

    @Test
    fun openAndCloseReason_updateTheStateAndTheSavedHandle() = runTest(dispatcher) {
        blocks.current = Result.success(coachBlock())
        val saved = SavedStateHandle()
        val model = viewModel(saved)
        advanceUntilIdle()

        model.onEvent(BlockPreviewEvent.OpenReason(blockExerciseKey(1, 0, 1)))
        assertEquals("ex-2", model.preview().reasonExercise?.exerciseId)
        assertEquals("1/0/1", saved.get<String>("block_reason_exercise"))

        model.onEvent(BlockPreviewEvent.CloseReason)
        assertNull(model.preview().reasonExercise)
        assertNull(saved.get<String>("block_reason_exercise"))
    }

    @Test
    fun save_closesTheScreenAndEmitsSaved() = runTest(dispatcher) {
        blocks.current = Result.success(coachBlock())
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(BlockPreviewEvent.Save)
            advanceUntilIdle()

            assertEquals(BlockPreviewEffect.Saved, awaitItem())
        }
        assertEquals(BlockPreviewUiState.Closed, model.uiState.value)
        assertEquals(listOf("plan-1"), blocks.saves)
    }

    @Test
    fun save_failure_keepsThePreview_andEmitsActionFailed() = runTest(dispatcher) {
        blocks.current = Result.success(coachBlock())
        blocks.saveResult = Result.failure(CoachException(CoachFailure.Conflict))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(BlockPreviewEvent.Save)
            advanceUntilIdle()

            assertEquals(BlockPreviewEffect.ActionFailed(offline = false), awaitItem())
        }
        assertFalse(model.preview().saving)
    }

    @Test
    fun save_offline_emitsTheOfflineFailure() = runTest(dispatcher) {
        blocks.current = Result.success(coachBlock())
        blocks.saveResult = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(BlockPreviewEvent.Save)
            advanceUntilIdle()

            assertEquals(BlockPreviewEffect.ActionFailed(offline = true), awaitItem())
        }
    }

    @Test
    fun doubleSave_whileSaving_isIgnored() = runTest(dispatcher) {
        blocks.current = Result.success(coachBlock())
        blocks.saveGate = CompletableDeferred()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(BlockPreviewEvent.Save)
        advanceUntilIdle()
        assertTrue(model.preview().saving)
        model.onEvent(BlockPreviewEvent.Save)
        blocks.saveGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, blocks.saves.size)
    }

    // Replacements (T-179)

    private fun withAlternatives() {
        exercises.rows = listOf(
            exerciseRow("ex-1", "bench"),
            exerciseRow("ex-2", "row"),
            exerciseRow("ex-3", "fly", equipment = Equipment.Cable),
            exerciseRow("ex-4", "band-long-bench", equipment = Equipment.BandLong),
        )
        library.entries = mapOf(
            "bench" to exerciseInfo("bench", "Press banca"),
            "row" to exerciseInfo("row", "Remo"),
            "fly" to exerciseInfo("fly", "Aperturas"),
            "band-long-bench" to exerciseInfo("band-long-bench", "Press con banda larga"),
        )
        exercises.alternativesById = mapOf(
            "ex-1" to listOf(ExerciseAlternative(exercises.rows[3], 1.0), ExerciseAlternative(exercises.rows[2], 0.8)),
        )
        blocks.current = Result.success(coachBlock())
    }

    private fun TestScope.openedPicker(model: BlockPreviewViewModel, session: Int = 0, position: Int = 0) {
        model.onEvent(BlockPreviewEvent.OpenReplace(session, position))
        advanceUntilIdle()
    }

    private fun BlockPreviewViewModel.alternatives() =
        assertIs<AlternativesState.Content>(preview().alternatives).rows.map { it.exercise.exercise.id }

    @Test
    fun openReplace_loadsOnlyThatExercisesAlternatives_mostSimilarFirst() = runTest(dispatcher) {
        withAlternatives()
        val model = viewModel()
        advanceUntilIdle()

        openedPicker(model, session = 1, position = 0)

        assertEquals(listOf("ex-4", "ex-3"), model.alternatives())
        assertEquals(listOf("ex-1"), exercises.alternativesCalls)
        assertEquals(replaceSlotKey(1, 0), model.preview().replaceSlot)
    }

    @Test
    fun openReplace_excludesExercisesAlreadyInTheSession() = runTest(dispatcher) {
        withAlternatives()
        exercises.alternativesById = mapOf(
            "ex-1" to listOf(ExerciseAlternative(exercises.rows[1], 0.9), ExerciseAlternative(exercises.rows[2], 0.8)),
        )
        val model = viewModel()
        advanceUntilIdle()

        openedPicker(model)

        assertEquals(listOf("ex-3"), model.alternatives())
    }

    @Test
    fun openReplace_stateFollowsTheRead_emptyOfflineAndError() = runTest(dispatcher) {
        withAlternatives()
        val model = viewModel()
        advanceUntilIdle()

        openedPicker(model, position = 1)
        assertEquals(listOf("ex-2"), exercises.alternativesCalls)
        assertEquals(AlternativesState.Empty, model.preview().alternatives)

        exercises.alternativesFailure = AppError.Network
        model.onEvent(BlockPreviewEvent.LoadAlternatives)
        advanceUntilIdle()
        assertEquals(AlternativesState.Offline, model.preview().alternatives)

        exercises.alternativesFailure = AppError.Unknown
        model.onEvent(BlockPreviewEvent.LoadAlternatives)
        advanceUntilIdle()
        assertEquals(AlternativesState.Error, model.preview().alternatives)

        exercises.alternativesFailure = null
        exercises.alternativesById = exercises.alternativesById + (
            "ex-2" to listOf(
                ExerciseAlternative(exercises.rows[2], 0.7),
            )
            )
        model.onEvent(BlockPreviewEvent.LoadAlternatives)
        advanceUntilIdle()
        assertEquals(listOf("ex-3"), model.alternatives())
    }

    @Test
    fun pickReplacement_overlaysEveryWeekOfThatSession_keepingSetsRepsAndRest() = runTest(dispatcher) {
        withAlternatives()
        val model = viewModel()
        advanceUntilIdle()
        openedPicker(model, session = 1, position = 0)

        model.onEvent(BlockPreviewEvent.PickReplacement("ex-4"))

        val preview = model.preview()
        assertNull(preview.replaceSlot)
        preview.weeks.forEach { week ->
            val replaced = week.sessions.first { it.sessionIndex == 1 }.exercises
            assertEquals("ex-4", replaced[0].exerciseId)
            assertEquals("band-long-bench", replaced[0].exerciseKey)
            assertEquals(
                coachBlock().sessions.first().exercises[0].copy(exerciseId = "ex-4", exerciseKey = "band-long-bench"),
                replaced[0],
            )
            assertEquals("ex-2", replaced[1].exerciseId)
            assertEquals("ex-1", week.sessions.first { it.sessionIndex == 0 }.exercises[0].exerciseId)
        }
        assertTrue(preview.isChanged(1, 0))
        assertFalse(preview.isChanged(0, 0))
        assertEquals(Equipment.BandLong, preview.replacementAt(1, 0)?.equipment)
    }

    @Test
    fun pickReplacement_afterSelectingAnotherWeek_keepsTheOverlay() = runTest(dispatcher) {
        withAlternatives()
        val model = viewModel()
        advanceUntilIdle()
        openedPicker(model)
        model.onEvent(BlockPreviewEvent.PickReplacement("ex-3"))

        model.onEvent(BlockPreviewEvent.SelectWeek(3))

        assertEquals("ex-3", model.preview().sessions.first { it.sessionIndex == 0 }.exercises[0].exerciseId)
    }

    @Test
    fun pickReplacement_writesNothingToTheRepository() = runTest(dispatcher) {
        withAlternatives()
        val model = viewModel()
        advanceUntilIdle()
        openedPicker(model)

        model.onEvent(BlockPreviewEvent.PickReplacement("ex-3"))
        advanceUntilIdle()

        assertTrue(blocks.saves.isEmpty())
        assertTrue(blocks.requests.isEmpty())
    }

    @Test
    fun pickReplacement_notAmongTheLoadedAlternatives_isIgnored() = runTest(dispatcher) {
        withAlternatives()
        val model = viewModel()
        advanceUntilIdle()
        openedPicker(model)

        model.onEvent(BlockPreviewEvent.PickReplacement("ex-2"))

        assertTrue(model.preview().replacements.isEmpty())
    }

    @Test
    fun clearReplacement_restoresTheOriginalAndClosesThePicker() = runTest(dispatcher) {
        withAlternatives()
        val model = viewModel()
        advanceUntilIdle()
        openedPicker(model)
        model.onEvent(BlockPreviewEvent.PickReplacement("ex-3"))
        openedPicker(model)

        model.onEvent(BlockPreviewEvent.ClearReplacement)

        assertTrue(model.preview().replacements.isEmpty())
        assertNull(model.preview().replaceSlot)
        assertEquals("ex-1", model.preview().sessions.first().exercises[0].exerciseId)
    }

    @Test
    fun closeReplace_dismissesThePickerWithoutChanges() = runTest(dispatcher) {
        withAlternatives()
        val model = viewModel()
        advanceUntilIdle()
        openedPicker(model)

        model.onEvent(BlockPreviewEvent.CloseReplace)

        assertNull(model.preview().replaceSlot)
        assertTrue(model.preview().replacements.isEmpty())
    }

    @Test
    fun save_sendsTheReplacementsAsOneBasedEdits() = runTest(dispatcher) {
        withAlternatives()
        val model = viewModel()
        advanceUntilIdle()
        openedPicker(model, session = 2, position = 0)
        model.onEvent(BlockPreviewEvent.PickReplacement("ex-4"))

        model.onEvent(BlockPreviewEvent.Save)
        advanceUntilIdle()

        assertEquals(listOf(listOf(BlockEdit(sessionIndex = 2, position = 1, exerciseId = "ex-4"))), blocks.saveEdits)
    }

    @Test
    fun save_withoutReplacements_sendsNoEdits() = runTest(dispatcher) {
        withAlternatives()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(BlockPreviewEvent.Save)
        advanceUntilIdle()

        assertEquals(listOf(emptyList()), blocks.saveEdits)
    }

    @Test
    fun save_invalidReplacement_staysOnScreen_dropsTheEditAndTellsTheUser() = runTest(dispatcher) {
        withAlternatives()
        blocks.saveResult = Result.failure(CoachException(CoachFailure.InvalidReplacement))
        val saved = SavedStateHandle()
        val model = viewModel(saved)
        advanceUntilIdle()
        openedPicker(model)
        model.onEvent(BlockPreviewEvent.PickReplacement("ex-3"))

        model.effects.test {
            model.onEvent(BlockPreviewEvent.Save)
            advanceUntilIdle()

            assertEquals(BlockPreviewEffect.ReplacementRejected, awaitItem())
        }
        val preview = model.preview()
        assertFalse(preview.saving)
        assertTrue(preview.replacements.isEmpty())
        assertEquals("ex-1", preview.sessions.first().exercises[0].exerciseId)
        assertNull(saved.get<String>("block_replacements"))
    }

    @Test
    fun aRecreatedModel_restoresTheReplacementsAndTheOpenPicker_withoutWriting() = runTest(dispatcher) {
        withAlternatives()
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        advanceUntilIdle()
        openedPicker(first, session = 1, position = 0)
        first.onEvent(BlockPreviewEvent.PickReplacement("ex-3"))
        openedPicker(first, session = 2, position = 0)

        val second = viewModel(saved)
        advanceUntilIdle()

        val preview = second.preview()
        assertEquals("ex-3", preview.sessions.first { it.sessionIndex == 1 }.exercises[0].exerciseId)
        assertTrue(preview.isChanged(1, 0))
        assertEquals(replaceSlotKey(2, 0), preview.replaceSlot)
        assertEquals(listOf("ex-4", "ex-3"), second.alternatives())
        assertTrue(blocks.saves.isEmpty())
        assertTrue(blocks.requests.isEmpty())
    }

    @Test
    fun aRestoredReplacementThatNoLongerFits_isDropped() = runTest(dispatcher) {
        withAlternatives()
        val saved = SavedStateHandle(mapOf("block_replacements" to "9|0|ex-3,0|7|ex-3,0|0|gone"))
        val model = viewModel(saved)

        advanceUntilIdle()

        assertTrue(model.preview().replacements.isEmpty())
    }

    @Test
    fun retry_discardsThePendingReplacements() = runTest(dispatcher) {
        withAlternatives()
        val saved = SavedStateHandle()
        val model = viewModel(saved)
        advanceUntilIdle()
        openedPicker(model)
        model.onEvent(BlockPreviewEvent.PickReplacement("ex-3"))
        blocks.current = Result.failure(AppException(AppError.Network))
        val reloaded = viewModel(saved)
        advanceUntilIdle()
        blocks.current = Result.success(coachBlock())

        reloaded.onEvent(BlockPreviewEvent.Retry)
        advanceUntilIdle()

        assertTrue(reloaded.preview().replacements.isEmpty())
        assertNull(saved.get<String>("block_replacements"))
    }

    @Test
    fun aNewRequest_clearsThePendingReplacements() = runTest(dispatcher) {
        withAlternatives()
        val saved = SavedStateHandle(mapOf("block_replacements" to "0|0|ex-3"))
        blocks.current = Result.success(null)
        val model = viewModel(saved)
        advanceUntilIdle()

        model.onEvent(BlockPreviewEvent.Request)
        advanceUntilIdle()

        assertTrue(model.preview().replacements.isEmpty())
        assertNull(saved.get<String>("block_replacements"))
    }

    @Test
    fun leavingTheScreen_neverSaves() = runTest(dispatcher) {
        blocks.current = Result.success(coachBlock())
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(BlockPreviewEvent.SelectWeek(2))
        model.onEvent(BlockPreviewEvent.OpenReason(blockExerciseKey(2, 0, 0)))
        model.onEvent(BlockPreviewEvent.CloseReason)

        assertTrue(blocks.saves.isEmpty())
    }
}
