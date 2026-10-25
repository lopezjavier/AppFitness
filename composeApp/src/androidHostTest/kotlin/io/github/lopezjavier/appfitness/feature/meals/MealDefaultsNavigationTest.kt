package io.github.lopezjavier.appfitness.feature.meals

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.BackInterceptor
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.LocalBackInterceptor
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.usecase.CookedItemNutrition
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.domain.usecase.ValidateMealSlots
import io.github.lopezjavier.appfitness.feature.diary.FakeMealSlotRepository
import io.github.lopezjavier.appfitness.feature.food.FakeFoodPreferencesRepository
import io.github.lopezjavier.appfitness.feature.meals.defaults.FakeMealDefaultsRepository
import io.github.lopezjavier.appfitness.feature.meals.defaults.MealDefaultsScreen
import io.github.lopezjavier.appfitness.feature.meals.defaults.MealDefaultsViewModel
import io.github.lopezjavier.appfitness.feature.templates.FakeMealTemplateRepository
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-51 navigation: Preferencias -> Comidas -> "Comidas por defecto" -> editor -> back to Comidas. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class MealDefaultsNavigationTest {
    @get:Rule
    val rule = createComposeRule()

    private val slots = FakeMealSlotRepository()
    private val connectivity = FakeConnectivityObserver()
    private val backInterceptor = BackInterceptor()
    private val defaultsOpen = mutableStateOf(false)

    private val meals = MealSlotsEditorViewModel(SavedStateHandle(), slots, ValidateMealSlots(), connectivity)
    private val defaults = MealDefaultsViewModel(
        slots,
        FakeMealTemplateRepository(),
        FakeMealDefaultsRepository(),
        FakeFoodPreferencesRepository(),
        CookedItemNutrition(ScaleNutrition()),
        connectivity,
        SavedStateHandle(),
    )

    @Composable
    private fun Flow() {
        val open by defaultsOpen
        CompositionLocalProvider(LocalBackInterceptor provides backInterceptor) {
            AppTheme {
                if (open) {
                    val state by defaults.uiState.collectAsState()
                    LaunchedEffect(Unit) { defaults.effects.collect { defaultsOpen.value = false } }
                    MealDefaultsScreen(state, defaults::onEvent)
                } else {
                    val state by meals.uiState.collectAsState()
                    MealSlotsScreen(state, meals::onEvent, onOpenDefaults = { defaultsOpen.value = true })
                }
            }
        }
    }

    private fun start() {
        rule.setContent { Flow() }
        rule.waitForIdle()
    }

    @Test
    fun theDefaultsButton_opensTheEditor_andBackReturnsToComidas() {
        start()

        rule.onNodeWithTag("meal_slots_defaults_button").assertIsEnabled().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("meal_defaults_screen").assertExists()

        rule.runOnUiThread { backInterceptor.intercept() }
        rule.waitForIdle()

        rule.onNodeWithTag("meals_screen").assertExists()
        rule.onNodeWithTag("meal_defaults_screen").assertDoesNotExist()
    }

    @Test
    fun withUnsavedMealChanges_theButtonIsDisabledAndExplainsWhy() {
        start()

        rule.onNodeWithTag("meals_add").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("meal_slots_defaults_button").assertIsNotEnabled()
        rule.onNodeWithTag("meal_slots_defaults_hint").assertExists()
    }

    @Test
    fun withoutChanges_noHintIsShown() {
        start()

        rule.onNodeWithTag("meal_slots_defaults_hint").assertDoesNotExist()
    }
}
