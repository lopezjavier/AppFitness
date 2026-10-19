package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.Biometrics
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SaveProfileSettingsTest {
    private val today = LocalDate(2026, 10, 14)
    private val birthDate = LocalDate(1996, 10, 14)
    private val saved = mutableListOf<Pair<Biometrics, NutritionGoals>>()
    private var result: Result<Unit> = Result.success(Unit)

    private val repository = object : ProfileRepository {
        override suspend fun getOwnProfile(): Result<ProfileSummary> = error("unused")

        override suspend fun getBiometrics(): Result<SavedBiometrics> = error("unused")

        override suspend fun updateBiometrics(biometrics: Biometrics): Result<Unit> = error("unused")

        override suspend fun markOnboardingCompleted(): Result<Unit> = error("unused")

        override suspend fun saveSettings(biometrics: Biometrics, goals: NutritionGoals): Result<Unit> {
            saved += biometrics to goals
            return result
        }
    }
    private val useCase = SaveProfileSettings(repository)

    private suspend fun save(
        sex: Sex = Sex.Male,
        height: Int = 180,
        adjustment: Int = -400,
        split: MacroSplit = MacroSplit.DEFAULT,
    ) = useCase(
        biometrics = Biometrics(sex, birthDate, height, ActivityLevel.Moderate),
        weightKg = 80.0,
        today = today,
        strategy = if (adjustment < 0) GoalStrategy.Deficit else GoalStrategy.Maintenance,
        adjustment = adjustment,
        split = split,
    )

    private fun tdee(sex: Sex, height: Int) = CalculateTdee(sex, birthDate, height, 80.0, ActivityLevel.Moderate, today)

    @Test
    fun storesTargetAsTdeePlusAdjustment() = runTest {
        assertTrue(save().isSuccess)

        val goals = saved.single().second
        assertEquals(tdee(Sex.Male, 180) - 400, goals.targetKcal)
        assertEquals(-400, goals.calorieAdjustment)
        assertEquals(GoalStrategy.Deficit, goals.strategy)
    }

    @Test
    fun changingTheHeight_recalculatesTheTdee() = runTest {
        save(height = 178)
        save(height = 180)

        assertEquals(tdee(Sex.Male, 178) - 400, saved[0].second.targetKcal)
        assertEquals(tdee(Sex.Male, 180) - 400, saved[1].second.targetKcal)
        assertEquals(180, saved[1].first.heightCm)
    }

    @Test
    fun sexChange_reFloorsByTheNewSex() = runTest {
        save(sex = Sex.Female, adjustment = -1000)

        assertEquals(maxOf(tdee(Sex.Female, 180) - 1000, 1200), saved.single().second.targetKcal)
    }

    @Test
    fun theSplitIsPassedThrough() = runTest {
        save(split = MacroSplit(35, 42, 23))

        assertEquals(MacroSplit(35, 42, 23), saved.single().second.split)
    }

    @Test
    fun repositoryFailure_propagates() = runTest {
        result = Result.failure(AppException(AppError.Network))

        assertEquals(AppError.Network, save().appErrorOrNull())
        assertEquals(1, saved.size)
    }
}
