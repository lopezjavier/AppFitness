package io.github.lopezjavier.appfitness.feature.weight

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.app.FakeProfileRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.usecase.Bmi
import io.github.lopezjavier.appfitness.domain.usecase.BodyMeasurementProblem
import io.github.lopezjavier.appfitness.domain.usecase.InterpretMetric
import io.github.lopezjavier.appfitness.domain.usecase.MetricKind
import io.github.lopezjavier.appfitness.domain.usecase.MetricStatus
import io.github.lopezjavier.appfitness.domain.usecase.TargetDistance
import io.github.lopezjavier.appfitness.domain.usecase.TargetDistanceResult
import io.github.lopezjavier.appfitness.domain.usecase.ValidateBodyMeasurement
import io.github.lopezjavier.appfitness.domain.usecase.WaistHipRatio
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One method per Gherkin scenario and rule.
class GoalViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val profile = FakeProfileRepository(Result.success(ProfileSummary("Ana", null, true)))
    private val weights = FakeWeightRepository()
    private val goals = FakeNutritionGoalsRepository()
    private val circumferences = FakeCircumferenceRepository()
    private val connectivity = FakeConnectivityObserver()
    private val savedState = SavedStateHandle()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        profile.biometrics = Result.success(SavedBiometrics(sex = Sex.Male, heightCm = 178))
        profile.targetWeight = Result.success(70.0)
        goals.row = NutritionGoals(GoalStrategy.Deficit, -400, 2000, MacroSplit(30, 40, 30))
        weights.rows += row("latest", 74.2, "2026-10-20T08:00:00Z", fat = 19.5)
        weights.onboarding = row("start", 78.0, "2026-09-01T08:00:00Z")
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun row(id: String, kg: Double, at: String, fat: Double? = null) =
        BodyMeasurement(id, Instant.parse(at), kg, MeasurementSource.Manual, bodyFatPct = fat)

    private fun viewModel() = GoalViewModel(
        profile, weights, goals, circumferences, Bmi(), TargetDistance(), InterpretMetric(),
        WaistHipRatio(), ValidateBodyMeasurement(), connectivity, savedState,
    )

    private fun TestScope.send(vm: GoalViewModel, event: GoalEvent) {
        vm.onEvent(event)
        advanceUntilIdle()
    }

    private fun GoalViewModel.content(): GoalContent = (uiState.value as GoalUiState.Content).content

    private fun GoalViewModel.sheet(): TargetSheetState = checkNotNull(content().targetSheet)

    @Test
    fun startsLoadingThenShowsBmiTargetDistanceAndChangeSinceTheFirstWeight() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(GoalUiState.Loading, vm.uiState.value)
        advanceUntilIdle()

        val content = vm.content()
        assertEquals(23.4, content.bmi?.value)
        assertEquals(MetricStatus.Normal, content.bmi?.status)
        assertEquals(70.0, content.targetKg)
        assertEquals(TargetDistanceResult.Remaining(4.2), content.distance)
        assertEquals(-3.8, content.sinceFirstKg)
        assertEquals(MetricStatus.BodyFatNormal, content.bodyFat?.status)
    }

    @Test
    fun maintenance_reportsTheSideOfTheTarget() = runTest(dispatcher) {
        goals.row = null
        profile.targetWeight = Result.success(73.2)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(GoalStrategy.Maintenance, vm.content().strategy)
        assertEquals(TargetDistanceResult.Above(1.0), vm.content().distance)
    }

    @Test
    fun noHeight_hidesBmi() = runTest(dispatcher) {
        profile.biometrics = Result.success(SavedBiometrics(sex = Sex.Male))
        val vm = viewModel()
        advanceUntilIdle()

        assertNull(vm.content().bmi)
    }

    @Test
    fun noTarget_hasNoDistance() = runTest(dispatcher) {
        profile.targetWeight = Result.success(null)
        val vm = viewModel()
        advanceUntilIdle()

        assertNull(vm.content().targetKg)
        assertNull(vm.content().distance)
        assertEquals(-3.8, vm.content().sinceFirstKg)
    }

    @Test
    fun noOnboardingWeight_hasNoChangeSinceTheFirstWeight() = runTest(dispatcher) {
        weights.onboarding = null
        val vm = viewModel()
        advanceUntilIdle()

        assertNull(vm.content().sinceFirstKg)
        assertIs<TargetDistanceResult.Remaining>(vm.content().distance)
    }

    @Test
    fun noSex_hasNoBodyFatNorWaistToHipStatus() = runTest(dispatcher) {
        profile.biometrics = Result.success(SavedBiometrics(heightCm = 178))
        val vm = viewModel()
        advanceUntilIdle()

        assertNull(vm.content().bodyFat)
        assertNull(vm.content().waistToHip)
    }

    @Test
    fun waistToHip_usesTheLatestCircumferences() = runTest(dispatcher) {
        val at = Instant.parse("2026-10-18T08:00:00Z")
        circumferences.rows += BodyCircumference("w", CircumferenceSite.Waist, 84.5, at, at)
        circumferences.rows += BodyCircumference("h", CircumferenceSite.Hip, 98.0, at, at)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(0.86, vm.content().waistToHip?.value)
        assertEquals(MetricStatus.WhrLowRisk, vm.content().waistToHip?.status)
    }

    @Test
    fun readFailure_isErrorAndRetryRecovers() = runTest(dispatcher) {
        weights.failReads = true
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(GoalUiState.Error, vm.uiState.value)

        weights.failReads = false
        send(vm, GoalEvent.Retry)
        advanceUntilIdle()

        assertIs<GoalUiState.Content>(vm.uiState.value)
    }

    @Test
    fun openingTheSheet_prefillsTheCurrentTargetAndNeedsAChangeToSave() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        send(vm, GoalEvent.OpenTargetSheet)

        assertEquals("70,0", vm.sheet().text)
        assertFalse(vm.sheet().canSave)
    }

    @Test
    fun validTarget_savesCloseTheSheetAndReloads() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        send(vm, GoalEvent.OpenTargetSheet)
        send(vm, GoalEvent.EditTarget("68,5"))
        assertTrue(vm.sheet().canSave)

        profile.targetWeight = Result.success(68.5)
        send(vm, GoalEvent.SaveTarget)
        advanceUntilIdle()

        assertEquals(listOf<Double?>(68.5), profile.targetWeightSaves)
        assertNull(vm.content().targetSheet)
        assertEquals(68.5, vm.content().targetKg)
    }

    @Test
    fun outOfRangeOrMalformedTarget_cannotBeSaved() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        send(vm, GoalEvent.OpenTargetSheet)

        send(vm, GoalEvent.EditTarget("19,9"))
        assertEquals(BodyMeasurementProblem.TooLow, vm.sheet().problem)
        assertFalse(vm.sheet().canSave)
        send(vm, GoalEvent.EditTarget("400,1"))
        assertEquals(BodyMeasurementProblem.TooHigh, vm.sheet().problem)
        send(vm, GoalEvent.EditTarget("abc"))
        assertEquals(BodyMeasurementProblem.NotANumber, vm.sheet().problem)
        send(vm, GoalEvent.EditTarget("70,55"))
        assertEquals(BodyMeasurementProblem.TooManyDecimals, vm.sheet().problem)
        send(vm, GoalEvent.EditTarget(""))
        assertNull(vm.sheet().problem)
        assertFalse(vm.sheet().canSave)
        send(vm, GoalEvent.EditTarget("20"))
        assertTrue(vm.sheet().canSave)
        send(vm, GoalEvent.EditTarget("400"))
        assertTrue(vm.sheet().canSave)

        send(vm, GoalEvent.EditTarget("19,9"))
        send(vm, GoalEvent.SaveTarget)
        advanceUntilIdle()
        assertTrue(profile.targetWeightSaves.isEmpty())
    }

    @Test
    fun offline_disablesSave() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        send(vm, GoalEvent.OpenTargetSheet)
        send(vm, GoalEvent.EditTarget("68"))
        connectivity.state.value = false
        advanceUntilIdle()

        assertFalse(vm.sheet().canSave)
        assertFalse(vm.content().isOnline)
        send(vm, GoalEvent.SaveTarget)
        advanceUntilIdle()
        assertTrue(profile.targetWeightSaves.isEmpty())
    }

    @Test
    fun saveFailure_keepsTheDraftWithAnError() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        send(vm, GoalEvent.OpenTargetSheet)
        send(vm, GoalEvent.EditTarget("68"))
        profile.targetWeightSaveResult = Result.failure(AppException(AppError.Network))

        send(vm, GoalEvent.SaveTarget)
        advanceUntilIdle()

        assertEquals(WeightSaveError.Offline, vm.sheet().saveError)
        assertEquals("68", vm.sheet().text)
        assertTrue(vm.sheet().canSave)
    }

    @Test
    fun dismiss_discardsTheDraft() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        send(vm, GoalEvent.OpenTargetSheet)
        send(vm, GoalEvent.EditTarget("68"))

        send(vm, GoalEvent.Dismiss)

        assertNull(vm.content().targetSheet)
        assertTrue(profile.targetWeightSaves.isEmpty())
        send(vm, GoalEvent.OpenTargetSheet)
        assertEquals("70,0", vm.sheet().text)
    }

    @Test
    fun recreatedFromTheSameSavedState_restoresDraftAndMetric() = runTest(dispatcher) {
        weights.rows += row("old", 75.6, "2026-10-10T08:00:00Z")
        val first = viewModel()
        advanceUntilIdle()
        send(first, GoalEvent.OpenTargetSheet)
        send(first, GoalEvent.EditTarget("66,5"))
        send(first, GoalEvent.OpenMetric(MetricKind.BodyFat))
        advanceUntilIdle()

        val second = viewModel()
        advanceUntilIdle()

        assertEquals("66,5", second.sheet().text)
        assertEquals(MetricKind.BodyFat, second.content().openMetric)
        send(second, GoalEvent.CloseMetric)
        assertNull(second.content().openMetric)
    }
}
