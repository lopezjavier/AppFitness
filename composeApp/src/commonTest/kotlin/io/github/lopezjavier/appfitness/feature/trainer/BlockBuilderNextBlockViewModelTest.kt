package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredCoachPlanRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredCoachReviewRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredDiscomfortRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredProfileRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.coach.builder.BuilderInputs
import io.github.lopezjavier.appfitness.domain.coach.builder.GroupPriority
import io.github.lopezjavier.appfitness.domain.coach.builder.NextBlockTargets
import io.github.lopezjavier.appfitness.domain.coach.builder.SplitCatalog
import io.github.lopezjavier.appfitness.domain.model.BlockAdherence
import io.github.lopezjavier.appfitness.domain.model.BlockExercise
import io.github.lopezjavier.appfitness.domain.model.BlockLift
import io.github.lopezjavier.appfitness.domain.model.BlockReview
import io.github.lopezjavier.appfitness.domain.model.BlockSaved
import io.github.lopezjavier.appfitness.domain.model.BlockSession
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.model.Discomfort
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import io.github.lopezjavier.appfitness.domain.repository.CoachPlanRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachReviewRepository
import io.github.lopezjavier.appfitness.domain.repository.DiscomfortRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import io.github.lopezjavier.appfitness.domain.usecase.BuildBlockUseCase
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.domain.usecase.LoadFolderBlockUseCase
import io.github.lopezjavier.appfitness.domain.usecase.LoadNextBlockContextUseCase
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.FakeFavoriteExercisesRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The next block in the builder (T-235 / US-102): targets step, seeded picks, suggestion and save. */
@OptIn(ExperimentalCoroutinesApi::class)
class BlockBuilderNextBlockViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val blocks = FakeCoachBlockRepository()
    private val prefs = FakeTrainingPreferencesRepository()
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
    }

    private val exerciseRepository = FakeExerciseRepository(rows())
    private val library = FakeExerciseLibrary(
        rows().filter { it.key != null }.associate { it.key!! to exerciseInfo(it.key!!, "Nombre ${it.key}") },
    )

    private fun exercise(id: String, group: MuscleGroup) = BlockExercise(
        exerciseId = id,
        exerciseKey = id,
        role = "main",
        muscleGroup = group.name.lowercase(),
        sets = 3,
        repMin = 8,
        repMax = 12,
        restSeconds = 120,
        reason = "",
    )

    private fun session(index: Int, name: String, types: List<MuscleGroup>) = BlockSession(
        week = 1,
        sessionIndex = index,
        name = name,
        focus = types.map { it.name.lowercase() },
        exercises = types.map { exercise("${it.name.lowercase()}-main", it) },
        routineId = "r$index",
    )

    private val previousBlock = CoachBlock(
        planId = "plan-1",
        training = blockTraining(days = 4).copy(weeklySets = groups.associate { it.name.lowercase() to 10 }),
        sessions = listOf(
            session(0, "Torso A", listOf(MuscleGroup.Chest, MuscleGroup.Back, MuscleGroup.Shoulders)),
            session(1, "Pierna A", listOf(MuscleGroup.Quads, MuscleGroup.Hamstrings, MuscleGroup.Glutes)),
            session(2, "Torso B", listOf(MuscleGroup.Chest, MuscleGroup.Back, MuscleGroup.Shoulders)),
            session(3, "Pierna B", listOf(MuscleGroup.Quads, MuscleGroup.Hamstrings, MuscleGroup.Glutes)),
        ),
        saved = true,
    )

    private var adherence: Int? = 90
    private var stalled = listOf("chest-main")
    private var reviewResult: Result<BlockReview?>? = null

    private fun review() = BlockReview(
        id = "rev",
        planId = "plan-1",
        endedOn = LocalDate(2026, 10, 18),
        adherence = BlockAdherence(14, 16, adherence),
        lifts = stalled.map { BlockLift(it, emptyList(), null, null, null, true) },
        groups = emptyList(),
        weight = null,
        outcomes = emptyList(),
        waist = null,
        stalls = emptyList(),
        phaseProposal = null,
        phaseDecision = null,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        prefs.result = Result.success(
            TrainingPreferences(intention = TrainingIntention.Muscle, daysPerWeek = 4, sessionMinutes = 180),
        )
        blocks.builderInputs = Result.success(BuilderInputs(emptyMap(), listOf("bike"), "bike"))
        blocks.current = Result.success(previousBlock)
        blocks.builtSaveResult = Result.success(BlockSaved("folder-2", listOf("r"), "plan-9"))
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun nextBlockUseCase(): LoadNextBlockContextUseCase {
        val reviews = object : CoachReviewRepository by UnconfiguredCoachReviewRepository() {
            override suspend fun openReview(today: LocalDate) = reviewResult ?: Result.success(review())
        }
        val plans = object : CoachPlanRepository by UnconfiguredCoachPlanRepository() {
            override suspend fun currentPlan(): Result<CoachPlan?> = Result.success(null)
        }
        val profile = object : ProfileRepository by UnconfiguredProfileRepository() {
            override suspend fun getBiometrics() = Result.success(SavedBiometrics())
        }
        return LoadNextBlockContextUseCase(
            reviews,
            blocks,
            plans,
            profile,
            object : DiscomfortRepository by UnconfiguredDiscomfortRepository() {
                override suspend fun unresolved(exerciseIds: Set<String>) = Result.success(emptyList<Discomfort>())
            },
            MutableClock(),
        )
    }

    private fun TestScope.viewModel(saved: SavedStateHandle = SavedStateHandle()): BlockBuilderViewModel {
        val model = BlockBuilderViewModel(
            planId = "plan-2",
            builder = BuildBlockUseCase(
                blocks,
                LoadFolderBlockUseCase(FakeRoutineRepository(), exerciseRepository, prefs),
            ),
            catalog = LoadExerciseCatalog(exerciseRepository, library),
            favourites = FakeFavoriteExercisesRepository(),
            exercises = exerciseRepository,
            preferences = prefs,
            plans = FakeCoachPlanRepository(),
            clock = MutableClock(),
            previousPlanId = "plan-1",
            nextBlock = nextBlockUseCase(),
            savedState = saved,
        )
        advanceUntilIdle()
        return model
    }

    private fun TestScope.send(model: BlockBuilderViewModel, vararg events: BlockBuilderEvent) {
        events.forEach { model.onEvent(it) }
        advanceUntilIdle()
    }

    private fun BlockBuilderViewModel.targets() = assertIs<BlockBuilderUiState.Targets>(uiState.value)
    private fun BlockBuilderViewModel.builder() = assertIs<BlockBuilderUiState.Builder>(uiState.value)

    private fun BlockBuilderUiState.Targets.row(group: MuscleGroup) = rows.first { it.group == group }

    private fun TestScope.toBuilder(model: BlockBuilderViewModel) = send(model, BlockBuilderEvent.ContinueFromTargets)

    /** Picks the first enabled row of every slot the seed left empty. */
    private fun TestScope.fillEmpty(model: BlockBuilderViewModel) {
        model.builder().state.days.forEachIndexed { d, day ->
            day.slots.forEachIndexed { i, filled ->
                if (filled.pick == null) {
                    send(model, BlockBuilderEvent.OpenSlot(SlotRef(d, i)))
                    val id = model.builder().pickerEntries.first { !it.disabled }.exercise.id
                    send(model, BlockBuilderEvent.PickExercise(id))
                }
            }
        }
    }

    @Test
    fun opensOnTheTargetsStepWithTheLastBlocksSets() = runTest(dispatcher) {
        val state = viewModel().targets()

        assertEquals(groups, state.rows.map { it.group })
        assertTrue(state.rows.all { it.before == 10 && it.after == 10 && it.choice == GroupPriority.None })
        assertEquals(90, state.adherencePct)
    }

    @Test
    fun aDaysPerWeekChange_offersTheSplitsOfTheNewDays_insteadOfSeedingTheOldSessions() = runTest(dispatcher) {
        prefs.result = Result.success(
            TrainingPreferences(intention = TrainingIntention.Muscle, daysPerWeek = 3, sessionMinutes = 180),
        )
        val model = viewModel()

        toBuilder(model)

        val splits = assertIs<BlockBuilderUiState.Splits>(model.uiState.value)
        assertEquals(3, splits.days)
        assertTrue(splits.splits.all { it.types.size == 3 })
    }

    @Test
    fun prioritiseShowsTheBeforeAfterLine() = runTest(dispatcher) {
        val model = viewModel()
        send(model, BlockBuilderEvent.SetPriority(MuscleGroup.Shoulders, GroupPriority.Prioritise))

        val row = model.targets().row(MuscleGroup.Shoulders)
        assertEquals(10, row.before)
        assertEquals(12, row.after)
        assertEquals(GroupPriority.Prioritise, row.choice)
    }

    @Test
    fun maintainLowersTheGroupAndFreedSetsGoToThePrioritisedOne() = runTest(dispatcher) {
        val model = viewModel()
        send(
            model,
            BlockBuilderEvent.SetPriority(MuscleGroup.Back, GroupPriority.Maintain),
            BlockBuilderEvent.SetPriority(MuscleGroup.Shoulders, GroupPriority.Prioritise),
        )

        assertEquals(4, model.targets().row(MuscleGroup.Back).after)
        assertEquals(18, model.targets().row(MuscleGroup.Shoulders).after)
    }

    @Test
    fun aFourthPrioritiseIsIgnoredAndTheOthersShowWhy() = runTest(dispatcher) {
        val model = viewModel()
        listOf(MuscleGroup.Chest, MuscleGroup.Back, MuscleGroup.Shoulders, MuscleGroup.Quads).forEach {
            send(model, BlockBuilderEvent.SetPriority(it, GroupPriority.Prioritise))
        }

        val state = model.targets()
        assertEquals(3, state.prioritisedCount)
        assertEquals(GroupPriority.None, state.row(MuscleGroup.Quads).choice)
        assertEquals(NextBlockTargets.Blocked.ThreeChosen, state.row(MuscleGroup.Quads).blocked)
    }

    @Test
    fun lowAdherenceAddsNoSetsAndSaysWhy() = runTest(dispatcher) {
        adherence = 70
        val model = viewModel()
        send(model, BlockBuilderEvent.SetPriority(MuscleGroup.Shoulders, GroupPriority.Prioritise))

        val row = model.targets().row(MuscleGroup.Shoulders)
        assertEquals(10, row.after)
        assertEquals(NextBlockTargets.Blocked.LowAdherence, row.blocked)
    }

    @Test
    fun theSuggestionChangesNothingUntilTappedAndSecondTapUndoesIt() = runTest(dispatcher) {
        val model = viewModel()
        assertEquals(SuggestionState.Offered, model.targets().row(MuscleGroup.Chest).suggestion)
        assertNull(model.targets().row(MuscleGroup.Back).suggestion)
        assertEquals(10, model.targets().row(MuscleGroup.Chest).after)

        send(model, BlockBuilderEvent.ApplySuggestion(MuscleGroup.Chest))
        assertEquals(12, model.targets().row(MuscleGroup.Chest).after)
        assertEquals(SuggestionState.Applied, model.targets().row(MuscleGroup.Chest).suggestion)

        send(model, BlockBuilderEvent.ApplySuggestion(MuscleGroup.Chest))
        assertEquals(10, model.targets().row(MuscleGroup.Chest).after)
        assertEquals(SuggestionState.Offered, model.targets().row(MuscleGroup.Chest).suggestion)
    }

    @Test
    fun noSuggestionWithLowAdherence() = runTest(dispatcher) {
        adherence = 79
        val model = viewModel()

        assertNull(model.targets().row(MuscleGroup.Chest).suggestion)
        send(model, BlockBuilderEvent.ApplySuggestion(MuscleGroup.Chest))
        assertEquals(10, model.targets().row(MuscleGroup.Chest).after)
    }

    @Test
    fun continueOpensTheBuilderWithThePreviousPicksTheTargetsAndTheStalledLifts() = runTest(dispatcher) {
        val model = viewModel()
        send(model, BlockBuilderEvent.SetPriority(MuscleGroup.Shoulders, GroupPriority.Prioritise))
        toBuilder(model)

        val state = model.builder()
        val picks = state.state.days.flatMap { d -> d.slots.mapNotNull { it.pick?.exerciseId } }
        assertTrue("chest-main" in picks)
        assertEquals(setOf("chest-main"), state.stalledExerciseIds)
        val shoulders = state.state.days.sumOf { d ->
            d.slots.filter { it.slot.group == MuscleGroup.Shoulders }.sumOf { it.slot.sets }
        }
        assertEquals(12, shoulders)
    }

    @Test
    fun saveSendsThePreviousPlanAndReadsTheNewPlanId() = runTest(dispatcher) {
        val model = viewModel()
        toBuilder(model)
        fillEmpty(model)

        send(model, BlockBuilderEvent.Save)

        assertEquals(listOf<String?>("plan-1"), blocks.builtSavePreviousPlanIds)
        assertEquals("plan-2", blocks.builtSaves.single().first)
        assertEquals("plan-9", model.builder().savedPlanId)
        assertEquals(false, model.builder().saving)
    }

    @Test
    fun aSaveThatFindsThePreviousBlockChangedIsPlanClosedAndKeepsTheState() = runTest(dispatcher) {
        blocks.builtSaveResult = Result.failure(CoachException(CoachFailure.Conflict))
        val model = viewModel()
        toBuilder(model)
        fillEmpty(model)
        val before = model.builder().state

        send(model, BlockBuilderEvent.Save)

        assertEquals(BuilderSaveError.PlanClosed, model.builder().saveError)
        assertEquals(before, model.builder().state)
    }

    @Test
    fun anOfflineSaveKeepsTheStateAndCanBeRetried() = runTest(dispatcher) {
        blocks.builtSaveResult = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        toBuilder(model)
        fillEmpty(model)

        send(model, BlockBuilderEvent.Save)
        assertEquals(BuilderSaveError.Offline, model.builder().saveError)

        blocks.builtSaveResult = Result.success(BlockSaved("folder-2", listOf("r"), "plan-9"))
        send(model, BlockBuilderEvent.Save)
        assertNull(model.builder().saveError)
        assertEquals("plan-9", model.builder().savedPlanId)
    }

    @Test
    fun aReviewOfAnotherPlanFailsTheLoad() = runTest(dispatcher) {
        reviewResult = Result.success(review().copy(planId = "other"))

        assertEquals(BlockBuilderUiState.Failed(offline = false), viewModel().uiState.value)
    }

    @Test
    fun anOfflineContextReadFailsAsOffline() = runTest(dispatcher) {
        reviewResult = Result.failure(AppException(AppError.Network))

        assertEquals(BlockBuilderUiState.Failed(offline = true), viewModel().uiState.value)
    }

    @Test
    fun recreatingFromTheSameHandleRestoresTheTargetsStepWithItsChoicesAndSuggestions() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        send(
            first,
            BlockBuilderEvent.SetPriority(MuscleGroup.Shoulders, GroupPriority.Prioritise),
            BlockBuilderEvent.SetPriority(MuscleGroup.Back, GroupPriority.Maintain),
            BlockBuilderEvent.ApplySuggestion(MuscleGroup.Chest),
        )
        val expected = first.targets()

        val restored = viewModel(handle).targets()

        assertEquals(expected, restored)
    }

    @Test
    fun recreatingFromTheSameHandleRestoresTheBuilderStepWithItsPicks() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        send(first, BlockBuilderEvent.SetPriority(MuscleGroup.Chest, GroupPriority.Prioritise))
        toBuilder(first)
        val expected = first.builder().state

        val restored = viewModel(handle).builder()

        assertEquals(expected, restored.state)
        assertEquals(setOf("chest-main"), restored.stalledExerciseIds)
    }

    @Test
    fun backFromTheBuilderWithoutPicksReturnsToTheTargets() = runTest(dispatcher) {
        blocks.current =
            Result.success(
                previousBlock.copy(
                    sessions = previousBlock.sessions.map {
                        it.copy(exercises = emptyList())
                    },
                ),
            )
        val model = viewModel()
        toBuilder(model)

        send(model, BlockBuilderEvent.Back)

        assertIs<BlockBuilderUiState.Targets>(model.uiState.value)
    }
}
