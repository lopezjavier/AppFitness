package io.github.lopezjavier.appfitness.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeBodyMeasurementRepository
import io.github.lopezjavier.appfitness.app.FakeClock
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.app.FakeProfileRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.Biometrics
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.usecase.CompleteOnboarding
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val BIRTH_DATE = LocalDate(1999, 6, 15)
private val SAVED_AT = Instant.parse("2026-10-13T08:00:00Z")

/** US-06 onboarding flow: steps, bounds, resume, save, retry, offline and process-death restore. */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One method per Gherkin scenario and rule.
class OnboardingViewModelTest {
    private val profile = FakeProfileRepository(Result.success(ProfileSummary("Ana", null, false)))
    private val weights = FakeBodyMeasurementRepository()
    private val goals = FakeNutritionGoalsRepository()
    private val connectivity = FakeConnectivityObserver(initial = true)
    private val clock = FakeClock()
    private val handle = SavedStateHandle()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(savedState: SavedStateHandle = handle) = OnboardingViewModel(
        savedState,
        profile,
        weights,
        goals,
        CompleteOnboarding(goals, profile),
        connectivity,
        clock,
    )

    private suspend fun TestScope.started(savedState: SavedStateHandle = handle): OnboardingViewModel =
        viewModel(savedState).also { advanceUntilIdle() }

    private fun OnboardingViewModel.send(vararg events: OnboardingEvent) = events.forEach(::onEvent)

    private val OnboardingViewModel.state get() = uiState.value

    /** Walks a fresh flow to step 3 with the Gherkin values: male, 15/06/1999, 178 cm, 74.2 kg, moderate. */
    private fun OnboardingViewModel.fillToActivity() {
        send(
            OnboardingEvent.SelectSex(Sex.Male),
            OnboardingEvent.SetBirthDate(BIRTH_DATE),
            OnboardingEvent.Continue,
            OnboardingEvent.SetHeight(178),
            OnboardingEvent.SetWeight(742),
            OnboardingEvent.Continue,
            OnboardingEvent.SelectActivity(ActivityLevel.Moderate),
        )
    }

    @Test
    fun newUser_startsOnStepOneWithDefaultsAndNothingSelected() = runTest {
        val vm = started()

        assertEquals(OnboardingPhase.Content, vm.state.phase)
        assertEquals(OnboardingStep.AboutYou, vm.state.step)
        assertNull(vm.state.sex)
        assertNull(vm.state.activity)
        assertEquals(LocalDate(1996, 10, 14), vm.state.birthDate)
        assertEquals(170, vm.state.heightCm)
        assertEquals(700, vm.state.weightTenths)
    }

    @Test
    fun completingOnboarding_savesBothRowsOnceAndShowsTheGoalsStep() = runTest {
        val vm = started()
        vm.fillToActivity()
        assertEquals(OnboardingStep.Activity, vm.state.step)

        vm.onEvent(OnboardingEvent.Continue)
        advanceUntilIdle()

        assertEquals(listOf(Biometrics(Sex.Male, BIRTH_DATE, 178, ActivityLevel.Moderate)), profile.biometricsUpdates)
        assertEquals(listOf(74.2 to clock.now()), weights.inserts)
        assertTrue(weights.updates.isEmpty())
        assertEquals(OnboardingStep.Goals, vm.state.step)
        assertEquals(OnboardingPhase.Content, vm.state.phase)
        assertNull(vm.state.saveError)
    }

    @Test
    fun indicatorValues_followTheSteps_andBackKeepsTheValues() = runTest {
        val vm = started()
        val seen = mutableListOf(vm.state.step.number)
        vm.send(
            OnboardingEvent.SelectSex(Sex.Female),
            OnboardingEvent.SetBirthDate(BIRTH_DATE),
            OnboardingEvent.Continue,
        )
        seen += vm.state.step.number
        assertEquals(OnboardingStep.Measures, vm.state.step)
        vm.send(OnboardingEvent.SetHeight(178), OnboardingEvent.SetWeight(705), OnboardingEvent.Continue)
        seen += vm.state.step.number
        assertEquals(OnboardingStep.Activity, vm.state.step)

        vm.onEvent(OnboardingEvent.Back)

        assertEquals(listOf(1, 2, 3), seen)
        assertEquals(OnboardingStep.Measures, vm.state.step)
        assertEquals(178, vm.state.heightCm)
        assertEquals(705, vm.state.weightTenths)

        vm.onEvent(OnboardingEvent.Back)

        assertEquals(OnboardingStep.AboutYou, vm.state.step)
        assertEquals(BIRTH_DATE, vm.state.birthDate)
        assertEquals(Sex.Female, vm.state.sex)
    }

    @Test
    fun totalSteps_isFour_withActivityThirdAndGoalsFourth() {
        assertEquals(4, OnboardingStep.TOTAL_STEPS)
        assertEquals(OnboardingStep.TOTAL_STEPS, OnboardingStep.entries.size)
        assertEquals(3, OnboardingStep.Activity.number)
        assertEquals(4, OnboardingStep.Goals.number)
    }

    @Test
    fun savedStateWithAFormerStepName_mapsToTheNewGroupWithoutCrashing() = runTest {
        suspend fun restoredStep(name: String): OnboardingStep {
            val saved = SavedStateHandle(mapOf("onboarding_ready" to true, "onboarding_step" to name))
            return started(saved).state.step
        }

        assertEquals(OnboardingStep.AboutYou, restoredStep("Sex"))
        assertEquals(OnboardingStep.AboutYou, restoredStep("BirthDate"))
        assertEquals(OnboardingStep.Measures, restoredStep("Height"))
        assertEquals(OnboardingStep.Measures, restoredStep("Weight"))
        assertEquals(OnboardingStep.Activity, restoredStep("Activity"))
        assertEquals(OnboardingStep.AboutYou, restoredStep("Unknown"))
    }

    @Test
    fun back_onTheFirstStepDoesNothing_andFromGoalsReturnsToActivity() = runTest {
        val vm = started()
        assertFalse(vm.state.canGoBack)
        vm.onEvent(OnboardingEvent.Back)
        assertEquals(OnboardingStep.AboutYou, vm.state.step)

        vm.fillToActivity()
        vm.onEvent(OnboardingEvent.Continue)
        advanceUntilIdle()
        assertEquals(OnboardingStep.Goals, vm.state.step)
        vm.onEvent(OnboardingEvent.Back)

        assertEquals(OnboardingStep.Activity, vm.state.step)
        assertEquals(ActivityLevel.Moderate, vm.state.activity)
    }

    @Test
    fun setters_clampToTheBounds() = runTest {
        val vm = started()

        vm.onEvent(OnboardingEvent.SetHeight(260))
        assertEquals(250, vm.state.heightCm)
        vm.onEvent(OnboardingEvent.SetHeight(99))
        assertEquals(100, vm.state.heightCm)
        vm.onEvent(OnboardingEvent.SetWeight(150))
        assertEquals(200, vm.state.weightTenths)
        vm.onEvent(OnboardingEvent.SetWeight(4001))
        assertEquals(4000, vm.state.weightTenths)
        vm.onEvent(OnboardingEvent.SetBirthDate(LocalDate(2014, 10, 14)))
        assertEquals(LocalDate(2012, 10, 14), vm.state.birthDate)
        vm.onEvent(OnboardingEvent.SetBirthDate(LocalDate(1900, 1, 1)))
        assertEquals(LocalDate(1925, 10, 15), vm.state.birthDate)
    }

    @Test
    fun resume_prefillsEveryStepFromWhatWasSaved() = runTest {
        profile.biometrics =
            Result.success(SavedBiometrics(Sex.Male, BIRTH_DATE, 178, ActivityLevel.Moderate))
        weights.row = BodyMeasurement("row-1", SAVED_AT, 74.2, MeasurementSource.Manual)

        val vm = started()

        assertEquals(OnboardingStep.AboutYou, vm.state.step)
        assertEquals(Sex.Male, vm.state.sex)
        assertEquals(BIRTH_DATE, vm.state.birthDate)
        assertEquals(178, vm.state.heightCm)
        assertEquals(742, vm.state.weightTenths)
        assertEquals(ActivityLevel.Moderate, vm.state.activity)
        assertTrue(vm.state.canContinue)
    }

    @Test
    fun resave_updatesTheExistingWeightRowInsteadOfInsertingAnother() = runTest {
        profile.biometrics = Result.success(SavedBiometrics(Sex.Male, BIRTH_DATE, 178, ActivityLevel.Moderate))
        weights.row = BodyMeasurement("row-1", SAVED_AT, 74.2, MeasurementSource.Manual)
        val vm = started()
        vm.send(
            OnboardingEvent.Continue,
            OnboardingEvent.SetWeight(750),
            OnboardingEvent.Continue,
            OnboardingEvent.Continue,
        )
        advanceUntilIdle()

        assertEquals(OnboardingStep.Goals, vm.state.step)
        assertTrue(weights.inserts.isEmpty())
        assertEquals(listOf(Triple("row-1", 75.0, clock.now())), weights.updates)
    }

    @Test
    fun saveFailure_keepsValuesAndStep_thenContinueAgainSucceeds() = runTest {
        val vm = started()
        vm.fillToActivity()
        profile.updateResult = Result.failure(AppException(AppError.Network))

        vm.onEvent(OnboardingEvent.Continue)
        advanceUntilIdle()

        assertEquals(OnboardingStep.Activity, vm.state.step)
        assertEquals(OnboardingSaveError.Network, vm.state.saveError)
        assertEquals(OnboardingPhase.Content, vm.state.phase)
        assertEquals(178, vm.state.heightCm)
        assertEquals(742, vm.state.weightTenths)
        assertEquals(ActivityLevel.Moderate, vm.state.activity)
        assertTrue(weights.inserts.isEmpty())

        profile.updateResult = Result.success(Unit)
        vm.onEvent(OnboardingEvent.Continue)
        advanceUntilIdle()

        assertEquals(OnboardingStep.Goals, vm.state.step)
        assertNull(vm.state.saveError)
        assertEquals(1, weights.inserts.size)
    }

    @Test
    fun weightSaveFailure_isOther_andContinuingAgainRepeatsBothIdempotentWrites() = runTest {
        val vm = started()
        vm.fillToActivity()
        weights.saveFailure = AppException(AppError.Unknown)

        vm.onEvent(OnboardingEvent.Continue)
        advanceUntilIdle()
        assertEquals(OnboardingSaveError.Other, vm.state.saveError)

        weights.saveFailure = null
        vm.onEvent(OnboardingEvent.Continue)
        advanceUntilIdle()

        assertEquals(2, profile.biometricsUpdates.size)
        assertEquals(OnboardingStep.Goals, vm.state.step)
    }

    @Test
    fun continueIsDisabled_withNothingSelected_whileSaving_andOfflineOnTheSaveStep() = runTest {
        val vm = started()
        assertFalse(vm.state.canContinue)
        vm.onEvent(OnboardingEvent.Continue)
        assertEquals(OnboardingStep.AboutYou, vm.state.step)

        vm.onEvent(OnboardingEvent.SelectSex(Sex.Male))
        vm.send(OnboardingEvent.Continue, OnboardingEvent.Continue)
        assertEquals(OnboardingStep.Activity, vm.state.step)
        assertFalse(vm.state.canContinue)

        vm.onEvent(OnboardingEvent.SelectActivity(ActivityLevel.Light))
        assertTrue(vm.state.canContinue)
        connectivity.state.value = false
        advanceUntilIdle()
        assertFalse(vm.state.canContinue)
        assertTrue(vm.state.showOfflineHint)
        vm.onEvent(OnboardingEvent.Continue)
        assertTrue(profile.biometricsUpdates.isEmpty())

        connectivity.state.value = true
        advanceUntilIdle()
        vm.onEvent(OnboardingEvent.Continue)
        assertEquals(OnboardingPhase.Saving, vm.state.phase)
        assertFalse(vm.state.canContinue)
        vm.onEvent(OnboardingEvent.Continue)
        advanceUntilIdle()
        assertEquals(1, profile.biometricsUpdates.size)
    }

    @Test
    fun stepsOneAndTwo_stayEnabledOffline() = runTest {
        connectivity.state.value = false
        val vm = started()
        vm.onEvent(OnboardingEvent.SelectSex(Sex.Female))

        listOf(OnboardingStep.Measures, OnboardingStep.Activity)
            .forEach { expected ->
                assertTrue(vm.state.canContinue)
                vm.onEvent(OnboardingEvent.Continue)
                assertEquals(expected, vm.state.step)
            }
        assertFalse(vm.state.canContinue)
    }

    @Test
    fun savedStateHandle_restoresStepAndValuesWithoutTouchingTheRepositories() = runTest {
        val first = started()
        first.fillToActivity()
        val readsBefore = profile.biometricsReads

        val restored = started(handle)

        assertEquals(OnboardingStep.Activity, restored.state.step)
        assertEquals(Sex.Male, restored.state.sex)
        assertEquals(BIRTH_DATE, restored.state.birthDate)
        assertEquals(178, restored.state.heightCm)
        assertEquals(742, restored.state.weightTenths)
        assertEquals(ActivityLevel.Moderate, restored.state.activity)
        assertEquals(OnboardingPhase.Content, restored.state.phase)
        assertEquals(readsBefore, profile.biometricsReads)
    }

    @Test
    fun loadFailure_showsLoadError_thenRetryLoadPrefills() = runTest {
        profile.biometrics = Result.failure(AppException(AppError.Network))
        val vm = started()
        assertEquals(OnboardingPhase.LoadError, vm.state.phase)

        profile.biometrics = Result.success(SavedBiometrics(sex = Sex.Female, heightCm = 165))
        vm.onEvent(OnboardingEvent.RetryLoad)
        advanceUntilIdle()

        assertEquals(OnboardingPhase.Content, vm.state.phase)
        assertEquals(Sex.Female, vm.state.sex)
        assertEquals(165, vm.state.heightCm)
    }

    @Test
    fun weightLoadFailure_isAlsoALoadError() = runTest {
        weights.readResult = Result.failure(AppException(AppError.Network))

        val vm = started()

        assertEquals(OnboardingPhase.LoadError, vm.state.phase)
    }

    @Test
    fun valueChanges_areIgnoredWhileSaving() = runTest {
        val vm = started()
        vm.fillToActivity()
        vm.onEvent(OnboardingEvent.Continue)
        assertEquals(OnboardingPhase.Saving, vm.state.phase)

        vm.send(
            OnboardingEvent.SetHeight(200),
            OnboardingEvent.SetWeight(900),
            OnboardingEvent.SelectSex(Sex.Female),
            OnboardingEvent.SelectActivity(ActivityLevel.High),
            OnboardingEvent.SetBirthDate(LocalDate(1980, 1, 1)),
        )
        advanceUntilIdle()

        assertEquals(178, vm.state.heightCm)
        assertEquals(742, vm.state.weightTenths)
        assertEquals(Sex.Male, vm.state.sex)
        assertEquals(ActivityLevel.Moderate, vm.state.activity)
        assertEquals(BIRTH_DATE, vm.state.birthDate)
        assertEquals(Biometrics(Sex.Male, BIRTH_DATE, 178, ActivityLevel.Moderate), profile.biometricsUpdates.single())
    }
}
