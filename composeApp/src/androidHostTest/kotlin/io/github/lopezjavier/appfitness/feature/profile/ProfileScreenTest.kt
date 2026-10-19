package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.designsystem.AppTheme
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

/** US-05 sign out, US-06 editing data, US-07 saving the goals and US-08 macro split on the Profile tab. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class ProfileScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate(2026, 10, 14)
    private val events = mutableListOf<ProfileEvent>()
    private var catalogOpens = 0

    /** 26-year-old, 180 cm, 100 kg, sedentary: TDEE 2400, so the default split reads 180/270/67 g. */
    private fun loaded(
        split: MacroSplit = MacroSplit.DEFAULT,
        saved: MacroSplit = split,
        isOnline: Boolean = true,
        isSaving: Boolean = false,
    ): ProfileUiState.Loaded {
        val form = ProfileForm(
            sex = Sex.Male,
            birthDate = LocalDate(2000, 3, 1),
            heightCm = 180,
            activity = ActivityLevel.Sedentary,
            strategy = GoalStrategy.Maintenance,
            adjustment = 0,
            split = split,
        )
        return ProfileUiState.Loaded(
            form = form,
            saved = form.copy(split = saved),
            weightKg = 100.0,
            today = today,
            isSaving = isSaving,
            isOnline = isOnline,
        )
    }

    private fun show(
        state: ProfileUiState = loaded(),
        fontScale: Float = 1f,
        effects: Channel<ProfileEffect> = Channel(Channel.BUFFERED),
        withCatalog: Boolean = false,
    ) {
        events.clear()
        catalogOpens = 0
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AppTheme {
                    ProfileScreen(
                        state = state,
                        onEvent = { events += it },
                        onOpenCatalog = if (withCatalog) ({ catalogOpens++ }) else null,
                        effects = effects.receiveAsFlow(),
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    // -- US-08 initial display ------------------------------------------------------------------------------------

    @Test
    fun initialDisplay_showsTheTotalBadgeTheBarAndTheMacroTexts() {
        show()
        rule.onNodeWithTag("profile_macro_total").performScrollTo().assertTextEquals("TOTAL: 100 %")
        rule.onNodeWithTag("profile_macro_bar").assertExists()
        rule.onNodeWithText("30 %").assertExists()
        rule.onNodeWithText("180 g · 720 kcal").assertExists()
        rule.onNodeWithText("45 %").assertExists()
        rule.onNodeWithText("270 g · 1.080 kcal").assertExists()
        rule.onNodeWithText("25 %").assertExists()
        rule.onNodeWithText("67 g · 600 kcal").assertExists()
    }

    @Test
    fun sliders_speakPercentGramsAndKcal() {
        show()
        rule.onNodeWithTag("profile_macro_protein_slider").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "30 por ciento, 180 gramos, 720 kilocalorías",
            ),
        )
    }

    @Test
    fun movingASlider_sendsTheMoveEvent() {
        show()
        rule.onNodeWithTag("profile_macro_protein_slider").performSemanticsAction(SemanticsActions.SetProgress) {
            it(35f)
        }
        assertEquals(listOf<ProfileEvent>(ProfileEvent.MoveMacro(Macronutrient.Protein, 35)), events)
    }

    @Test
    fun rebalancedSplit_isShown() {
        show(loaded(split = MacroSplit(35, 42, 23), saved = MacroSplit.DEFAULT))
        rule.onNodeWithText("35 %").assertExists()
        rule.onNodeWithText("42 %").assertExists()
        rule.onNodeWithText("23 %").assertExists()
        rule.onNodeWithText("210 g · 840 kcal").assertExists()
    }

    // -- US-06 editing data ---------------------------------------------------------------------------------------

    @Test
    fun weight_isReadOnly_withItsHint() {
        show()
        rule.onNodeWithTag(
            "profile_weight_value",
        ).performScrollTo().assertTextEquals("100,0 kg").assertHasNoClickAction()
        rule.onNodeWithText("Se actualiza desde Progreso").assertExists()
    }

    @Test
    fun personalRows_haveAccessibleNames_andTheTouchTarget() {
        show()
        rule.onNodeWithTag("profile_height_row")
            .assertContentDescriptionEquals("Altura, 180 centímetros, editar")
            .assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("profile_birth_date_row").assertContentDescriptionEquals("Edad, 26 años, editar")
        rule.onNodeWithTag("profile_activity_row").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun sexSelector_sendsTheSelection() {
        show()
        rule.onNodeWithTag("profile_sex_female").performClick()
        assertEquals(listOf<ProfileEvent>(ProfileEvent.SelectSex(Sex.Female)), events)
    }

    @Test
    fun heightSheet_appliesOnlyWithListo() {
        show()
        rule.onNodeWithTag("profile_height_row").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("profile_sheet_height").assertExists()
        assertTrue("nothing is applied while the sheet is open", events.isEmpty())

        rule.onNodeWithTag("profile_sheet_done").performClick()
        rule.waitForIdle()
        assertEquals(listOf<ProfileEvent>(ProfileEvent.SetHeight(180)), events)
    }

    @Test
    fun activitySheet_listsTheLevels_andListoAppliesTheChoice() {
        show()
        rule.onNodeWithTag("profile_activity_row").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("profile_activity_light").performClick()
        rule.onNodeWithTag("profile_sheet_done").performClick()
        rule.waitForIdle()
        assertEquals(listOf<ProfileEvent>(ProfileEvent.SelectActivity(ActivityLevel.Light)), events)
    }

    @Test
    fun birthDateSheet_hasTheWheels() {
        show()
        rule.onNodeWithTag("profile_birth_date_row").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("profile_sheet_birth_date").assertExists()
        rule.onNodeWithTag("profile_birth_year_picker").assertExists()
    }

    // -- US-07 saving the goals on Profile ------------------------------------------------------------------------

    @Test
    fun energyCard_showsTheTdee() {
        show()
        rule.onNodeWithTag("profile_energy_tdee").performScrollTo().assertExists()
        rule.onNodeWithTag("profile_energy_target").assertExists()
    }

    @Test
    fun save_isDisabledWhenNothingChanged() {
        show()
        rule.onNodeWithTag("profile_save_button").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun save_isEnabledWhenDirty_andSendsSave() {
        show(loaded(split = MacroSplit(35, 42, 23), saved = MacroSplit.DEFAULT))
        rule.onNodeWithTag("profile_save_button").assertIsEnabled().performClick()
        assertEquals(listOf<ProfileEvent>(ProfileEvent.Save), events)
    }

    @Test
    fun save_isDisabledOffline() {
        show(loaded(split = MacroSplit(35, 42, 23), saved = MacroSplit.DEFAULT, isOnline = false))
        rule.onNodeWithTag("profile_save_button").assertIsNotEnabled()
    }

    @Test
    fun save_isDisabledWhileSaving() {
        rule.mainClock.autoAdvance = false
        show(loaded(split = MacroSplit(35, 42, 23), saved = MacroSplit.DEFAULT, isSaving = true))
        rule.onNodeWithTag("profile_save_button").assertIsNotEnabled()
    }

    @Test
    fun saveFooter_staysVisibleWhileTheCardsScroll() {
        show()
        rule.onNodeWithTag("profile_sign_out_button").performScrollTo()
        rule.onNodeWithTag("profile_save_button").assertIsDisplayed()
    }

    @Test
    fun savedEffect_showsTheConfirmationSnackbar() {
        val effects = Channel<ProfileEffect>(Channel.BUFFERED)
        show(effects = effects)
        effects.trySend(ProfileEffect.Saved)
        rule.waitForIdle()
        rule.onNodeWithText("Cambios guardados").assertIsDisplayed()
    }

    @Test
    fun failedEffect_showsTheErrorMessages() {
        val effects = Channel<ProfileEffect>(Channel.BUFFERED)
        show(effects = effects)
        effects.trySend(ProfileEffect.SaveFailed(offline = false))
        rule.waitForIdle()
        rule.onNodeWithText("No se pudo guardar. Inténtalo de nuevo").assertIsDisplayed()
    }

    // -- states -----------------------------------------------------------------------------------------------------

    @Test
    fun loading_showsTheSkeleton() {
        rule.mainClock.autoAdvance = false
        show(ProfileUiState.Loading)
        rule.onNodeWithTag("profile_loading").assertExists()
        rule.onNodeWithTag("profile_content").assertDoesNotExist()
    }

    @Test
    fun loadFailed_showsRetry_andRetryIsSent() {
        show(ProfileUiState.LoadFailed)
        rule.onNodeWithTag("profile_error_action").assertTextEquals("Reintentar").performClick()
        assertEquals(listOf<ProfileEvent>(ProfileEvent.Retry), events)
        rule.onNodeWithTag("profile_sign_out_button").assertExists()
    }

    @Test
    fun stateChange_fromLoadingToLoaded_replacesTheSkeleton() {
        var state by mutableStateOf<ProfileUiState>(ProfileUiState.LoadFailed)
        rule.setContent { AppTheme { ProfileScreen(state, {}) } }
        rule.onNodeWithTag("profile_error").assertExists()
        state = loaded()
        rule.waitForIdle()
        rule.onNodeWithTag("profile_content").assertExists()
        rule.onNodeWithTag("profile_error").assertDoesNotExist()
    }

    // -- layouts ----------------------------------------------------------------------------------------------------

    @Test
    @Config(qualifiers = "w840dp-h1000dp-mdpi")
    fun tablet_usesTwoColumns_andKeepsGuardarVisible() {
        show()
        val personal = rule.onNodeWithTag("profile_sex_male").getUnclippedBoundsInRoot()
        val macros = rule.onNodeWithTag("profile_macro_bar").getUnclippedBoundsInRoot()
        assertTrue("macro card is to the right of the personal card", macros.left >= personal.right)
        rule.onNodeWithTag("profile_save_button").assertIsDisplayed()
        rule.onNodeWithTag("profile_sign_out_button").assertExists()
    }

    @Test
    fun largeFont_keepsTheSaveButtonTheMacroTextsAndTheSheetButton() {
        show(fontScale = 1.3f)
        rule.onNodeWithTag("profile_save_button").assertIsDisplayed()
        rule.onNodeWithText("180 g · 720 kcal").assertExists()
        rule.onNodeWithTag("profile_height_row").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("profile_sheet_done").assertHeightIsAtLeast(48.dp)
    }

    // -- US-05 sign out ---------------------------------------------------------------------------------------------

    @Test
    fun catalogButton_isShownOnlyWhenProvided() {
        show(withCatalog = false)
        rule.onNodeWithTag("profile_catalog_button").assertDoesNotExist()
    }

    @Test
    fun catalogButton_opensTheCatalog() {
        show(withCatalog = true)
        rule.onNodeWithTag("profile_catalog_button").performScrollTo().performClick()
        assertEquals(1, catalogOpens)
    }

    @Test
    fun signOutButton_isShown_andMeetsTheTouchTarget() {
        show()
        rule.onNodeWithTag("profile_sign_out_button").performScrollTo()
            .assertTextEquals("Cerrar sesión").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("sign_out_dialog").assertDoesNotExist()
    }

    @Test
    fun tappingSignOut_opensTheDialogWithoutSigningOut() {
        show()
        rule.onNodeWithTag("profile_sign_out_button").performScrollTo().performClick()
        rule.onNodeWithTag("sign_out_dialog").assertExists()
        assertTrue(events.isEmpty())
    }

    @Test
    fun confirming_signsOutOnce_andClosesTheDialog() {
        show()
        rule.onNodeWithTag("profile_sign_out_button").performScrollTo().performClick()
        rule.onNodeWithTag("sign_out_dialog_confirm").assertTextEquals("Cerrar sesión").performClick()
        rule.waitForIdle()
        assertEquals(listOf<ProfileEvent>(ProfileEvent.SignOutConfirmed), events)
        rule.onNodeWithTag("sign_out_dialog").assertDoesNotExist()
    }

    @Test
    fun cancelling_closesTheDialog_andNothingElseHappens() {
        show()
        rule.onNodeWithTag("profile_sign_out_button").performScrollTo().performClick()
        rule.onNodeWithTag("sign_out_dialog_cancel").assertTextEquals("Cancelar").performClick()
        rule.waitForIdle()
        assertTrue(events.isEmpty())
        rule.onNodeWithTag("sign_out_dialog").assertDoesNotExist()
        rule.onNodeWithTag("profile_screen").assertExists()
    }
}
