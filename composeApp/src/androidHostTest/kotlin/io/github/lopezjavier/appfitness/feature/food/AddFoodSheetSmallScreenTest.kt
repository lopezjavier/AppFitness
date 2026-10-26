package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.MealCode
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.Micronutrient
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.MicronutrientUnit
import io.github.lopezjavier.appfitness.domain.model.Micronutrients
import io.github.lopezjavier.appfitness.domain.model.NutritionSnapshot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-47: with the full usual label (8 rows) the quantity field stays on a 360x640 dp screen. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class AddFoodSheetSmallScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun usualLabelRows_leaveTheQuantityFieldOnScreen() {
        val usual = listOf(
            MicronutrientKind.SaturatedFat,
            MicronutrientKind.Sugars,
            MicronutrientKind.Fiber,
            MicronutrientKind.Salt,
        )
        val state = AddFoodUiState.Content(
            AddFoodContent(
                food = food("yogurt", "Yogur casero", isManual = true),
                amountText = "100",
                selectedPortionId = null,
                slots = listOf(MealSlot("lunch", MealCode.Lunch, "Comida", 1)),
                selectedSlotId = "lunch",
                resolvedAmount = 100.0,
                preview = NutritionSnapshot(110.0, 10.0, 20.0, 5.0),
                quantityError = null,
                isOffline = false,
                submitting = false,
                micronutrients = MicronutrientsState.Shown(
                    Micronutrients(usual.map { Micronutrient(it, 1.0, MicronutrientUnit.G) }),
                ),
            ),
        )
        rule.setContent { AppTheme { AddFoodSheet(state, {}, {}) } }
        rule.waitForIdle()

        val amount = rule.onNodeWithTag("food_sheet_amount").getUnclippedBoundsInRoot()
        assertTrue("quantity field within 640 dp: ${amount.bottom}", amount.bottom <= 640.dp)
    }
}
