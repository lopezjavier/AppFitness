package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.coach.builder.BuilderInputs
import io.github.lopezjavier.appfitness.domain.coach.builder.DisabledReason
import io.github.lopezjavier.appfitness.domain.coach.builder.PickerDisabledReason
import io.github.lopezjavier.appfitness.domain.coach.builder.RoutineType
import io.github.lopezjavier.appfitness.domain.coach.builder.SlotKind
import io.github.lopezjavier.appfitness.domain.coach.builder.SplitCatalog
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import io.github.lopezjavier.appfitness.domain.usecase.BuildBlockUseCase
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.FakeFavoriteExercisesRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BlockBuilderViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val blocks = FakeCoachBlockRepository()
    private val plans = FakeCoachPlanRepository()
    private val prefs = FakeTrainingPreferencesRepository()
    private val favourites = FakeFavoriteExercisesRepository()
    private val groups = SplitCatalog.ALL_GROUPS

    private val mainPattern = mapOf(
        MuscleGroup.Chest to MovementPattern.HorizontalPush,
        MuscleGroup.Back to MovementPattern.HorizontalPull,
        MuscleGroup.Shoulders to MovementPattern.VerticalPush,
        MuscleGroup.Quads to MovementPattern.Squat,
        MuscleGroup.Hamstrings to MovementPattern.Hinge,
        MuscleGroup.Glutes to MovementPattern.Hinge,
    )

    private fun rows(): List<Exercise> = groups.flatMap { g ->
        val key = g.name.lowercase()
        listOf(exerciseRow("$key-main", "$key-main", g, Equipment.Barbell, pattern = mainPattern.getValue(g))) +
            (1..4).map {
                exerciseRow("$key-iso$it", "$key-iso$it", g, Equipment.Machine, pattern = MovementPattern.Isolation)
            }
    } + listOf(
        exerciseRow(
            "bike",
            "bike",
            MuscleGroup.FullBody,
            metric = ExerciseMetricType.Duration,
            pattern = MovementPattern.Cardio,
        ),
        exerciseRow(
            "rower",
            "rower",
            MuscleGroup.FullBody,
            metric = ExerciseMetricType.Duration,
            pattern = MovementPattern.Cardio,
        ),
        exerciseRow("curl", "curl", MuscleGroup.Biceps, Equipment.Dumbbell, pattern = MovementPattern.Isolation),
        exerciseRow(
            "mine",
            null,
            MuscleGroup.Chest,
            Equipment.Machine,
            name = "Mi press",
            custom = true,
            pattern = null,
        ),
    )

    private val exerciseRepository = FakeExerciseRepository(rows())
    private val library = FakeExerciseLibrary(
        rows().filter { it.key != null }.associate { it.key!! to exerciseInfo(it.key!!, "Nombre ${it.key}") },
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        prefs.result =
            Result.success(
                TrainingPreferences(intention = TrainingIntention.Muscle, daysPerWeek = 4, sessionMinutes = 180),
            )
        blocks.builderInputs = Result.success(BuilderInputs(emptyMap(), listOf("bike", "rower"), "bike"))
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(saved: SavedStateHandle = SavedStateHandle()): BlockBuilderViewModel {
        val model = BlockBuilderViewModel(
            planId = "plan-1",
            builder = BuildBlockUseCase(blocks),
            catalog = LoadExerciseCatalog(exerciseRepository, library),
            favourites = favourites,
            exercises = exerciseRepository,
            preferences = prefs,
            plans = plans,
            clock = MutableClock(),
            savedState = saved,
        )
        advanceUntilIdle()
        return model
    }

    private fun TestScope.send(model: BlockBuilderViewModel, vararg events: BlockBuilderEvent) {
        events.forEach { model.onEvent(it) }
        advanceUntilIdle()
    }

    private fun BlockBuilderViewModel.splits() = assertIs<BlockBuilderUiState.Splits>(uiState.value)
    private fun BlockBuilderViewModel.types() = assertIs<BlockBuilderUiState.Types>(uiState.value)
    private fun BlockBuilderViewModel.builder() = assertIs<BlockBuilderUiState.Builder>(uiState.value)

    private fun TestScope.toBuilder(model: BlockBuilderViewModel) {
        send(model, BlockBuilderEvent.Continue, BlockBuilderEvent.Continue)
    }

    /** Picks the first enabled row of every required slot. */
    private fun TestScope.fillAll(model: BlockBuilderViewModel) {
        val days = model.builder().state.days
        days.forEachIndexed { d, day ->
            day.slots.indices.forEach { i ->
                send(model, BlockBuilderEvent.OpenSlot(SlotRef(d, i)))
                val id = model.builder().pickerEntries.first { !it.disabled }.exercise.id
                send(model, BlockBuilderEvent.PickExercise(id))
            }
        }
    }

    private suspend fun ReceiveTurbine<BlockBuilderEffect>.awaitEffect(expected: BlockBuilderEffect) {
        while (awaitItem() != expected) {
            // Skips the haptic effects emitted before.
        }
    }

    @Test
    fun load_showsTheRecommendedSplitFirstWithItsReason() = runTest(dispatcher) {
        val state = viewModel().splits()

        assertEquals(4, state.days)
        assertEquals(3, state.splits.size)
        assertTrue(state.splits.first().recommended)
        assertNotNull(state.splits.first().reason)
        assertEquals(0, state.selectedIndex)
    }

    @Test
    fun load_splitsPerDayCount_followThePreference() = runTest(dispatcher) {
        listOf(1 to 1, 2 to 3, 6 to 2).forEach { (days, count) ->
            prefs.result = Result.success(TrainingPreferences(daysPerWeek = days, sessionMinutes = 180))
            assertEquals(count, viewModel().splits().splits.size, "days $days")
        }
    }

    @Test
    fun load_noTrainingDays_isAPlainFailure() = runTest(dispatcher) {
        prefs.result = Result.success(TrainingPreferences())

        assertEquals(BlockBuilderUiState.Failed(offline = false), viewModel().uiState.value)
    }

    @Test
    fun load_offline_failsAsOffline_andRetryLoadsAgain() = runTest(dispatcher) {
        blocks.builderInputs = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        assertEquals(BlockBuilderUiState.Failed(offline = true), model.uiState.value)

        blocks.builderInputs = Result.success(BuilderInputs(emptyMap(), listOf("bike"), "bike"))
        send(model, BlockBuilderEvent.Retry)

        model.splits()
    }

    @Test
    fun pickSplit_changesTheSelection_andIgnoresAnUnknownIndex() = runTest(dispatcher) {
        val model = viewModel()

        send(model, BlockBuilderEvent.PickSplit(1))
        assertEquals(1, model.splits().selectedIndex)
        send(model, BlockBuilderEvent.PickSplit(9))
        assertEquals(1, model.splits().selectedIndex)
    }

    @Test
    fun continue_fromSplits_showsTheTypesOfTheChosenSplit() = runTest(dispatcher) {
        val model = viewModel()
        send(model, BlockBuilderEvent.PickSplit(1), BlockBuilderEvent.Continue)

        assertEquals(
            listOf(RoutineType.Push, RoutineType.Pull, RoutineType.Push, RoutineType.Pull),
            model.types().chosen,
        )
        assertEquals(4, model.types().options.size)
        assertTrue(model.types().canContinue)
    }

    @Test
    fun pickType_appliesToTheOpenDay_andClosesTheSheet() = runTest(dispatcher) {
        val model = viewModel()
        send(model, BlockBuilderEvent.Continue, BlockBuilderEvent.OpenDay(0))
        assertEquals(0, model.types().openDay)

        send(model, BlockBuilderEvent.PickType(RoutineType.FullBody))

        assertEquals(RoutineType.FullBody, model.types().chosen[0])
        assertNull(model.types().openDay)
    }

    @Test
    fun pickType_thatLeavesAGroupWithoutADay_isDisabledWithItsReason_andIgnored() = runTest(dispatcher) {
        val model = viewModel()
        // Upper, Lower, Upper, Lower: turning the only two Lower days into Upper leaves the legs untrained.
        send(
            model,
            BlockBuilderEvent.Continue,
            BlockBuilderEvent.OpenDay(1),
            BlockBuilderEvent.PickType(RoutineType.Upper),
        )
        send(model, BlockBuilderEvent.OpenDay(3))

        val option = model.types().options[3].first { it.type == RoutineType.Upper }
        assertIs<DisabledReason.LeavesGroupUntrained>(option.disabledReason)
        send(model, BlockBuilderEvent.PickType(RoutineType.Upper))
        assertEquals(RoutineType.Lower, model.types().chosen[3])
    }

    @Test
    fun strengthWithSomeGroupOnOneDay_flagsTheNoteWithoutDisabling() = runTest(dispatcher) {
        prefs.result = Result.success(
            TrainingPreferences(intention = TrainingIntention.Strength, daysPerWeek = 2, sessionMinutes = 180),
        )
        val model = viewModel()
        send(model, BlockBuilderEvent.PickSplit(1), BlockBuilderEvent.Continue)

        val options = model.types().options.flatten()
        assertTrue(options.any { it.strengthNote && it.enabled })
    }

    @Test
    fun types_thatDoNotFit_blockContinue_withBothNumbers_andRecomputeOnEveryChange() = runTest(dispatcher) {
        prefs.result = Result.success(TrainingPreferences(daysPerWeek = 4, sessionMinutes = 10))
        val model = viewModel()
        send(model, BlockBuilderEvent.Continue)

        val noFit = assertNotNull(model.types().noFit)
        assertEquals(10, noFit.limitMinutes)
        assertTrue(noFit.neededMinutes > 10)
        assertFalse(model.types().canContinue)

        send(model, BlockBuilderEvent.Continue)
        assertIs<BlockBuilderUiState.Types>(model.uiState.value)
    }

    @Test
    fun builder_startsAtZeroPercent_withNoSaveAndNoServerCall() = runTest(dispatcher) {
        val model = viewModel()
        toBuilder(model)

        val state = model.builder()
        assertEquals(0, state.state.score.percent)
        assertFalse(state.canSave)
        assertTrue(blocks.builtSaves.isEmpty())
        assertEquals(0, blocks.splitRequests)
        assertTrue(blocks.requests.isEmpty())
    }

    @Test
    fun pickingRaisesTheScore_andClearingLowersIt() = runTest(dispatcher) {
        val model = viewModel()
        toBuilder(model)

        send(model, BlockBuilderEvent.OpenSlot(SlotRef(0, 0)))
        send(model, BlockBuilderEvent.PickExercise(model.builder().pickerEntries.first { !it.disabled }.exercise.id))
        val raised = model.builder().state.score.percent
        assertTrue(raised > 0)
        assertNull(model.builder().openSlot)

        send(model, BlockBuilderEvent.ClearSlot(0, 0))
        assertEquals(0, model.builder().state.score.percent)
    }

    @Test
    fun pickerOfASlot_isFilteredToItsGroup_andRepeatsInTheRoutineAreDisabled() = runTest(dispatcher) {
        val model = viewModel()
        toBuilder(model)
        send(model, BlockBuilderEvent.OpenSlot(SlotRef(0, 0)))
        val first = model.builder().pickerEntries.first { !it.disabled }.exercise
        send(model, BlockBuilderEvent.PickExercise(first.id))
        val group = model.builder().state.days[0].slots[1].slot.group

        send(model, BlockBuilderEvent.OpenSlot(SlotRef(0, 1)))
        val entries = model.builder().pickerEntries

        assertTrue(entries.all { it.exercise.exercise.muscleGroup == group })
        if (group == first.exercise.muscleGroup) {
            assertEquals(
                PickerDisabledReason.AlreadyInRoutine,
                entries.first {
                    it.exercise.id == first.id
                }.disabledReason,
            )
        }
        send(model, BlockBuilderEvent.PickExercise(first.id))
        assertNull(model.builder().state.days[0].slots[1].pick.takeIf { group == first.exercise.muscleGroup })
    }

    @Test
    fun exerciseWithoutMovementPattern_isListedDisabled_andCannotBePicked() = runTest(dispatcher) {
        val model = viewModel()
        toBuilder(model)
        val chestSlot = model.builder().state.days[0].slots.indexOfFirst { it.slot.group == MuscleGroup.Chest }
        send(model, BlockBuilderEvent.OpenSlot(SlotRef(0, chestSlot)))

        val mine = model.builder().pickerEntries.first { it.exercise.id == "mine" }
        assertEquals(PickerDisabledReason.NoMovementPattern, mine.disabledReason)
        send(model, BlockBuilderEvent.PickExercise("mine"))

        assertNull(model.builder().state.days[0].slots[chestSlot].pick)
        assertNotNull(model.builder().openSlot)
    }

    private fun custom(pattern: MovementPattern?, name: String = "Mi remo") =
        BlockBuilderEvent.CreateCustomExercise(name, Equipment.Machine, pattern)

    @Test
    fun createCustomExercise_savesItAndPicksItIntoTheOpenSlot() = runTest(dispatcher) {
        val model = viewModel()
        toBuilder(model)
        val slot = model.builder().state.days[0].slots.indexOfFirst { it.slot.kind != SlotKind.Main }
        val group = model.builder().state.days[0].slots[slot].slot.group
        send(model, BlockBuilderEvent.OpenSlot(SlotRef(0, slot)))

        send(model, custom(MovementPattern.Isolation))

        assertEquals(1, exerciseRepository.createCalls)
        assertEquals(MovementPattern.Isolation, exerciseRepository.lastCreatedMovementPattern)
        assertEquals(group, exerciseRepository.lastCreated?.second)
        assertEquals("custom-1", model.builder().state.days[0].slots[slot].pick?.exerciseId)
        assertNull(model.builder().openSlot)
        assertFalse(model.builder().createFailed)
    }

    @Test
    fun createCustomExercise_withoutPattern_isIgnored() = runTest(dispatcher) {
        val model = viewModel()
        toBuilder(model)
        send(model, BlockBuilderEvent.OpenSlot(SlotRef(0, 0)))

        send(model, custom(null))

        assertEquals(0, exerciseRepository.createCalls)
        assertNotNull(model.builder().openSlot)
    }

    @Test
    fun createCustomExercise_withBlankName_isIgnored() = runTest(dispatcher) {
        val model = viewModel()
        toBuilder(model)
        send(model, BlockBuilderEvent.OpenSlot(SlotRef(0, 0)))

        send(model, custom(MovementPattern.Isolation, name = "  "))

        assertEquals(0, exerciseRepository.createCalls)
    }

    @Test
    fun mainSlot_acceptsOnlyMainPatterns_forACustomExercise() = runTest(dispatcher) {
        prefs.result = Result.success(
            TrainingPreferences(intention = TrainingIntention.Strength, daysPerWeek = 4, sessionMinutes = 180),
        )
        val model = viewModel()
        toBuilder(model)
        val main = model.builder().state.days[0].slots.indexOfFirst { it.slot.kind == SlotKind.Main }
        assertTrue(main >= 0)
        send(model, BlockBuilderEvent.OpenSlot(SlotRef(0, main)))

        assertTrue(MovementPattern.Isolation !in model.builder().validPatterns)
        assertTrue(MovementPattern.Squat in model.builder().validPatterns)
        send(model, custom(MovementPattern.Isolation))
        assertEquals(0, exerciseRepository.createCalls)

        send(model, custom(MovementPattern.HorizontalPush))
        assertEquals("custom-1", model.builder().state.days[0].slots[main].pick?.exerciseId)
    }

    @Test
    fun otherSlots_acceptEveryPatternExceptCardio() = runTest(dispatcher) {
        val model = viewModel()
        toBuilder(model)
        val slot = model.builder().state.days[0].slots.indexOfFirst { it.slot.kind != SlotKind.Main }
        send(model, BlockBuilderEvent.OpenSlot(SlotRef(0, slot)))

        assertTrue(MovementPattern.Cardio !in model.builder().validPatterns)
        assertTrue(MovementPattern.Isolation in model.builder().validPatterns)
        send(model, custom(MovementPattern.Cardio))
        assertEquals(0, exerciseRepository.createCalls)
    }

    @Test
    fun createCustomExercise_failure_keepsThePickerOpenAndFlagsTheError() = runTest(dispatcher) {
        val model = viewModel()
        toBuilder(model)
        send(model, BlockBuilderEvent.OpenSlot(SlotRef(0, 0)))
        exerciseRepository.createFailure = AppError.Network

        send(model, custom(MovementPattern.Isolation))

        assertTrue(model.builder().createFailed)
        assertNull(model.builder().state.days[0].slots[0].pick)
        assertNotNull(model.builder().openSlot)
    }

    @Test
    fun builderState_carriesTheFavouriteIds() = runTest(dispatcher) {
        favourites.setFavorite("chest-main", true)
        val model = viewModel()
        toBuilder(model)

        assertEquals(setOf("chest-main"), model.builder().favouriteIds)
    }

    @Test
    fun save_isIgnoredBelow100Percent() = runTest(dispatcher) {
        val model = viewModel()
        toBuilder(model)

        send(model, BlockBuilderEvent.Save)

        assertTrue(blocks.builtSaves.isEmpty())
    }

    @Test
    fun save_at100Percent_savesOnceThroughTheUseCase_andEmitsSaved() = runTest(dispatcher) {
        val model = viewModel()
        toBuilder(model)
        fillAll(model)
        assertTrue(model.builder().canSave)

        send(model, BlockBuilderEvent.Save)

        model.effects.test { awaitEffect(BlockBuilderEffect.Saved) }
        assertEquals(1, blocks.builtSaves.size)
        assertEquals("plan-1", blocks.builtSaves.single().first)
        assertEquals(4, blocks.builtSaves.single().second.daysPerWeek)
        assertFalse(model.builder().saving)
    }

    @Test
    fun saveFailures_showTheirKind_andKeepThePicks() = runTest(dispatcher) {
        listOf(
            CoachException(CoachFailure.Conflict) to BuilderSaveError.PlanClosed,
            CoachException(CoachFailure.InvalidBlock) to BuilderSaveError.Generic,
            AppException(AppError.Network) to BuilderSaveError.Offline,
        ).forEach { (failure, kind) ->
            blocks.builtSaveResult = Result.failure(failure)
            val model = viewModel()
            toBuilder(model)
            fillAll(model)

            send(model, BlockBuilderEvent.Save)

            assertEquals(kind, model.builder().saveError)
            assertFalse(model.builder().saving)
            assertEquals(100, model.builder().state.score.percent)
            assertEquals(kind != BuilderSaveError.PlanClosed, model.builder().canSave)
        }
    }

    @Test
    fun extras_doNotChangeTheScore_andReportMinutesOver() = runTest(dispatcher) {
        prefs.result = Result.success(TrainingPreferences(daysPerWeek = 4, sessionMinutes = 60))
        val model = viewModel()
        toBuilder(model)
        fillAll(model)
        val before = model.builder().state.score.percent

        send(model, BlockBuilderEvent.AddExtra(0))
        assertEquals(true, model.builder().openSlot?.extra)
        send(model, BlockBuilderEvent.PickExercise("curl"))

        assertEquals(before, model.builder().state.score.percent)
        assertEquals(1, model.builder().state.days[0].extras.size)

        send(model, BlockBuilderEvent.RemoveExtra(0, 0))
        assertTrue(model.builder().state.days[0].extras.isEmpty())
    }

    @Test
    fun cardio_isTheLastExerciseOfEveryRoutine_andSwappingKeepsTheMinutes() = runTest(dispatcher) {
        val model = viewModel()
        toBuilder(model)
        fillAll(model)
        val minutes = model.builder().state.cardioPlan.perRoutineMin

        assertEquals("bike", model.builder().cardioExercise?.id)
        send(model, BlockBuilderEvent.SwapCardio("rower"))
        send(model, BlockBuilderEvent.SwapCardio("not-an-option"))

        assertEquals("rower", model.builder().cardioExercise?.id)
        assertEquals(minutes, model.builder().state.cardioPlan.perRoutineMin)
        val block = assertNotNull(model.builder().state.toBuiltBlock())
        assertTrue(block.sessions.all { it.exercises.last().exerciseId == "rower" })
    }

    @Test
    fun cardioTarget_defaultsTo150WhenThePlanHasNone() = runTest(dispatcher) {
        plans.current = Result.success(null)
        val model = viewModel()
        toBuilder(model)

        val plan = model.builder().state.cardioPlan
        assertEquals(150, plan.targetMinWeek)
        assertEquals(listOf(40, 40, 35, 35), plan.perRoutineMin)
    }

    @Test
    fun cardioTarget_comesFromThePlan_cappedAt60PerRoutine() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(activity = defaultActivity.copy(cardioMinWeek = 300)))
        val model = viewModel()
        toBuilder(model)

        val plan = model.builder().state.cardioPlan
        assertEquals(listOf(60, 60, 60, 60), plan.perRoutineMin)
        assertEquals(60, plan.onYourOwnMin)
    }

    @Test
    fun sameInputsAndPicks_buildTheSameBlock() = runTest(dispatcher) {
        val first = viewModel()
        toBuilder(first)
        fillAll(first)
        val second = viewModel()
        toBuilder(second)
        fillAll(second)

        assertEquals(first.builder().state.toBuiltBlock(), second.builder().state.toBuiltBlock())
    }

    @Test
    fun setsAreSharedOverTheDays_4DaysUpperLower_5Plus5() = runTest(dispatcher) {
        val model = viewModel()
        toBuilder(model)

        val chestSets = model.builder().state.days.map { day ->
            day.slots.filter { it.slot.group == MuscleGroup.Chest }.sumOf { it.slot.sets }
        }
        assertEquals(listOf(5, 0, 5, 0), chestSets)
    }

    @Test
    fun setsAreSharedOverTheDays_6Days_4Plus3Plus3() = runTest(dispatcher) {
        prefs.result = Result.success(TrainingPreferences(daysPerWeek = 6, sessionMinutes = 180))
        val model = viewModel()
        toBuilder(model)

        val chestSets = model.builder().state.days
            .map { day -> day.slots.filter { it.slot.group == MuscleGroup.Chest }.sumOf { it.slot.sets } }
            .filter { it > 0 }
        assertEquals(listOf(4, 3, 3), chestSets)
    }

    @Test
    fun strengthWith5SetsASession_startsWithThe3SetMainLiftThenTwo() = runTest(dispatcher) {
        prefs.result = Result.success(
            TrainingPreferences(intention = TrainingIntention.Strength, daysPerWeek = 4, sessionMinutes = 180),
        )
        val model = viewModel()
        toBuilder(model)

        val chest = model.builder().state.days[0].slots.filter { it.slot.group == MuscleGroup.Chest }
        assertEquals(listOf(SlotKind.Main to 3, SlotKind.Accessory to 2), chest.map { it.slot.kind to it.slot.sets })
    }

    @Test
    fun back_fromTheBuilderWithoutPicks_returnsToTypes_andFromThereToSplits() = runTest(dispatcher) {
        val model = viewModel()
        toBuilder(model)

        send(model, BlockBuilderEvent.Back)
        model.types()
        send(model, BlockBuilderEvent.Back)
        model.splits()
    }

    @Test
    fun back_fromTheBuilderWithPicks_asksToDiscard_andConfirmingLeaves() = runTest(dispatcher) {
        val model = viewModel()
        toBuilder(model)
        send(model, BlockBuilderEvent.OpenSlot(SlotRef(0, 0)))
        send(model, BlockBuilderEvent.PickExercise(model.builder().pickerEntries.first { !it.disabled }.exercise.id))

        send(model, BlockBuilderEvent.Back)
        assertTrue(model.builder().discardDialog)
        send(model, BlockBuilderEvent.CancelDiscard)
        assertFalse(model.builder().discardDialog)

        send(model, BlockBuilderEvent.Back, BlockBuilderEvent.ConfirmDiscard)
        model.effects.test { awaitEffect(BlockBuilderEffect.Close) }
        assertTrue(blocks.builtSaves.isEmpty())
    }

    @Test
    fun back_withAnOpenPicker_closesItFirst() = runTest(dispatcher) {
        val model = viewModel()
        toBuilder(model)
        send(model, BlockBuilderEvent.OpenSlot(SlotRef(0, 0)))

        send(model, BlockBuilderEvent.Back)

        assertNull(model.builder().openSlot)
        assertFalse(model.builder().discardDialog)
    }

    @Test
    fun restoration_splitsStep_keepsTheChosenSplit() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        send(viewModel(saved), BlockBuilderEvent.PickSplit(2))

        assertEquals(2, viewModel(saved).splits().selectedIndex)
    }

    @Test
    fun restoration_typesStep_keepsTheTypesAndTheOpenSheet() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val model = viewModel(saved)
        send(
            model,
            BlockBuilderEvent.Continue,
            BlockBuilderEvent.OpenDay(0),
            BlockBuilderEvent.PickType(RoutineType.FullBody),
            BlockBuilderEvent.OpenDay(2),
        )

        val restored = viewModel(saved).types()

        assertEquals(model.types(), restored)
        assertEquals(2, restored.openDay)
    }

    @Test
    fun restoration_builderStep_keepsPicksOpenSlotAndDiscardDialog_withoutBillingAnything() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val model = viewModel(saved)
        toBuilder(model)
        send(model, BlockBuilderEvent.SelectDay(1), BlockBuilderEvent.OpenSlot(SlotRef(1, 0)))
        send(model, BlockBuilderEvent.PickExercise(model.builder().pickerEntries.first { !it.disabled }.exercise.id))
        send(model, BlockBuilderEvent.OpenSlot(SlotRef(1, 1)), BlockBuilderEvent.Back)
        send(model, BlockBuilderEvent.Back)
        val before = model.builder()
        assertTrue(before.discardDialog)

        val restored = viewModel(saved).builder()

        assertEquals(before, restored)
        assertEquals(1, restored.selectedDay)
        assertEquals(0, blocks.splitRequests)
        assertTrue(blocks.requests.isEmpty())
        assertTrue(blocks.builtSaves.isEmpty())
    }

    @Test
    fun restoration_openSlotPicker_isRebuilt() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val model = viewModel(saved)
        toBuilder(model)
        send(model, BlockBuilderEvent.OpenSlot(SlotRef(0, 0)))

        val restored = viewModel(saved).builder()

        assertEquals(SlotRef(0, 0), restored.openSlot)
        assertEquals(model.builder().pickerEntries, restored.pickerEntries)
    }
}
