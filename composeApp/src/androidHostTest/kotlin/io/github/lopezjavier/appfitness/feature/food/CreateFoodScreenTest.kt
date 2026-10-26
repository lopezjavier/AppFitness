package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
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
    private var backs = 0

    @Composable
    private fun Host(model: CreateFoodViewModel) {
        val state by model.uiState.collectAsState()
        androidx.compose.runtime.LaunchedEffect(model) {
            model.effects.collect { if (it is CreateFoodEffect.Saved) saved += it.foodId }
        }
        AppTheme { CreateFoodScreen(state, model::onEvent, { backs++ }) }
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
        rule.onNodeWithTag("create_food_save").assertIsNotEnabled()

        fillTortilla()
        rule.onNodeWithTag("create_food_save").assertIsEnabled()
        tap("create_food_save")

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
        tap("create_food_save")

        assertEquals(listOf(NewPortion("1 ración", 250.0)), foods.created.single().portions)
    }

    @Test
    fun labelFields_areOptional_withNoSectionTitleNorPer100Text_andSavingWithoutThemStaysEnabled() {
        show()
        rule.onNodeWithTag("create_food_label_title").assertDoesNotExist()
        rule.onNodeWithText("Información de la etiqueta (opcional)").assertDoesNotExist()
        listOf("saturated_fat", "sugars", "fiber", "salt").forEach {
            rule.onNodeWithTag("create_food_nutrient_$it").assertExists()
        }

        fillTortilla()

        rule.onNodeWithTag("create_food_save").assertIsEnabled()
    }

    @Test
    fun labelValues_reachTheCreatedFood_withCommaOrPoint() {
        show()
        fillTortilla()
        type("create_food_nutrient_fiber", "2,5")
        type("create_food_nutrient_salt", "0.5")
        tap("create_food_save")

        val micro = foods.created.single().micronutrients
        assertEquals(mapOf(MicronutrientKind.Fiber to 2.5, MicronutrientKind.Salt to 0.5), micro.values)
    }

    @Test
    fun negativeLabelValue_showsTheErrorAndDisablesSave() {
        rule.setContent {
            AppTheme {
                CreateFoodScreen(
                    CreateFoodUiState(
                        name = "Pan",
                        nutrients = mapOf(MicronutrientKind.Sugars to "-3"),
                        errors = NewFoodErrors(nutrients = mapOf(MicronutrientKind.Sugars to NewFoodError.Negative)),
                    ),
                    {},
                    {},
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("create_food_nutrient_sugars_error").assertTextContains("No puede ser negativo")
        rule.onNodeWithTag("create_food_save").assertIsNotEnabled()
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
        rule.onNodeWithTag("create_food_save").assertIsNotEnabled()
    }

    @Test
    fun negativeKcal_showsTheError_andDisablesSave() {
        rule.setContent {
            AppTheme {
                CreateFoodScreen(
                    CreateFoodUiState(name = "Pan", kcal = "-1", errors = NewFoodErrors(kcal = NewFoodError.Negative)),
                    {},
                    {},
                )
            }
        }

        rule.onNodeWithText("No puede ser negativo").assertExists()
        rule.onNodeWithTag("create_food_save").assertIsNotEnabled()
    }

    @Test
    fun tenPortions_disableTheAddButton() {
        rule.setContent {
            AppTheme {
                CreateFoodScreen(
                    CreateFoodUiState(portions = List(10) { PortionDraft("p$it", "1") }, canAddPortion = false),
                    {},
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
        tap("create_food_save")

        assertEquals(7.5, foods.created.single().nutrition.proteinG, 0.0)
    }

    @Test
    fun offline_disablesSave_andSaysWhy() {
        connectivity.state.value = false
        show()
        fillTortilla()

        rule.onNodeWithTag("create_food_save").assertIsNotEnabled()
        rule.onNodeWithTag("offline_hint").assertExists()
    }

    @Test
    fun saveFailure_keepsTheDraft_andLeavesSaveEnabledToRetry() {
        foods.createFailure = AppException(AppError.Network)
        show()
        fillTortilla()

        tap("create_food_save")

        rule.onNodeWithTag("create_food_name_field").assertTextContains("Tortilla de patatas casera")
        rule.onNodeWithTag("create_food_save").assertIsEnabled()
        assertEquals(emptyList<String>(), saved)
    }

    @Test
    fun backWithoutChanges_leavesAtOnce() {
        show()

        tap("create_food_back")

        rule.onNodeWithTag("create_food_discard_dialog").assertDoesNotExist()
        assertEquals(1, backs)
    }

    @Test
    fun backAfterTyping_asksToDiscard_keepEditingStays_andDiscardLeaves() {
        show()
        type("create_food_name_field", "Tor")

        tap("create_food_back")
        rule.onNodeWithTag("create_food_discard_dialog").assertExists()
        rule.onNodeWithText("¿Descartar los cambios?").assertExists()
        tap("create_food_keep_editing")
        assertEquals(0, backs)
        rule.onNodeWithTag("create_food_name_field").assertTextContains("Tor")

        tap("create_food_back")
        tap("create_food_discard_confirm")
        assertEquals(1, backs)
        assertEquals(emptyList<String>(), saved)
    }

    @Test
    fun nextOnTheKeyboard_movesFromNameToBrand_andFromCaloriesToFatToCarbs() {
        show()

        rule.onNodeWithTag("create_food_name_field").performClick()
        rule.onNodeWithTag("create_food_name_field").performImeAction()
        rule.waitForIdle()
        rule.onNodeWithTag("create_food_brand_field").assertIsFocused()

        rule.onNodeWithTag("create_food_kcal_field").performClick()
        rule.onNodeWithTag("create_food_kcal_field").performImeAction()
        rule.waitForIdle()
        rule.onNodeWithTag("create_food_fat_field").assertIsFocused()
    }
}
