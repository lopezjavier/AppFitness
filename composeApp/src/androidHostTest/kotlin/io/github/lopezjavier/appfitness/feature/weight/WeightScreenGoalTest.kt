package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.app.FakeProfileRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.BodyMetric
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.model.OutcomeKind
import io.github.lopezjavier.appfitness.domain.model.PlanOutcome
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.usecase.Bmi
import io.github.lopezjavier.appfitness.domain.usecase.InterpretMetric
import io.github.lopezjavier.appfitness.domain.usecase.MeasurementsByDay
import io.github.lopezjavier.appfitness.domain.usecase.RateTone
import io.github.lopezjavier.appfitness.domain.usecase.SaveBodyMeasurement
import io.github.lopezjavier.appfitness.domain.usecase.TrendData
import io.github.lopezjavier.appfitness.domain.usecase.ValidateBodyMeasurement
import io.github.lopezjavier.appfitness.domain.usecase.WaistHipRatio
import io.github.lopezjavier.appfitness.domain.usecase.WeeklyRate
import io.github.lopezjavier.appfitness.domain.usecase.WeightChartRange
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

private val GoalToday = LocalDate(2026, 10, 21)

private object GoalClock : Clock {
    override fun today(): LocalDate = GoalToday

    override fun now(): Instant = Instant.parse("2026-10-21T10:30:00Z")
}

/** US-27 metrics and the plan outcome on the weight screen: BMI and body-fat cards, detail sheets, outcome card. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class WeightScreenGoalTest {
    @get:Rule
    val rule = createComposeRule()

    private val weights = FakeWeightRepository()
    private val profile = FakeProfileRepository(Result.success(ProfileSummary("Ana", null, true)))
    private val goals =
        FakeNutritionGoalsRepository(NutritionGoals(GoalStrategy.Deficit, -400, 2000, MacroSplit(30, 40, 30)))
    private val circumferences = FakeCircumferenceRepository()
    private lateinit var weightModel: WeightViewModel

    private fun manual(id: String, kg: Double, at: String, fat: Double? = null) =
        BodyMeasurement(id, Instant.parse(at), kg, MeasurementSource.Manual, bodyFatPct = fat)

    private fun prepare() {
        profile.biometrics = Result.success(SavedBiometrics(sex = Sex.Male, heightCm = 178))
        weights.rows += manual("latest", 74.2, "2026-10-20T08:00:00Z", fat = 19.5)
        weights.onboarding = manual("start", 78.0, "2026-09-01T08:00:00Z")
    }

    private fun newWeightModel(): WeightViewModel = WeightViewModel(
        repository = weights,
        measurementsByDay = MeasurementsByDay(),
        saveBodyMeasurement = SaveBodyMeasurement(weights, GoalClock, TimeZone.UTC),
        validate = ValidateBodyMeasurement(),
        connectivity = FakeConnectivityObserver(),
        clock = GoalClock,
        timeZone = TimeZone.UTC,
        savedState = SavedStateHandle(),
    )

    private fun newGoalModel(state: SavedStateHandle) = GoalViewModel(
        profile, weights, goals, circumferences, Bmi(), InterpretMetric(),
        WaistHipRatio(), FakeConnectivityObserver(), state,
    )

    @Composable
    private fun Host(goalState: SavedStateHandle, trend: WeightTrendUiState? = null) {
        val weight = remember { newWeightModel().also { weightModel = it } }
        val goal = remember { newGoalModel(goalState) }
        val weightState by weight.uiState.collectAsState()
        val goalUi by goal.uiState.collectAsState()
        AppTheme {
            WeightScreen(
                state = weightState,
                onEvent = weight::onEvent,
                circumferences = CircumferencesUiState.Loading,
                onCircumferenceEvent = {},
                trend = trend,
                goal = goalUi,
                onGoalEvent = goal::onEvent,
                timeZone = TimeZone.UTC,
            )
        }
    }

    private fun show(goalState: SavedStateHandle = SavedStateHandle(), trend: WeightTrendUiState? = null) {
        prepare()
        rule.setContent { Host(goalState, trend) }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun bmiTile_showsTheValueAndItsStatus() {
        show()

        rule.onNodeWithTag("weight_bmi_tile").assertTextContains("23,4", substring = true)
        rule.onNodeWithTag("weight_bmi_tile").assertTextContains("Normal", substring = true)
        rule.onNodeWithTag("weight_bmi_tile").assertTextContains("IMC", substring = true)
    }

    @Test
    fun withoutHeight_theBmiTileIsHidden() {
        prepare()
        profile.biometrics = Result.success(SavedBiometrics(sex = Sex.Male))
        rule.setContent { Host(SavedStateHandle()) }
        rule.waitForIdle()

        rule.onNodeWithTag("weight_bmi_tile").assertDoesNotExist()
    }

    @Test
    fun tappingTheBmiTile_opensTheDetailWithTheDisclaimer() {
        show()

        tap("weight_bmi_tile")

        rule.onNodeWithTag("weight_metric_status").assertTextContains("Normal", substring = true)
        rule.onNodeWithTag("weight_metric_explanation").assertIsDisplayed()
        rule.onNodeWithTag(
            "weight_metric_disclaimer",
        ).assertTextContains("no es un dispositivo médico", substring = true)
        rule.onNodeWithTag("weight_metric_close_button").assertDoesNotExist()
        tap("weight_metric_sheet_close")
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("weight_metric_sheet_title").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun daySheet_hasNoCompareAction() {
        show()

        weightModel.onEvent(WeightEvent.OpenDay(LocalDate(2026, 10, 20)))
        rule.waitForIdle()

        rule.onNodeWithText("Comparar").assertDoesNotExist()
    }

    @Test
    fun healthConnectRow_showsItsCompositionValues() {
        prepare()
        weights.rows += BodyMeasurement(
            "hc",
            Instant.parse("2026-10-12T07:00:00Z"),
            75.0,
            MeasurementSource.HealthConnect,
            leanMassKg = 58.1,
            waterKg = 41.0,
            boneKg = 3.2,
            bmrKcal = 1700,
        )
        rule.setContent { Host(SavedStateHandle()) }
        rule.waitForIdle()

        weightModel.onEvent(WeightEvent.OpenDay(LocalDate(2026, 10, 12)))
        rule.waitForIdle()

        rule.onNodeWithTag("weight_day_composition_lean_mass").assertTextContains("58,1 kg", substring = true)
        rule.onNodeWithTag("weight_day_composition_water").assertTextContains("41,0 kg", substring = true)
        rule.onNodeWithTag("weight_day_composition_bone").assertTextContains("3,2 kg", substring = true)
        rule.onNodeWithTag("weight_day_composition_bmr").assertTextContains("1700 kcal", substring = true)
    }

    @Test
    fun bmiAndBodyFat_shareARow_atTheDefaultFontScale() {
        show()

        val bmi = rule.onNodeWithTag("weight_bmi_tile").getUnclippedBoundsInRoot()
        val fat = rule.onNodeWithTag("weight_body_fat_tile").getUnclippedBoundsInRoot()
        assertEquals(bmi.top, fat.top)
    }

    @Test
    fun bmiAndBodyFat_stackAtLargeFontScale() {
        prepare()
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) { Host(SavedStateHandle()) }
        }
        rule.waitForIdle()

        val bmi = rule.onNodeWithTag("weight_bmi_tile").getUnclippedBoundsInRoot()
        val fat = rule.onNodeWithTag("weight_body_fat_tile").getUnclippedBoundsInRoot()
        assertTrue(fat.top >= bmi.bottom)
    }

    private fun trendWith(outcome: PlanOutcome?) = WeightTrendUiState.Content(
        range = WeightChartRange.Quarter,
        metric = BodyMetric.Weight,
        trend = TrendData(emptyList(), WeeklyRate(null, RateTone.Neutral), hasEnoughData = false),
        weeklyRate = WeeklyRate(null, RateTone.Neutral),
        selectedPoint = null,
        targetKg = outcome?.targetValue,
        planOutcome = outcome,
    )

    private val weightOutcome =
        PlanOutcome(OutcomeKind.Weight, "weight", 72.5, "Bajar de forma gradual.", LocalDate(2026, 11, 16))

    @Test
    fun withAnAcceptedPlan_theWeightOutcomeIsShownReadOnly() {
        show(trend = trendWith(weightOutcome))

        rule.onNodeWithTag("weight_plan_outcome").assertExists()
        rule.onNodeWithText("Objetivo del plan").assertExists()
        rule.onNodeWithText("72,5 kg").assertExists()
        rule.onNodeWithText("Para el 16 de noviembre").assertExists()
        tap("weight_plan_outcome_help")
        rule.onNodeWithText("Bajar de forma gradual.").assertExists()
    }

    @Test
    fun withoutAPlanOutcome_nothingIsShownForTheGoal() {
        show(trend = trendWith(null))

        rule.onNodeWithTag("weight_plan_outcome").assertDoesNotExist()
        rule.onNodeWithText("Objetivo del plan").assertDoesNotExist()
    }

    @Test
    fun theTargetWeightIsNeverAnInput() {
        show(trend = trendWith(weightOutcome))

        rule.onNodeWithText("Fijar objetivo").assertDoesNotExist()
        rule.onNodeWithText("Peso objetivo (kg)").assertDoesNotExist()
        rule.onNodeWithTag("weight_goal_target_sheet").assertDoesNotExist()
        rule.onNodeWithTag("weight_plan_outcome").assertHasNoClickAction()
    }
}
