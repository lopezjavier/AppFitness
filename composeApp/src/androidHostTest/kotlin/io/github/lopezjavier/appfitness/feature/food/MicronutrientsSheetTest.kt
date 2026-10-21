package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
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

/** US-25: the micronutrients section of the add-food sheet. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class MicronutrientsSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private val yogurt = food("yogurt", "Yogur natural")

    private fun show(state: AddFoodUiState) {
        rule.setContent { AppTheme { AddFoodSheet(state, {}, {}) } }
        rule.waitForIdle()
    }

    @Test
    fun offFood_showsItsNutrientsPer100g() {
        show(
            sheet(
                yogurt,
                shown(
                    item(MicronutrientKind.Fiber, 2.5),
                    item(MicronutrientKind.Sugars, 4.1),
                    item(MicronutrientKind.Salt, 0.5),
                    item(MicronutrientKind.VitaminC, 12.0, MicronutrientUnit.MG),
                ),
            ),
        )

        rule.onNodeWithTag("add_food_micronutrients").assertExists()
        rule.onNodeWithTag("add_food_micronutrients_title").assertTextEquals("Información nutricional adicional")
        rule.onNodeWithTag("add_food_micronutrients_basis").assertTextEquals("Por 100 g")
        rule.onNodeWithTag("add_food_micronutrient_fiber").assertTextContains("Fibra", substring = true)
        rule.onNodeWithTag("add_food_micronutrient_fiber").assertTextContains("2,5 g", substring = true)
        rule.onNodeWithTag("add_food_micronutrient_sugars").assertTextContains("4,1 g", substring = true)
        rule.onNodeWithTag("add_food_micronutrient_salt").assertTextContains("0,5 g", substring = true)
        rule.onNodeWithTag("add_food_micronutrient_vitamin_c").assertTextContains("Vitamina C", substring = true)
        rule.onNodeWithTag("add_food_micronutrient_vitamin_c").assertTextContains("12 mg", substring = true)
    }

    @Test
    fun drink_showsThePer100mlBasis() {
        show(
            sheet(
                food("milk", "Leche", baseUnit = "ml"),
                shown(item(MicronutrientKind.Calcium, 120.0, MicronutrientUnit.MG)),
            ),
        )

        rule.onNodeWithTag("add_food_micronutrients_basis").assertTextEquals("Por 100 ml")
    }

    @Test
    fun microgramsAndTwoDecimals_areFormattedWithCommaAndUnit() {
        show(
            sheet(
                yogurt,
                shown(
                    item(MicronutrientKind.VitaminB9, 80.0, MicronutrientUnit.UG),
                    item(MicronutrientKind.Zinc, 0.25, MicronutrientUnit.MG),
                ),
            ),
        )

        rule.onNodeWithTag("add_food_micronutrient_vitamin_b9").assertTextContains("80 µg", substring = true)
        rule.onNodeWithTag("add_food_micronutrient_zinc").assertTextContains("0,25 mg", substring = true)
    }

    @Test
    fun nutrientTheProductDoesNotProvide_hasNoRow() {
        show(sheet(yogurt, shown(item(MicronutrientKind.Fiber, 2.5))))

        rule.onNodeWithTag("add_food_micronutrient_sugars").assertDoesNotExist()
    }

    @Test
    fun foodWithoutData_hasNoSectionAndNoHeading() {
        show(sheet(yogurt, MicronutrientsState.Hidden))

        rule.onNodeWithTag("add_food_micronutrients").assertDoesNotExist()
        rule.onNodeWithTag("add_food_micronutrients_title").assertDoesNotExist()
    }

    @Test
    fun whileLoading_nothingIsShown() {
        show(sheet(yogurt, MicronutrientsState.Loading))

        rule.onNodeWithTag("add_food_micronutrients").assertDoesNotExist()
    }

    @Test
    fun upToSixRows_haveNoToggle() {
        show(
            sheet(
                yogurt,
                shown(
                    item(MicronutrientKind.Fiber, 1.0),
                    item(MicronutrientKind.Sugars, 1.0),
                    item(MicronutrientKind.SaturatedFat, 1.0),
                    item(MicronutrientKind.Salt, 1.0),
                    item(MicronutrientKind.Sodium, 1.0),
                    item(MicronutrientKind.Iron, 1.0, MicronutrientUnit.MG),
                ),
            ),
        )

        rule.onNodeWithTag("add_food_micronutrient_iron").assertExists()
        rule.onNodeWithTag("add_food_micronutrients_toggle").assertDoesNotExist()
    }

    @Test
    fun moreThanSixRows_startCollapsedAndTheToggleExpandsAndCollapses() {
        show(sheet(yogurt, ManyRows))

        rule.onNodeWithTag("add_food_micronutrient_vitamin_c").assertExists()
        rule.onNodeWithTag("add_food_micronutrient_calcium").assertDoesNotExist()
        rule.onNodeWithTag("add_food_micronutrients_toggle").assertTextEquals("Ver todos (9)")

        rule.onNodeWithTag("add_food_micronutrients_toggle").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("add_food_micronutrient_calcium").assertExists()
        rule.onNodeWithTag("add_food_micronutrient_vitamin_b9").assertExists()
        rule.onNodeWithTag("add_food_micronutrients_toggle").assertTextEquals("Ver menos")

        rule.onNodeWithTag("add_food_micronutrients_toggle").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("add_food_micronutrient_calcium").assertDoesNotExist()
    }
}
