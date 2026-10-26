package io.github.lopezjavier.appfitness.feature.templates

import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.centerY
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.right
import androidx.compose.ui.test.swipeLeft
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.domain.model.Micronutrient
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.MicronutrientUnit
import io.github.lopezjavier.appfitness.domain.model.Micronutrients
import io.github.lopezjavier.appfitness.feature.food.FakeFoodRepository
import io.github.lopezjavier.appfitness.feature.food.food
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * US-21 / US-47 comida editor: name field, rows and header with nutrition, the food sheet, swipe to remove, "Añadir
 * alimento" and a single "Guardar" that applies everything together. Nothing is stored before "Guardar".
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class TemplateDetailsScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val eggs = food("eggs", "Huevos revueltos", kcal = 100.0)
    private val shake = food("shake", "Batido de proteína", kcal = 50.0)
    private val templates = FakeMealTemplateRepository(mutableMapOf("eggs" to eggs, "shake" to shake))
    private val foods = FakeFoodRepository()
    private val connectivity = FakeConnectivityObserver()
    private val callbacks = DetailsCallbacks()
    private val desayunoA = MealTemplate(
        id = "t1",
        name = "Desayuno A",
        mealSlotId = "breakfast",
        items = listOf(MealTemplateItem(eggs, 120.0, null), MealTemplateItem(shake, 300.0, "1 vaso")),
    )

    private fun showDetails() {
        rule.setContent { DetailsHost(detailsModel(templates, connectivity, foods = foods), callbacks) }
        rule.waitForIdle()
    }

    private fun editFirstFoodTo(grams: String) {
        rule.onNodeWithTag("template_edit_item_0").performClick()
        rule.onNodeWithTag("food_sheet_amount").performTextReplacement(grams)
        rule.onNodeWithTag("food_sheet_commit").performClick()
        rule.waitForIdle()
    }

    private fun typeInFoodSheet(grams: String) {
        rule.onNodeWithTag("template_edit_item_0").performClick()
        rule.onNodeWithTag("food_sheet_amount").performTextReplacement(grams)
        rule.waitForIdle()
    }

    private fun swipeRowToTheEnd(index: Int) {
        rule.onNodeWithTag("template_edit_item_$index").performTouchInput {
            swipeLeft(startX = right - 1f, endX = 0f)
        }
        rule.waitForIdle()
    }

    @Test
    fun details_showTheNameTheTotalAndEveryFoodWithItsQuantityAndNutrition() {
        templates.templates += desayunoA
        showDetails()

        rule.onNodeWithTag("template_edit_name").assertTextContains("Desayuno A")
        rule.onNodeWithTag("template_edit_kcal").assertTextContains("270 kcal")
        rule.onNodeWithTag("template_edit_macros").assertIsDisplayed()
        rule.onNodeWithText("P 42 g").assertIsDisplayed()
        rule.assertRowText("template_edit_item_0", "Huevos revueltos")
        rule.assertRowText("template_edit_item_0", "120 g")
        rule.assertRowText("template_edit_item_0", "120 kcal")
        rule.assertRowText("template_edit_item_1", "Batido de proteína")
        rule.assertRowText("template_edit_item_1", "1 vaso")
        rule.assertRowText("template_edit_item_1", "300 g")
        rule.assertRowText("template_edit_item_1", "150 kcal")
    }

    @Test
    fun editor_hasTheNameField_andNoRenamePencil_andNoSaveAsNew() {
        templates.templates += desayunoA
        showDetails()

        rule.onNodeWithTag("template_edit_name").assertIsDisplayed()
        rule.onNodeWithTag("template_details_rename").assertDoesNotExist()
        rule.onNodeWithText("Guardar como nueva").assertDoesNotExist()
        rule.onNodeWithTag("template_edit_add_item").assertIsDisplayed()
    }

    @Test
    fun anEmptyName_disablesGuardar() {
        templates.templates += desayunoA
        showDetails()

        rule.onNodeWithTag("template_edit_name").performTextReplacement("   ")
        rule.waitForIdle()

        rule.onNodeWithTag("template_edit_save").assertIsNotEnabled()
    }

    @Test
    fun aNameTakenByAnotherComida_showsTheHint_andDisablesGuardar() {
        templates.templates += desayunoA
        templates.templates += desayunoA.copy(id = "t2", name = "Comida 2")
        showDetails()

        rule.onNodeWithTag("template_edit_name").performTextReplacement("comida 2 ")
        rule.waitForIdle()

        rule.onNodeWithTag("template_edit_name_error").assertTextEquals("Ya existe una comida con ese nombre")
        rule.onNodeWithTag("template_edit_save").assertIsNotEnabled()
    }

    @Test
    fun theOwnNameUnchanged_showsNoHint() {
        templates.templates += desayunoA
        showDetails()

        rule.onNodeWithTag("template_edit_name_error").assertDoesNotExist()
    }

    @Test
    fun anAddFoodTap_asksForThePicker() {
        templates.templates += desayunoA
        showDetails()

        rule.onNodeWithTag("template_edit_add_item").performClick()
        rule.waitForIdle()

        assertEquals(1, callbacks.pickerOpened)
        assertEquals(2, templates.templates.single().items.size)
    }

    @Test
    fun tappingAFood_opensItsSheet_andTypingGramsOnlyPreviewsThem() {
        templates.templates += desayunoA
        showDetails()

        rule.onNodeWithTag("template_edit_item_0").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("food_sheet_title").assertTextContains("Huevos revueltos")
        rule.onNodeWithTag("food_sheet_amount").performTextReplacement("180")
        rule.waitForIdle()

        rule.onNodeWithTag("food_sheet_totals_kcal", useUnmergedTree = true).assertTextContains("180 kcal")
        rule.onNodeWithTag("template_edit_kcal").assertTextContains("270 kcal")
        rule.assertRowText("template_edit_item_0", "120 kcal")
        assertTrue(templates.templates.single().items.first().amount == 120.0)
    }

    @Test
    fun foodSheet_withMicronutrients_showsThem() {
        templates.templates += desayunoA
        foods.micronutrientResults["eggs"] =
            Result.success(Micronutrients(listOf(Micronutrient(MicronutrientKind.Fiber, 2.0, MicronutrientUnit.G))))
        showDetails()

        rule.onNodeWithTag("template_edit_item_0").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("food_sheet_details_toggle").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("food_sheet_details").assertIsDisplayed()
    }

    @Test
    fun quitarDeLaComida_removesTheRow_andStoresNothingUntilGuardar() {
        templates.templates += desayunoA
        showDetails()

        rule.onNodeWithTag("template_edit_item_1").performClick()
        rule.onNodeWithTag("food_sheet_remove").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("template_edit_item_1").assertDoesNotExist()
        rule.onNodeWithTag("food_sheet").assertDoesNotExist()
        rule.onNodeWithTag("template_edit_kcal").assertTextContains("120 kcal")
        assertEquals(2, templates.templates.single().items.size)
    }

    @Test
    fun theLastFood_hasNoQuitarButton_andIsNotSwipeable() {
        templates.templates += desayunoA
        showDetails()
        swipeRowToTheEnd(1)

        rule.onNodeWithTag("template_edit_item_0").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("food_sheet_remove").assertIsNotEnabled()
        rule.onNodeWithTag("food_sheet_close").performClick()
        rule.waitForIdle()
        swipeRowToTheEnd(0)

        rule.onNodeWithTag("template_edit_item_0").assertIsDisplayed()
    }

    @Test
    fun swipingARowToTheEnd_removesIt_withoutStoringIt() {
        templates.templates += desayunoA
        showDetails()

        swipeRowToTheEnd(1)

        rule.onNodeWithTag("template_edit_item_1").assertDoesNotExist()
        rule.onNodeWithTag("template_edit_item_0").assertIsDisplayed()
        assertEquals(2, templates.templates.single().items.size)
    }

    @Test
    fun aShortDrag_doesNotRemoveTheRow() {
        templates.templates += desayunoA
        showDetails()

        rule.onNodeWithTag("template_edit_item_1").performTouchInput {
            down(Offset(right - 1f, centerY))
            moveTo(Offset(right * 0.55f, centerY))
            up()
        }
        rule.waitForIdle()

        rule.onNodeWithTag("template_edit_item_1").assertIsDisplayed()
    }

    @Test
    fun guardar_isDisabledUntilTheDraftChanges() {
        templates.templates += desayunoA
        showDetails()
        rule.onNodeWithTag("template_edit_save").assertIsNotEnabled()

        editFirstFoodTo("150")

        rule.onNodeWithTag("template_edit_save").assertIsEnabled()
    }

    @Test
    fun guardar_withAnInvalidAmount_isDisabled_andTheRowShowsTheError() {
        templates.templates += desayunoA
        showDetails()

        typeInFoodSheet("0")

        rule.onNodeWithTag("food_sheet_commit").assertIsNotEnabled()
        rule.onNodeWithTag("template_edit_save").assertIsNotEnabled()
    }

    @Test
    fun foodSheet_closingWithTheCloseButton_discardsTheTypedQuantity() {
        templates.templates += desayunoA
        showDetails()
        typeInFoodSheet("180")

        rule.onNodeWithTag("food_sheet_close").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("food_sheet").assertDoesNotExist()
        rule.assertRowText("template_edit_item_0", "120 kcal")
        rule.onNodeWithTag("template_edit_kcal").assertTextContains("270 kcal")
        rule.onNodeWithTag("template_edit_save").assertIsNotEnabled()
    }

    @Test
    fun foodSheet_dismissedWithBack_discardsTheTypedQuantity() {
        templates.templates += desayunoA
        showDetails()
        typeInFoodSheet("180")

        // The sheet lives in its own dialog window: back goes to the dialog, not to the activity.
        rule.runOnUiThread {
            val dialog = ShadowDialog.getLatestDialog() as ComponentDialog
            dialog.onBackPressedDispatcher.onBackPressed()
        }
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("food_sheet").fetchSemanticsNodes().isEmpty()
        }

        rule.onNodeWithTag("food_sheet").assertDoesNotExist()
        rule.onNodeWithTag("template_edit_kcal").assertTextContains("270 kcal")
        rule.onNodeWithTag("template_edit_save").assertIsNotEnabled()
    }

    @Test
    fun foodSheet_guardar_appliesTheQuantityAndClosesTheSheet() {
        templates.templates += desayunoA
        showDetails()
        typeInFoodSheet("180")

        rule.onNodeWithTag("food_sheet_commit").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("food_sheet").assertDoesNotExist()
        rule.assertRowText("template_edit_item_0", "180 g")
        rule.assertRowText("template_edit_item_0", "180 kcal")
        rule.onNodeWithTag("template_edit_kcal").assertTextContains("330 kcal")
        assertEquals(120.0, templates.templates.single().items.first().amount, 0.0)
    }

    @Test
    fun guardar_appliesNameRemovalAndAmountTogether_inOneSave_andLeaves() {
        templates.templates += desayunoA
        showDetails()
        rule.onNodeWithTag("template_edit_name").performTextReplacement("Desayuno B")
        editFirstFoodTo("150")
        swipeRowToTheEnd(1)

        rule.onNodeWithTag("template_edit_save").performClick()
        rule.waitForIdle()

        val saved = templates.templates.single()
        assertEquals(1, templates.saveCalls)
        assertEquals("Desayuno B", saved.name)
        assertEquals(listOf("eggs" to 150.0), saved.items.map { it.food.id to it.amount })
        assertTrue(callbacks.saved)
    }

    @Test
    fun guardar_withANameTakenOnTheServer_showsTheHint_andKeepsTheDraft() {
        templates.templates += desayunoA
        showDetails()
        rule.onNodeWithTag("template_edit_name").performTextReplacement("Cena")
        templates.templates += desayunoA.copy(id = "t2", name = "Cena")
        rule.waitForIdle()

        rule.onNodeWithTag("template_edit_save").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("template_edit_name_error").assertTextEquals("Ya existe una comida con ese nombre")
        rule.onNodeWithTag("template_edit_name").assertTextContains("Cena")
        assertEquals("Desayuno A", templates.templates.first { it.id == "t1" }.name)
        assertFalse(callbacks.saved)
    }

    @Test
    fun offline_guardarIsDisabled_andTheBannerExplainsWhy() {
        templates.templates += desayunoA
        showDetails()
        editFirstFoodTo("150")
        connectivity.state.value = false
        rule.waitForIdle()

        rule.onNodeWithTag("template_edit_save").assertIsNotEnabled()
        rule.onNodeWithTag("offline_hint").assertIsDisplayed()
    }

    private val twiceEggs = MealTemplate(
        id = "t1",
        name = "Doble huevo",
        mealSlotId = "breakfast",
        items = listOf(MealTemplateItem(eggs, 100.0, null), MealTemplateItem(eggs, 50.0, "1 huevo")),
    )

    @Test
    fun guardar_afterEditingOneOfTwoRowsOfTheSameFood_keepsTheOtherRowUntouched() {
        templates.templates += twiceEggs
        showDetails()
        rule.onNodeWithTag("template_edit_item_1").performClick()
        rule.onNodeWithTag("food_sheet_amount").performTextReplacement("80")
        rule.onNodeWithTag("food_sheet_commit").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("template_edit_save").performClick()
        rule.waitForIdle()

        val saved = templates.templates.single()
        assertEquals(listOf("eggs" to 100.0, "eggs" to 80.0), saved.items.map { it.food.id to it.amount })
    }

    @Test
    fun aComidaThatCannotBeRead_showsTheErrorState_andRetryLoadsIt() {
        templates.listFailure = AppError.Network
        templates.templates += desayunoA
        showDetails()

        rule.onNodeWithTag("template_edit_error_state_action").assertIsDisplayed()
        templates.listFailure = null
        rule.onNodeWithTag("template_edit_error_state_action").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("template_edit_item_0").assertIsDisplayed()
    }
}
