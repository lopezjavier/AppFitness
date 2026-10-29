package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeBodyMeasurementRepository
import io.github.lopezjavier.appfitness.app.FakeClock
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNotificationSettingsRepository
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.app.FakeProfileRepository
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.MyTraining
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.model.TrainingPlace
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import io.github.lopezjavier.appfitness.domain.usecase.SavePreferences
import io.github.lopezjavier.appfitness.feature.food.FakeFoodPreferencesRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

/**
 * US-65 on the "Mi entrenamiento" editor, driven by the real [PreferencesViewModel] over fakes: the intention list,
 * choosing and saving it, the place seeding the equipment, and the optional days and session length.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1000dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class MyTrainingSectionTest {
    @get:Rule
    val rule = createComposeRule()

    private val prefs = FakeTrainingPreferencesRepository()
    private val connectivity = FakeConnectivityObserver()
    private val messages = RecordingMessages()
    private val haptics = CountingHaptics()
    private var closes = 0

    private fun show(stored: TrainingPreferences = TrainingPreferences(false)) {
        prefs.result = Result.success(stored)
        val profile = FakeProfileRepository(Result.success(ProfileSummary("Ana", null, true))).apply {
            biometrics = Result.success(SavedBiometrics(Sex.Male, LocalDate(2000, 3, 1), 178, ActivityLevel.Moderate))
        }
        val weight = BodyMeasurement("w1", Instant.parse("2026-10-10T08:00:00Z"), 80.0, MeasurementSource.Manual)
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalAppMessages provides messages, LocalAppHaptics provides haptics) {
                    val vm = remember {
                        PreferencesViewModel(
                            profile,
                            FakeNutritionGoalsRepository(
                                NutritionGoals(GoalStrategy.Deficit, -400, 2000, MacroSplit.DEFAULT),
                            ),
                            FakeBodyMeasurementRepository(weight),
                            prefs,
                            FakeFoodPreferencesRepository(),
                            FakeNotificationSettingsRepository(),
                            SavePreferences(profile),
                            connectivity,
                            FakeClock(),
                            TimeZone.UTC,
                            SavedStateHandle(mapOf("section" to "mi_entrenamiento")),
                        )
                    }
                    PreferencesSectionRoot(onClose = { closes++ }, viewModel = vm)
                }
            }
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        val inSheet = tag.contains("_option_") || tag.endsWith("_save")
        if (inSheet) {
            rule.onNodeWithTag(
                tag,
            ).performClick()
        } else {
            rule.onNodeWithTag(tag).performScrollTo().performClick()
        }
        rule.waitForIdle()
    }

    private fun assertChips(checked: Set<Equipment>) = Equipment.entries.forEach {
        val chip = rule.onNodeWithTag("preferences_equipment_${it.name.lowercase()}").performScrollTo()
        if (it in checked) chip.assertIsOn() else chip.assertIsOff()
    }

    @Test
    fun intentionPicker_listsExactlyTheFiveLabels_plusSinDefinir() {
        show()
        tap("preferences_intention_field")

        listOf("Ganar fuerza", "Ganar musculatura", "Perder grasa", "Recomposición", "Mantenimiento").forEach {
            rule.onNodeWithText(it).assertIsDisplayed()
        }
        rule.onNodeWithTag("preferences_intention_option_none").assertTextContains("Sin definir")
        listOf("strength", "muscle", "fat_loss", "recomposition", "maintenance").forEach {
            rule.onNodeWithTag("preferences_intention_option_$it").assertIsDisplayed()
        }
    }

    @Test
    fun choosingTheIntention_updatesTheField_andGuardarStoresIt() {
        show()
        rule.onNodeWithTag("preferences_mi_entrenamiento_save").assertIsNotEnabled()

        tap("preferences_intention_field")
        tap("preferences_intention_option_strength")

        rule.onNodeWithTag("preferences_intention_field").assertTextContains("Ganar fuerza", substring = true)
        rule.onNodeWithTag("preferences_mi_entrenamiento_save").assertIsEnabled().performClick()
        rule.waitForIdle()
        assertEquals(listOf(MyTraining(intention = TrainingIntention.Strength)), prefs.savedMyTraining)
        assertEquals(1, closes)
    }

    @Test
    fun pickingCasa_selectsDumbbellBandAndBodyweight() {
        show()
        tap("preferences_place_home")
        assertChips(setOf(Equipment.Dumbbell, Equipment.Band, Equipment.Bodyweight))
    }

    @Test
    fun pickingGimnasio_selectsAllEquipment_andOtroOnlyBodyweight() {
        show()
        tap("preferences_place_gym")
        assertChips(Equipment.entries.toSet())

        tap("preferences_place_other")
        assertChips(setOf(Equipment.Bodyweight))
    }

    @Test
    fun afterPickingAPlace_theChipsStayEditable() {
        show()
        tap("preferences_place_home")
        tap("preferences_equipment_barbell")
        tap("preferences_equipment_band")

        assertChips(setOf(Equipment.Dumbbell, Equipment.Bodyweight, Equipment.Barbell))
        tap("preferences_mi_entrenamiento_save")
        assertEquals(
            listOf(
                MyTraining(
                    place = TrainingPlace.Home,
                    equipment = setOf(Equipment.Dumbbell, Equipment.Bodyweight, Equipment.Barbell),
                ),
            ),
            prefs.savedMyTraining,
        )
    }

    @Test
    fun dumbbellChip_readsMancuernas() {
        show()
        rule.onNodeWithTag("preferences_equipment_dumbbell").performScrollTo().assertTextContains("Mancuernas")
    }

    @Test
    fun withNothingDefined_theRowsReadSinDefinir_andSavingStoresNulls() {
        show(TrainingPreferences(false, intention = TrainingIntention.Muscle, daysPerWeek = 4, sessionMinutes = 60))
        tap("preferences_intention_field")
        tap("preferences_intention_option_none")
        repeat(4) { tap("preferences_days_stepper_decrement") }
        repeat(4) { tap("preferences_session_stepper_decrement") }

        rule.onNodeWithTag("preferences_intention_field").assertTextContains("Sin definir", substring = true)
        rule.onNodeWithTag("preferences_days_stepper_value").assertTextEquals("Sin definir")
        rule.onNodeWithTag("preferences_session_stepper_value").assertTextEquals("Sin definir")
        tap("preferences_mi_entrenamiento_save")
        assertEquals(listOf(MyTraining()), prefs.savedMyTraining)
    }

    @Test
    fun days_useAStepperWithoutPickerSheet_fromSinDefinirUpToSeven() {
        show()
        rule.onNodeWithTag("preferences_days_field").assertDoesNotExist()
        rule.onNodeWithTag("preferences_days_stepper_value").performScrollTo().assertTextEquals("Sin definir")
        rule.onNodeWithTag("preferences_days_stepper_decrement").assertIsNotEnabled()

        tap("preferences_days_stepper_increment")
        rule.onNodeWithTag("preferences_days_stepper_value").assertTextEquals("3 días")
        tap("preferences_days_stepper_decrement")
        tap("preferences_days_stepper_decrement")
        rule.onNodeWithTag("preferences_days_stepper_value").assertTextEquals("1 día")
        repeat(6) { tap("preferences_days_stepper_increment") }
        rule.onNodeWithTag("preferences_days_stepper_value").assertTextEquals("7 días")
        rule.onNodeWithTag("preferences_days_stepper_increment").assertIsNotEnabled()
        tap("preferences_mi_entrenamiento_save")
        assertEquals(listOf(MyTraining(daysPerWeek = 7)), prefs.savedMyTraining)
    }

    @Test
    fun days_goingBelowOne_returnsToSinDefinir() {
        show(TrainingPreferences(false, daysPerWeek = 1))
        tap("preferences_days_stepper_decrement")

        rule.onNodeWithTag("preferences_days_stepper_value").assertTextEquals("Sin definir")
    }

    @Test
    fun session_stepsByFifteenMinutes_andReadsDurationsWithoutZeroParts() {
        show()
        rule.onNodeWithTag("preferences_session_stepper_value").performScrollTo().assertTextEquals("Sin definir")

        tap("preferences_session_stepper_increment")
        rule.onNodeWithTag("preferences_session_stepper_value").assertTextEquals("1 h")
        tap("preferences_session_stepper_increment")
        rule.onNodeWithTag("preferences_session_stepper_value").assertTextEquals("1 h 15 min")
        repeat(2) { tap("preferences_session_stepper_decrement") }
        rule.onNodeWithTag("preferences_session_stepper_value").assertTextEquals("45 min")
        repeat(3) { tap("preferences_session_stepper_increment") }
        rule.onNodeWithTag("preferences_session_stepper_value").assertTextEquals("1 h 30 min")
        repeat(6) { tap("preferences_session_stepper_increment") }
        rule.onNodeWithTag("preferences_session_stepper_value").assertTextEquals("3 h")
        rule.onNodeWithTag("preferences_session_stepper_increment").assertIsNotEnabled()
    }

    @Test
    fun session_withAStoredValueOffTheStep_snapsToTheNextMultiple() {
        show(TrainingPreferences(false, sessionMinutes = 50))
        tap("preferences_session_stepper_increment")
        rule.onNodeWithTag("preferences_session_stepper_value").assertTextEquals("1 h")

        tap("preferences_session_stepper_decrement")
        rule.onNodeWithTag("preferences_session_stepper_value").assertTextEquals("45 min")
        tap("preferences_mi_entrenamiento_save")
        assertEquals(listOf(MyTraining(sessionMinutes = 45)), prefs.savedMyTraining)
    }

    @Test
    fun intentionHelp_opensTheInfoSheetListingTheFiveIntentions() {
        show()
        tap("preferences_intention_help")

        rule.onNodeWithTag("preferences_intention_help_sheet").assertIsDisplayed()
        listOf("Ganar fuerza:", "Ganar musculatura:", "Perder grasa:", "Recomposición:", "Mantenimiento:").forEach {
            rule.onNodeWithText(it, substring = true).assertIsDisplayed()
        }
    }

    @Test
    fun leavingWithChanges_asksToDiscard() {
        show()
        tap("preferences_intention_field")
        tap("preferences_intention_option_muscle")

        rule.onNodeWithTag("preferences_mi_entrenamiento_back").performClick()

        rule.onNodeWithTag("preferences_mi_entrenamiento_discard_dialog").assertIsDisplayed()
        rule.onNodeWithText("¿Descartar los cambios?").assertIsDisplayed()
    }

    @Test
    fun offline_guardarIsDisabled_withTheHint() {
        show()
        tap("preferences_intention_field")
        tap("preferences_intention_option_muscle")
        connectivity.state.value = false
        rule.waitForIdle()

        rule.onNodeWithTag("preferences_mi_entrenamiento_save").assertIsNotEnabled()
        rule.onNodeWithTag("offline_hint").assertTextEquals("Sin conexión: no se puede guardar")
    }
}
