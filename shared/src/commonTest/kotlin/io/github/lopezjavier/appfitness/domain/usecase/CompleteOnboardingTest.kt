package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.Biometrics
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.repository.NutritionGoalsRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompleteOnboardingTest {
    private val calls = mutableListOf<String>()
    private var upsertResult: Result<Unit> = Result.success(Unit)
    private var flagResult: Result<Unit> = Result.success(Unit)
    private val goals = NutritionGoals(GoalStrategy.Deficit, -400, 2281)

    private val goalsRepository = object : NutritionGoalsRepository {
        override suspend fun getOwn(): Result<NutritionGoals?> = Result.success(null)

        override suspend fun upsert(goals: NutritionGoals): Result<Unit> {
            calls += "upsert"
            return upsertResult
        }
    }
    private val profileRepository = object : ProfileRepository {
        override suspend fun getOwnProfile(): Result<ProfileSummary> = error("unused")

        override suspend fun getBiometrics(): Result<SavedBiometrics> = error("unused")

        override suspend fun updateBiometrics(biometrics: Biometrics): Result<Unit> = error("unused")

        override suspend fun markOnboardingCompleted(): Result<Unit> {
            calls += "flag"
            return flagResult
        }
    }
    private val completeOnboarding = CompleteOnboarding(goalsRepository, profileRepository)

    @Test
    fun savesTheGoalsThenSetsTheFlag() = runTest {
        assertTrue(completeOnboarding(goals).isSuccess)
        assertEquals(listOf("upsert", "flag"), calls)
    }

    @Test
    fun upsertFailure_leavesTheFlagAlone() = runTest {
        upsertResult = Result.failure(AppException(AppError.Network))

        val result = completeOnboarding(goals)

        assertEquals(AppError.Network, result.appErrorOrNull())
        assertEquals(listOf("upsert"), calls)
    }

    @Test
    fun flagFailure_isReturned() = runTest {
        flagResult = Result.failure(AppException(AppError.Unknown))

        assertEquals(AppError.Unknown, completeOnboarding(goals).appErrorOrNull())
        assertEquals(listOf("upsert", "flag"), calls)
    }
}
