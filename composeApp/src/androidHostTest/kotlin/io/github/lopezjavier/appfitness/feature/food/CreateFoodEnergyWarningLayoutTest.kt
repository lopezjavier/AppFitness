package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T-252 / ADR-104: the energy block keeps the footer reachable at a large font and its targets are 48 dp. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class CreateFoodEnergyWarningLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    private val foods = FakeFoodRepository()
    private val connectivity = FakeConnectivityObserver()

    @Composable
    private fun Host(model: CreateFoodViewModel, fontScale: Float) {
        val state by model.uiState.collectAsState()
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            AppTheme { CreateFoodScreen(state, model::onEvent, {}) }
        }
    }

    private fun show(fontScale: Float = 1f) {
        val model = CreateFoodViewModel(null, null, foods, connectivity)
        rule.setContent { Host(model, fontScale) }
        rule.waitForIdle()
    }

    private fun type(tag: String, text: String) {
        rule.onNodeWithTag(tag).performTextInput(text)
        rule.waitForIdle()
    }

    private fun openWarning() {
        type("create_food_name_field", "Barrita")
        type("create_food_kcal_field", "500")
        type("create_food_protein_field", "10")
        type("create_food_carbs_field", "30")
        type("create_food_fat_field", "10")
    }

    @Test
    fun largeFont_keepsTheBlockedSaveAndTheHelpDisplayed() {
        show(fontScale = 2f)
        openWarning()

        rule.onNodeWithTag("create_food_save").assertIsDisplayed()
        rule.onNodeWithTag("create_food_energy_warning").assertIsDisplayed()
        rule.onNodeWithTag("create_food_energy_warning_help").assertIsDisplayed()
    }

    @Test
    fun help_isAtLeast48dp() {
        show()
        openWarning()

        rule.onNodeWithTag("create_food_energy_warning_help").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("create_food_energy_warning_help").assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun invalidAlcohol_showsTheFieldError() {
        show()
        rule.onNodeWithTag("create_food_section_alcohol_toggle").performScrollTo().performClick()
        rule.waitForIdle()
        type("create_food_alcohol_field", "150")

        rule.onNodeWithTag("create_food_alcohol_field_error").assertExists()
    }

    @Test
    fun gapBelowZero_helpShowsTheFibreAndAlcoholLine_only() {
        show()
        openWarning()
        rule.onNodeWithTag("create_food_energy_warning_help").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Si lleva fibra o alcohol", substring = true).assertExists()
        rule.onNodeWithText("Si lleva polialcoholes", substring = true).assertDoesNotExist()
    }
}
