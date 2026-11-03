package io.github.lopezjavier.appfitness.feature.exercises

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseAlternative
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.ExerciseSessionRecords
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SessionStatus
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.model.WorkoutSet
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.feature.workout.FakeWorkoutSessionRepository
import io.github.lopezjavier.appfitness.feature.workout.SESSION_START
import io.github.lopezjavier.appfitness.feature.workout.doneSet
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
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
import kotlin.time.Duration.Companion.days

@Suppress("LargeClass") // One class per ViewModel; the edit form tests grow it.
class ExerciseDetailViewModelTest {
    private val repository = FakeExerciseRepository(listOf(exerciseRow("1", "bench")))
    private val library = FakeExerciseLibrary(mapOf("bench" to exerciseInfo("bench", "Press de banca")))
    private val sessions = FakeWorkoutSessionRepository()
    private var savedState = SavedStateHandle()
    private val favorites = FakeFavoriteExercisesRepository()
    private val connectivity = FakeConnectivityObserver()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(id: String = "1") = ExerciseDetailViewModel(
        id,
        LoadExerciseCatalog(repository, library),
        repository,
        sessions,
        favorites,
        connectivity,
        savedState,
    )

    private fun TestScope.loaded(vm: ExerciseDetailViewModel): ExerciseDetailUiState.Loaded {
        advanceUntilIdle()
        return assertIs<ExerciseDetailUiState.Loaded>(vm.uiState.value)
    }

    private fun TestScope.historyContent(vm: ExerciseDetailViewModel): HistoryState.Content {
        advanceUntilIdle()
        return assertIs<HistoryState.Content>(assertIs<ExerciseDetailUiState.Loaded>(vm.uiState.value).history)
    }

    /** `n` completed sessions of exercise "1", newest has the highest number. */
    private fun addSessions(n: Int) {
        for (i in 1..n) sessions.completed += session("s%03d".format(i), daysAgo = n - i)
    }

    private fun session(id: String, daysAgo: Int, sets: List<WorkoutSet> = listOf(doneSet(1, 80.0, 8))) =
        WorkoutSession(
            id = id,
            routineId = "r1",
            name = "Torso",
            status = SessionStatus.Completed,
            startedAt = SESSION_START - daysAgo.days,
            endedAt = SESSION_START - daysAgo.days,
            exercises = listOf(SessionExercise("1", 0, null, sets)),
        )

    private fun records(id: String, daysAgo: Int, heaviest: Double, oneRm: Double = heaviest) = ExerciseSessionRecords(
        sessionId = id,
        startedAt = SESSION_START - daysAgo.days,
        heaviestLift = heaviest,
        bestSet = heaviest * 8,
        sessionVolume = heaviest * 8,
        bestOneRm = oneRm,
        totalReps = 8,
    )

    // Detail

    @Test
    fun loadsTheExerciseWithItsLibraryEntry() = runTest {
        val vm = viewModel()
        assertEquals(ExerciseDetailUiState.Loading, vm.uiState.value)

        val state = loaded(vm)

        assertEquals("Press de banca", state.detail.name)
    }

    @Test
    fun unknownId_isAnError_andRetryReloads() = runTest {
        val vm = viewModel("zzz")
        advanceUntilIdle()
        assertEquals(ExerciseDetailUiState.Error, vm.uiState.value)

        vm.retry()
        advanceUntilIdle()

        assertEquals(ExerciseDetailUiState.Error, vm.uiState.value)
    }

    @Test
    fun customExercise_withoutLibraryEntry_loadsFine() = runTest {
        repository.rows = listOf(exerciseRow("c1", null, name = "Mi ejercicio", custom = true))

        val state = loaded(viewModel("c1"))

        assertNull(state.detail.info)
        assertEquals("Mi ejercicio", state.detail.name)
    }

    // Tabs

    @Test
    fun defaultTab_isSummary_andSelectTabChangesIt() = runTest {
        val vm = viewModel()
        assertEquals(ExerciseDetailTab.Summary, loaded(vm).tab)

        vm.selectTab(ExerciseDetailTab.HowTo)

        assertEquals(ExerciseDetailTab.HowTo, loaded(vm).tab)
    }

    @Test
    fun selectedTab_survivesRecreationFromTheSameSavedState() = runTest {
        val first = viewModel()
        loaded(first)
        first.selectTab(ExerciseDetailTab.History)
        advanceUntilIdle()

        val recreated = viewModel()

        assertEquals(ExerciseDetailTab.History, loaded(recreated).tab)
    }

    @Test
    fun unknownStoredTab_fallsBackToSummary() = runTest {
        savedState = SavedStateHandle(mapOf("tab" to "Nonsense"))

        assertEquals(ExerciseDetailTab.Summary, loaded(viewModel()).tab)
    }

    // Similar tab

    private fun withAlternatives() {
        repository.rows = listOf(
            exerciseRow("1", "bench"),
            exerciseRow("2", "fly", equipment = Equipment.Cable),
            exerciseRow("3", "band-long-bench", equipment = Equipment.BandLong),
            exerciseRow("4", "crossover", equipment = Equipment.Cable),
        )
        library.entries = mapOf(
            "bench" to exerciseInfo("bench", "Press de banca"),
            "fly" to exerciseInfo("fly", "Aperturas"),
            "band-long-bench" to exerciseInfo("band-long-bench", "Press con banda larga"),
            "crossover" to exerciseInfo("crossover", "Cruce de poleas"),
        )
        repository.alternativesById = mapOf(
            "1" to listOf(
                ExerciseAlternative(repository.rows[2], 1.0),
                ExerciseAlternative(repository.rows[3], 0.874),
                ExerciseAlternative(repository.rows[1], 0.874),
            ),
        )
    }

    @Test
    fun tabOrder_isSummarySimilarHistoryHowTo() {
        assertEquals(
            listOf(
                ExerciseDetailTab.Summary,
                ExerciseDetailTab.Similar,
                ExerciseDetailTab.History,
                ExerciseDetailTab.HowTo,
            ),
            ExerciseDetailTab.entries,
        )
    }

    @Test
    fun similar_isNotLoadedUntilTheTabIsSelected() = runTest {
        withAlternatives()
        val vm = viewModel()

        assertEquals(SimilarState.Loading, loaded(vm).similar)
        assertTrue(repository.alternativesCalls.isEmpty())
    }

    @Test
    fun similar_loadsOnFirstSelection_sortedBySimilarityThenName_withPercent() = runTest {
        withAlternatives()
        val vm = viewModel()
        loaded(vm)

        vm.selectTab(ExerciseDetailTab.Similar)
        val content = assertIs<SimilarState.Content>(loaded(vm).similar)

        assertEquals(
            listOf("Press con banda larga", "Aperturas", "Cruce de poleas"),
            content.rows.map { it.exercise.name },
        )
        assertEquals(listOf(100, 87, 87), content.rows.map { it.percent })
        assertEquals(Equipment.BandLong, content.rows.first().exercise.exercise.equipment)
    }

    @Test
    fun similar_isNotFilteredByEquipment_showsEveryAlternativeTheRepositoryReturns() = runTest {
        withAlternatives()
        val vm = viewModel()
        loaded(vm)

        vm.selectTab(ExerciseDetailTab.Similar)
        val content = assertIs<SimilarState.Content>(loaded(vm).similar)

        assertEquals(
            setOf(Equipment.BandLong, Equipment.Cable),
            content.rows.map { it.exercise.exercise.equipment }.toSet(),
        )
        assertEquals(3, content.rows.size)
    }

    @Test
    fun similar_loadsOnlyOnce_acrossTabSwitches() = runTest {
        withAlternatives()
        val vm = viewModel()
        loaded(vm)

        vm.selectTab(ExerciseDetailTab.Similar)
        advanceUntilIdle()
        vm.selectTab(ExerciseDetailTab.Summary)
        vm.selectTab(ExerciseDetailTab.Similar)
        advanceUntilIdle()

        assertEquals(listOf("1"), repository.alternativesCalls)
    }

    @Test
    fun similar_withoutAlternatives_isEmpty() = runTest {
        val vm = viewModel()
        loaded(vm)

        vm.selectTab(ExerciseDetailTab.Similar)

        assertEquals(SimilarState.Empty, loaded(vm).similar)
    }

    @Test
    fun similar_offline_isOffline() = runTest {
        val vm = viewModel()
        loaded(vm)
        repository.alternativesFailure = AppError.Network

        vm.selectTab(ExerciseDetailTab.Similar)

        assertEquals(SimilarState.Offline, loaded(vm).similar)
    }

    @Test
    fun similar_failure_isError_andRetryReloads() = runTest {
        withAlternatives()
        val vm = viewModel()
        loaded(vm)
        repository.alternativesFailure = AppError.Unknown
        vm.selectTab(ExerciseDetailTab.Similar)
        assertEquals(SimilarState.Error, loaded(vm).similar)

        repository.alternativesFailure = null
        vm.retrySimilar()

        assertIs<SimilarState.Content>(loaded(vm).similar)
        assertEquals(2, repository.alternativesCalls.size)
    }

    @Test
    fun similar_retryOutsideTheErrorState_doesNothing() = runTest {
        withAlternatives()
        val vm = viewModel()
        loaded(vm)
        vm.selectTab(ExerciseDetailTab.Similar)
        advanceUntilIdle()

        vm.retrySimilar()
        advanceUntilIdle()

        assertEquals(1, repository.alternativesCalls.size)
    }

    @Test
    fun similarTab_survivesRecreation_andLoadsItsContent() = runTest {
        withAlternatives()
        val first = viewModel()
        loaded(first)
        first.selectTab(ExerciseDetailTab.Similar)
        advanceUntilIdle()

        val recreated = loaded(viewModel())

        assertEquals(ExerciseDetailTab.Similar, recreated.tab)
        assertIs<SimilarState.Content>(recreated.similar)
    }

    // Records

    @Test
    fun weightExercise_showsFourMarks_andHidesTotalReps() = runTest {
        sessions.sessionRecords = mapOf("1" to listOf(records("a", 3, 80.0), records("b", 1, 90.0, oneRm = 110.0)))

        val state = loaded(viewModel())

        val content = assertIs<RecordsState.Content>(state.records)
        assertEquals(
            listOf(
                PersonalRecordType.HeaviestLift,
                PersonalRecordType.BestSet,
                PersonalRecordType.MaxVolume,
                PersonalRecordType.Best1Rm,
            ),
            content.records.map { it.type },
        )
        assertEquals(90.0, content.records.first { it.type == PersonalRecordType.HeaviestLift }.value)
        assertEquals(110.0, content.records.first { it.type == PersonalRecordType.Best1Rm }.value)
    }

    @Test
    fun repsOnlyExercise_showsTotalReps() = runTest {
        repository.rows = listOf(exerciseRow("1", "bench").copy(metricType = ExerciseMetricType.RepsOnly))
        sessions.sessionRecords = mapOf("1" to listOf(records("a", 3, 0.0).copy(heaviestLift = null)))

        val content = assertIs<RecordsState.Content>(loaded(viewModel()).records)

        assertEquals(listOf(PersonalRecordType.TotalReps), content.records.map { it.type })
    }

    @Test
    fun distanceDurationExercise_showsBestDistanceAndBestDuration() = runTest {
        repository.rows = listOf(exerciseRow("1", "bench").copy(metricType = ExerciseMetricType.DistanceDuration))
        sessions.sessionRecords = mapOf(
            "1" to listOf(
                ExerciseSessionRecords("a", SESSION_START, bestDurationS = 1800, bestDistanceM = 5000.0),
            ),
        )

        val content = assertIs<RecordsState.Content>(loaded(viewModel()).records)

        assertEquals(
            listOf(PersonalRecordType.BestDistance, PersonalRecordType.BestDuration),
            content.records.map { it.type },
        )
    }

    @Test
    fun durationExercise_showsBestDuration() = runTest {
        repository.rows = listOf(exerciseRow("1", "bench").copy(metricType = ExerciseMetricType.Duration))
        sessions.sessionRecords = mapOf("1" to listOf(ExerciseSessionRecords("a", SESSION_START, bestDurationS = 60)))

        val content = assertIs<RecordsState.Content>(loaded(viewModel()).records)

        assertEquals(listOf(PersonalRecordType.BestDuration), content.records.map { it.type })
    }

    @Test
    fun noRecordsLogged_isEmpty() = runTest {
        assertEquals(RecordsState.Empty, loaded(viewModel()).records)
    }

    @Test
    fun recordsFailure_isAnErrorWithoutLosingTheDetail_andRetryRecovers() = runTest {
        sessions.recordsFailure = AppError.Network
        val vm = viewModel()
        val state = loaded(vm)
        assertEquals(RecordsState.Error, state.records)
        assertEquals("Press de banca", state.detail.name)

        sessions.recordsFailure = null
        sessions.sessionRecords = mapOf("1" to listOf(records("a", 1, 80.0)))
        vm.retryRecords()

        assertIs<RecordsState.Content>(loaded(vm).records)
    }

    // History

    @Test
    fun history_isNotLoadedUntilTheTabIsSelected() = runTest {
        addSessions(2)
        val vm = viewModel()

        assertEquals(HistoryState.Loading, loaded(vm).history)
        assertTrue(sessions.exerciseSessionCalls.isEmpty())

        vm.selectTab(ExerciseDetailTab.History)
        advanceUntilIdle()

        assertEquals(1, sessions.exerciseSessionCalls.size)
    }

    @Test
    fun restoredHistoryTab_loadsTheFirstPageWithTheDetail() = runTest {
        addSessions(2)
        savedState = SavedStateHandle(mapOf("tab" to "History"))

        val content = historyContent(viewModel())

        assertEquals(2, content.rows.size)
    }

    @Test
    fun history_listsNewestFirst_withSetTypeAndRpe() = runTest {
        sessions.completed += session("old", daysAgo = 5)
        sessions.completed += session(
            "new",
            daysAgo = 1,
            sets = listOf(
                WorkoutSet(1, SetType.WarmUp, SetEntry.WeightReps(40.0, 10), SESSION_START),
                WorkoutSet(2, SetType.Working, SetEntry.WeightReps(80.0, 8), SESSION_START, rpe = 8.5),
            ),
        )
        val vm = viewModel()
        loaded(vm)
        vm.selectTab(ExerciseDetailTab.History)

        val content = historyContent(vm)

        assertEquals(listOf("new", "old"), content.rows.map { it.sessionId })
        val sets = content.rows.first().sets
        assertEquals(listOf(1, 2), sets.map { it.setIndex })
        assertEquals(SetType.WarmUp, sets[0].setType)
        assertNull(sets[0].rpe)
        assertEquals(8.5, sets[1].rpe)
        assertEquals("Torso", content.rows.first().name)
    }

    @Test
    fun emptyFirstPage_isEmpty() = runTest {
        val vm = viewModel()
        loaded(vm)
        vm.selectTab(ExerciseDetailTab.History)
        advanceUntilIdle()

        assertEquals(HistoryState.Empty, assertIs<ExerciseDetailUiState.Loaded>(vm.uiState.value).history)
    }

    @Test
    fun firstPageFailure_isAnError_andRetryLoads() = runTest {
        addSessions(1)
        sessions.exerciseSessionsFailure = AppError.Network
        val vm = viewModel()
        loaded(vm)
        vm.selectTab(ExerciseDetailTab.History)
        advanceUntilIdle()
        assertEquals(HistoryState.Error, assertIs<ExerciseDetailUiState.Loaded>(vm.uiState.value).history)

        sessions.exerciseSessionsFailure = null
        vm.retryHistory()

        assertEquals(1, historyContent(vm).rows.size)
    }

    @Test
    fun fullPage_isNotTheEnd_andShortPageIs() = runTest {
        addSessions(23)
        val vm = viewModel()
        loaded(vm)
        vm.selectTab(ExerciseDetailTab.History)

        val first = historyContent(vm)
        assertEquals(20, first.rows.size)
        assertFalse(first.endReached)

        vm.loadMoreHistory()
        val second = historyContent(vm)

        assertEquals(23, second.rows.size)
        assertTrue(second.endReached)
        assertEquals(listOf(0, 20), sessions.exerciseSessionCalls.map { it.second })
    }

    @Test
    fun exactlyOnePage_needsOneMoreCallToKnowItEnded() = runTest {
        addSessions(20)
        val vm = viewModel()
        loaded(vm)
        vm.selectTab(ExerciseDetailTab.History)
        assertFalse(historyContent(vm).endReached)

        vm.loadMoreHistory()
        val content = historyContent(vm)

        assertEquals(20, content.rows.size)
        assertTrue(content.endReached)
    }

    @Test
    fun loadMore_appendsWithoutDuplicates_andAdvancesTheOffsetByTheReturnedSize() = runTest {
        val pageOne = (1..20).map { session("p1-$it", daysAgo = 100 - it) }
        sessions.exerciseSessionsPage = { offset ->
            if (offset == 0) pageOne else listOf(pageOne.last(), session("p2-1", daysAgo = 1))
        }
        val vm = viewModel()
        loaded(vm)
        vm.selectTab(ExerciseDetailTab.History)
        historyContent(vm)

        vm.loadMoreHistory()
        val content = historyContent(vm)

        assertEquals(21, content.rows.size)
        assertEquals(content.rows.map { it.sessionId }.distinct(), content.rows.map { it.sessionId })
        assertTrue(content.endReached)
    }

    @Test
    fun pageError_keepsRows_andRetryResumesAtTheSameOffset() = runTest {
        addSessions(25)
        sessions.exerciseSessionsFailureFromOffset = 20
        val vm = viewModel()
        loaded(vm)
        vm.selectTab(ExerciseDetailTab.History)
        historyContent(vm)

        vm.loadMoreHistory()
        val failed = historyContent(vm)
        assertTrue(failed.pageError)
        assertEquals(20, failed.rows.size)

        sessions.exerciseSessionsFailureFromOffset = null
        vm.retryHistoryPage()
        val recovered = historyContent(vm)

        assertFalse(recovered.pageError)
        assertEquals(25, recovered.rows.size)
        assertEquals(listOf(0, 20, 20), sessions.exerciseSessionCalls.map { it.second })
    }

    @Test
    fun loadMore_isIgnoredAtTheEnd() = runTest {
        addSessions(3)
        val vm = viewModel()
        loaded(vm)
        vm.selectTab(ExerciseDetailTab.History)
        historyContent(vm)

        vm.loadMoreHistory()
        advanceUntilIdle()

        assertEquals(1, sessions.exerciseSessionCalls.size)
    }

    @Test
    fun loadMore_isIgnoredWhileAPageIsLoading() = runTest {
        addSessions(40)
        val vm = viewModel()
        loaded(vm)
        vm.selectTab(ExerciseDetailTab.History)
        historyContent(vm)
        val before = sessions.exerciseSessionCalls.size

        vm.loadMoreHistory()
        vm.loadMoreHistory()
        advanceUntilIdle()

        assertEquals(before + 1, sessions.exerciseSessionCalls.size)
    }

    // Beaten records marks

    @Test
    fun rows_markTheRecordsEachSessionBeat_firstSessionCounts_equalValueDoesNot() = runTest {
        sessions.completed += session("a", daysAgo = 10)
        sessions.completed += session("b", daysAgo = 5)
        sessions.completed += session("c", daysAgo = 1)
        sessions.sessionRecords = mapOf(
            "1" to listOf(records("a", 10, 80.0), records("b", 5, 80.0), records("c", 1, 85.0)),
        )
        val vm = viewModel()
        loaded(vm)
        vm.selectTab(ExerciseDetailTab.History)

        val rows = historyContent(vm).rows.associateBy { it.sessionId }

        assertTrue(PersonalRecordType.HeaviestLift in rows.getValue("a").records)
        assertTrue(rows.getValue("b").records.isEmpty())
        assertTrue(PersonalRecordType.HeaviestLift in rows.getValue("c").records)
    }

    @Test
    fun sessionRecordsFailure_showsRowsWithoutMarks_andNoError() = runTest {
        addSessions(2)
        sessions.recordsFailure = AppError.Network
        val vm = viewModel()
        loaded(vm)
        vm.selectTab(ExerciseDetailTab.History)

        val content = historyContent(vm)

        assertEquals(2, content.rows.size)
        assertTrue(content.rows.all { it.records.isEmpty() })
    }

    // Manual exercises: edit and delete (US-09)

    private fun custom(pattern: MovementPattern? = null) {
        repository.rows = listOf(
            exerciseRow("c1", null, MuscleGroup.Chest, Equipment.Machine, "Press Hammer", custom = true)
                .copy(movementPattern = pattern),
        )
    }

    @Test
    fun aCatalogueExercise_cannotBeEditedOrDeleted() = runTest {
        val vm = viewModel("1")
        advanceUntilIdle()

        vm.openEdit()
        vm.delete()
        advanceUntilIdle()

        assertNull(loaded(vm).editForm)
        assertFalse(loaded(vm).canManage)
        assertEquals(0, repository.deleteCalls)
    }

    @Test
    fun openingTheEdit_prefillsTheForm_andNothingIsSavedUntilGuardar() = runTest {
        custom()
        val vm = viewModel("c1")
        advanceUntilIdle()

        vm.openEdit()
        vm.setEditName("Press Hammer 2")
        vm.setEditPerHand(true)

        val form = loaded(vm).editForm!!
        assertEquals("Press Hammer 2", form.name)
        assertEquals(MuscleGroup.Chest, form.muscleGroup)
        assertEquals(Equipment.Machine, form.equipment)
        assertTrue(form.perHand)
        assertEquals(0, repository.updateCalls)
    }

    @Test
    fun dismissingTheEdit_discardsTheChanges() = runTest {
        custom()
        val vm = viewModel("c1")
        advanceUntilIdle()
        vm.openEdit()
        vm.setEditName("Otro")

        vm.dismissEdit()

        assertNull(loaded(vm).editForm)
        assertEquals("Press Hammer", repository.rows.single().name)
    }

    @Test
    fun savingTheEdit_updatesTheExercise_closesTheForm_andShowsTheNewValues() = runTest {
        custom()
        val vm = viewModel("c1")
        advanceUntilIdle()
        vm.openEdit()
        vm.setEditName("  Press Hammer unilateral ")
        vm.selectEditMuscleGroup(MuscleGroup.Back)
        vm.selectEditEquipment(Equipment.Cable)
        vm.setEditPerHand(true)

        vm.submitEdit()
        advanceUntilIdle()

        val state = loaded(vm)
        assertNull(state.editForm)
        val row = repository.rows.single()
        assertEquals(MuscleGroup.Back, row.muscleGroup)
        assertEquals(Equipment.Cable, row.equipment)
        assertTrue(row.perHand)
        assertEquals("Press Hammer unilateral", state.detail.name)
        assertEquals(MuscleGroup.Back, state.detail.exercise.muscleGroup)
    }

    @Test
    fun aBlankName_disablesGuardar_andSubmitDoesNothing() = runTest {
        custom()
        val vm = viewModel("c1")
        advanceUntilIdle()
        vm.openEdit()
        vm.setEditName("   ")

        vm.submitEdit()
        advanceUntilIdle()

        assertFalse(loaded(vm).editForm!!.canSubmit)
        assertEquals(0, repository.updateCalls)
    }

    @Test
    fun aFailedSave_keepsTheFormOpenWithAnError_andRetrySucceeds() = runTest {
        custom()
        val vm = viewModel("c1")
        advanceUntilIdle()
        vm.openEdit()
        vm.setEditPerHand(true)
        repository.updateFailure = AppError.Network

        vm.submitEdit()
        advanceUntilIdle()

        val failed = loaded(vm).editForm!!
        assertFalse(failed.isSubmitting)
        repository.updateFailure = null
        vm.submitEdit()
        advanceUntilIdle()
        assertNull(loaded(vm).editForm)
        assertTrue(repository.rows.single().perHand)
    }

    @Test
    fun theEditForm_survivesARecreatedViewModel() = runTest {
        custom()
        val first = viewModel("c1")
        advanceUntilIdle()
        first.openEdit()
        first.setEditName("Borrador")
        first.setEditPerHand(true)

        val second = viewModel("c1")
        val form = loaded(second).editForm!!

        assertEquals("Borrador", form.name)
        assertTrue(form.perHand)
    }

    @Test
    fun theEditForm_isPrefilledWithTheStoredMovementPattern() = runTest {
        custom(MovementPattern.Hinge)
        val vm = viewModel("c1")
        advanceUntilIdle()

        vm.openEdit()

        assertEquals(MovementPattern.Hinge, loaded(vm).editForm!!.movementPattern)
    }

    @Test
    fun changingAndSavingTheMovementPattern_passesItToTheRepository() = runTest {
        custom()
        val vm = viewModel("c1")
        advanceUntilIdle()
        vm.openEdit()
        assertNull(loaded(vm).editForm!!.movementPattern)

        vm.setEditMovementPattern(MovementPattern.Squat)
        vm.submitEdit()
        advanceUntilIdle()

        assertEquals(MovementPattern.Squat, repository.rows.single().movementPattern)
        assertEquals(MovementPattern.Squat, loaded(vm).detail.exercise.movementPattern)
    }

    @Test
    fun clearingTheMovementPattern_savesNone() = runTest {
        custom(MovementPattern.Carry)
        val vm = viewModel("c1")
        advanceUntilIdle()
        vm.openEdit()

        vm.setEditMovementPattern(null)
        vm.submitEdit()
        advanceUntilIdle()

        assertNull(repository.rows.single().movementPattern)
    }

    @Test
    fun aFailedSave_keepsTheChosenMovementPattern() = runTest {
        custom()
        val vm = viewModel("c1")
        advanceUntilIdle()
        vm.openEdit()
        vm.setEditMovementPattern(MovementPattern.Core)
        repository.updateFailure = AppError.Network

        vm.submitEdit()
        advanceUntilIdle()

        assertEquals(MovementPattern.Core, loaded(vm).editForm!!.movementPattern)
        assertNull(repository.rows.single().movementPattern)
    }

    @Test
    fun theMovementPatternDraft_survivesARecreatedViewModel() = runTest {
        custom()
        val first = viewModel("c1")
        advanceUntilIdle()
        first.openEdit()
        first.setEditMovementPattern(MovementPattern.VerticalPull)

        val second = viewModel("c1")

        assertEquals(MovementPattern.VerticalPull, loaded(second).editForm!!.movementPattern)
    }

    @Test
    fun aClearedMovementPatternDraft_staysNullAfterRecreation() = runTest {
        custom(MovementPattern.Carry)
        val first = viewModel("c1")
        advanceUntilIdle()
        first.openEdit()
        first.setEditMovementPattern(null)

        val second = viewModel("c1")

        assertNull(loaded(second).editForm!!.movementPattern)
    }

    @Test
    fun delete_removesTheExerciseAtOnce_andEmitsDeleted() = runTest {
        custom()
        val vm = viewModel("c1")
        advanceUntilIdle()

        vm.effects.test {
            vm.delete()
            advanceUntilIdle()

            assertEquals(ExerciseDetailEffect.Deleted, awaitItem())
        }
        assertTrue(repository.rows.isEmpty())
    }

    @Test
    fun aSecondDeleteTapBeforeNavigation_doesNotFireAnotherDelete() = runTest {
        custom()
        val vm = viewModel("c1")
        advanceUntilIdle()

        vm.effects.test {
            vm.delete()
            advanceUntilIdle()
            assertEquals(ExerciseDetailEffect.Deleted, awaitItem())

            vm.delete()
            advanceUntilIdle()

            expectNoEvents()
        }
        assertTrue(loaded(vm).isDeleting)
    }

    @Test
    fun anExerciseInUse_emitsInUseWithTheCounts_andIsNotDeleted() = runTest {
        custom()
        val vm = viewModel("c1")
        advanceUntilIdle()
        repository.deleteFailure = AppError.InUse(sets = 2, routines = 1)

        vm.effects.test {
            vm.delete()
            advanceUntilIdle()

            assertEquals(ExerciseDetailEffect.InUse(2, 1), awaitItem())
        }
        assertEquals(1, repository.rows.size)
        assertFalse(loaded(vm).isDeleting)
    }

    @Test
    fun aGenericDeleteFailure_emitsDeleteFailed_andRetrySucceeds() = runTest {
        custom()
        val vm = viewModel("c1")
        advanceUntilIdle()
        repository.deleteFailure = AppError.Network

        vm.effects.test {
            vm.delete()
            advanceUntilIdle()
            assertEquals(ExerciseDetailEffect.DeleteFailed, awaitItem())

            repository.deleteFailure = null
            vm.delete()
            advanceUntilIdle()
            assertEquals(ExerciseDetailEffect.Deleted, awaitItem())
        }
    }

    @Test
    fun deleteOffline_isRefused_andNothingIsSent() = runTest {
        custom()
        connectivity.state.value = false
        val vm = viewModel("c1")
        advanceUntilIdle()

        vm.delete()
        advanceUntilIdle()

        assertFalse(loaded(vm).isOnline)
        assertEquals(0, repository.deleteCalls)
    }

    @Test
    fun favourite_startsAsStoredAndTogglesOnThenOff() = runTest {
        val vm = viewModel()
        assertFalse(loaded(vm).isFavorite)

        vm.toggleFavorite()
        advanceUntilIdle()
        assertTrue(loaded(vm).isFavorite)

        vm.toggleFavorite()
        advanceUntilIdle()
        assertFalse(loaded(vm).isFavorite)
        assertEquals(listOf("1" to true, "1" to false), favorites.setCalls)
    }

    @Test
    fun favourite_alreadyStarred_startsFavourite() = runTest {
        favorites.set(setOf("1"))

        assertTrue(loaded(viewModel()).isFavorite)
    }

    @Test
    fun favourite_onACustomExercise_toggles() = runTest {
        custom()
        val vm = viewModel("c1")
        advanceUntilIdle()

        vm.toggleFavorite()
        advanceUntilIdle()

        assertTrue(loaded(vm).isFavorite)
        assertEquals(setOf("c1"), favorites.favoriteIds.value)
    }

    @Test
    fun favourite_failure_sendsTheEffectAndKeepsTheStar() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        favorites.failure = AppError.Network

        vm.effects.test {
            vm.toggleFavorite()
            advanceUntilIdle()
            assertEquals(ExerciseDetailEffect.FavoriteFailed, awaitItem())
        }
        assertFalse(loaded(vm).isFavorite)
        assertFalse(loaded(vm).isTogglingFavorite)
    }

    @Test
    fun favourite_offline_isIgnored() = runTest {
        connectivity.state.value = false
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleFavorite()
        advanceUntilIdle()

        assertTrue(favorites.setCalls.isEmpty())
    }

    @Test
    fun favourite_doubleTapWhileInFlight_sendsOnce() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        favorites.setGate = CompletableDeferred()

        vm.toggleFavorite()
        advanceUntilIdle()
        assertTrue(loaded(vm).isTogglingFavorite)
        vm.toggleFavorite()
        favorites.setGate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, favorites.setCalls.size)
        assertTrue(loaded(vm).isFavorite)
    }

    @Test
    fun aDoubleTapOnDelete_deletesOnce() = runTest {
        custom()
        val vm = viewModel("c1")
        advanceUntilIdle()

        vm.delete()
        vm.delete()
        advanceUntilIdle()

        assertEquals(1, repository.deleteCalls)
    }

    @Test
    fun savingTheEdit_emitsSaved() = runTest {
        custom()
        val vm = viewModel("c1")
        advanceUntilIdle()
        vm.openEdit()
        vm.setEditPerHand(true)

        vm.effects.test {
            vm.submitEdit()
            advanceUntilIdle()

            assertEquals(ExerciseDetailEffect.Saved, awaitItem())
        }
    }

    @Test
    fun aFailedSave_emitsSaveFailed() = runTest {
        custom()
        val vm = viewModel("c1")
        advanceUntilIdle()
        vm.openEdit()
        repository.updateFailure = AppError.Network

        vm.effects.test {
            vm.submitEdit()
            advanceUntilIdle()

            assertEquals(ExerciseDetailEffect.SaveFailed, awaitItem())
        }
    }

    @Test
    fun saveOffline_isRefused() = runTest {
        custom()
        connectivity.state.value = false
        val vm = viewModel("c1")
        advanceUntilIdle()
        vm.openEdit()

        vm.submitEdit()
        advanceUntilIdle()

        assertEquals(0, repository.updateCalls)
    }

    @Test
    fun aCatalogueExerciseWithoutLibraryData_isTheErrorStateNotAnUnnamedDetail() = runTest {
        repository.rows = listOf(exerciseRow("2", "unknown-key"))
        val vm = viewModel("2")

        advanceUntilIdle()

        assertEquals(ExerciseDetailUiState.Error, vm.uiState.value)
    }
}
