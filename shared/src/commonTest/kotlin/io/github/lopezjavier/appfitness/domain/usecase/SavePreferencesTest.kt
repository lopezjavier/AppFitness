package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.Biometrics
import io.github.lopezjavier.appfitness.domain.model.CalculatorSettings
import io.github.lopezjavier.appfitness.domain.model.FoodPreferences
import io.github.lopezjavier.appfitness.domain.model.PreviousValuesMode
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.model.WeightEntry
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@Suppress("LongParameterList") // Mirrors the repository call it records.
private class Saved(
    val name: String,
    val biometrics: Biometrics,
    val weight: WeightEntry?,
    val warmUpIncluded: Boolean?,
    val previousValuesMode: PreviousValuesMode?,
    val calculators: CalculatorSettings?,
    val foodPreferences: FoodPreferences? = null,
    val autoUpdateRoutine: Boolean? = null,
)

class SavePreferencesTest {
    private val zone = TimeZone.of("Europe/Madrid")

    // 2026-10-19 12:00 Madrid (UTC+2) = 10:00Z; the local day is 2026-10-18T22:00Z .. 2026-10-19T22:00Z.
    private val now = Instant.parse("2026-10-19T10:00:00Z")
    private val birthDate = LocalDate(2000, 10, 19)
    private val saved = mutableListOf<Saved>()
    private var result: Result<Unit> = Result.success(Unit)

    private val repository = object : ProfileRepository {
        override suspend fun getOwnProfile(): Result<ProfileSummary> = error("unused")

        override suspend fun getBiometrics(): Result<SavedBiometrics> = error("unused")

        override suspend fun updateBiometrics(biometrics: Biometrics): Result<Unit> = error("unused")

        override suspend fun markOnboardingCompleted(): Result<Unit> = error("unused")

        override suspend fun updateDisplayName(name: String): Result<Unit> = error("unused")

        override fun hasProvidedFullName(): Boolean = error("unused")

        override suspend fun savePreferences(
            displayName: String,
            biometrics: Biometrics,
            weight: WeightEntry?,
            warmUpIncluded: Boolean?,
            previousValuesMode: PreviousValuesMode?,
            calculators: CalculatorSettings?,
            foodPreferences: FoodPreferences?,
            autoUpdateRoutine: Boolean?,
        ): Result<Unit> {
            saved += Saved(
                displayName,
                biometrics,
                weight,
                warmUpIncluded,
                previousValuesMode,
                calculators,
                foodPreferences,
                autoUpdateRoutine,
            )
            return result
        }
    }
    private val useCase = SavePreferences(repository)

    private suspend fun save(
        name: String = "Ana",
        sex: Sex = Sex.Male,
        height: Int = 180,
        weightKg: Double = 100.0,
        weightChanged: Boolean = false,
        warmUpIncluded: Boolean? = false,
        previousValuesMode: PreviousValuesMode? = PreviousValuesMode.Overall,
        calculators: CalculatorSettings? = null,
        foodPreferences: FoodPreferences? = null,
        autoUpdateRoutine: Boolean? = null,
    ) = useCase(
        displayName = name,
        biometrics = Biometrics(sex, birthDate, height, ActivityLevel.Sedentary),
        weightKg = weightKg,
        weightChanged = weightChanged,
        now = now,
        timeZone = zone,
        warmUpIncluded = warmUpIncluded,
        previousValuesMode = previousValuesMode,
        calculators = calculators,
        foodPreferences = foodPreferences,
        autoUpdateRoutine = autoUpdateRoutine,
    )

    @Test
    fun passesTheAutoUpdateRoutineSettingToTheRepository_andNullWhenNotCarried() = runTest {
        save(autoUpdateRoutine = true)
        save()

        assertEquals(listOf<Boolean?>(true, null), saved.map { it.autoUpdateRoutine })
    }

    @Test
    fun forwardsNullWarmUpAndPreviousValuesModeSoTheyStayUntouched() = runTest {
        save(warmUpIncluded = null, previousValuesMode = null)

        assertEquals(null, saved.single().warmUpIncluded)
        assertEquals(null, saved.single().previousValuesMode)
    }

    @Test
    fun unchangedWeight_isNotSent() = runTest {
        save(weightChanged = false)

        assertNull(saved.single().weight)
    }

    @Test
    fun changedWeight_isSentWithTheLocalDayBounds() = runTest {
        save(weightKg = 81.5, weightChanged = true)

        assertEquals(
            WeightEntry(
                weightKg = 81.5,
                measuredAt = now,
                dayStart = Instant.parse("2026-10-18T22:00:00Z"),
                dayEnd = Instant.parse("2026-10-19T22:00:00Z"),
            ),
            saved.single().weight,
        )
    }

    @Test
    fun nameIsTrimmed() = runTest {
        save(name = "  Ana López  ")

        assertEquals("Ana López", saved.single().name)
    }

    @Test
    fun blankName_isRejectedWithoutCallingTheRepository() = runTest {
        assertEquals(AppError.Validation, save(name = "   ").appErrorOrNull())
        assertTrue(saved.isEmpty())
    }

    @Test
    fun nameLimit_is100CharactersAfterTrimming() = runTest {
        assertTrue(save(name = "a".repeat(100)).isSuccess)
        assertEquals(AppError.Validation, save(name = "a".repeat(101)).appErrorOrNull())
        assertEquals(1, saved.size)
    }

    @Test
    fun weightBounds_are20To400() = runTest {
        assertEquals(AppError.Validation, save(weightKg = 19.9, weightChanged = true).appErrorOrNull())
        assertTrue(save(weightKg = 20.0, weightChanged = true).isSuccess)
        assertTrue(save(weightKg = 400.0, weightChanged = true).isSuccess)
        assertEquals(AppError.Validation, save(weightKg = 400.1, weightChanged = true).appErrorOrNull())
        assertEquals(2, saved.size)
    }

    @Test
    fun theWarmUpFlagIsPassedThrough() = runTest {
        save(warmUpIncluded = true)
        save()

        assertEquals(listOf(true, false), saved.map { it.warmUpIncluded })
    }

    @Test
    fun thePreviousValuesModeIsPassedThrough() = runTest {
        save(previousValuesMode = PreviousValuesMode.Routine)
        save()

        assertEquals(
            listOf(PreviousValuesMode.Routine, PreviousValuesMode.Overall),
            saved.map { it.previousValuesMode },
        )
    }

    @Test
    fun theCalculatorSettingsArePassedThrough_andNullMeansUnchanged() = runTest {
        val settings = CalculatorSettings(7.5, listOf(20.0, 2.5), 1.0)
        save(calculators = settings)
        save()

        assertEquals(listOf(settings, null), saved.map { it.calculators })
    }

    @Test
    fun theFoodPreferencesArePassedThrough_andNullMeansUnchanged() = runTest {
        val prefs = FoodPreferences(10.0, 0.0, 20.0)
        save(foodPreferences = prefs)
        save()

        assertEquals(listOf(prefs, null), saved.map { it.foodPreferences })
    }

    @Test
    fun fatPercentOutOfRange_isRejectedAndNothingIsSaved() = runTest {
        val result = save(foodPreferences = FoodPreferences(fatPctFried = 20.1))

        assertEquals(AppError.Validation, result.appErrorOrNull())
        assertTrue(saved.isEmpty())
    }

    @Test
    fun repositoryFailure_propagates() = runTest {
        result = Result.failure(AppException(AppError.Network))

        assertEquals(AppError.Network, save().appErrorOrNull())
        assertEquals(1, saved.size)
    }
}
