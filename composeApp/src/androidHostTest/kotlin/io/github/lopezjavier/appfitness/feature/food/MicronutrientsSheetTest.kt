package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.MealCode
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.Micronutrient
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.MicronutrientUnit
import io.github.lopezjavier.appfitness.domain.model.Micronutrients
import io.github.lopezjavier.appfitness.domain.model.NutritionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun item(kind: MicronutrientKind, amount: Double, unit: MicronutrientUnit = MicronutrientUnit.G) =
    Micronutrient(kind, amount, unit)

private fun sheet(food: Food, state: MicronutrientsState) = AddFoodUiState.Content(
    AddFoodContent(
        food = food,
        amountText = "100",
        selectedPortionId = null,
        slots = listOf(MealSlot("lunch", MealCode.Lunch, "Comida", 1)),
        selectedSlotId = "lunch",
        resolvedAmount = 100.0,
        preview = NutritionSnapshot(110.0, 10.0, 20.0, 5.0),
        quantityError = null,
        isOffline = false,
        submitting = false,
        micronutrients = state,
    ),
)

private fun shown(vararg items: Micronutrient) = MicronutrientsState.Shown(Micronutrients(items.toList()))

private val ManyRows = shown(
    item(MicronutrientKind.Fiber, 2.5),
    item(MicronutrientKind.Sugars, 4.1),
    item(MicronutrientKind.SaturatedFat, 1.2),
    item(MicronutrientKind.Salt, 0.5),
    item(MicronutrientKind.Sodium, 0.2),
    item(MicronutrientKind.VitaminC, 12.0, MicronutrientUnit.MG),
    item(MicronutrientKind.Calcium, 120.0, MicronutrientUnit.MG),
    item(MicronutrientKind.Iron, 1.4, MicronutrientUnit.MG),
    item(MicronutrientKind.VitaminB9, 80.0, MicronutrientUnit.UG),
)

/** US-25 / US-47: the label-ordered nutrient card of the add-food sheet. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class MicronutrientsSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private val yogurt = food("yogurt", "Yogur natural")

    private fun show(state: AddFoodUiState) {
        rule.setContent { AppTheme { AddFoodSheet(state, {}, {}) } }
        rule.waitForIdle()
        rule.onNodeWithTag("food_sheet_details_toggle").performClick()
        rule.waitForIdle()
    }

    private fun expand() {
        rule.onNodeWithTag("add_food_label_toggle").performClick()
        rule.waitForIdle()
    }

    private fun top(row: String) = rule.onNodeWithTag("add_food_label_row_$row").getUnclippedBoundsInRoot().top

    @Test
    fun food_showsItsMacroRowsPer100g() {
        show(sheet(yogurt, MicronutrientsState.Hidden))

        rule.onNodeWithTag("add_food_label_nutrients").assertExists()
        rule.onNodeWithTag("add_food_label_basis").assertTextEquals("Por 100 g")
        rule.onNodeWithTag("add_food_label_row_energy").assertTextContains("100 kcal", substring = true)
        rule.onNodeWithTag("add_food_label_row_protein").assertTextContains("10 g", substring = true)
        rule.onNodeWithTag("add_food_label_row_carbs").assertTextContains("20 g", substring = true)
        rule.onNodeWithTag("add_food_label_row_fat").assertTextContains("5 g", substring = true)
    }

    @Test
    fun offFood_showsItsExtraNutrientsWithTheirUnits() {
        show(
            sheet(
                yogurt,
                shown(
                    item(MicronutrientKind.Fiber, 2.5),
                    item(MicronutrientKind.Sugars, 4.1),
                    item(MicronutrientKind.Salt, 0.5),
                    item(MicronutrientKind.VitaminC, 12.0, MicronutrientUnit.MG),
                    item(MicronutrientKind.VitaminB9, 80.0, MicronutrientUnit.UG),
                    item(MicronutrientKind.Zinc, 0.25, MicronutrientUnit.MG),
                ),
            ),
        )
        expand()

        rule.onNodeWithTag("add_food_label_row_fiber").assertTextContains("Fibra", substring = true)
        rule.onNodeWithTag("add_food_label_row_fiber").assertTextContains("2,5 g", substring = true)
        rule.onNodeWithTag("add_food_label_row_sugars").assertTextContains("4,1 g", substring = true)
        rule.onNodeWithTag("add_food_label_row_salt").assertTextContains("0,5 g", substring = true)
        rule.onNodeWithTag("add_food_label_row_vitamin_c").assertTextContains("Vitamina C", substring = true)
        rule.onNodeWithTag("add_food_label_row_vitamin_c").assertTextContains("12 mg", substring = true)
        rule.onNodeWithTag("add_food_label_row_vitamin_b9").assertTextContains("80 µg", substring = true)
        rule.onNodeWithTag("add_food_label_row_zinc").assertTextContains("0,25 mg", substring = true)
    }

    @Test
    fun drink_showsThePer100mlBasis() {
        show(sheet(food("milk", "Leche", baseUnit = "ml"), shown(item(MicronutrientKind.Calcium, 120.0))))

        rule.onNodeWithTag("add_food_label_basis").assertTextEquals("Por 100 ml")
    }

    @Test
    fun nutrientTheFoodDoesNotHave_hasNoRow() {
        show(sheet(yogurt, shown(item(MicronutrientKind.Fiber, 2.5))))

        rule.onNodeWithTag("add_food_label_row_fiber").assertExists()
        rule.onNodeWithTag("add_food_label_row_sugars").assertDoesNotExist()
    }

    @Test
    fun foodWithoutExtras_hasNoToggle() {
        show(sheet(yogurt, shown(item(MicronutrientKind.Fiber, 1.0), item(MicronutrientKind.Salt, 1.0))))

        rule.onNodeWithTag("add_food_label_toggle").assertDoesNotExist()
    }

    @Test
    fun whileLoading_theMacroRowsAreShownAndNoExtras() {
        show(sheet(yogurt, MicronutrientsState.Loading))

        rule.onNodeWithTag("add_food_label_row_energy").assertExists()
        rule.onNodeWithTag("add_food_label_toggle").assertDoesNotExist()
    }

    @Test
    fun realZeroOfALabelNutrient_isShown() {
        show(sheet(yogurt, shown(item(MicronutrientKind.Sugars, 0.0))))

        rule.onNodeWithTag("add_food_label_row_sugars").assertTextContains("0 g", substring = true)
    }

    @Test
    fun usualRowsAreVisible_theRestSitsBehindTheToggle_whichExpandsAndCollapses() {
        show(sheet(yogurt, ManyRows))

        rule.onNodeWithTag("add_food_label_row_salt").assertExists()
        rule.onNodeWithTag("add_food_label_row_vitamin_c").assertDoesNotExist()
        rule.onNodeWithTag("add_food_label_row_sodium").assertDoesNotExist()
        rule.onNodeWithTag("add_food_label_toggle").assertTextEquals("Ver todos (13)")

        expand()

        rule.onNodeWithTag("add_food_label_row_calcium").assertExists()
        rule.onNodeWithTag("add_food_label_row_vitamin_b9").assertExists()
        rule.onNodeWithTag("add_food_label_toggle").assertTextEquals("Ver menos")

        expand()

        rule.onNodeWithTag("add_food_label_row_calcium").assertDoesNotExist()
    }

    @Test
    fun rowsFollowTheLabelOrder_forEveryFood() {
        show(sheet(yogurt, ManyRows))
        expand()

        val order = listOf(
            "energy", "fat", "saturated_fat", "carbs", "sugars", "fiber", "protein", "salt", "sodium",
            "vitamin_c", "vitamin_b9", "calcium", "iron",
        )
        val tops = order.map(::top)
        assertEquals(tops.sorted(), tops)
        assertTrue(tops.distinct().size == tops.size)
    }
}
