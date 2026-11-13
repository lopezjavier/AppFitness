package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.MealCode
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.NutritionSnapshot
import io.github.lopezjavier.appfitness.domain.usecase.EnergyCheckLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Warning = EnergyWarning(EnergyCheckLevel.ClearlyWrong, kcal = 500.0, computedKcal = 250.0, gap = -250.0)

private fun warningState(
    warning: EnergyWarning? = Warning,
    offline: Boolean = false,
    food: Food = food("off1", "Galletas", kcal = 500.0, isOff = true),
) = AddFoodUiState.Content(
    AddFoodContent(
        food = food,
        amountText = "100",
        selectedPortionId = null,
        slots = listOf(MealSlot("lunch", MealCode.Lunch, "Comida", 1)),
        selectedSlotId = "lunch",
        resolvedAmount = 100.0,
        preview = NutritionSnapshot(500.0, 10.0, 20.0, 5.0),
        quantityError = null,
        isOffline = offline,
        submitting = false,
        energyWarning = warning,
    ),
)

/** US-116 (OFF-copy path): the energy warning of an Open Food Facts food inside the add sheet. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class AddFoodEnergyWarningSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<AddFoodEvent>()

    private fun show(state: AddFoodUiState) {
        rule.setContent { AppTheme { AddFoodSheet(state, { events += it }, {}) } }
        rule.waitForIdle()
    }

    @Test
    fun noWarning_showsNoNotice() {
        show(warningState(warning = null))

        rule.onNodeWithTag("add_food_energy_warning").assertDoesNotExist()
    }

    @Test
    fun warning_showsFactsWithClearlyWrongPrefix_andBlocksAnadir() {
        show(warningState())

        rule.onNodeWithTag("add_food_energy_warning").assertExists()
        rule.onNodeWithText("Parece un error:", substring = true).assertExists()
        rule.onNodeWithText("Declaradas: 500 kcal", substring = true).assertExists()
        rule.onNodeWithText("los macros suman 250 kcal", substring = true).assertExists()
        rule.onNodeWithTag("food_sheet_commit").assertExists().assertIsNotEnabled()
        rule.onNodeWithText("Añadir igualmente").assertDoesNotExist()
    }

    @Test
    fun corregirYGuardar_sendsCorrectOff() {
        show(warningState())

        rule.onNodeWithTag("add_food_energy_fix").assertTextEquals("Corregir y guardar mi copia")
        rule.onNodeWithTag("add_food_energy_fix").performClick()

        assertEquals(listOf<AddFoodEvent>(AddFoodEvent.CorrectOff), events)
    }

    @Test
    fun help_usesTheOffSpecificAdvice() {
        show(warningState())

        rule.onNodeWithTag("add_food_energy_warning_help").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("add_food_energy_warning").assertExists()
        rule.onNodeWithText("Open Food Facts", substring = true).assertExists()
    }

    @Test
    fun copyOfAnOffFood_offersEditar_butTheOffRowDoesNot() {
        show(warningState(warning = null, food = food("copy-off1", "Galletas", isManual = true)))
        rule.onNodeWithTag("food_sheet_edit_food").assertExists()
    }

    @Test
    fun offFood_hasNoEditar() {
        show(warningState(warning = null))
        rule.onNodeWithTag("food_sheet_edit_food").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun smallScreen_keepsAnadirAndTheFixButtonOnScreen() {
        show(warningState())

        val commit = rule.onNodeWithTag("food_sheet_commit").getUnclippedBoundsInRoot()
        val fix = rule.onNodeWithTag("add_food_energy_fix").getUnclippedBoundsInRoot()
        assertTrue("commit within 640 dp: ${commit.bottom}", commit.bottom <= 640.dp)
        assertTrue("fix within 640 dp: ${fix.bottom}", fix.bottom <= 640.dp)
    }
}
