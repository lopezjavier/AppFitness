package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.FoodPortion
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
    fun labelFields_surviveRecreation() {
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { FormHost(CreateFoodViewModel(null, null, foods, connectivity, saved)) }
        rule.waitForIdle()
        rule.onNodeWithTag("create_food_nutrient_saturated_fat").performTextInput("1,5")
        rule.onNodeWithTag("create_food_nutrient_sugars").performTextInput("4")
        rule.onNodeWithTag("create_food_nutrient_fiber").performTextInput("2")
        rule.onNodeWithTag("create_food_nutrient_salt").performTextInput("0,3")
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("create_food_nutrient_saturated_fat").assertTextContains("1,5")
        rule.onNodeWithTag("create_food_nutrient_sugars").assertTextContains("4")
        rule.onNodeWithTag("create_food_nutrient_fiber").assertTextContains("2")
        rule.onNodeWithTag("create_food_nutrient_salt").assertTextContains("0,3")
    }

    @Test
    fun editForm_keepsEditedValuesAndOpenSections_afterRecreation_withoutReloadingTheFood() {
        foods.known["yogurt"] = food(
            "yogurt",
            "Yogur casero",
            kcal = 60.0,
            portions = listOf(FoodPortion("p1", "1 tarrina", 125.0)),
            isManual = true,
        )
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { FormHost(CreateFoodViewModel(null, null, foods, connectivity, saved, "yogurt")) }
        rule.waitForIdle()
        rule.onNodeWithTag("create_food_kcal_field").performTextReplacement("70")
        rule.onNodeWithTag("create_food_portion_amount_0").performTextReplacement("120")
        rule.onNodeWithTag("create_food_section_vitamins_toggle").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("create_food_nutrient_vitamin_c").performTextInput("12")
        rule.waitForIdle()
        val loads = foods.editableLoads.size

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("create_food_kcal_field").assertTextContains("70")
        rule.onNodeWithTag("create_food_portion_amount_0").assertTextContains("120")
        rule.onNodeWithTag("create_food_nutrient_vitamin_c").assertTextContains("12")
        rule.onNodeWithTag("create_food_nutrient_calcium").assertDoesNotExist()
        assertEquals(loads, foods.editableLoads.size)
    }

    @Test
    fun editForm_keepsTheChangedUnitAndTheName_afterRecreation() {
        foods.known["yogurt"] = food("yogurt", "Yogur casero", kcal = 60.0, isManual = true)
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { FormHost(CreateFoodViewModel(null, null, foods, connectivity, saved, "yogurt")) }
        rule.waitForIdle()
        rule.onNodeWithTag("create_food_unit_ml").performClick()
        rule.onNodeWithTag("create_food_name_field").performTextReplacement("Yogur líquido")
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("create_food_unit_ml").assertIsSelected()
        rule.onNodeWithTag("create_food_unit_ml").assertIsEnabled()
        rule.onNodeWithTag("create_food_name_field").assertTextContains("Yogur líquido")
    }

    @Test
    fun editForm_keepsTheLockedUnitAndTheRefusal_afterRecreation() {
        foods.known["yogurt"] = food("yogurt", "Yogur casero", kcal = 60.0, isManual = true)
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { FormHost(CreateFoodViewModel(null, null, foods, connectivity, saved, "yogurt")) }
        rule.waitForIdle()
        rule.onNodeWithTag("create_food_unit_ml").performClick()
        foods.inUse += "yogurt"
        rule.onNodeWithTag("create_food_save_button").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("create_food_unit_refused").assertExists()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("create_food_unit_refused").assertExists()
        rule.onNodeWithTag("create_food_unit_g").assertIsSelected()
        rule.onNodeWithTag("create_food_unit_g").assertIsNotEnabled()
    }

    @Test
    fun usedFood_staysLocked_afterRecreation() {
        foods.known["yogurt"] = food("yogurt", "Yogur casero", kcal = 60.0, isManual = true)
        foods.inUse += "yogurt"
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { FormHost(CreateFoodViewModel(null, null, foods, connectivity, saved, "yogurt")) }
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("create_food_unit_locked").assertExists()
        rule.onNodeWithTag("create_food_unit_ml").assertIsNotEnabled()
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
