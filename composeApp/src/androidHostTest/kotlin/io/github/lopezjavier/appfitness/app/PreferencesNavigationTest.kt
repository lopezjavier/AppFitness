package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.feature.profile.AccountScreen
import io.github.lopezjavier.appfitness.feature.profile.AccountUiState
import io.github.lopezjavier.appfitness.feature.profile.PreferencesEvent
import io.github.lopezjavier.appfitness.feature.profile.PreferencesForm
import io.github.lopezjavier.appfitness.feature.profile.PreferencesHubActions
import io.github.lopezjavier.appfitness.feature.profile.PreferencesHubScreen
import io.github.lopezjavier.appfitness.feature.profile.PreferencesSection
import io.github.lopezjavier.appfitness.feature.profile.PreferencesSectionScreen
import io.github.lopezjavier.appfitness.feature.profile.PreferencesUiState
import io.github.lopezjavier.appfitness.feature.profile.ProfileRootActions
import io.github.lopezjavier.appfitness.feature.profile.ProfileRootScreen
import io.github.lopezjavier.appfitness.feature.profile.ProfileRootUiState
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * US-56 navigation: the Perfil gear opens the Preferencias hub, a row opens its editor and a saved editor returns to
 * the hub; the hub also leads to "Cuenta" and "Comidas del día"; the Diario goal prompt opens the hub.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h900dp-xxhdpi")
class PreferencesNavigationTest {
    @get:Rule
    val rule = createComposeRule()

    private val form = PreferencesForm(
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

    private val profileSlot: ProfileSlot = { modifier, onOpenPreferences, _, _, _, _, _, _ ->
        ProfileRootScreen(
            state = ProfileRootUiState.Loaded("Ana", null),
            onEvent = {},
            modifier = modifier,
            actions = ProfileRootActions(onOpenPreferences = onOpenPreferences),
        )
    }
    private val hubSlot: PreferencesSlot = { modifier, onBack, onOpenSection, onOpenMeals, onOpenAccount ->
        PreferencesHubScreen(
            onBack = onBack,
            actions = PreferencesHubActions({ onOpenSection(it.key) }, onOpenMeals, onOpenAccount),
            modifier = modifier,
        )
    }

    /** A macros editor whose "Guardar" marks the section saved, as the view model would. */
    private val sectionSlot: PreferencesSectionSlot = { modifier, onClose ->
        var state by remember {
            mutableStateOf(
                PreferencesUiState.Loaded(
                    form.copy(split = MacroSplit(35, 42, 23)),
                    form,
                    PreferencesSection.Macros,
                    today,
                ),
            )
        }
        PreferencesSectionScreen(
            section = PreferencesSection.Macros,
            state = state,
            onEvent = { if (it == PreferencesEvent.Save) state = state.copy(saved = state.form, isSaved = true) },
            onClose = onClose,
            modifier = modifier,
        )
    }
    private val accountSlot: AccountSlot = { modifier, onBack ->
        AccountScreen(AccountUiState(), {}, onBack, modifier)
    }
    private val mealSlotsSlot: MealSlotsSlot = { modifier, _, _ -> Box(modifier.fillMaxSize().testTag("meals_stub")) }
    private val diarySlot: DiarySlot = { modifier, onOpenPreferences, _, _, _, _ ->
        Box(modifier.fillMaxSize().testTag("diary_screen")) {
            Box(Modifier.fillMaxSize().clickable(onClick = onOpenPreferences).testTag("diary_goal_prompt"))
        }
    }

    private val today = LocalDate(2026, 10, 14)

    private fun showShell() {
        rule.setContent {
            AppTheme {
                AppScaffold(
                    navController = rememberNavController(),
                    profile = profileSlot,
                    preferences = hubSlot,
                    preferencesSection = sectionSlot,
                    account = accountSlot,
                    mealSlots = mealSlotsSlot,
                    diary = diarySlot,
                )
            }
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun openHubFromPerfil() {
        tap("nav_tab_profile")
        tap("profile_action_0")
    }

    @Test
    fun gear_opensTheHub_andBackReturnsToPerfil() {
        showShell()
        openHubFromPerfil()
        rule.onNodeWithTag("preferences_hub").assertExists()
        rule.onNodeWithTag("nav_bar").assertDoesNotExist()

        tap("preferences_back")

        rule.onNodeWithTag("profile_screen").assertExists()
        rule.onNodeWithTag("nav_bar").assertExists()
    }

    @Test
    fun savingAnEditor_closesItAndReturnsToTheHub() {
        showShell()
        openHubFromPerfil()
        tap("preferences_row_macros")
        rule.onNodeWithTag("preferences_macros_save").assertExists()

        tap("preferences_macros_save")

        rule.onNodeWithTag("preferences_hub").assertExists()
        rule.onNodeWithTag("preferences_macros_save").assertDoesNotExist()
    }

    @Test
    fun cuentaRow_opensTheAccountScreen_andBackReturnsToTheHub() {
        showShell()
        openHubFromPerfil()
        tap("preferences_row_account")
        rule.onNodeWithTag("account_screen").assertExists()
        rule.onNodeWithTag("account_sign_out").assertExists()

        tap("account_back")

        rule.onNodeWithTag("preferences_hub").assertExists()
    }

    @Test
    fun comidasDelDiaRow_opensTheMealsEditor() {
        showShell()
        openHubFromPerfil()
        tap("preferences_row_meals")
        rule.onNodeWithTag("meals_stub").assertExists()
    }

    @Test
    fun diaryGoalPrompt_stillOpensTheHub() {
        showShell()
        tap("diary_goal_prompt")
        rule.onNodeWithTag("preferences_hub").assertExists()
    }
}
