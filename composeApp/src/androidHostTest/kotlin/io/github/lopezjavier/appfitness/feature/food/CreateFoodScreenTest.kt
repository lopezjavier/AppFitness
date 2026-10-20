package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.NewFoodError
import io.github.lopezjavier.appfitness.domain.model.NewFoodErrors
import io.github.lopezjavier.appfitness.domain.model.NewPortion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-18 manual creation and US-22 named portions: the form wired to the real [CreateFoodViewModel] and fakes. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class CreateFoodScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val foods = FakeFoodRepository()
    private val connectivity = FakeConnectivityObserver()
    private val saved = mutableListOf<String>()

    @Composable
    private fun Host(model: CreateFoodViewModel) {
        val state by model.uiState.collectAsState()
        androidx.compose.runtime.LaunchedEffect(model) {
            model.effects.collect { if (it is CreateFoodEffect.Saved) saved += it.foodId }
        }
        AppTheme { CreateFoodScreen(state, model::onEvent) }
    }

    private fun show(barcode: String? = null, name: String? = null) {
        val model = CreateFoodViewModel(barcode, name, foods, connectivity)
        rule.setContent { Host(model) }
        rule.waitForIdle()
    }

    private fun type(tag: String, text: String) {
        rule.onNodeWithTag(tag).performTextInput(text)
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun fillTortilla() {
        type("create_food_name_field", "Tortilla de patatas casera")
        type("create_food_kcal_field", "190")
        type("create_food_protein_field", "7")
        type("create_food_carbs_field", "15")
        type("create_food_fat_field", "11")
    }

    @Test
    fun manualCreation_savesTheFoodInGrams_andReportsTheNewId() {
        show()
        rule.onNodeWithTag("create_food_save_button").assertIsNotEnabled()

        fillTortilla()
        rule.onNodeWithTag("create_food_save_button").assertIsEnabled()
        tap("create_food_save_button")

        val created = foods.created.single()
        assertEquals("Tortilla de patatas casera", created.name)
        assertEquals("g", created.baseUnit)
        assertEquals(190.0, created.nutrition.kcal, 0.0)
        assertEquals(7.0, created.nutrition.proteinG, 0.0)
        assertEquals(listOf("new-1"), saved)
    }

    @Test
    fun namedPortion_isSentWithItsAmount() {
        show()
        fillTortilla()

        tap("create_food_add_portion_button")
        type("create_food_portion_label_0", "1 ración")
        type("create_food_portion_amount_0", "250")
        tap("create_food_save_button")

        assertEquals(listOf(NewPortion("1 ración", 250.0)), foods.created.single().portions)
    }

    @Test
    fun removingAPortion_dropsItsRow() {
        show()
        tap("create_food_add_portion_button")

        rule.onNode(hasContentDescription("Quitar porción")).assertExists()
        tap("create_food_portion_remove_0")

        rule.onNodeWithTag("create_food_portion_label_0").assertDoesNotExist()
    }

    @Test
    fun unitSelector_switchesToMillilitres_andRelabelsTheAmounts() {
        show()
        tap("create_food_add_portion_button")
        rule.onNodeWithText("Cantidad (g)").assertExists()

        tap("create_food_unit_ml")

        rule.onNodeWithTag("create_food_unit_ml").assertIsSelected()
        rule.onNodeWithText("Cantidad (ml)").assertExists()
        rule.onNodeWithText("Calorías por 100 ml").assertExists()
    }

    @Test
    fun emptyName_showsTheError_andKeepsSaveDisabled() {
        show()
        fillTortilla()

        rule.onNodeWithTag("create_food_name_field").performTextClearance()
        rule.waitForIdle()

        rule.onNodeWithText("Este campo es obligatorio").assertExists()
        rule.onNodeWithTag("create_food_save_button").assertIsNotEnabled()
    }

    @Test
    fun negativeKcal_showsTheError_andDisablesSave() {
        rule.setContent {
            AppTheme {
                CreateFoodScreen(
                    CreateFoodUiState(name = "Pan", kcal = "-1", errors = NewFoodErrors(kcal = NewFoodError.Negative)),
                    {},
                )
            }
        }

        rule.onNodeWithText("No puede ser negativo").assertExists()
        rule.onNodeWithTag("create_food_save_button").assertIsNotEnabled()
    }

    @Test
    fun tenPortions_disableTheAddButton() {
        rule.setContent {
            AppTheme {
                CreateFoodScreen(
                    CreateFoodUiState(portions = List(10) { PortionDraft("p$it", "1") }, canAddPortion = false),
                    {},
                )
            }
        }

        rule.onNodeWithTag("create_food_add_portion_button").assertIsNotEnabled()
    }

    @Test
    fun prefill_fromTheScannedBarcodeAndTheTypedQuery() {
        show(barcode = "8410000000017", name = "tortilla")

        rule.onNodeWithTag("create_food_name_field").assertTextContains("tortilla")
        rule.onNodeWithTag("create_food_barcode_text").assertTextContains("8410000000017", substring = true)
    }

    @Test
    fun withoutABarcode_noBarcodeTextIsShown() {
        show()

        rule.onNodeWithTag("create_food_barcode_text").assertDoesNotExist()
    }

    @Test
    fun commaDecimals_areAccepted() {
        show()
        type("create_food_name_field", "Queso")
        type("create_food_kcal_field", "250")
        type("create_food_protein_field", "7,5")
        tap("create_food_save_button")

        assertEquals(7.5, foods.created.single().nutrition.proteinG, 0.0)
    }

    @Test
    fun offline_disablesSave_andSaysWhy() {
        connectivity.state.value = false
        show()
        fillTortilla()

        rule.onNodeWithTag("create_food_save_button").assertIsNotEnabled()
        rule.onNodeWithTag("create_food_offline_hint").assertExists()
    }

    @Test
    fun saveFailure_showsTheInlineError_andKeepsTheDraft() {
        foods.createFailure = AppException(AppError.Network)
        show()
        fillTortilla()

        tap("create_food_save_button")

        rule.onNodeWithTag("create_food_save_error").assertExists()
        rule.onNodeWithTag("create_food_name_field").assertTextContains("Tortilla de patatas casera")
        assertEquals(emptyList<String>(), saved)
    }
}
