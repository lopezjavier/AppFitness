package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** US-56 / LST-1: the food sheet in its Edit context. */
@Suppress("TooManyFunctions") // One method per behaviour.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class FoodSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private var saves = 0
    private var deletes = 0
    private var dismissed = 0
    private var open by mutableStateOf(true)

    private fun edit(
        canSave: Boolean = true,
        canDelete: Boolean = true,
        saving: Boolean = false,
        hint: String? = null,
    ) = FoodSheetContext.Edit(canSave, saving, { saves++ }, { deletes++ }, canDelete, hint)

    private fun show(context: FoodSheetContext = edit(), withDetails: Boolean = true) {
        rule.setContent { AppTheme { Sheet(context, withDetails) } }
        rule.waitForIdle()
    }

    @Composable
    private fun Sheet(context: FoodSheetContext, withDetails: Boolean = true) {
        if (open) {
            FoodSheet(
                name = "Avena",
                brand = "Marca",
                quantity = { Text("Cantidad", Modifier.testTag("food_sheet_amount")) },
                totals = FoodSheetTotals("165", "6", "28", "3,5"),
                details = if (withDetails) ({ Text("Por 100 g", Modifier.testTag("per_100")) }) else null,
                extra = { Text("Comidas", Modifier.testTag("food_sheet_slots")) },
                context = context,
                onDismiss = {
                    dismissed++
                    open = false
                },
            )
        }
    }

    @Test
    fun editContext_showsHeaderQuantityTotalsAndBothPinnedActions() {
        show()
        rule.onNodeWithTag("food_sheet_title").assertTextEquals("Avena")
        rule.onNodeWithTag("food_sheet_brand").assertTextEquals("Marca")
        rule.onNodeWithTag("food_sheet_amount").assertIsDisplayed()
        rule.onNodeWithTag("food_sheet_totals_kcal", useUnmergedTree = true).assertTextEquals("165 kcal")
        rule.onNodeWithTag("food_sheet_slots").assertIsDisplayed()
        rule.onNodeWithTag("food_sheet_commit").assertIsDisplayed().assertIsEnabled()
        rule.onNodeWithTag("food_sheet_delete").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun detailsToggle_expandsAndCollapses() {
        show()
        rule.onNodeWithTag("per_100").assertDoesNotExist()
        rule.onNodeWithTag("food_sheet_details_toggle").performClick()
        rule.onNodeWithTag("per_100").assertIsDisplayed()
        rule.onNodeWithTag("food_sheet_details_toggle").performClick()
        rule.onNodeWithTag("per_100").assertDoesNotExist()
    }

    @Test
    fun withoutDetails_thereIsNoToggle() {
        show(withDetails = false)
        rule.onNodeWithTag("food_sheet_details_toggle").assertDoesNotExist()
    }

    @Test
    fun guardar_isDisabledWhenItCannotSave() {
        show(edit(canSave = false))
        rule.onNodeWithTag("food_sheet_commit").assertIsNotEnabled()
    }

    @Test
    fun eliminar_isDisabledOfflineAndTheHintShows() {
        show(edit(canDelete = false, canSave = false, hint = offlineHint("guardar ni eliminar")))
        rule.onNodeWithTag("food_sheet_delete").assertIsNotEnabled()
        rule.onNodeWithTag("food_sheet_commit_hint").assertTextEquals("Sin conexión: no se puede guardar ni eliminar")
    }

    @Test
    fun actions_callTheirCallbacks() {
        show()
        rule.onNodeWithTag("food_sheet_commit").performClick()
        rule.onNodeWithTag("food_sheet_delete").performClick()
        assertEquals(1, saves)
        assertEquals(1, deletes)
    }

    @Test
    fun closing_withXAndBack_onlyDismisses() {
        show()
        rule.onNodeWithTag("food_sheet_close").performClick()
        rule.waitForIdle()
        assertEquals(1, dismissed)
        assertEquals(0, saves)
        open = true
        rule.waitForIdle()
        Espresso.pressBack()
        rule.waitForIdle()
        assertEquals(2, dismissed)
        assertEquals(0, saves)
        assertEquals(0, deletes)
    }

    @Test
    fun detailsExpandedState_survivesRecreation() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent { AppTheme { Sheet(edit()) } }
        rule.waitForIdle()
        rule.onNodeWithTag("food_sheet_details_toggle").performClick()
        rule.onNodeWithTag("per_100").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.onNodeWithTag("food_sheet_details_toggle").assertExists()
        rule.onNodeWithTag("per_100").assertExists()
    }
}
