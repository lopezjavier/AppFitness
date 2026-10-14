package io.github.lopezjavier.appfitness.feature.onboarding

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeBodyMeasurementRepository
import io.github.lopezjavier.appfitness.app.FakeClock
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.app.FakeProfileRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.WheelPickerDefaults
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.BiometricBounds
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.usecase.CompleteOnboarding
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val CONFIRMATION_START_MS = 100L
private const val UNIT_LINE_HEIGHT_SP = 24

private val ACTIVITY_TEXTS =
    listOf(
        Triple("sedentary", "Sedentario", "Trabajo sentado y poco o ningún ejercicio"),
        Triple("light", "Ligero", "Ejercicio 1–3 días por semana, o trabajo sentado pero caminas a diario"),
        Triple("moderate", "Moderado", "Ejercicio 3–5 días por semana"),
        Triple("high", "Alto", "Ejercicio intenso 6–7 días por semana"),
        Triple("very_high", "Muy alto", "Entrenas dos veces al día o tienes un trabajo físico exigente"),
    )

/** US-06 onboarding scenarios as the screen shows them, driven through the real ViewModel with fakes. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario and accessibility contract.
class OnboardingScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val profile = FakeProfileRepository(Result.success(ProfileSummary("Ana", null, false)))
    private val weights = FakeBodyMeasurementRepository()
    private val goals = FakeNutritionGoalsRepository()
    private val connectivity = FakeConnectivityObserver(initial = true)
    private val clock = FakeClock()
    private val haptics = mutableListOf<HapticFeedbackType>()
    private var completions = 0

    private val recordingHaptics = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            haptics += hapticFeedbackType
        }
    }

    private fun show() {
        val viewModel = OnboardingViewModel(
            SavedStateHandle(),
            profile,
            weights,
            goals,
            CompleteOnboarding(goals, profile),
            connectivity,
            clock,
        )
        rule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides recordingHaptics) {
                AppTheme { OnboardingRoute(onComplete = { completions++ }, viewModel = viewModel) }
            }
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun next() = tap("onboarding_continue")

    private fun goToStep(step: Int) {
        tap("onboarding_sex_male")
        repeat(step - 1) { next() }
    }

    private fun setWheel(tag: String, value: Float) {
        rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
        rule.waitForIdle()
    }

    @Test
    fun firstStep_showsSexSelectorWithNothingSelected_andContinueDisabledUntilChosen() {
        show()

        rule.onNodeWithTag("onboarding_screen").assertExists()
        rule.onNodeWithTag("onboarding_step_indicator").assertTextEquals("1 / 4")
        rule.onNodeWithTag("onboarding_step_title").assertTextEquals("Sobre ti")
        rule.onNodeWithTag("onboarding_label_sex").assertTextEquals("Sexo")
        rule.onNodeWithTag("onboarding_label_birth_date").assertTextEquals("Fecha de nacimiento")
        rule.onNodeWithTag("onboarding_sex_male").assertTextEquals("Hombre").assertIsNotSelected()
        rule.onNodeWithTag("onboarding_sex_female").assertTextEquals("Mujer").assertIsNotSelected()
        rule.onNodeWithTag("onboarding_continue").assertIsNotEnabled()
        rule.onNodeWithTag("onboarding_back").assertDoesNotExist()

        tap("onboarding_sex_male")

        rule.onNodeWithTag("onboarding_sex_male").assertIsSelected()
        rule.onNodeWithTag("onboarding_continue").assertIsEnabled()
    }

    @Test
    fun indicatorAndBack_threeOfFour_thenAtrasReturnsWithTheValuesKept() {
        show()
        goToStep(1)
        setWheel("onboarding_birth_year_picker", 1990f)
        rule.onNodeWithTag("onboarding_age_label").assertTextEquals("36 años")

        next()

        rule.onNodeWithTag("onboarding_step_indicator").assertTextEquals("2 / 4")
        rule.onNodeWithTag("onboarding_step_title").assertTextEquals("Medidas")
        setWheel("onboarding_height_picker", 181f)
        next()

        rule.onNodeWithTag("onboarding_step_indicator").assertTextEquals("3 / 4")
        rule.onNodeWithTag("onboarding_step_indicator").assertContentDescriptionEquals("Paso 3 de 4")
        rule.onNodeWithTag("onboarding_step_title").assertTextEquals("Nivel de actividad")

        tap("onboarding_back")

        rule.onNodeWithTag("onboarding_step_indicator").assertTextEquals("2 / 4")
        rule.onNodeWithTag("onboarding_height_picker").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "181 cm"),
        )

        tap("onboarding_back")

        rule.onNodeWithTag("onboarding_step_indicator").assertTextEquals("1 / 4")
        rule.onNodeWithTag("onboarding_age_label").assertTextEquals("36 años")
        rule.onNodeWithTag("onboarding_birth_year_picker").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1990"),
        )
    }

    @Test
    fun birthDateStep_showsDayMonthAndYearWheels_withSpanishMonthsAndTheAge() {
        show()
        goToStep(1)

        rule.onNodeWithTag("onboarding_birth_day_picker").assertContentDescriptionEquals("Día")
        rule.onNodeWithTag("onboarding_birth_month_picker").assertContentDescriptionEquals("Mes")
        rule.onNodeWithTag("onboarding_birth_month_picker").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "octubre"),
        )
        rule.onNodeWithTag("onboarding_birth_year_picker").assertContentDescriptionEquals("Año")
        rule.onNodeWithTag("onboarding_age_label").assertTextEquals("30 años")
    }

    @Test
    fun rangeValidation_ageTwelveHeight260AndWeight15AreClamped() {
        show()
        goToStep(1)
        val range = BiometricBounds.birthDateRange(LocalDate(2026, 10, 14))
        setWheel("onboarding_birth_year_picker", 2014f)
        rule.onNodeWithTag("onboarding_age_label").assertTextEquals("14 años")
        assertEquals(2012, range.endInclusive.year)

        next()
        setWheel("onboarding_height_picker", 260f)
        rule.onNodeWithTag("onboarding_height_picker").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "250 cm"),
        )

        setWheel("onboarding_weight_kg_picker", 15f)
        rule.onNodeWithTag("onboarding_weight_kg_picker").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "20 kg"),
        )
    }

    @Test
    fun measuresStep_showsHeightAndWeightWheelsWithUnitsAndTheDecimalPair() {
        show()
        goToStep(2)
        rule.onNodeWithTag("onboarding_label_height").assertTextEquals("Altura")
        rule.onNodeWithTag("onboarding_label_weight").assertTextEquals("Peso actual")
        rule.onNodeWithTag("onboarding_height_picker").assertContentDescriptionEquals("Altura")
        rule.onNodeWithTag("onboarding_height_picker").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "170 cm"),
        )
        rule.onNodeWithTag("onboarding_height_picker_item_170").assertExists()
        rule.onNodeWithTag("onboarding_weight_kg_picker").assertContentDescriptionEquals("Peso en kilos")
        rule.onNodeWithTag("onboarding_weight_decimal_picker").assertContentDescriptionEquals("Decimales del peso")
        rule.onNodeWithTag("onboarding_weight_kg_picker").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "70 kg"),
        )
        rule.onNodeWithTag("onboarding_weight_decimal_picker").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, ",0"),
        )
        rule.onNodeWithTag("onboarding_height_unit").assertTextEquals("cm")
        rule.onNodeWithTag("onboarding_weight_unit").assertTextEquals("kg")
    }

    @Test
    fun measuresStep_theDecimalWheelIsVisibleAndPicksTenths() {
        val events = mutableListOf<OnboardingEvent>()
        showMeasures(events)

        rule.onNodeWithTag("onboarding_weight_decimal_picker").assertIsDisplayed()
            .assertWidthIsAtLeast(WheelPickerDefaults.CompactWidth)
        rule.onNodeWithTag("onboarding_weight_decimal_picker")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(5f) }
        rule.waitForIdle()

        assertEquals(OnboardingEvent.SetWeight(705), events.last())
    }

    @Test
    fun measuresStep_unitLabelsStayOnOneLine_atFontScale1() = assertUnitLabelsOnOneLine(1f)

    @Test
    fun measuresStep_unitLabelsStayOnOneLine_atFontScale1p3() = assertUnitLabelsOnOneLine(1.3f)

    private fun assertUnitLabelsOnOneLine(scale: Float) {
        showMeasures(mutableListOf(), fontScale = scale)
        val lineHeight = with(Density(rule.density.density, scale)) { UNIT_LINE_HEIGHT_SP.sp.toDp() }
        listOf("onboarding_height_unit", "onboarding_weight_unit").forEach { tag ->
            val height = rule.onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot().height
            assertTrue("$tag is $height tall at scale $scale", height <= lineHeight)
        }
    }

    private fun showMeasures(events: MutableList<OnboardingEvent>, fontScale: Float = 1f) {
        val today = LocalDate(2026, 10, 14)
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AppTheme {
                    OnboardingScreen(
                        state = OnboardingUiState(
                            step = OnboardingStep.Measures,
                            birthDate = BiometricBounds.defaultBirthDate(today),
                            phase = OnboardingPhase.Content,
                            today = today,
                        ),
                        onEvent = { events += it },
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun activityStep_showsTheFiveCardsWithTheBusinessRuleTexts_andContinueNeedsOne() {
        show()
        goToStep(3)

        rule.onNodeWithTag("onboarding_step_indicator").assertTextEquals("3 / 4")
        ACTIVITY_TEXTS.forEach { (key, title, description) ->
            rule.onNodeWithTag("onboarding_activity_$key").assertTextContains(title, substring = true)
            rule.onNodeWithTag("onboarding_activity_$key").assertTextContains(description, substring = true)
            rule.onNodeWithTag("onboarding_activity_$key").assertHeightIsAtLeast(48.dp).assertIsNotSelected()
        }
        rule.onNodeWithTag("onboarding_continue").assertIsNotEnabled()

        tap("onboarding_activity_moderate")

        rule.onNodeWithTag("onboarding_activity_moderate").assertIsSelected()
        rule.onNodeWithTag("onboarding_continue").assertIsEnabled()
    }

    private fun goToGoalsStep() {
        show()
        goToStep(3)
        tap("onboarding_activity_moderate")
        next()
    }

    @Test
    fun completing_savesTheDataAndShowsTheGoalsStep() {
        goToGoalsStep()

        rule.onNodeWithTag("onboarding_step_title").assertTextEquals("Objetivos")
        rule.onNodeWithTag("onboarding_step_indicator").assertTextEquals("4 / 4")
        rule.onNodeWithTag("onboarding_goals_tdee").assertExists()
        rule.onNodeWithTag("onboarding_goals_save").assertTextEquals("Guardar objetivos").assertIsEnabled()
        rule.onNodeWithTag("onboarding_continue").assertDoesNotExist()
        assertEquals(1, profile.biometricsUpdates.size)
        assertEquals(Sex.Male, profile.biometricsUpdates.single().sex)
        assertEquals(ActivityLevel.Moderate, profile.biometricsUpdates.single().activityLevel)
        assertEquals(1, weights.inserts.size)

        tap("onboarding_back")

        rule.onNodeWithTag("onboarding_step_indicator").assertTextEquals("3 / 4")
    }

    @Test
    fun goalsStep_showsTheTdeeTheStrategiesAndTheDefaultMaintenanceTarget() {
        goToGoalsStep()

        rule.onNodeWithTag("onboarding_goals_tdee").assertTextContains("TDEE: ", substring = true)
        rule.onNodeWithTag("onboarding_goals_strategy_maintenance").assertIsSelected()
        rule.onNodeWithTag("onboarding_goals_pill").assertTextEquals("0 kcal · Mantenimiento")
        rule.onNodeWithTag("onboarding_goals_floor_warning").assertDoesNotExist()
    }

    @Test
    fun choosingDeficitAndSteppingDown_updatesThePill() {
        goToGoalsStep()

        tap("onboarding_goals_strategy_deficit")
        rule.onNodeWithTag("onboarding_goals_pill").assertTextEquals("−400 kcal · Déficit moderado")
        tap("onboarding_goals_adjust_decrement")
        tap("onboarding_goals_adjust_decrement")

        rule.onNodeWithTag("onboarding_goals_pill").assertTextEquals("−500 kcal · Déficit moderado")
        rule.onNodeWithTag("onboarding_goals_adjust_value").assertTextEquals("−500 kcal")
    }

    @Test
    fun savingTheGoals_confirmsOnceWithAHaptic_andCallsBackAfterTheDelay() {
        goToGoalsStep()
        tap("onboarding_goals_strategy_deficit")
        rule.mainClock.autoAdvance = false

        rule.onNodeWithTag("onboarding_goals_save").performClick()
        rule.mainClock.advanceTimeBy(CONFIRMATION_START_MS)

        rule.onNodeWithTag("onboarding_goals_saved").assertTextEquals("Objetivos guardados")
        rule.onNodeWithTag("onboarding_goals_save").assertDoesNotExist()
        assertEquals(listOf(HapticFeedbackType.Confirm), haptics)
        assertEquals(0, completions)
        assertEquals(1, goals.upserts.size)
        assertEquals(1, profile.completedCalls)

        rule.mainClock.advanceTimeBy(OnboardingDefaults.ConfirmationDuration.inWholeMilliseconds)

        assertEquals(1, completions)
        assertEquals(listOf(HapticFeedbackType.Confirm), haptics)
    }

    @Test
    fun rotationDuringTheConfirmation_doesNotRepeatTheHaptic() {
        val restoration = StateRestorationTester(rule)
        val today = LocalDate(2026, 10, 14)
        restoration.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides recordingHaptics) {
                AppTheme {
                    OnboardingScreen(
                        state = OnboardingUiState(
                            step = OnboardingStep.Goals,
                            birthDate = BiometricBounds.defaultBirthDate(today),
                            phase = OnboardingPhase.Completed,
                            today = today,
                        ),
                        onEvent = {},
                        onComplete = { completions++ },
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(CONFIRMATION_START_MS)
        assertEquals(1, haptics.size)

        restoration.emulateSavedInstanceStateRestore()
        rule.mainClock.advanceTimeBy(CONFIRMATION_START_MS)

        assertEquals(1, haptics.size)
    }

    @Test
    fun goalsStepOffline_disablesSaveAndExplainsWhy() {
        goToGoalsStep()

        connectivity.state.value = false
        rule.waitForIdle()

        rule.onNodeWithTag("onboarding_goals_save").assertIsNotEnabled()
        rule.onNodeWithTag("onboarding_offline_hint").assertTextEquals("Sin conexión. Conéctate para guardar tus datos")
    }

    @Test
    fun goalsSaveError_showsMessageOnly_andTheSaveButtonCompletes() {
        goToGoalsStep()
        goals.upsertResult = Result.failure(AppException(AppError.Network))

        tap("onboarding_goals_save")

        rule.onNodeWithText("Sin conexión. Inténtalo de nuevo").assertExists()
        rule.onAllNodesWithText("Reintentar").assertCountEquals(0)
        rule.onNodeWithTag("onboarding_goals_save").assertExists()
        assertEquals(0, completions)

        goals.upsertResult = Result.success(Unit)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("onboarding_goals_save").performClick()
        rule.mainClock.advanceTimeBy(CONFIRMATION_START_MS)

        rule.onNodeWithTag("onboarding_goals_saved").assertExists()
    }

    @Test
    fun saveError_showsAMessageOnlySnackbar_andContinueFinishesTheFlow() {
        show()
        goToStep(3)
        tap("onboarding_activity_moderate")
        profile.updateResult = Result.failure(AppException(AppError.Network))

        next()

        rule.onNodeWithText("Sin conexión. Inténtalo de nuevo").assertExists()
        rule.onAllNodesWithText("Reintentar").assertCountEquals(0)
        rule.onNodeWithTag("onboarding_activity_moderate").assertIsSelected()
        rule.onNodeWithTag("onboarding_goals_save").assertDoesNotExist()

        profile.updateResult = Result.success(Unit)
        next()

        rule.onNodeWithTag("onboarding_goals_save").assertExists()
    }

    @Test
    fun saveStepOffline_disablesContinueAndExplainsWhy_whileEarlierStepsStayEnabled() {
        show()
        goToStep(2)
        connectivity.state.value = false
        rule.waitForIdle()
        rule.onNodeWithTag("onboarding_continue").assertIsEnabled()
        rule.onNodeWithTag("onboarding_offline_hint").assertDoesNotExist()

        next()
        tap("onboarding_activity_light")

        rule.onNodeWithTag("onboarding_continue").assertIsNotEnabled()
        rule.onNodeWithTag("onboarding_offline_hint").assertTextEquals("Sin conexión. Conéctate para guardar tus datos")

        connectivity.state.value = true
        rule.waitForIdle()
        rule.onNodeWithTag("onboarding_continue").assertIsEnabled()
        rule.onNodeWithTag("onboarding_offline_hint").assertDoesNotExist()
    }

    @Test
    fun resume_prefillsTheSexAndKeepsContinueEnabled() {
        profile.biometrics =
            Result.success(SavedBiometrics(Sex.Female, LocalDate(1999, 6, 15), 165, ActivityLevel.Light))

        show()

        rule.onNodeWithTag("onboarding_sex_female").assertIsSelected()
        rule.onNodeWithTag("onboarding_continue").assertIsEnabled()
        rule.onNodeWithTag("onboarding_age_label").assertTextEquals("27 años")
    }

    @Test
    fun loadFailure_showsTheErrorStateWithRetry() {
        profile.biometrics = Result.failure(AppException(AppError.Network))
        show()

        rule.onNodeWithTag("onboarding_error").assertExists()
        rule.onNodeWithTag("onboarding_continue").assertDoesNotExist()

        profile.biometrics = Result.success(SavedBiometrics())
        tap("onboarding_error_action")

        rule.onNodeWithTag("onboarding_sex_male").assertExists()
    }

    @Test
    fun loadingState_showsASkeleton() {
        val today = LocalDate(2026, 10, 14)
        rule.setContent {
            AppTheme {
                OnboardingScreen(
                    state = OnboardingUiState(birthDate = BiometricBounds.defaultBirthDate(today), today = today),
                    onEvent = {},
                )
            }
        }
        rule.mainClock.autoAdvance = false

        rule.onNodeWithTag("onboarding_loading").assertExists()
        rule.onNodeWithTag("onboarding_continue").assertDoesNotExist()
    }
}
