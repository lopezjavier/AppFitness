package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.data.repository.UnconfiguredCoachBlockRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredCoachPlanRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredCoachReviewRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredDiscomfortRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredProfileRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BlockAdherence
import io.github.lopezjavier.appfitness.domain.model.BlockExercise
import io.github.lopezjavier.appfitness.domain.model.BlockLift
import io.github.lopezjavier.appfitness.domain.model.BlockReview
import io.github.lopezjavier.appfitness.domain.model.BlockSession
import io.github.lopezjavier.appfitness.domain.model.BlockTraining
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.model.Discomfort
import io.github.lopezjavier.appfitness.domain.model.DiscomfortLevel
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.GroupSets
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseChangeReason
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.model.PhaseProposal
import io.github.lopezjavier.appfitness.domain.model.PlanNutrition
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.StallCause
import io.github.lopezjavier.appfitness.domain.model.StallDiagnosis
import io.github.lopezjavier.appfitness.domain.repository.CoachBlockRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachPlanRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachReviewRepository
import io.github.lopezjavier.appfitness.domain.repository.DiscomfortRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class LoadNextBlockContextUseCaseTest {
    // FixedTestClock.today() is 2026-10-22.
    private val now = Instant.parse("2026-10-22T08:00:00Z")

    private fun exercise(id: String, group: String, role: String = "main") = BlockExercise(
        exerciseId = id,
        exerciseKey = id,
        role = role,
        muscleGroup = group,
        sets = 3,
        repMin = 8,
        repMax = 12,
        restSeconds = 120,
        reason = "",
    )

    private val block = CoachBlock(
        planId = "p1",
        training = BlockTraining(
            daysPerWeek = 2,
            daysChosen = false,
            sessionMinutes = 60,
            repScheme = "",
            blockWeeks = 4,
            equipment = emptyList(),
            weeklySets = mapOf("chest" to 12, "back" to 10),
            sessionsPerMuscle = emptyMap(),
        ),
        sessions = listOf(
            BlockSession(
                1,
                1,
                "Torso A",
                listOf("chest", "back"),
                listOf(exercise("chest-main", "chest"), exercise("back-main", "back")),
                "r1",
            ),
        ),
        saved = true,
    )

    private fun review(
        planId: String = "p1",
        pct: Int? = 90,
        lifts: List<BlockLift> = emptyList(),
        stalls: List<StallDiagnosis> = emptyList(),
        proposal: PhaseProposal? = null,
        decision: PhaseDecision? = null,
        groups: List<GroupSets> = emptyList(),
    ) = BlockReview(
        id = "rev",
        planId = planId,
        endedOn = LocalDate(2026, 10, 21),
        adherence = BlockAdherence(14, 16, pct),
        lifts = lifts,
        groups = groups,
        weight = null,
        outcomes = emptyList(),
        waist = null,
        stalls = stalls,
        phaseProposal = proposal,
        phaseDecision = decision,
    )

    private fun lift(id: String, stalled: Boolean) = BlockLift(id, emptyList(), null, null, null, stalled)

    private fun plan(phase: Phase) = CoachPlan(
        id = "p2",
        blockStart = LocalDate(2026, 10, 22),
        blockWeeks = 4,
        intention = "muscle",
        status = PlanStatus.Proposed,
        nutrition = PlanNutrition(
            targetKcal = 2500, floorKcal = null, limitedBy = null, expenditureKcal = null, ratePctWeek = 0.0,
            rateKgWeek = 0.0, strategy = GoalStrategy.Maintenance, proteinG = 150, carbsG = 300, fatG = 70,
            proteinPct = 25, carbsPct = 50, fatPct = 25, distribution = null, editedByUser = false, phase = phase,
        ),
        outcomes = emptyList(),
        acceptedAt = null,
    )

    private fun discomfort(exerciseId: String, loggedAt: Instant, resolvedAt: Instant? = null) =
        Discomfort("d-$exerciseId", exerciseId, loggedAt, DiscomfortLevel.Mild, null, resolvedAt)

    private fun useCase(
        review: Result<BlockReview?> = Result.success(review()),
        current: Result<CoachBlock?> = Result.success(block),
        plan: Result<CoachPlan?> = Result.success(plan(Phase.Maintenance)),
        birthDate: LocalDate? = LocalDate(1990, 5, 1),
        discomforts: List<Discomfort> = emptyList(),
    ): LoadNextBlockContextUseCase {
        val reviews = object : CoachReviewRepository by UnconfiguredCoachReviewRepository() {
            override suspend fun openReview(today: LocalDate) = review
        }
        val blocks = object : CoachBlockRepository by UnconfiguredCoachBlockRepository() {
            override suspend fun currentBlock(planId: String) = current
        }
        val plans = object : CoachPlanRepository by UnconfiguredCoachPlanRepository() {
            override suspend fun currentPlan() = plan
        }
        val profile = object : ProfileRepository by UnconfiguredProfileRepository() {
            override suspend fun getBiometrics() = Result.success(SavedBiometrics(birthDate = birthDate))
        }
        val repo = object : DiscomfortRepository by UnconfiguredDiscomfortRepository() {
            override suspend fun unresolved(exerciseIds: Set<String>) =
                Result.success(discomforts.filter { it.exerciseId in exerciseIds })
        }
        return LoadNextBlockContextUseCase(reviews, blocks, plans, profile, repo, FixedTestClock(now))
    }

    @Test
    fun `reads adherence, base sets and age`() = runTest {
        val context = useCase().invoke("p1").getOrThrow()

        assertEquals(90, context.adherencePct)
        assertEquals(12, context.baseSets.getValue(MuscleGroup.Chest))
        assertEquals(10, context.baseSets.getValue(MuscleGroup.Quads))
        assertEquals(36, context.age)
        assertEquals(Phase.Maintenance, context.nextPhase)
        assertFalse(context.cut)
        assertEquals(setOf("chest-main", "back-main"), context.mainLiftIds)
    }

    @Test
    fun `a group the block does not hold falls back to what the review measured`() = runTest {
        val measured = review(groups = listOf(GroupSets(MuscleGroup.Quads, 13.6)))

        val context = useCase(review = Result.success(measured)).invoke("p1").getOrThrow()

        assertEquals(14, context.baseSets.getValue(MuscleGroup.Quads))
    }

    @Test
    fun `stalled lifts and diagnosed stalls give the stalled groups`() = runTest {
        val stalled = review(
            lifts = listOf(lift("chest-main", stalled = true), lift("back-main", stalled = false)),
            stalls = listOf(StallDiagnosis("back-main", StallCause.Sessions(1.0, 2))),
        )

        val context = useCase(review = Result.success(stalled)).invoke("p1").getOrThrow()

        assertEquals(setOf("chest-main"), context.stalledLifts)
        assertEquals(setOf(MuscleGroup.Chest, MuscleGroup.Back), context.stalledGroups)
    }

    @Test
    fun `an open discomfort marks its group, a resolved or expired one does not`() = runTest {
        val rows = listOf(
            discomfort("chest-main", loggedAt = Instant.parse("2026-10-20T08:00:00Z")),
            discomfort("back-main", loggedAt = Instant.parse("2026-10-10T08:00:00Z")),
        )

        val context = useCase(discomforts = rows).invoke("p1").getOrThrow()

        assertEquals(setOf(MuscleGroup.Chest), context.discomfortGroups)
    }

    @Test
    fun `an accepted phase proposal sets the next phase`() = runTest {
        val accepted = review(
            proposal = PhaseProposal(Phase.Maintenance, PhaseChangeReason.CutLength, emptyMap()),
            decision = PhaseDecision.Accepted,
        )

        val context = useCase(review = Result.success(accepted), plan = Result.success(plan(Phase.Cut)))
            .invoke("p1").getOrThrow()

        assertEquals(Phase.Maintenance, context.nextPhase)
    }

    @Test
    fun `a kept phase uses the phase of the current plan, and a cut turns cut on`() = runTest {
        val kept = review(
            proposal = PhaseProposal(Phase.Maintenance, PhaseChangeReason.CutLength, emptyMap()),
            decision = PhaseDecision.Kept,
        )

        val context = useCase(review = Result.success(kept), plan = Result.success(plan(Phase.Cut)))
            .invoke("p1").getOrThrow()

        assertTrue(context.cut)
    }

    @Test
    fun `a suggestion needs the stalled group, high adherence, no discomfort and no cut`() = runTest {
        val stalled = review(lifts = listOf(lift("chest-main", stalled = true)))
        val context = useCase(review = Result.success(stalled)).invoke("p1").getOrThrow()

        assertTrue(context.suggestion(MuscleGroup.Chest, 12))
        assertFalse(context.suggestion(MuscleGroup.Back, 10))
        assertFalse(context.suggestion(MuscleGroup.Chest, 19))
    }

    @Test
    fun `a review of another plan or none is a conflict`() = runTest {
        val other = useCase(review = Result.success(review(planId = "other"))).invoke("p1")
        val none = useCase(review = Result.success(null)).invoke("p1")

        assertEquals(CoachFailure.Conflict, (other.exceptionOrNull() as CoachException).failure)
        assertIs<CoachException>(none.exceptionOrNull())
    }

    @Test
    fun `a repository failure is passed through`() = runTest {
        val result = useCase(review = Result.failure(AppException(AppError.Network))).invoke("p1")

        assertEquals(AppError.Network, (result.exceptionOrNull() as AppException).error)
    }
}
