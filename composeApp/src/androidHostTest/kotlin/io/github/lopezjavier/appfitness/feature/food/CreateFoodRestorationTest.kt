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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoMode
import io.github.lopezjavier.appfitness.domain.model.FoodPortion
import io.github.lopezjavier.appfitness.domain.model.NewFoodDraft
import io.github.lopezjavier.appfitness.domain.model.NewFoodDraftCodec
import io.github.lopezjavier.appfitness.domain.model.NewMicronutrients
import io.github.lopezjavier.appfitness.domain.port.ScanResult
import io.github.lopezjavier.appfitness.feature.food.photo.FakePhotoCapture
import io.github.lopezjavier.appfitness.feature.food.photo.FoodPhotoRetryStore
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
@Suppress("TooManyFunctions") // One method per restoration scenario.
class CreateFoodRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val foods = FakeFoodRepository()
    private val connectivity = FakeConnectivityObserver()

    @Composable
    private fun FormHost(model: CreateFoodViewModel) {
        val state by model.uiState.collectAsState()
        AppTheme { CreateFoodScreen(state, model::onEvent, {}) }
    }

    @Composable
    private fun SearchHost(model: FoodSearchViewModel) {
        val state by model.uiState.collectAsState()
        AppTheme { FoodSearchScreen(state, model::onEvent, {}) }
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
    fun alcoholAndOpenWarning_surviveRecreation() {
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { FormHost(CreateFoodViewModel(null, null, foods, connectivity, saved)) }
        rule.waitForIdle()
        rule.onNodeWithTag("create_food_name_field").performTextInput("Barrita")
        rule.onNodeWithTag("create_food_kcal_field").performTextInput("500")
        rule.onNodeWithTag("create_food_protein_field").performTextInput("10")
        rule.onNodeWithTag("create_food_carbs_field").performTextInput("30")
        rule.onNodeWithTag("create_food_fat_field").performTextInput("10")
        rule.onNodeWithTag("create_food_section_alcohol_toggle").performClick()
        rule.onNodeWithTag("create_food_alcohol_field").performTextInput("0")
        rule.waitForIdle()
        rule.onNodeWithTag("create_food_energy_warning").assertExists()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("create_food_energy_warning").assertExists()
        rule.onNodeWithTag("create_food_alcohol_field").assertTextContains("0")
        rule.onNodeWithTag("create_food_save").assertIsNotEnabled()
    }

    @Test
    fun alcoholSectionInGradosMode_survivesRecreation() {
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { FormHost(CreateFoodViewModel(null, null, foods, connectivity, saved)) }
        rule.waitForIdle()
        rule.onNodeWithTag("create_food_unit_ml").performClick()
        rule.onNodeWithTag("create_food_section_alcohol_toggle").performClick()
        rule.onNodeWithTag("create_food_alcohol_mode_abv").performClick()
        rule.onNodeWithTag("create_food_alcohol_abv_field").performTextInput("5")
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("create_food_alcohol_mode_abv").assertIsSelected()
        rule.onNodeWithTag("create_food_alcohol_abv_field").assertTextContains("5")
        rule.onNodeWithTag("create_food_alcohol_converted").assertTextContains("3,9", substring = true)
    }

    @Test
    fun viewModelRecreatedFromTheSameSavedState_restoresModeAbvAndGrams() {
        val saved = SavedStateHandle()
        val first = CreateFoodViewModel(null, null, foods, connectivity, saved)
        first.onEvent(CreateFoodEvent.UnitSelected("ml"))
        first.onEvent(CreateFoodEvent.AlcoholModeSelected(AlcoholMode.Grados))
        first.onEvent(CreateFoodEvent.AlcoholAbvChanged("5"))

        val restored = CreateFoodViewModel(null, null, foods, connectivity, saved).uiState.value

        assertEquals(AlcoholMode.Grados, restored.alcoholMode)
        assertEquals("5", restored.alcoholAbv)
        assertEquals("3,9", restored.alcohol)
    }

    private val failingAiDraft = NewFoodDraft(
        name = "Lentejas",
        brand = null,
        baseUnit = "g",
        kcal = 500.0,
        protein = 10.0,
        carbs = 30.0,
        fat = 10.0,
        micronutrients = NewMicronutrients(),
        portions = emptyList(),
    )

    private fun aiForm(store: FoodPhotoRetryStore, token: String?, saved: SavedStateHandle) = CreateFoodViewModel(
        barcode = null,
        name = null,
        foodRepository = foods,
        connectivity = connectivity,
        savedState = saved,
        prefill = NewFoodDraftCodec.encode(failingAiDraft),
        retryToken = token,
        retryStore = store,
    )

    @Test
    fun aiDraftWithRetry_survivesRecreationInProcess_withTheIconAvailable() {
        val store = FoodPhotoRetryStore(FakePhotoCapture())
        val token = store.put("photo-1", FoodPhotoMode.Dish, null)
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { FormHost(aiForm(store, token, saved)) }
        rule.waitForIdle()
        rule.onNodeWithTag("create_food_energy_retry").assertExists()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("create_food_energy_warning").assertExists()
        rule.onNodeWithTag("create_food_energy_retry").assertExists()
        rule.onNodeWithTag("create_food_save").assertIsNotEnabled()
    }

    @Test
    fun aiDraftRecreatedWithAnEmptyStore_keepsTheWarning_andSaysThePhotoIsGone() {
        val store = FoodPhotoRetryStore(FakePhotoCapture())
        val token = store.put("photo-1", FoodPhotoMode.Dish, null)
        val saved = SavedStateHandle()
        aiForm(store, token, saved)
        rule.setContent { FormHost(aiForm(FoodPhotoRetryStore(FakePhotoCapture()), null, saved)) }
        rule.waitForIdle()

        rule.onNodeWithTag("create_food_energy_warning").assertExists()
        rule.onNodeWithTag("create_food_retry_photo_gone").assertExists()
        rule.onNodeWithTag("create_food_energy_retry").assertDoesNotExist()
        rule.onNodeWithTag("create_food_save").assertIsNotEnabled()
    }

    @Test
    fun textDraftRecreatedWithAnEmptyStore_saysTheTextIsGone() {
        val store = FoodPhotoRetryStore(FakePhotoCapture())
        val token = store.putText("pechuga con ensalada")
        val saved = SavedStateHandle()
        aiForm(store, token, saved)
        rule.setContent { FormHost(aiForm(FoodPhotoRetryStore(FakePhotoCapture()), null, saved)) }
        rule.waitForIdle()

        rule.onNodeWithTag("create_food_energy_warning").assertExists()
        rule.onNodeWithTag("create_food_retry_photo_gone")
            .assertTextContains("El texto ya no está disponible. Corrige los valores a mano.")
        rule.onNodeWithTag("create_food_save").assertIsNotEnabled()
    }

    @Test
    fun retryOffline_isDisabled_withTheStandardHint() {
        val store = FoodPhotoRetryStore(FakePhotoCapture())
        val token = store.put("photo-1", FoodPhotoMode.Dish, null)
        connectivity.state.value = false
        rule.setContent { FormHost(aiForm(store, token, SavedStateHandle())) }
        rule.waitForIdle()

        rule.onNodeWithTag("create_food_energy_retry").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede repetir la estimación").assertExists()
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
        foods.known["yogurt"] =
            food("yogurt", "Yogur casero", kcal = 60.0, isManual = true, protein = 3.0, carbs = 10.0, fat = 2.0)
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
        foods.known["yogurt"] =
            food("yogurt", "Yogur casero", kcal = 60.0, isManual = true, protein = 3.0, carbs = 10.0, fat = 2.0)
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { FormHost(CreateFoodViewModel(null, null, foods, connectivity, saved, "yogurt")) }
        rule.waitForIdle()
        rule.onNodeWithTag("create_food_unit_ml").performClick()
        foods.inUse += "yogurt"
        rule.onNodeWithTag("create_food_save").performClick()
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
        foods.known["yogurt"] =
            food("yogurt", "Yogur casero", kcal = 60.0, isManual = true, protein = 3.0, carbs = 10.0, fat = 2.0)
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
        rule.onNodeWithTag("food_search_action_scan").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("food_search_not_found_card").assertExists()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("food_search_not_found_card").assertExists()
    }
}
