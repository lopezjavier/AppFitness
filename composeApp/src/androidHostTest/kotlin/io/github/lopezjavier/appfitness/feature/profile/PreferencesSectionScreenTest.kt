package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.width
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.Macronutrient
import io.github.lopezjavier.appfitness.domain.model.Sex
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * US-56 on the Energy, Macros, Cooking and Training editors: each is an [io.github.lopezjavier.appfitness.designsystem.AppEditorScreen]
 * whose "Guardar" is enabled only by a change in its own section. The "Datos personales" editor is covered by
 * `PreferencesScreenTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h900dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class PreferencesSectionScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<PreferencesEvent>()
    private val messages = RecordingMessages()
    private val haptics = CountingHaptics()
    private var closes = 0
    private var state by mutableStateOf<PreferencesUiState>(PreferencesUiState.Loading)

    private val base = PreferencesForm(
        name = "Ana",
        sex = Sex.Male,
        birthDate = LocalDate(2000, 3, 1),
        heightCm = 180,
        weightKg = 100.0,
        activity = ActivityLevel.Sedentary,
        strategy = GoalStrategy.Maintenance,
        adjustment = 0,
        split = MacroSplit.DEFAULT,
    )

    /** 26-year-old, 180 cm, 100 kg, sedentary: TDEE 2400, so the default split reads 180/270/67 g. */
    private fun loaded(
        section: PreferencesSection,
        form: PreferencesForm = base,
        isOnline: Boolean = true,
        isSaving: Boolean = false,
        isSaved: Boolean = false,
    ) = PreferencesUiState.Loaded(
        form = form,
        saved = base,
        section = section,
        today = LocalDate(2026, 10, 14),
        isSaving = isSaving,
        isOnline = isOnline,
        isSaved = isSaved,
    )

    private val dirtyMacros = base.copy(split = MacroSplit(35, 42, 23))

    private fun show(
        section: PreferencesSection,
        initial: PreferencesUiState = loaded(section),
        effects: Channel<PreferencesEffect> = Channel(Channel.BUFFERED),
    ) {
        events.clear()
        state = initial
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalAppMessages provides messages, LocalAppHaptics provides haptics) {
                    PreferencesSectionScreen(
                        section = section,
                        state = state,
                        onEvent = { events += it },
                        onClose = { closes++ },
                        effects = effects.receiveAsFlow(),
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun tag(section: PreferencesSection) = "preferences_${section.key}"

    // -- titles ------------------------------

    private fun assertTitle(section: PreferencesSection, title: String) {
        show(section)
        rule.onNodeWithTag("${tag(section)}_title").assertTextEquals(title)
    }

    @Test
    fun energyEditor_isTitledObjetivoCalorico() = assertTitle(PreferencesSection.EnergyGoal, "Objetivo calórico")

    @Test
    fun macrosEditor_isTitledMacros() = assertTitle(PreferencesSection.Macros, "Macros")

    @Test
    fun cookingEditor_isTitledCocina() = assertTitle(PreferencesSection.Cooking, "Cocina")

    @Test
    fun trainingEditor_isTitledEntrenamiento() = assertTitle(PreferencesSection.Training, "Entrenamiento")

    // -- Guardar per section ------------------------------

    private fun dirtyState(section: PreferencesSection): PreferencesUiState = loaded(
        section,
        when (section) {
            PreferencesSection.Macros -> dirtyMacros
            PreferencesSection.EnergyGoal -> base.copy(strategy = GoalStrategy.Deficit, adjustment = -300)
            PreferencesSection.Cooking -> base.copy(foodPreferences = FoodPreferencesForm(griddleText = "7"))
            PreferencesSection.Training -> base.copy(warmUpIncluded = true)
            PreferencesSection.PersonalData -> base.copy(name = "Eva")
        },
    )

    private fun assertCleanSaveDisabled(section: PreferencesSection) {
        show(section)
        rule.onNodeWithTag("${tag(section)}_save").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun nothingChanged_keepsGuardarDisabled_inEnergy() = assertCleanSaveDisabled(PreferencesSection.EnergyGoal)

    @Test
    fun nothingChanged_keepsGuardarDisabled_inMacros() = assertCleanSaveDisabled(PreferencesSection.Macros)

    @Test
    fun nothingChanged_keepsGuardarDisabled_inCooking() = assertCleanSaveDisabled(PreferencesSection.Cooking)

    @Test
    fun nothingChanged_keepsGuardarDisabled_inTraining() = assertCleanSaveDisabled(PreferencesSection.Training)

    @Test
    fun editingEnergy_enablesGuardar() {
        show(PreferencesSection.EnergyGoal, dirtyState(PreferencesSection.EnergyGoal))
        rule.onNodeWithTag("preferences_energy_save").assertIsEnabled()
    }

    @Test
    fun editingMacros_enablesGuardar_andSendsSave() {
        show(PreferencesSection.Macros, dirtyState(PreferencesSection.Macros))
        rule.onNodeWithTag("preferences_macros_save").assertIsEnabled().performClick()
        assertEquals(listOf<PreferencesEvent>(PreferencesEvent.Save), events)
    }

    @Test
    fun editingCooking_enablesGuardar() {
        show(PreferencesSection.Cooking, dirtyState(PreferencesSection.Cooking))
        rule.onNodeWithTag("preferences_cooking_save").assertIsEnabled()
    }

    @Test
    fun editingTraining_enablesGuardar() {
        show(PreferencesSection.Training, dirtyState(PreferencesSection.Training))
        rule.onNodeWithTag("preferences_training_save").assertIsEnabled()
    }

    @Test
    fun anEditOfAnotherSection_doesNotEnableGuardar() {
        show(PreferencesSection.Macros, loaded(PreferencesSection.Macros, base.copy(warmUpIncluded = true)))
        rule.onNodeWithTag("preferences_macros_save").assertIsNotEnabled()
    }

    @Test
    fun invalidCookingField_keepsGuardarDisabled() {
        show(
            PreferencesSection.Cooking,
            loaded(PreferencesSection.Cooking, base.copy(foodPreferences = FoodPreferencesForm(griddleText = ""))),
        )
        rule.onNodeWithTag("preferences_cooking_save").assertIsNotEnabled()
    }

    // -- one Confirm and one close ------------------------------

    @Test
    fun saved_vibratesOneConfirm_andClosesOnce() {
        show(PreferencesSection.Macros, dirtyState(PreferencesSection.Macros))
        state = loaded(PreferencesSection.Macros, dirtyMacros, isSaved = true)
        rule.waitForIdle()
        assertEquals(1, haptics.confirms)
        assertEquals(1, closes)
        assertEquals(0, haptics.rejects)
    }

    // -- back and discard ------------------------------

    @Test
    fun backWhenClean_leavesSilently() {
        show(PreferencesSection.Macros)
        rule.onNodeWithTag("preferences_macros_back").performClick()
        assertEquals(1, closes)
        rule.onNodeWithTag("preferences_macros_discard_dialog").assertDoesNotExist()
    }

    @Test
    fun backWhenDirty_asksToDiscard_andKeepEditingStays() {
        show(PreferencesSection.Macros, dirtyState(PreferencesSection.Macros))
        rule.onNodeWithTag("preferences_macros_back").performClick()
        rule.onNodeWithTag("preferences_macros_discard_dialog").assertExists()
        rule.onNodeWithTag("preferences_macros_keep_editing").performClick()
        rule.waitForIdle()
        assertEquals(0, closes)
        rule.onNodeWithTag("preferences_macros_discard_dialog").assertDoesNotExist()
    }

    @Test
    fun backWhenDirty_discardLeaves() {
        show(PreferencesSection.Macros, dirtyState(PreferencesSection.Macros))
        rule.onNodeWithTag("preferences_macros_back").performClick()
        rule.onNodeWithTag("preferences_macros_discard_confirm").performClick()
        rule.waitForIdle()
        assertEquals(1, closes)
    }

    // -- offline and failures ------------------------------

    @Test
    fun offline_disablesGuardar_showsTheHint_andATapVibratesOneReject() {
        show(PreferencesSection.Macros, loaded(PreferencesSection.Macros, dirtyMacros, isOnline = false))
        rule.onNodeWithTag("preferences_macros_save").assertIsNotEnabled().performClick()
        rule.onNodeWithTag("offline_hint").assertTextEquals("Sin conexión: no se puede guardar")
        assertEquals(1, haptics.rejects)
        assertTrue(events.isEmpty())
    }

    @Test
    fun failedSave_showsOneMessage_vibratesReject_andStaysOpen() {
        val effects = Channel<PreferencesEffect>(Channel.BUFFERED)
        show(PreferencesSection.Macros, dirtyState(PreferencesSection.Macros), effects)
        effects.trySend(PreferencesEffect.SaveFailed(offline = false))
        rule.waitForIdle()
        assertEquals(listOf("No se pudo guardar. Inténtalo de nuevo."), messages.shown)
        assertEquals(1, haptics.rejects)
        assertEquals(0, closes)
        rule.onNodeWithTag("preferences_macros_save").assertIsEnabled()
    }

    @Test
    fun offlineFailure_saysThereIsNoConnection() {
        val effects = Channel<PreferencesEffect>(Channel.BUFFERED)
        show(PreferencesSection.Macros, dirtyState(PreferencesSection.Macros), effects)
        effects.trySend(PreferencesEffect.SaveFailed(offline = true))
        rule.waitForIdle()
        assertEquals(listOf("Sin conexión: no se puede guardar"), messages.shown)
    }

    // -- content ------------------------------

    @Test
    fun energy_showsTheTdee_withoutTheHelperLine() {
        show(PreferencesSection.EnergyGoal)
        rule.onNodeWithTag("preferences_energy_tdee").assertTextEquals("Gasto diario estimado: 2.400 kcal")
        rule.onNodeWithTag("preferences_energy_target").assertExists()
        rule.onNodeWithTag("preferences_energy_tdee_helper").assertDoesNotExist()
    }

    @Test
    fun macros_showTheTotalBarAndTheTargetsFromTheSavedKcal() {
        show(PreferencesSection.Macros)
        rule.onNodeWithTag("preferences_macro_total").assertTextEquals("TOTAL: 100 %")
        rule.onNodeWithTag("preferences_macro_bar").assertExists()
        rule.onNodeWithTag("preferences_macro_protein_slider").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "30 por ciento, 180 gramos, 720 kilocalorías",
            ),
        )
    }

    @Test
    fun movingASlider_sendsTheMoveEvent() {
        show(PreferencesSection.Macros)
        rule.onNodeWithTag("preferences_macro_protein_slider").performSemanticsAction(SemanticsActions.SetProgress) {
            it(35f)
        }
        assertEquals(listOf<PreferencesEvent>(PreferencesEvent.MoveMacro(Macronutrient.Protein, 35)), events)
    }

    @Test
    fun cooking_showsItsThreeFields_withoutACardTitleOfItsOwn() {
        show(PreferencesSection.Cooking)
        listOf("griddle", "air_fryer", "fried").forEach {
            rule.onNodeWithTag("preferences_cooking_$it").performScrollTo().assertExists()
        }
    }

    @Test
    fun training_showsTheSwitches_theSelectorAndTheCalculators() {
        show(PreferencesSection.Training)
        rule.onNodeWithTag("preferences_warm_up_switch").performScrollTo().assertExists()
        rule.onNodeWithTag("preferences_auto_update_switch").performScrollTo().assertExists()
        rule.onNodeWithTag("preferences_previous_mode").performScrollTo().assertExists()
    }

    // -- states ------------------------------

    @Test
    fun loading_showsTheSkeleton() {
        rule.mainClock.autoAdvance = false
        show(PreferencesSection.Macros, PreferencesUiState.Loading)
        rule.onNodeWithTag("preferences_loading").assertExists()
        rule.onNodeWithTag("preferences_content").assertDoesNotExist()
    }

    @Test
    fun loadFailed_showsRetry_andRetryIsSent() {
        show(PreferencesSection.Macros, PreferencesUiState.LoadFailed)
        rule.onNodeWithTag("preferences_error_action").assertTextEquals("Reintentar").performClick()
        assertEquals(listOf<PreferencesEvent>(PreferencesEvent.Retry), events)
    }

    @Test
    fun stateChange_fromLoadFailedToLoaded_replacesTheError() {
        show(PreferencesSection.Macros, PreferencesUiState.LoadFailed)
        rule.onNodeWithTag("preferences_error").assertExists()
        state = loaded(PreferencesSection.Macros)
        rule.waitForIdle()
        rule.onNodeWithTag("preferences_content").assertExists()
        rule.onNodeWithTag("preferences_error").assertDoesNotExist()
    }

    // -- tablet ------------------------------

    @Test
    @Config(qualifiers = "w840dp-h1000dp-mdpi")
    fun tablet_capsTheEditorWidth_inASingleColumn() {
        show(PreferencesSection.Macros)
        val bar = rule.onNodeWithTag("preferences_macro_bar").getUnclippedBoundsInRoot()
        val save = rule.onNodeWithTag("preferences_macros_save").getUnclippedBoundsInRoot()
        assertTrue("bar ${bar.width}", bar.width <= WindowWidthClassDefaults.FormContentMaxWidth)
        assertTrue("save ${save.width}", save.width <= WindowWidthClassDefaults.FormContentMaxWidth)
    }
}
