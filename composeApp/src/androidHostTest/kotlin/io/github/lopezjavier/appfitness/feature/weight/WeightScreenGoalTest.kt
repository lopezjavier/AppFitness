package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.app.FakeProfileRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.usecase.Bmi
import io.github.lopezjavier.appfitness.domain.usecase.InterpretMetric
import io.github.lopezjavier.appfitness.domain.usecase.MeasurementsByDay
import io.github.lopezjavier.appfitness.domain.usecase.SaveBodyMeasurement
import io.github.lopezjavier.appfitness.domain.usecase.TargetDistance
import io.github.lopezjavier.appfitness.domain.usecase.ValidateBodyMeasurement
import io.github.lopezjavier.appfitness.domain.usecase.WaistHipRatio
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

/** US-27 goal and metrics on the weight screen: card, BMI, detail sheets, target sheet and comparison. */
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
        profile.targetWeight = Result.success(70.0)
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
        profile, weights, goals, circumferences, Bmi(), TargetDistance(), InterpretMetric(),
        WaistHipRatio(), ValidateBodyMeasurement(), FakeConnectivityObserver(), state,
    )

    @Composable
    private fun Host(goalState: SavedStateHandle) {
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
                goal = goalUi,
                onGoalEvent = goal::onEvent,
                timeZone = TimeZone.UTC,
            )
        }
    }

    private fun show(goalState: SavedStateHandle = SavedStateHandle()) {
        prepare()
        rule.setContent { Host(goalState) }
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
    fun goalCard_showsTheDistance() {
        show()

        rule.onNodeWithTag("weight_goal_distance").assertTextContains("faltan 4,2 kg")
    }

    @Test
    fun goalCard_showsTheChangeSinceTheFirstWeightAsPlainTextWithoutPercentage() {
        show()

        rule.onNodeWithTag("weight_goal_since_first").assertTextEquals("Desde tu primer peso: −3,8 kg")
        rule.onNode(hasText("%", substring = true) and hasAnyAncestor(hasTestTag("weight_goal_card")))
            .assertDoesNotExist()
    }

    @Test
    fun maintenance_saysWhichSideOfTheTargetYouAreOn() {
        prepare()
        goals.row = null
        profile.targetWeight = Result.success(73.2)
        rule.setContent { Host(SavedStateHandle()) }
        rule.waitForIdle()

        rule.onNodeWithTag("weight_goal_distance").assertTextContains("1,0 kg por encima del objetivo")
    }

    @Test
    fun withoutTarget_theCardOffersToSetOneAndSavingSendsIt() {
        prepare()
        profile.targetWeight = Result.success(null)
        rule.setContent { Host(SavedStateHandle()) }
        rule.waitForIdle()

        tap("weight_goal_set_button")
        rule.onNodeWithTag("weight_target_save_button").assertIsNotEnabled()
        rule.onNodeWithTag("weight_target_field").performTextReplacement("70,0")
        rule.waitForIdle()
        rule.onNodeWithTag("weight_target_save_button").assertIsEnabled()
        tap("weight_target_save_button")

        assertEquals(listOf<Double?>(70.0), profile.targetWeightSaves)
        rule.onNodeWithTag("weight_target_sheet").assertDoesNotExist()
    }

    @Test
    fun targetSheet_outOfRange_disablesGuardarAndShowsTheRange() {
        show()

        tap("weight_goal_change_button")
        rule.onNodeWithTag("weight_target_field").performTextReplacement("5,0")
        rule.waitForIdle()

        rule.onNodeWithTag("weight_target_save_button").assertIsNotEnabled()
        rule.onNodeWithTag("weight_target_error").assertIsDisplayed()
    }

    @Test
    fun cancellingTheTargetSheet_savesNothing() {
        show()

        tap("weight_goal_change_button")
        rule.onNodeWithTag("weight_target_field").performTextReplacement("65,0")
        tap("weight_target_cancel_button")

        assertTrue(profile.targetWeightSaves.isEmpty())
        rule.onNodeWithTag("weight_target_sheet").assertDoesNotExist()
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
        tap("weight_metric_close_button")
        rule.onNodeWithTag("weight_metric_sheet").assertDoesNotExist()
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
    @Config(qualifiers = "w900dp-h1200dp-mdpi")
    fun wideWindow_putsTheGoalCardInTheSummaryColumnLeftOfTheCalendar() {
        show()

        val card = rule.onNodeWithTag("weight_goal_card").getUnclippedBoundsInRoot()
        val calendar = rule.onNodeWithTag("weight_calendar").getUnclippedBoundsInRoot()
        assertTrue(calendar.left >= card.right)
    }
}
