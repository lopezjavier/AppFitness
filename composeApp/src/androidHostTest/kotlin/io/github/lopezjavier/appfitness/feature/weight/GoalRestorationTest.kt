package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.app.FakeProfileRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

private object RestorationGoalClock : Clock {
    override fun today(): LocalDate = LocalDate(2026, 10, 21)

    override fun now(): Instant = Instant.parse("2026-10-21T10:30:00Z")
}

/** The target draft, the open metric detail survive recreation (ADR-40). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class GoalRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val weights = FakeWeightRepository(
        BodyMeasurement(
            "latest",
            Instant.parse("2026-10-20T08:00:00Z"),
            74.2,
            MeasurementSource.Manual,
            bodyFatPct = 19.5,
        ),
        BodyMeasurement("older", Instant.parse("2026-10-10T12:00:00Z"), 75.6, MeasurementSource.Manual),
    )
    private val profile = FakeProfileRepository(Result.success(ProfileSummary("Ana", null, true)))
    private val goals = FakeNutritionGoalsRepository()

    init {
        profile.biometrics = Result.success(SavedBiometrics(sex = Sex.Male, heightCm = 178))
        profile.targetWeight = Result.success(70.0)
        weights.onboarding =
            BodyMeasurement("start", Instant.parse("2026-09-01T08:00:00Z"), 78.0, MeasurementSource.Manual)
    }

    private fun weightModel(): WeightViewModel = WeightViewModel(
        repository = weights,
        measurementsByDay = MeasurementsByDay(),
        saveBodyMeasurement = SaveBodyMeasurement(weights, RestorationGoalClock, TimeZone.UTC),
        validate = ValidateBodyMeasurement(),
        connectivity = FakeConnectivityObserver(),
        clock = RestorationGoalClock,
        timeZone = TimeZone.UTC,
        savedState = SavedStateHandle(),
    )

    private fun goalModel(state: SavedStateHandle) = GoalViewModel(
        profile, weights, goals, FakeCircumferenceRepository(), Bmi(), TargetDistance(),
        InterpretMetric(), WaistHipRatio(), ValidateBodyMeasurement(),
        FakeConnectivityObserver(), state,
    )

    @Composable
    private fun Host(state: SavedStateHandle) {
        val weight = remember { weightModel() }
        val goal = remember { goalModel(state) }
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

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun editableText(tag: String): String =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    @Test
    fun targetSheet_keepsItsDraft_afterRecreation() {
        val state = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { Host(state) }
        rule.waitForIdle()
        tap("weight_goal_change_button")
        rule.onNodeWithTag("weight_target_field").performTextReplacement("66,5")
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        assertEquals("66,5", editableText("weight_target_field"))
    }

    @Test
    fun metricDetail_staysOpen_afterRecreation() {
        val state = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { Host(state) }
        rule.waitForIdle()
        tap("weight_bmi_tile")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("weight_metric_disclaimer").assertIsDisplayed()
    }

    @Test
    fun closedSheets_stayClosed_afterRecreation() {
        val state = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { Host(state) }
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("weight_target_sheet").assertDoesNotExist()
        rule.onNodeWithTag("weight_metric_sheet").assertDoesNotExist()
    }
}
