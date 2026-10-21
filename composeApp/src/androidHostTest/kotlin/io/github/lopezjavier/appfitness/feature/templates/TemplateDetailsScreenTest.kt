package io.github.lopezjavier.appfitness.feature.templates

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.centerY
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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

/**
 * US-21 comida details as the editor: rows and header with nutrition, the food sheet, swipe to remove, and "Guardar"
 * with "Sobrescribir" / "Guardar como nueva". Nothing is stored before "Guardar".
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
        rule.onNodeWithTag("template_details_item_0").performClick()
        rule.onNodeWithTag("template_food_sheet_amount").performTextReplacement(grams)
        rule.onNodeWithTag("template_food_sheet_save").performClick()
        rule.waitForIdle()
    }

    private fun typeInFoodSheet(grams: String) {
        rule.onNodeWithTag("template_details_item_0").performClick()
        rule.onNodeWithTag("template_food_sheet_amount").performTextReplacement(grams)
        rule.waitForIdle()
    }

    private fun swipeRowToTheEnd(index: Int) {
        rule.onNodeWithTag("template_details_item_$index").performTouchInput {
            swipeLeft(startX = right - 1f, endX = 0f)
        }
        rule.waitForIdle()
    }

    @Test
    fun details_showTheNameTheTotalAndEveryFoodWithItsQuantityAndNutrition() {
        templates.templates += desayunoA
        showDetails()

        rule.onNodeWithTag("template_details_name").assertTextContains("Desayuno A")
        rule.onNodeWithTag("template_details_kcal").assertTextContains("270 kcal")
        rule.onNodeWithTag("template_details_macros").assertIsDisplayed()
        rule.onNodeWithText("P 42 g").assertIsDisplayed()
        rule.onNodeWithTag("template_details_item_0").assertTextContains("Huevos revueltos")
        rule.onNodeWithTag("template_details_item_0").assertTextContains("120 g")
        rule.onNodeWithTag("template_details_item_0").assertTextContains("120 kcal", substring = true)
        rule.onNodeWithTag("template_details_item_1").assertTextContains("Batido de proteína")
        rule.onNodeWithTag("template_details_item_1").assertTextContains("1 vaso")
        rule.onNodeWithTag("template_details_item_1").assertTextContains("300 g")
        rule.onNodeWithTag("template_details_item_1").assertTextContains("150 kcal", substring = true)
    }

    @Test
    fun details_haveNoEditButton() {
        templates.templates += desayunoA
        showDetails()

        rule.onNodeWithTag("template_details_edit").assertDoesNotExist()
        rule.onNodeWithText("Editar").assertDoesNotExist()
    }

    @Test
    fun tappingAFood_opensItsSheet_andTypingGramsOnlyPreviewsThem() {
        templates.templates += desayunoA
        showDetails()

        rule.onNodeWithTag("template_details_item_0").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("template_food_sheet_name").assertTextContains("Huevos revueltos")
        rule.onNodeWithTag("template_food_sheet_amount").performTextReplacement("180")
        rule.waitForIdle()

        rule.onNodeWithTag("template_food_sheet_preview_kcal", useUnmergedTree = true).assertTextContains("180 kcal")
        rule.onNodeWithTag("template_details_kcal").assertTextContains("270 kcal")
        rule.onNodeWithTag("template_details_item_0").assertTextContains("120 kcal", substring = true)
        assertTrue(templates.templates.single().items.first().amount == 120.0)
    }

    @Test
    fun foodSheet_withMicronutrients_showsThem() {
        templates.templates += desayunoA
        foods.micronutrientResults["eggs"] =
            Result.success(Micronutrients(listOf(Micronutrient(MicronutrientKind.Fiber, 2.0, MicronutrientUnit.G))))
        showDetails()

        rule.onNodeWithTag("template_details_item_0").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("template_food_sheet_micronutrients").assertIsDisplayed()
    }

    @Test
    fun quitarDeLaComida_removesTheRow_andStoresNothingUntilGuardar() {
        templates.templates += desayunoA
        showDetails()

        rule.onNodeWithTag("template_details_item_1").performClick()
        rule.onNodeWithTag("template_food_sheet_remove").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("template_details_item_1").assertDoesNotExist()
        rule.onNodeWithTag("template_food_sheet").assertDoesNotExist()
        rule.onNodeWithTag("template_details_kcal").assertTextContains("120 kcal")
        assertEquals(2, templates.templates.single().items.size)
    }

    @Test
    fun theLastFood_hasNoQuitarButton_andIsNotSwipeable() {
        templates.templates += desayunoA
        showDetails()
        swipeRowToTheEnd(1)

        rule.onNodeWithTag("template_details_item_0").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("template_food_sheet_remove").assertDoesNotExist()
        rule.onNodeWithTag("template_food_sheet_close").performClick()
        rule.waitForIdle()
        swipeRowToTheEnd(0)

        rule.onNodeWithTag("template_details_item_0").assertIsDisplayed()
    }

    @Test
    fun swipingARowToTheEnd_removesIt_withoutStoringIt() {
        templates.templates += desayunoA
        showDetails()

        swipeRowToTheEnd(1)

        rule.onNodeWithTag("template_details_item_1").assertDoesNotExist()
        rule.onNodeWithTag("template_details_item_0").assertIsDisplayed()
        assertEquals(2, templates.templates.single().items.size)
    }

    @Test
    fun aShortDrag_doesNotRemoveTheRow() {
        templates.templates += desayunoA
        showDetails()

        rule.onNodeWithTag("template_details_item_1").performTouchInput {
            down(Offset(right - 1f, centerY))
            moveTo(Offset(right * 0.55f, centerY))
            up()
        }
        rule.waitForIdle()

        rule.onNodeWithTag("template_details_item_1").assertIsDisplayed()
    }

    @Test
    fun guardar_isDisabledUntilTheDraftChanges() {
        templates.templates += desayunoA
        showDetails()
        rule.onNodeWithTag("template_details_save").assertIsNotEnabled()

        editFirstFoodTo("150")

        rule.onNodeWithTag("template_details_save").assertIsEnabled()
    }

    @Test
    fun guardar_withAnInvalidAmount_isDisabled_andTheRowShowsTheError() {
        templates.templates += desayunoA
        showDetails()

        typeInFoodSheet("0")

        rule.onNodeWithTag("template_food_sheet_save").assertIsNotEnabled()
        rule.onNodeWithTag("template_details_save").assertIsNotEnabled()
    }

    @Test
    fun foodSheet_closingWithTheCloseButton_discardsTheTypedQuantity() {
        templates.templates += desayunoA
        showDetails()
        typeInFoodSheet("180")

        rule.onNodeWithTag("template_food_sheet_close").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("template_food_sheet").assertDoesNotExist()
        rule.onNodeWithTag("template_details_item_0").assertTextContains("120 kcal", substring = true)
        rule.onNodeWithTag("template_details_kcal").assertTextContains("270 kcal")
        rule.onNodeWithTag("template_details_save").assertIsNotEnabled()
    }

    @Test
    fun foodSheet_dismissedWithBack_discardsTheTypedQuantity() {
        templates.templates += desayunoA
        showDetails()
        typeInFoodSheet("180")

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        rule.onNodeWithTag("template_food_sheet").assertDoesNotExist()
        rule.onNodeWithTag("template_details_kcal").assertTextContains("270 kcal")
        rule.onNodeWithTag("template_details_save").assertIsNotEnabled()
    }

    @Test
    fun foodSheet_guardar_appliesTheQuantityAndClosesTheSheet() {
        templates.templates += desayunoA
        showDetails()
        typeInFoodSheet("180")

        rule.onNodeWithTag("template_food_sheet_save").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("template_food_sheet").assertDoesNotExist()
        rule.onNodeWithTag("template_details_item_0").assertTextContains("180 g", substring = true)
        rule.onNodeWithTag("template_details_item_0").assertTextContains("180 kcal", substring = true)
        rule.onNodeWithTag("template_details_kcal").assertTextContains("330 kcal")
        assertEquals(120.0, templates.templates.single().items.first().amount, 0.0)
    }

    @Test
    fun guardar_afterChangingAnAmountAndRemovingAFood_asksWhetherToOverwriteOrSaveAsNew() {
        templates.templates += desayunoA
        showDetails()
        editFirstFoodTo("150")
        swipeRowToTheEnd(1)

        rule.onNodeWithTag("template_details_save").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("template_save_overwrite").assertIsDisplayed()
        rule.onNodeWithTag("template_save_as_new").assertIsDisplayed()
    }

    @Test
    fun sobrescribir_replacesTheFoodsAndQuantities_keepsTheName_andLeaves() {
        templates.templates += desayunoA
        showDetails()
        editFirstFoodTo("150")
        swipeRowToTheEnd(1)

        rule.onNodeWithTag("template_details_save").performClick()
        rule.onNodeWithTag("template_save_overwrite").performClick()
        rule.waitForIdle()

        val saved = templates.templates.single()
        assertEquals("Desayuno A", saved.name)
        assertEquals(listOf("eggs" to 150.0), saved.items.map { it.food.id to it.amount })
        assertTrue(callbacks.saved)
    }

    @Test
    fun guardarComoNueva_withANewName_createsASecondComida_andLeavesTheOriginalUntouched() {
        templates.templates += desayunoA
        showDetails()
        editFirstFoodTo("200")

        rule.onNodeWithTag("template_details_save").performClick()
        rule.onNodeWithTag("template_save_as_new").performClick()
        rule.onNodeWithTag("new_template_name").performTextReplacement("Desayuno B")
        rule.onNodeWithTag("new_template_submit").performClick()
        rule.waitForIdle()

        assertEquals(listOf("Desayuno A", "Desayuno B"), templates.templates.map { it.name }.sorted())
        assertEquals(120.0, templates.templates.first { it.name == "Desayuno A" }.items.first().amount, 0.0)
        assertTrue(callbacks.saved)
    }

    @Test
    fun guardarComoNueva_withATakenName_rejectsItInline_andSavesNothing() {
        templates.templates += desayunoA
        showDetails()
        editFirstFoodTo("200")

        rule.onNodeWithTag("template_details_save").performClick()
        rule.onNodeWithTag("template_save_as_new").performClick()
        rule.onNodeWithTag("new_template_name").performTextReplacement("desayuno a")
        rule.onNodeWithTag("new_template_submit").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("new_template_name_error").assertTextEquals("Ya existe una comida con ese nombre")
        assertEquals(1, templates.templates.size)
        assertFalse(callbacks.saved)
    }

    @Test
    fun offline_guardarIsDisabled_andTheBannerExplainsWhy() {
        templates.templates += desayunoA
        showDetails()
        editFirstFoodTo("150")
        connectivity.state.value = false
        rule.waitForIdle()

        rule.onNodeWithTag("template_details_save").assertIsNotEnabled()
        rule.onNodeWithTag("template_details_offline").assertIsDisplayed()
    }

    private val twiceEggs = MealTemplate(
        id = "t1",
        name = "Doble huevo",
        mealSlotId = "breakfast",
        items = listOf(MealTemplateItem(eggs, 100.0, null), MealTemplateItem(eggs, 50.0, "1 huevo")),
    )

    @Test
    fun sobrescribir_afterEditingOneOfTwoRowsOfTheSameFood_keepsTheOtherRowUntouched() {
        templates.templates += twiceEggs
        showDetails()
        rule.onNodeWithTag("template_details_item_1").performClick()
        rule.onNodeWithTag("template_food_sheet_amount").performTextReplacement("80")
        rule.onNodeWithTag("template_food_sheet_save").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("template_details_save").performClick()
        rule.onNodeWithTag("template_save_overwrite").performClick()
        rule.waitForIdle()

        val saved = templates.templates.single()
        assertEquals(listOf("eggs" to 100.0, "eggs" to 80.0), saved.items.map { it.food.id to it.amount })
    }

    @Test
    fun aComidaThatCannotBeRead_showsTheErrorState_andRetryLoadsIt() {
        templates.listFailure = AppError.Network
        templates.templates += desayunoA
        showDetails()

        rule.onNodeWithTag("template_details_error_state_action").assertIsDisplayed()
        templates.listFailure = null
        rule.onNodeWithTag("template_details_error_state_action").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("template_details_item_0").assertIsDisplayed()
    }
}
