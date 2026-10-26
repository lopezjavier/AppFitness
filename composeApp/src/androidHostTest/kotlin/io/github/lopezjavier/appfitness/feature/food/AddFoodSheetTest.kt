package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.FoodPortion
import io.github.lopezjavier.appfitness.domain.model.MealCode
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.NutritionSnapshot
import io.github.lopezjavier.appfitness.domain.usecase.QuantityCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Slots = listOf(
    MealSlot("breakfast", MealCode.Breakfast, "Desayuno", 0),
    MealSlot("lunch", MealCode.Lunch, "Comida", 1),
    MealSlot("snack", MealCode.Snack, "Merienda", 2),
    MealSlot("dinner", MealCode.Dinner, "Cena", 3),
)

private fun content(
    food: Food,
    amount: String = "100",
    portionId: String? = null,
    slotId: String? = "lunch",
    preview: NutritionSnapshot? = NutritionSnapshot(110.0, 10.0, 20.0, 5.0),
    error: QuantityCheck? = null,
    slots: List<MealSlot> = Slots,
    offline: Boolean = false,
    submitting: Boolean = false,
    pick: Boolean = false,
) = AddFoodUiState.Content(
    AddFoodContent(
        food = food,
        amountText = amount,
        selectedPortionId = portionId,
        slots = slots,
        selectedSlotId = slotId,
        resolvedAmount = preview?.let { 100.0 },
        preview = preview,
        quantityError = error,
        isOffline = offline,
        submitting = submitting,
        pick = pick,
    ),
)

/** US-17 / US-22 add-food sheet, rendered from a given state. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class AddFoodSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<AddFoodEvent>()
    private val chicken = food("chicken", "Pechuga de pollo", kcal = 110.0)
    private val egg = food("egg", "Huevo", kcal = 200.0, portions = listOf(FoodPortion("p1", "huevo", 55.0)))

    private fun show(state: AddFoodUiState, dismissed: () -> Unit = {}) {
        rule.setContent {
            AppTheme { AddFoodSheet(state, { events += it }, dismissed) }
        }
        rule.waitForIdle()
    }

    @Test
    fun sheet_showsTheFoodNutritionPer100AndThePrefilledAmount() {
        show(content(chicken))

        rule.onNodeWithTag("food_sheet_title").assertTextEquals("Pechuga de pollo")
        rule.onNodeWithTag("food_sheet_details_toggle").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Por 100 g").assertExists()
        rule.onNodeWithTag("food_sheet_amount").assertTextContains("100")
        rule.onNodeWithTag("food_sheet_totals").assertTextContains("110 kcal", substring = true)
    }

    @Test
    fun mealSelector_isLabelledAnadirAAboveTheChips() {
        show(content(chicken))

        rule.onNodeWithText("Añadir a:").assertExists()
        val labelTop = rule.onNodeWithText("Añadir a:").getUnclippedBoundsInRoot().top
        val chipsTop = rule.onNodeWithTag("food_sheet_meals").getUnclippedBoundsInRoot().top
        assertTrue(labelTop < chipsTop)
    }

    @Test
    fun pickMode_hidesTheMealSelector_andKeepsAddEnabledOffline() {
        show(content(chicken, slotId = null, slots = emptyList(), offline = true, pick = true))

        rule.onNodeWithTag("food_sheet_meals").assertDoesNotExist()
        rule.onNodeWithTag("food_sheet_no_meal").assertDoesNotExist()
        rule.onNodeWithTag("food_sheet_commit").assertTextEquals("Añadir")
        rule.onNodeWithTag("food_sheet_commit").assertIsEnabled()
    }

    @Test
    fun normalMode_stillShowsTheMealSelector() {
        show(content(chicken))

        rule.onNodeWithTag("food_sheet_meals").assertExists()
    }

    @Test
    fun liquid_isShownPer100Millilitres_andRoundsTheKcalHalfUp() {
        val milk = food("milk", "Leche", kcal = 45.0, baseUnit = "ml")
        show(content(milk, amount = "250", preview = NutritionSnapshot(112.5, 8.0, 12.0, 8.0)))

        rule.onNodeWithTag("food_sheet_details_toggle").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Por 100 ml").assertExists()
        rule.onNodeWithTag("food_sheet_totals").assertTextContains("113 kcal", substring = true)
    }

    @Test
    fun foodWithPortions_offersThemNextToTheBaseUnit_andReportsThePick() {
        show(content(egg))

        rule.onNodeWithTag("food_sheet_portion_option_base").assertIsSelected()
        rule.onNodeWithText("huevo (55 g)").assertExists()
        rule.onNodeWithTag("food_sheet_portion_option_p1").performClick()

        assertEquals(listOf<AddFoodEvent>(AddFoodEvent.PortionSelected("p1")), events)
    }

    @Test
    fun foodWithoutPortions_hasNoPortionRow() {
        show(content(chicken))

        rule.onNodeWithTag("food_sheet_portion").assertDoesNotExist()
    }

    @Test
    fun invalidQuantity_disablesAdd_andShowsTheFieldError() {
        show(content(chicken, amount = "0", preview = null, error = QuantityCheck.TooLow))

        rule.onNodeWithTag("food_sheet_error").assertTextEquals("Introduce una cantidad entre 0 y 5000")
        rule.onNodeWithTag("food_sheet_commit").assertIsNotEnabled()
    }

    @Test
    fun quantityAbove5000_showsTheSameError() {
        show(content(chicken, amount = "5001", preview = null, error = QuantityCheck.TooHigh))

        rule.onNodeWithTag("food_sheet_error").assertExists()
        rule.onNodeWithTag("food_sheet_commit").assertIsNotEnabled()
    }

    @Test
    fun preselectedMeal_isTheSelectedOne_andChangingItEmitsTheChoice() {
        show(content(chicken, slotId = "dinner"))

        rule.onNodeWithTag("food_sheet_meal_dinner").assertIsSelected()
        rule.onNodeWithTag("food_sheet_meal_snack").performClick()

        assertEquals(listOf<AddFoodEvent>(AddFoodEvent.SlotSelected("snack")), events)
    }

    @Test
    fun fiveMeals_areSingleSelectChips_andChoosingOneEmitsIt() {
        val five = Slots + MealSlot("second", MealCode.Snack, "Segundo desayuno", 4)
        show(content(chicken, slotId = "lunch", slots = five))

        rule.onNodeWithTag("food_sheet_meal_lunch").assertIsSelected()
        rule.onNodeWithTag("food_sheet_meal_second").assertIsNotSelected()
        rule.onNodeWithTag("food_sheet_meal_second").performClick()

        assertEquals(listOf<AddFoodEvent>(AddFoodEvent.SlotSelected("second")), events)
    }

    @Test
    fun fourMeals_alsoUseChips_withExactlyOneSelected() {
        show(content(chicken, slotId = "dinner"))

        rule.onNodeWithTag("food_sheet_meal_dinner").assertIsSelected()
        rule.onNodeWithTag("food_sheet_meal_breakfast").assertIsNotSelected()
        rule.onNodeWithTag("food_sheet_meal_lunch").assertIsNotSelected()
        rule.onNodeWithTag("food_sheet_meal_snack").assertIsNotSelected()
    }

    @Test
    fun quantityComesBeforeTheTotalsAndTheMeals_withTheDetailsCollapsed() {
        show(content(chicken))

        rule.onNodeWithTag("food_sheet_details_toggle").assertExists()
        rule.onNodeWithTag("add_food_label_nutrients").assertDoesNotExist()
        rule.onNodeWithTag("food_sheet_totals").assertExists()
    }

    @Test
    fun offline_showsTheOneOfflineHint_andDisablesAdd() {
        show(content(chicken, offline = true))

        rule.onNodeWithTag("food_sheet_commit_hint").assertTextEquals("Sin conexión: no se puede añadir")
        rule.onNodeWithTag("food_sheet_commit").assertIsNotEnabled()
    }

    @Test
    fun validSheet_enablesAdd_andSubmits() {
        show(content(chicken))

        rule.onNodeWithTag("food_sheet_commit").assertIsEnabled().performClick()

        assertEquals(listOf<AddFoodEvent>(AddFoodEvent.Submit), events)
    }

    @Test
    fun offline_disablesAdd() {
        show(content(chicken, offline = true))

        rule.onNodeWithTag("food_sheet_commit").assertIsNotEnabled()
    }

    @Test
    fun noEnabledMeal_showsAHint_andDisablesAdd() {
        show(content(chicken, slotId = null, slots = emptyList()))

        rule.onNodeWithTag("food_sheet_no_meal").assertExists()
        rule.onNodeWithTag("food_sheet_commit").assertIsNotEnabled()
    }

    @Test
    fun loading_showsPlaceholders_andFailureOffersARetry() {
        show(AddFoodUiState.Loading)
        rule.onNodeWithTag("food_sheet_loading").assertExists()
    }

    @Test
    fun loadError_offersARetry() {
        show(AddFoodUiState.Error)

        rule.onNodeWithTag("food_sheet_error_state_action").performClick()

        assertEquals(listOf<AddFoodEvent>(AddFoodEvent.Retry), events)
    }
}
