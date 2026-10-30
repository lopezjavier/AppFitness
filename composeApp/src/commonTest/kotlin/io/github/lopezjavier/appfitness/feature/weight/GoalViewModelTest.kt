package io.github.lopezjavier.appfitness.feature.weight

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.app.FakeProfileRepository
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
import io.github.lopezjavier.appfitness.domain.usecase.InterpretMetric
import io.github.lopezjavier.appfitness.domain.usecase.MetricKind
import io.github.lopezjavier.appfitness.domain.usecase.MetricStatus
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
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
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
        goals.row = NutritionGoals(GoalStrategy.Deficit, -400, 2000, MacroSplit(30, 40, 30))
        weights.rows += row("latest", 74.2, "2026-10-20T08:00:00Z", fat = 19.5)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun row(id: String, kg: Double, at: String, fat: Double? = null) =
        BodyMeasurement(id, Instant.parse(at), kg, MeasurementSource.Manual, bodyFatPct = fat)

    private fun viewModel() = GoalViewModel(
        profile, weights, goals, circumferences, Bmi(), InterpretMetric(), WaistHipRatio(), connectivity, savedState,
    )

    private fun TestScope.send(vm: GoalViewModel, event: GoalEvent) {
        vm.onEvent(event)
        advanceUntilIdle()
    }

    private fun GoalViewModel.content(): GoalContent = (uiState.value as GoalUiState.Content).content

    @Test
    fun startsLoadingThenShowsBmiAndBodyFat() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(GoalUiState.Loading, vm.uiState.value)
        advanceUntilIdle()

        val content = vm.content()
        assertEquals(23.4, content.bmi?.value)
        assertEquals(MetricStatus.Normal, content.bmi?.status)
        assertEquals(MetricStatus.BodyFatNormal, content.bodyFat?.status)
        assertEquals(74.2, content.currentKg)
    }

    @Test
    fun withoutAGoalsRow_strategyIsMaintenance() = runTest(dispatcher) {
        goals.row = null
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(GoalStrategy.Maintenance, vm.content().strategy)
    }

    @Test
    fun noHeight_hidesBmi() = runTest(dispatcher) {
        profile.biometrics = Result.success(SavedBiometrics(sex = Sex.Male))
        val vm = viewModel()
        advanceUntilIdle()

        assertNull(vm.content().bmi)
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
    fun offline_isReflectedInTheContent() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        connectivity.state.value = false
        advanceUntilIdle()

        assertFalse(vm.content().isOnline)
    }

    @Test
    fun recreatedFromTheSameSavedState_restoresTheOpenMetric() = runTest(dispatcher) {
        val first = viewModel()
        advanceUntilIdle()
        send(first, GoalEvent.OpenMetric(MetricKind.BodyFat))

        val second = viewModel()
        advanceUntilIdle()

        assertEquals(MetricKind.BodyFat, second.content().openMetric)
        send(second, GoalEvent.CloseMetric)
        assertNull(second.content().openMetric)
    }
}
