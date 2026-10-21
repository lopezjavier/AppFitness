package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.port.ScanResult
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val CODE = "8410000000017"

/** ADR-40: the half-filled create-food form and the search's not-found card survive recreation. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class CreateFoodRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val foods = FakeFoodRepository()
    private val connectivity = FakeConnectivityObserver()

    @Composable
    private fun FormHost(model: CreateFoodViewModel) {
        val state by model.uiState.collectAsState()
        AppTheme { CreateFoodScreen(state, model::onEvent) }
    }

    @Composable
    private fun SearchHost(model: FoodSearchViewModel) {
        val state by model.uiState.collectAsState()
        AppTheme { FoodSearchScreen(state, model::onEvent) }
    }

    @Test
    fun halfFilledForm_survivesRecreation_fromTheSameSavedState() {
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { FormHost(CreateFoodViewModel(CODE, null, foods, connectivity, saved)) }
        rule.waitForIdle()
        rule.onNodeWithTag("create_food_name_field").performTextInput("Tortilla")
        rule.onNodeWithTag("create_food_kcal_field").performTextInput("190")
        rule.onNodeWithTag("create_food_add_portion_button").performClick()
        rule.onNodeWithTag("create_food_portion_label_0").performTextInput("1 ración")
        rule.onNodeWithTag("create_food_portion_amount_0").performTextInput("250")
        rule.waitForIdle()
        val before = CreateFoodViewModel(CODE, null, foods, connectivity, saved).uiState.value

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("create_food_name_field").assertTextContains("Tortilla")
        rule.onNodeWithTag("create_food_kcal_field").assertTextContains("190")
        rule.onNodeWithTag("create_food_portion_label_0").assertTextContains("1 ración")
        rule.onNodeWithTag("create_food_portion_amount_0").assertTextContains("250")
        rule.onNodeWithTag("create_food_barcode_text").assertTextContains(CODE, substring = true)
        val after = CreateFoodViewModel(CODE, null, foods, connectivity, saved).uiState.value
        assertEquals(before, after)
    }

    @Test
    fun notFoundCard_survivesRecreation() {
        val saved = SavedStateHandle()
        val scanner = FakeBarcodeScanner(ScanResult.Code(CODE))
        val tester = StateRestorationTester(rule)
        tester.setContent { SearchHost(foodSearchModel(foods, connectivity, scanner, saved)) }
        rule.waitForIdle()
        rule.onNodeWithTag("food_search_scan_button").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("food_search_not_found_card").assertExists()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("food_search_not_found_card").assertExists()
    }
}
