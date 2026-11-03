package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BlockEdit
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseAlternative
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.TrainingPlace
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.FakeFavoriteExercisesRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("LargeClass") // One class per ViewModel.
@OptIn(ExperimentalCoroutinesApi::class)
class BlockPreviewViewModelTest {
    private val favorites = FakeFavoriteExercisesRepository()
    private val dispatcher = StandardTestDispatcher()
    private val blocks = FakeCoachBlockRepository()
    private val prefs = FakeTrainingPreferencesRepository()
    private val exercises = FakeExerciseRepository(listOf(exerciseRow("ex-1", "bench"), exerciseRow("ex-2", "row")))
    private val library = FakeExerciseLibrary(
        mapOf("bench" to exerciseInfo("bench", "Press banca"), "row" to exerciseInfo("row", "Remo")),
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) =
        BlockPreviewViewModel("plan-1", blocks, exercises, library, prefs, favorites, saved)

    private fun BlockPreviewViewModel.preview() = assertIs<BlockPreviewUiState.Preview>(uiState.value)

    private fun BlockPreviewViewModel.failure() = assertIs<BlockPreviewUiState.Failed>(uiState.value).kind

    @Test
    fun withoutAStoredBlock_theScreenFailsAndNeverCallsTheCoach() = runTest(dispatcher) {
        val model = viewModel()
        assertEquals(BlockPreviewUiState.Loading, model.uiState.value)

        advanceUntilIdle()

        assertEquals(PlanFailureKind.Interrupted, model.failure())
        assertTrue(blocks.requests.isEmpty())
        assertEquals(0, blocks.splitRequests)
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
    fun theNotesOfTheStoredBlock_areExposed() = runTest(dispatcher) {
        val training = blockTraining().copy(
            splitReason = "Encaja con tus dias.",
            changesVsLastBlock = "Mas espalda.",
            notes = listOf("below_recommended"),
        )
        blocks.current = Result.success(coachBlock(training = training))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals("Encaja con tus dias.", model.preview().splitReason)
        assertEquals("Mas espalda.", model.preview().changesVsLastBlock)
        assertTrue(model.preview().belowRecommended)
    }

    @Test
    fun anOlderBlockWithoutNotes_exposesNone() = runTest(dispatcher) {
        blocks.current = Result.success(coachBlock())
        val model = viewModel()

        advanceUntilIdle()

        assertNull(model.preview().splitReason)
        assertNull(model.preview().changesVsLastBlock)
        assertFalse(model.preview().belowRecommended)
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
    fun retry_showsABlockStoredMeanwhile_withoutAskingTheCoach() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        blocks.current = Result.success(coachBlock())

        model.onEvent(BlockPreviewEvent.Retry)
        advanceUntilIdle()

        assertEquals(coachBlock(), model.preview().block)
        assertTrue(blocks.requests.isEmpty())
    }

    @Test
    fun retry_withNothingStored_failsAgainWithoutAskingTheCoach() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(BlockPreviewEvent.Retry)
        advanceUntilIdle()

        assertEquals(PlanFailureKind.Interrupted, model.failure())
        assertTrue(blocks.requests.isEmpty())
        assertEquals(0, blocks.splitRequests)
    }

    @Test
    fun exerciseNames_resolveFromTheCatalog_andCustomOnesFromTheirRow() = runTest(dispatcher) {
        exercises.rows = exercises.rows + exerciseRow("c-1", null, name = "Mi press", custom = true)
        blocks.current = Result.success(blockOf("ex-1", "c-1"))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(
            mapOf("ex-1" to "Press banca", "c-1" to "Mi press"),
            model.preview().exerciseNames.filterKeys {
                it !=
                    "ex-2"
            },
        )
    }

    @Test
    fun anExerciseWithoutAName_failsTheBlockInsteadOfShowingAGenericRow() = runTest(dispatcher) {
        blocks.current = Result.success(blockOf("ex-1", "gone"))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(PlanFailureKind.Other, model.failure())
    }

    @Test
    fun anUnreadableCatalog_failsOffline_andRetryShowsTheBlockWithoutAskingTheCoachAgain() = runTest(dispatcher) {
        exercises.failure = AppError.Network
        blocks.current = Result.success(coachBlock())
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(PlanFailureKind.Offline, model.failure())

        exercises.failure = null
        model.onEvent(BlockPreviewEvent.Retry)
        advanceUntilIdle()

        assertEquals("Press banca", model.preview().exerciseNames["ex-1"])
        assertTrue(blocks.requests.isEmpty())
    }

    @Test
    fun aRenamedCustomExercise_updatesTheShownName() = runTest(dispatcher) {
        exercises.rows = exercises.rows + exerciseRow("c-1", null, name = "Mi press", custom = true)
        blocks.current = Result.success(blockOf("ex-1", "c-1"))
        val model = viewModel()
        advanceUntilIdle()

        exercises.updateCustom("c-1", "Press nuevo", MuscleGroup.Chest, Equipment.Barbell, false, null)
        advanceUntilIdle()

        assertEquals("Press nuevo", model.preview().exerciseNames["c-1"])
    }

    @Test
    fun aRecreatedViewModel_resolvesTheNamesAgain() = runTest(dispatcher) {
        blocks.current = Result.success(coachBlock())
        val saved = SavedStateHandle()
        viewModel(saved)
        advanceUntilIdle()

        val recreated = viewModel(saved)
        advanceUntilIdle()

        assertEquals("Remo", recreated.preview().exerciseNames["ex-2"])
    }

    private fun blockOf(vararg ids: String) = coachBlock(sessionsPerWeek = 1).let { block ->
        block.copy(sessions = block.sessions.map { it.copy(exercises = ids.map(::blockExercise)) })
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

        exercises.alternativesCalls.clear()
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

        exercises.alternativesCalls.clear()
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

    // Equipment filter (T-209)

    private fun withMixedEquipmentAlternatives() {
        exercises.rows = listOf(
            exerciseRow("ex-1", "bench"),
            exerciseRow("ex-2", "row"),
            exerciseRow("ex-3", "fly", equipment = Equipment.Cable),
            exerciseRow("ex-4", "ez-curl", equipment = Equipment.EzBar),
            exerciseRow("ex-5", "chest-press", equipment = Equipment.Machine),
            exerciseRow("ex-6", "db-press", equipment = Equipment.Dumbbell),
            exerciseRow("ex-7", "push-up", equipment = Equipment.Bodyweight),
            exerciseRow("ex-8", "walking", equipment = Equipment.CardioMachine),
            exerciseRow("ex-9", "running", equipment = Equipment.CardioMachine),
            exerciseRow("ex-10", "stationary-bike", equipment = Equipment.CardioMachine),
        )
        library.entries = exercises.rows.associate { it.key!! to exerciseInfo(it.key!!, "Name ${it.key}") }
        exercises.alternativesById = mapOf("ex-1" to exercises.rows.drop(2).map { ExerciseAlternative(it, 0.9) })
        blocks.current = Result.success(coachBlock())
    }

    private fun setEquipment(vararg equipment: Equipment, place: TrainingPlace? = null) {
        prefs.result = Result.success(TrainingPreferences(place = place, equipment = equipment.toSet()))
    }

    private fun BlockPreviewViewModel.offeredIds() = alternatives().toSet()

    @Test
    fun openReplace_withDumbbellOnly_hidesCableEzBarMachineAndOtherCardio() = runTest(dispatcher) {
        withMixedEquipmentAlternatives()
        setEquipment(Equipment.Dumbbell)
        val model = viewModel()
        advanceUntilIdle()

        openedPicker(model)

        assertEquals(setOf("ex-6", "ex-7", "ex-8", "ex-9"), model.offeredIds())
    }

    @Test
    fun openReplace_withNoEquipmentAtTheGym_offersEverything() = runTest(dispatcher) {
        withMixedEquipmentAlternatives()
        setEquipment(place = TrainingPlace.Gym)
        val model = viewModel()
        advanceUntilIdle()

        openedPicker(model)

        assertEquals(exercises.rows.drop(2).map { it.id }.toSet(), model.offeredIds())
    }

    @Test
    fun openReplace_withNoEquipmentAtHome_offersBodyweightAndWalkingRunningOnly() = runTest(dispatcher) {
        withMixedEquipmentAlternatives()
        setEquipment(place = TrainingPlace.Home)
        val model = viewModel()
        advanceUntilIdle()

        openedPicker(model)

        assertEquals(setOf("ex-7", "ex-8", "ex-9"), model.offeredIds())
    }

    @Test
    fun openReplace_withTheCardioMachineSelected_offersOtherCardioMachines() = runTest(dispatcher) {
        withMixedEquipmentAlternatives()
        setEquipment(Equipment.CardioMachine)
        val model = viewModel()
        advanceUntilIdle()

        openedPicker(model)

        assertEquals(setOf("ex-7", "ex-8", "ex-9", "ex-10"), model.offeredIds())
    }

    @Test
    fun openReplace_readsThePreferencesAgainOnEveryOpen() = runTest(dispatcher) {
        withMixedEquipmentAlternatives()
        setEquipment(Equipment.Dumbbell)
        val model = viewModel()
        advanceUntilIdle()
        openedPicker(model)
        assertFalse("ex-3" in model.offeredIds())
        model.onEvent(BlockPreviewEvent.CloseReplace)

        setEquipment(Equipment.Dumbbell, Equipment.Cable)
        openedPicker(model)

        assertTrue("ex-3" in model.offeredIds())
    }

    @Test
    fun openReplace_whenEverythingIsFilteredOut_isEmptyAndTheSlotCannotBeSwapped() = runTest(dispatcher) {
        withMixedEquipmentAlternatives()
        exercises.alternativesById = mapOf("ex-1" to listOf(ExerciseAlternative(exercises.rows[2], 0.9)))
        setEquipment(Equipment.Dumbbell)
        val model = viewModel()
        advanceUntilIdle()

        openedPicker(model)

        assertEquals(AlternativesState.Empty, model.preview().alternatives)
        assertFalse(model.preview().canSwap(0, 0))
    }

    @Test
    fun openReplace_whenThePreferencesCannotBeRead_isAnErrorAndRetryWorks() = runTest(dispatcher) {
        withMixedEquipmentAlternatives()
        val model = viewModel()
        advanceUntilIdle()
        prefs.result = Result.failure(AppException(AppError.Unknown))

        openedPicker(model)
        assertEquals(AlternativesState.Error, model.preview().alternatives)

        setEquipment(Equipment.Cable)
        model.onEvent(BlockPreviewEvent.LoadAlternatives)
        advanceUntilIdle()

        assertTrue("ex-3" in model.offeredIds())
        assertFalse("ex-4" in model.offeredIds())
    }

    @Test
    fun aPendingSwapOutsideTheCurrentEquipment_isDroppedWhenRestored() = runTest(dispatcher) {
        withMixedEquipmentAlternatives()
        val saved = SavedStateHandle(mapOf("block_replacements" to "0|0|ex-3,1|0|ex-6"))
        setEquipment(Equipment.Dumbbell)
        val model = viewModel(saved)
        advanceUntilIdle()

        assertEquals(setOf(replaceSlotKey(1, 0)), model.preview().replacements.keys)
        assertEquals("1|0|ex-6", saved.get<String>("block_replacements"))
    }

    @Test
    fun anOpenPickerRestoredWithNarrowedEquipment_reloadsFiltered() = runTest(dispatcher) {
        withMixedEquipmentAlternatives()
        val saved = SavedStateHandle(mapOf("block_replace_slot" to replaceSlotKey(0, 0)))
        setEquipment(Equipment.Dumbbell)
        val model = viewModel(saved)
        advanceUntilIdle()

        assertEquals(setOf("ex-6", "ex-7", "ex-8", "ex-9"), model.offeredIds())
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
