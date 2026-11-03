package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.FolderOp
import io.github.lopezjavier.appfitness.domain.model.ReviewPurpose
import io.github.lopezjavier.appfitness.domain.model.RoutineFolder
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.routines.routineOf
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FolderReviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val reviews = FakeFolderReviewRepository()
    private val routines = FakeRoutineRepository(
        listOf(routineOf("r1", "Torso", "id-bench", "id-row"), routineOf("r2", "Pierna", "id-squat")),
    ).apply {
        folderOf["r1"] = "folder-1"
        folderOf["r2"] = "folder-1"
        folders += RoutineFolder("copy-1", "Mi carpeta (copia)")
    }
    private val exercises = FakeExerciseRepository(
        listOf(exerciseRow("id-bench", "bench"), exerciseRow("id-row", "row"), exerciseRow("id-squat", "squat")),
    )
    private val library = FakeExerciseLibrary(
        mapOf(
            "bench" to exerciseInfo("bench", "Press banca"),
            "row" to exerciseInfo("row", "Remo"),
            "squat" to exerciseInfo("squat", "Sentadilla"),
            "curl" to exerciseInfo("curl", "Curl de biceps"),
        ),
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun args(purpose: String = "review", planId: String? = null) = SavedStateHandle().apply {
        set("folderId", "folder-1")
        set("purpose", purpose)
        planId?.let { set("planId", it) }
    }

    private fun viewModel(saved: SavedStateHandle = args()) = FolderReviewViewModel(
        reviews,
        ExerciseNameResolver(exercises, library),
        library,
        routines,
        MutableClock(),
        saved,
    )

    private fun FolderReviewViewModel.content() = assertIs<FolderReviewUiState.Content>(uiState.value)

    private suspend fun FolderReviewViewModel.start() {
        onEvent(FolderReviewEvent.Request)
        dispatcher.scheduler.advanceUntilIdle()
    }

    private fun given(purpose: ReviewPurpose = ReviewPurpose.Review, withFalta: Boolean = false) {
        reviews.reviewResult = Result.success(
            review(
                purpose = purpose,
                falta = if (withFalta) listOf(falta("a", routine = 1)) else emptyList(),
                changes = listOf(
                    change("c1", FolderOp.SetSets(1, 1, 4), fixes = if (withFalta) "a" else null),
                    change("c2", FolderOp.RemoveExercise(1, 2)),
                ),
            ),
        )
    }

    private fun failure(failure: CoachFailure) = Result.failure<Nothing>(CoachException(failure))

    // ---- Request ----

    @Test
    fun creation_neverAsksTheCoachByItself() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(FolderReviewUiState.Idle, model.uiState.value)
        assertEquals(0, reviews.reviewCalls)
    }

    @Test
    fun request_showsProgressThenTheContentWithEveryChangeChecked() = runTest(dispatcher) {
        given()
        reviews.reviewGate = CompletableDeferred()
        val model = viewModel()
        model.onEvent(FolderReviewEvent.Request)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(FolderReviewUiState.Requesting, model.uiState.value)

        reviews.reviewGate!!.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        val content = model.content()
        assertEquals(setOf("c1", "c2"), content.accepted)
        assertTrue(content.rows.all { it.checked })
        assertEquals("Mensaje", content.review.message)
    }

    @Test
    fun request_sendsTheRouteArgumentsAndIsIgnoredOnceAsked() = runTest(dispatcher) {
        given(ReviewPurpose.Block)
        val model = viewModel(args("block", "plan-1"))
        model.start()

        model.onEvent(FolderReviewEvent.Request)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            Triple<String, ReviewPurpose, String?>("folder-1", ReviewPurpose.Block, "plan-1"),
            reviews.reviewArgs.single(),
        )
    }

    @Test
    fun rows_describeEachChangeWithTheResolvedNames() = runTest(dispatcher) {
        given()
        val model = viewModel()
        model.start()

        val lines = model.content().rows.map { it.line }

        assertEquals(
            listOf(
                ChangeLine(ChangeKind.SetSets, 1, "Torso", "Press banca", listOf("4")),
                ChangeLine(ChangeKind.Remove, 1, "Torso", "Remo", emptyList()),
            ),
            lines,
        )
    }

    @Test
    fun aChangeWhoseExerciseHasNoName_failsInsteadOfInventingOne() = runTest(dispatcher) {
        reviews.reviewResult = Result.success(review(changes = listOf(change("c1", FolderOp.RemoveExercise(1, 9)))))
        val model = viewModel()

        model.start()

        assertEquals(FolderReviewUiState.Failed(PlanFailureKind.Other), model.uiState.value)
    }

    // ---- Toggle and Falta ----

    @Test
    fun toggle_uncheckingAndCheckingAgain() = runTest(dispatcher) {
        given()
        val model = viewModel()
        model.start()

        model.onEvent(FolderReviewEvent.Toggle("c1"))
        assertEquals(setOf("c2"), model.content().accepted)
        assertFalse(model.content().rows.first { it.id == "c1" }.checked)
        model.onEvent(FolderReviewEvent.Toggle("c1"))

        assertEquals(setOf("c1", "c2"), model.content().accepted)
    }

    @Test
    fun toggle_inAConflictGroupLeavesOnlyTheCheckedOne() = runTest(dispatcher) {
        reviews.reviewResult = Result.success(
            review(
                changes = listOf(
                    change("c1", FolderOp.SetSets(1, 1, 4), conflictsWith = listOf("c2")),
                    change("c2", FolderOp.SetSets(1, 1, 5), conflictsWith = listOf("c1")),
                ),
            ),
        )
        val model = viewModel()
        model.start()
        assertEquals(setOf("c1"), model.content().accepted)

        model.onEvent(FolderReviewEvent.Toggle("c2"))

        assertEquals(setOf("c2"), model.content().accepted)
    }

    @Test
    fun falta_isClosedByAnAcceptedChangeAndReopenedWhenItIsRejected() = runTest(dispatcher) {
        given(ReviewPurpose.Block, withFalta = true)
        val model = viewModel(args("block", "plan-1"))
        model.start()
        assertTrue(model.content().falta.isEmpty())

        model.onEvent(FolderReviewEvent.Toggle("c1"))

        assertEquals(listOf("a"), model.content().falta.map { it.falta.code })
        assertEquals("Torso", model.content().falta.single().routineName)
        assertFalse(model.content().canCreateBlock)
    }

    @Test
    fun goodFolder_hasNoFaltaAndNoFooterInReviewPurpose() = runTest(dispatcher) {
        reviews.reviewResult = Result.success(review())
        val model = viewModel()
        model.start()

        assertTrue(model.content().allFalta.isEmpty())
        assertFalse(model.content().showsFooter)
    }

    // ---- Review purpose: apply ----

    @Test
    fun apply_sendsOnlyTheCheckedIdsInReviewOrderAndEmitsApplied() = runTest(dispatcher) {
        given()
        val model = viewModel()
        model.start()
        model.onEvent(FolderReviewEvent.Toggle("c1"))
        model.onEvent(FolderReviewEvent.Toggle("c1"))
        model.onEvent(FolderReviewEvent.Toggle("c1"))
        model.onEvent(FolderReviewEvent.Toggle("c2"))
        model.onEvent(FolderReviewEvent.Toggle("c2"))

        model.effects.test {
            model.onEvent(FolderReviewEvent.Apply)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(FolderReviewEffect.Applied("copy-1", "Mi carpeta (copia)"), awaitItem())
        }

        assertEquals(listOf("rev-1" to listOf("c2")), reviews.applied)
        assertEquals(0, reviews.blocks.size)
    }

    @Test
    fun apply_withNothingCheckedDoesNothing() = runTest(dispatcher) {
        given()
        val model = viewModel()
        model.start()
        model.onEvent(FolderReviewEvent.Toggle("c1"))
        model.onEvent(FolderReviewEvent.Toggle("c2"))
        assertFalse(model.content().canApply)

        model.onEvent(FolderReviewEvent.Apply)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(reviews.applied.isEmpty())
    }

    @Test
    fun apply_conflictAsksToReviewAgain() = runTest(dispatcher) {
        given()
        reviews.applyResult = failure(CoachFailure.Conflict)
        val model = viewModel()
        model.start()

        model.onEvent(FolderReviewEvent.Apply)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(FolderReviewUiState.Failed(PlanFailureKind.Other, changed = true), model.uiState.value)
    }

    @Test
    fun apply_invalidReplacementIsTreatedAsAChangedFolder() = runTest(dispatcher) {
        given()
        reviews.applyResult = failure(CoachFailure.InvalidReplacement)
        val model = viewModel()
        model.start()

        model.onEvent(FolderReviewEvent.Apply)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(FolderReviewUiState.Failed(PlanFailureKind.Other, changed = true), model.uiState.value)
    }

    @Test
    fun retryAfterAConflict_reviewsTheFolderAgain() = runTest(dispatcher) {
        given()
        reviews.applyResult = failure(CoachFailure.Conflict)
        val model = viewModel()
        model.start()
        model.onEvent(FolderReviewEvent.Apply)
        dispatcher.scheduler.advanceUntilIdle()

        model.onEvent(FolderReviewEvent.Retry)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, reviews.reviewCalls)
        model.content()
    }

    @Test
    fun apply_offlineFailureKeepsTheContentAndEmitsActionFailed() = runTest(dispatcher) {
        given()
        reviews.applyResult = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        model.start()

        model.effects.test {
            model.onEvent(FolderReviewEvent.Apply)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(FolderReviewEffect.ActionFailed(offline = true), awaitItem())
        }

        assertFalse(model.content().working)
    }

    // ---- Block purpose ----

    @Test
    fun continue_isIgnoredWhileAFaltaIsOpen() = runTest(dispatcher) {
        given(ReviewPurpose.Block, withFalta = true)
        val model = viewModel(args("block", "plan-1"))
        model.start()
        model.onEvent(FolderReviewEvent.Toggle("c1"))

        model.onEvent(FolderReviewEvent.Continue)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(reviews.blocks.isEmpty())
    }

    @Test
    fun continue_buildsTheBlockWithTheUtcDateAndEmitsBlockReady() = runTest(dispatcher) {
        given(ReviewPurpose.Block, withFalta = true)
        val model = viewModel(args("block", "plan-1"))
        model.start()

        model.effects.test {
            model.onEvent(FolderReviewEvent.Continue)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(FolderReviewEffect.BlockReady, awaitItem())
        }

        assertEquals(listOf(listOf("c1", "c2")), reviews.blocks)
        assertEquals(listOf("plan-1"), reviews.blockPlans)
        assertEquals(listOf(LocalDate(2026, 10, 19)), reviews.blockDates)
    }

    @Test
    fun continue_needsChangesShowsTheServersFaltaAndEmitsNothing() = runTest(dispatcher) {
        given(ReviewPurpose.Block)
        reviews.blockResult = failure(CoachFailure.NeedsChanges(listOf(falta("a", routine = 2))))
        val model = viewModel(args("block", "plan-1"))
        model.start()

        model.effects.test {
            model.onEvent(FolderReviewEvent.Continue)
            dispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }

        val content = model.content()
        assertEquals(listOf("a"), content.blockedFalta.map { it.falta.code })
        assertEquals("Pierna", content.blockedFalta.single().routineName)
        assertFalse(content.working)
    }

    @Test
    fun toggle_clearsTheBlockedFaltaOfAPreviousAttempt() = runTest(dispatcher) {
        given(ReviewPurpose.Block)
        reviews.blockResult = failure(CoachFailure.NeedsChanges(listOf(falta("a"))))
        val model = viewModel(args("block", "plan-1"))
        model.start()
        model.onEvent(FolderReviewEvent.Continue)
        dispatcher.scheduler.advanceUntilIdle()

        model.onEvent(FolderReviewEvent.Toggle("c1"))

        assertTrue(model.content().blockedFalta.isEmpty())
    }

    // ---- Failures ----

    @Test
    fun limitReached_showsTheLimitKindWithoutAskingAgain() = runTest(dispatcher) {
        reviews.reviewResult = failure(CoachFailure.LimitReached(CoachFailure.Scope.Global))
        val model = viewModel()

        model.start()

        assertEquals(
            FolderReviewUiState.Failed(PlanFailureKind.Limit(CoachFailure.Scope.Global)),
            model.uiState.value,
        )
        assertEquals(1, reviews.reviewCalls)
    }

    @Test
    fun offlineAndTimeout_mapToTheirKinds() = runTest(dispatcher) {
        reviews.reviewResult = Result.failure(AppException(AppError.Network))
        val offline = viewModel()
        offline.start()
        assertEquals(FolderReviewUiState.Failed(PlanFailureKind.Offline), offline.uiState.value)

        reviews.reviewResult = failure(CoachFailure.Timeout)
        val timeout = viewModel()
        timeout.start()
        assertEquals(FolderReviewUiState.Failed(PlanFailureKind.Timeout), timeout.uiState.value)
    }

    @Test
    fun retry_asksOnceMoreAndRecovers() = runTest(dispatcher) {
        reviews.reviewResult = failure(CoachFailure.Timeout)
        val model = viewModel()
        model.start()
        given()

        model.onEvent(FolderReviewEvent.Retry)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, reviews.reviewCalls)
        model.content()
    }

    @Test
    fun leaving_writesNothing() = runTest(dispatcher) {
        given()
        val model = viewModel()
        model.start()
        model.onEvent(FolderReviewEvent.Toggle("c1"))

        assertTrue(reviews.applied.isEmpty())
        assertTrue(reviews.blocks.isEmpty())
        assertEquals(1, reviews.reviewCalls)
    }

    // ---- Recreation (ADR-40) ----

    @Test
    fun recreated_readsTheStoredReviewKeepsTheCheckedSetAndDoesNotAskAgain() = runTest(dispatcher) {
        given()
        val saved = args()
        val first = viewModel(saved)
        first.start()
        first.onEvent(FolderReviewEvent.Toggle("c1"))
        reviews.storedResult = reviews.reviewResult.map { it }

        val second = viewModel(saved)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf("c2"), second.content().accepted)
        assertEquals(1, reviews.reviewCalls)
        assertEquals(listOf("rev-1"), reviews.storedReads)
    }

    @Test
    fun recreatedWhileAsking_withNothingStoredIsInterruptedAndNeverAsksByItself() = runTest(dispatcher) {
        reviews.reviewGate = CompletableDeferred()
        val saved = args()
        val first = viewModel(saved)
        first.onEvent(FolderReviewEvent.Request)
        dispatcher.scheduler.advanceUntilIdle()

        val second = viewModel(saved)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(FolderReviewUiState.Failed(PlanFailureKind.Interrupted), second.uiState.value)
        assertEquals(1, reviews.reviewCalls)
    }

    @Test
    fun recreated_doesNotRepeatTheApply() = runTest(dispatcher) {
        given()
        val saved = args()
        val first = viewModel(saved)
        first.start()
        first.onEvent(FolderReviewEvent.Apply)
        dispatcher.scheduler.advanceUntilIdle()
        reviews.storedResult = reviews.reviewResult.map { it }

        viewModel(saved)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, reviews.applied.size)
    }
}
