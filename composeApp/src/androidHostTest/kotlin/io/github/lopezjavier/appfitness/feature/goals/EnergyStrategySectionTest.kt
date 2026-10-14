package io.github.lopezjavier.appfitness.feature.goals

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.usecase.AdjustmentIntensity
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TAG = "goals"
private val TODAY = LocalDate(2026, 10, 14)

/** US-07 scenarios as the shared section shows them (T-037 reuses it on Profile). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class EnergyStrategySectionTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<EnergyStrategyEvent>()

    private fun state(
        sex: Sex = Sex.Male,
        weightKg: Double = 74.2,
        heightCm: Int = 178,
        activity: ActivityLevel = ActivityLevel.Moderate,
        strategy: GoalStrategy = GoalStrategy.Maintenance,
        adjustment: Int = 0,
        enabled: Boolean = true,
    ) = energyStrategyUiState(
        sex = sex,
        birthDate = LocalDate(2000, 3, 1),
        heightCm = heightCm,
        weightKg = weightKg,
        activityLevel = activity,
        today = TODAY,
        strategy = strategy,
        adjustment = adjustment,
        enabled = enabled,
    )

    private fun show(state: EnergyStrategyUiState) {
        rule.setContent {
            AppTheme { EnergyStrategySection(state, { events += it }, TAG) }
        }
    }

    @Test
    fun showsTheTdeeOfTheBiometrics() {
        show(state())

        rule.onNodeWithTag("${TAG}_tdee").assertTextEquals("TDEE: 2.681 kcal")
    }

    @Test
    fun deficitOfTdee2800_showsTheDailyTargetAndThePill() {
        show(
            EnergyStrategyUiState(
                tdeeKcal = 2800,
                strategy = GoalStrategy.Deficit,
                adjustment = -400,
                targetKcal = 2400,
                isFloored = false,
                floorKcal = 1500,
                intensity = AdjustmentIntensity.Moderate,
                canStepDown = true,
                canStepUp = true,
            ),
        )

        rule.onNodeWithTag("${TAG}_target").assertTextEquals("Meta diaria: 2.400 kcal/día")
        rule.onNodeWithTag("${TAG}_pill").assertTextEquals("−400 kcal · Déficit moderado")
        rule.onNodeWithTag("${TAG}_adjust_value").assertTextEquals("−400 kcal")
    }

    @Test
    fun maintenanceAndSurplusPills() {
        var current by mutableStateOf(state())
        rule.setContent { AppTheme { EnergyStrategySection(current, { events += it }, TAG) } }
        rule.onNodeWithTag("${TAG}_pill").assertTextEquals("0 kcal · Mantenimiento")

        current = state(strategy = GoalStrategy.Surplus, adjustment = 250)

        rule.onNodeWithTag("${TAG}_pill").assertTextEquals("+250 kcal · Superávit ligero")
        rule.onNodeWithTag("${TAG}_strategy_surplus").assertIsSelected()
    }

    @Test
    fun tappingASegmentOrAStepper_emitsTheEvents() {
        show(state(strategy = GoalStrategy.Deficit, adjustment = -400))

        rule.onNodeWithTag("${TAG}_strategy_surplus").performClick()
        rule.onNodeWithTag("${TAG}_adjust_decrement").performClick()
        rule.onNodeWithTag("${TAG}_adjust_increment").performClick()

        assertEquals(
            listOf(
                EnergyStrategyEvent.SelectStrategy(GoalStrategy.Surplus),
                EnergyStrategyEvent.StepDown,
                EnergyStrategyEvent.StepUp,
            ),
            events,
        )
    }

    @Test
    fun floorWarning_showsOnlyWhenFlooredWithTheFloorOfTheSex() {
        // Female, 150 cm, 40 kg, sedentary: TDEE 1.256, so -400 falls under the 1.200 floor.
        show(
            state(
                sex = Sex.Female,
                weightKg = 40.0,
                heightCm = 150,
                activity = ActivityLevel.Sedentary,
                strategy = GoalStrategy.Deficit,
                adjustment = -400,
            ),
        )

        rule.onNodeWithTag(
            "${TAG}_floor_warning",
        ).assertTextEquals("Meta ajustada al mínimo de seguridad (1.200 kcal/día)")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        rule.onNodeWithTag("${TAG}_target").assertTextEquals("Meta diaria: 1.200 kcal/día")
        rule.onNodeWithTag("${TAG}_adjust_decrement").assertIsNotEnabled()
        rule.onNodeWithTag("${TAG}_adjust_increment").assertIsEnabled()
    }

    @Test
    fun maleFloorWarning_names1500() {
        show(
            state(
                sex = Sex.Male,
                weightKg = 45.0,
                heightCm = 150,
                activity = ActivityLevel.Sedentary,
                strategy = GoalStrategy.Deficit,
                adjustment = -1000,
            ),
        )

        rule.onNodeWithTag(
            "${TAG}_floor_warning",
        ).assertTextEquals("Meta ajustada al mínimo de seguridad (1.500 kcal/día)")
    }

    @Test
    fun noWarning_whenTheTargetIsAboveTheFloor() {
        show(state(strategy = GoalStrategy.Deficit, adjustment = -400))

        rule.onNodeWithTag("${TAG}_floor_warning").assertDoesNotExist()
        rule.onNodeWithTag("${TAG}_adjust_decrement").assertIsEnabled()
    }

    @Test
    fun decrementIsDisabledAtTheRangeEnd_andTargetsAre48dp() {
        show(state(strategy = GoalStrategy.Deficit, adjustment = -1000))

        rule.onNodeWithTag("${TAG}_adjust_decrement").assertIsNotEnabled()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        rule.onNodeWithTag("${TAG}_adjust_increment").assertIsEnabled().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun stepper_hasSpanishDescriptionsAndAState() {
        show(state(strategy = GoalStrategy.Deficit, adjustment = -400))

        rule.onNodeWithTag("${TAG}_adjust_decrement").assertContentDescriptionEquals("Reducir ajuste 50 kcal")
        rule.onNodeWithTag("${TAG}_adjust_increment").assertContentDescriptionEquals("Aumentar ajuste 50 kcal")
    }

    @Test
    fun whileSaving_theStrategySelectorIsDisabledToo() {
        show(state(strategy = GoalStrategy.Deficit, adjustment = -400, enabled = false))

        rule.onNodeWithTag("${TAG}_strategy_surplus").assertIsNotEnabled().performClick()
        assertEquals(emptyList<EnergyStrategyEvent>(), events)
    }

    @Test
    fun whileSaving_bothStepperButtonsAreDisabled() {
        show(state(strategy = GoalStrategy.Deficit, adjustment = -400, enabled = false))

        rule.onNodeWithTag("${TAG}_adjust_decrement").assertIsNotEnabled()
        rule.onNodeWithTag("${TAG}_adjust_increment").assertIsNotEnabled()
    }
}
