package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BlockPause
import io.github.lopezjavier.appfitness.domain.model.CalorieProposal
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.CoachChange
import io.github.lopezjavier.appfitness.domain.model.CoachChangeStatus
import io.github.lopezjavier.appfitness.domain.model.CoachDerivedValues
import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.model.DietBreakApplication
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.repository.CoachBlockRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachChangesRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachPlanRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachWeeksRepository
import io.github.lopezjavier.appfitness.domain.repository.NutritionGoalsRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
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

private const val LATENCY_MS = 100L

/**
 * T-270: the Trainer root reads its hub in parallel. Every repository call takes [LATENCY_MS] of virtual time, so the
 * elapsed time of the first open is the sequential depth of the reads.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrainerFetchDepthTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeCoachRepository()
    private val plans = FakeCoachPlanRepository()
    private val blocks = FakeCoachBlockRepository()
    private val weeks = FakeCoachWeeksRepository()
    private val changes = FakeCoachChangesRepository()
    private val goals = FakeNutritionGoalsRepository()
    private var cancelDerivedValues = false

    /** Call name to the virtual time it started, in start order. */
    private val starts = mutableListOf<Pair<String, Long>>()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.timed(name: String) {
        starts += name to currentTime
    }

    private suspend fun TestScope.slow(name: String) {
        timed(name)
        delay(LATENCY_MS)
    }

    private fun TestScope.viewModel() = TrainerViewModel(
        repository = object : CoachRepository by repository {
            override suspend fun derivedValues(today: LocalDate): Result<CoachDerivedValues> {
                slow("derivedValues")
                if (cancelDerivedValues) throw CancellationException("cancelled")
                return repository.derivedValues(today)
            }
        },
        clock = MutableClock(),
        plans = object : CoachPlanRepository by plans {
            override suspend fun currentPlan(): Result<CoachPlan?> {
                slow("currentPlan")
                return plans.currentPlan()
            }

            override suspend fun applyDietBreak(today: LocalDate): Result<DietBreakApplication> {
                slow("applyDietBreak")
                return plans.applyDietBreak(today).also { starts += "applyDietBreak.end" to currentTime }
            }

            override suspend fun pendingCalorieProposal(today: LocalDate): Result<CalorieProposal?> {
                slow("pendingCalorieProposal")
                return plans.pendingCalorieProposal(today)
            }
        },
        blocks = object : CoachBlockRepository by blocks {
            override suspend fun currentBlock(planId: String): Result<CoachBlock?> {
                slow("currentBlock")
                return blocks.currentBlock(planId)
            }
        },
        coachWeeks = object : CoachWeeksRepository by weeks {
            override suspend fun evaluateDeload(today: LocalDate): Result<CoachChange?> {
                slow("evaluateDeload")
                return weeks.evaluateDeload(today)
            }

            override suspend fun weeks(planId: String, today: LocalDate): Result<List<PlanWeek>> {
                slow("weeks")
                return weeks.weeks(planId, today)
            }

            override suspend fun blockPause(planId: String, today: LocalDate): Result<BlockPause> {
                slow("blockPause")
                return weeks.blockPause(planId, today)
            }
        },
        goals = object : NutritionGoalsRepository by goals {
            override suspend fun getOwn(): Result<NutritionGoals?> {
                timed("getOwn")
                return goals.getOwn()
            }
        },
        coachChanges = object : CoachChangesRepository by changes {
            override suspend fun list(planId: String?, statuses: Set<CoachChangeStatus>): Result<List<CoachChange>> {
                slow("changes")
                return changes.list(planId, statuses)
            }
        },
    )

    private fun openSavedBlock() {
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        blocks.current = Result.success(coachBlock(saved = true))
    }

    private fun startOf(name: String) = starts.first { it.first == name }.second

    @Test
    fun firstOpen_withASavedBlock_readsEachSourceOnceAndTakesOnlyTheDependentChain() = runTest(dispatcher) {
        openSavedBlock()
        val model = viewModel()

        advanceUntilIdle()

        assertIs<TrainerUiState.Content>(model.uiState.value)
        assertEquals(
            listOf(
                "applyDietBreak.end",
                "currentPlan",
                "derivedValues",
                "applyDietBreak",
                "currentBlock",
                "pendingCalorieProposal",
                "evaluateDeload",
                "weeks",
                "blockPause",
                "changes",
            ).sorted(),
            starts.map { it.first }.sorted(),
        )
        // plan, (block | diet break + proposal), deload rule, (weeks | pause | changes): four sequential steps.
        assertEquals(4 * LATENCY_MS, currentTime)
    }

    @Test
    fun independentReads_startTogetherWithTheFirstPlanRead() = runTest(dispatcher) {
        openSavedBlock()
        viewModel()

        advanceUntilIdle()

        assertEquals(startOf("currentPlan"), startOf("derivedValues"))
    }

    @Test
    fun planDependentReads_keepTheirOrder() = runTest(dispatcher) {
        openSavedBlock()
        viewModel()

        advanceUntilIdle()

        val plan = startOf("currentPlan")
        assertTrue(plan + LATENCY_MS <= startOf("applyDietBreak"))
        assertTrue(startOf("applyDietBreak") + LATENCY_MS <= startOf("pendingCalorieProposal"))
        assertTrue(plan + LATENCY_MS <= startOf("currentBlock"))
        val deload = startOf("evaluateDeload")
        assertTrue(startOf("currentBlock") + LATENCY_MS <= deload)
        for (name in listOf("weeks", "blockPause", "changes")) {
            assertTrue(deload + LATENCY_MS <= startOf(name), "$name must start after the deload rule")
        }
    }

    @Test
    fun withoutAPlan_noPlanDependentReadHappens() = runTest(dispatcher) {
        viewModel()

        advanceUntilIdle()

        assertEquals(listOf("currentPlan", "derivedValues"), starts.map { it.first }.sorted())
    }

    @Test
    fun aFailingIndependentRead_doesNotCancelItsSiblings() = runTest(dispatcher) {
        openSavedBlock()
        repository.loaded = Result.failure(AppException(AppError.Network))
        weeks.pauseResult = Result.failure(AppException(AppError.Network))
        val model = viewModel()

        advanceUntilIdle()

        val content = assertIs<TrainerUiState.Content>(model.uiState.value)
        assertEquals(null, content.provisionalCount)
        assertEquals(null, content.pause)
        assertEquals(BlockSummary.Saved(coachBlock(saved = true)), content.block)
        assertTrue(starts.any { it.first == "changes" })
    }

    @Test
    fun goalsChecklistRead_startsOnlyAfterTheDietBreakWasApplied() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted, editedByUser = true))
        blocks.current = Result.success(coachBlock(saved = true))
        viewModel()

        advanceUntilIdle()

        assertTrue(startOf("applyDietBreak.end") <= startOf("getOwn"))
    }

    @Test
    fun cancellationInARead_propagatesInsteadOfShowingContent() = runTest(dispatcher) {
        openSavedBlock()
        cancelDerivedValues = true
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(TrainerUiState.Loading, model.uiState.value)
    }
}
