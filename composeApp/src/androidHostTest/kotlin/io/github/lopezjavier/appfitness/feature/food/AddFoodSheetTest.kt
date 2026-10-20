package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
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

    private fun show(state: AddFoodUiState, dismissed: () -> Unit = {}, submitFailed: Boolean = false) {
        rule.setContent {
            AppTheme { AddFoodSheet(state, { events += it }, dismissed, submitFailed = submitFailed) }
        }
        rule.waitForIdle()
    }

    @Test
    fun sheet_showsTheFoodNutritionPer100AndThePrefilledAmount() {
        show(content(chicken))

        rule.onNodeWithTag("add_food_sheet").assertExists()
        rule.onNodeWithTag("add_food_name").assertTextEquals("Pechuga de pollo")
        rule.onNodeWithText("Por 100 g").assertExists()
        rule.onNodeWithTag("add_food_amount").assertTextContains("100")
        rule.onNodeWithTag("add_food_preview").assertTextContains("110 kcal", substring = true)
    }

    @Test
    fun liquid_isShownPer100Millilitres_andRoundsTheKcalHalfUp() {
        val milk = food("milk", "Leche", kcal = 45.0, baseUnit = "ml")
        show(content(milk, amount = "250", preview = NutritionSnapshot(112.5, 8.0, 12.0, 8.0)))

        rule.onNodeWithText("Por 100 ml").assertExists()
        rule.onNodeWithTag("add_food_preview").assertTextContains("113 kcal", substring = true)
    }

    @Test
    fun foodWithPortions_offersThemNextToTheBaseUnit_andReportsThePick() {
        show(content(egg))

        rule.onNodeWithTag("add_food_portion_option_base").assertIsSelected()
        rule.onNodeWithText("huevo (55 g)").assertExists()
        rule.onNodeWithTag("add_food_portion_option_p1").performClick()

        assertEquals(listOf<AddFoodEvent>(AddFoodEvent.PortionSelected("p1")), events)
    }

    @Test
    fun foodWithoutPortions_hasNoPortionRow() {
        show(content(chicken))

        rule.onNodeWithTag("add_food_portion").assertDoesNotExist()
    }

    @Test
    fun invalidQuantity_disablesAdd_andShowsTheFieldError() {
        show(content(chicken, amount = "0", preview = null, error = QuantityCheck.TooLow))

        rule.onNodeWithTag("add_food_error").assertTextEquals("Introduce una cantidad entre 0 y 5000")
        rule.onNodeWithTag("add_food_submit").assertIsNotEnabled()
    }

    @Test
    fun quantityAbove5000_showsTheSameError() {
        show(content(chicken, amount = "5001", preview = null, error = QuantityCheck.TooHigh))

        rule.onNodeWithTag("add_food_error").assertExists()
        rule.onNodeWithTag("add_food_submit").assertIsNotEnabled()
    }

    @Test
    fun preselectedMeal_isTheSelectedOne_andChangingItEmitsTheChoice() {
        show(content(chicken, slotId = "dinner"))

        rule.onNodeWithTag("add_food_meal_dinner").assertIsSelected()
        rule.onNodeWithTag("add_food_meal_snack").performClick()

        assertEquals(listOf<AddFoodEvent>(AddFoodEvent.SlotSelected("snack")), events)
    }

    @Test
    fun validSheet_enablesAdd_andSubmits() {
        show(content(chicken))

        rule.onNodeWithTag("add_food_submit").assertIsEnabled().performClick()

        assertEquals(listOf<AddFoodEvent>(AddFoodEvent.Submit), events)
    }

    @Test
    fun offline_disablesAdd() {
        show(content(chicken, offline = true))

        rule.onNodeWithTag("add_food_submit").assertIsNotEnabled()
    }

    @Test
    fun noEnabledMeal_showsAHint_andDisablesAdd() {
        show(content(chicken, slotId = null, slots = emptyList()))

        rule.onNodeWithTag("add_food_no_meal").assertExists()
        rule.onNodeWithTag("add_food_submit").assertIsNotEnabled()
    }

    @Test
    fun submitFailure_isShownUnderTheButton() {
        show(content(chicken), submitFailed = true)

        rule.onNodeWithTag("add_food_submit_error").assertExists()
    }

    @Test
    fun loading_showsPlaceholders_andFailureOffersARetry() {
        show(AddFoodUiState.Loading)
        rule.onNodeWithTag("add_food_loading").assertExists()
    }

    @Test
    fun loadError_offersARetry() {
        show(AddFoodUiState.Error)

        rule.onNodeWithTag("add_food_error_state_action").performClick()

        assertEquals(listOf<AddFoodEvent>(AddFoodEvent.Retry), events)
    }
}
