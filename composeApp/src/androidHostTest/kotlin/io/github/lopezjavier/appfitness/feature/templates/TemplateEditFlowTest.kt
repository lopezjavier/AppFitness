package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.github.lopezjavier.appfitness.app.AddFoodSlot
import io.github.lopezjavier.appfitness.app.AppScaffold
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FoodSearchSlot
import io.github.lopezjavier.appfitness.app.TemplateDetailsRoute
import io.github.lopezjavier.appfitness.app.TemplateDetailsSlot
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.feature.food.FakeFoodRepository
import io.github.lopezjavier.appfitness.feature.food.food
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * US-47 "Adding a food", "Everything applies together" and "Leaving without saving" as a journey: editor -> food picker
 * (search in pick mode) -> add-food sheet in pick mode -> back to the editor with the new row -> "Guardar". The search
 * and sheet are stubs that only check the pick flag and hand a food back; the editor is the real screen and view model.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class TemplateEditFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val eggs = food("eggs", "Huevos revueltos", kcal = 100.0)
    private val shake = food("shake", "Batido de proteína", kcal = 50.0)
    private val rice = food("rice", "Arroz", kcal = 130.0)
    private val templates = FakeMealTemplateRepository(mutableMapOf("eggs" to eggs, "shake" to shake, "rice" to rice))
    private val foods = FakeFoodRepository().apply { known["rice"] = rice }
    private val connectivity = FakeConnectivityObserver()
    private val callbacks = DetailsCallbacks()
    private lateinit var nav: NavHostController

    private val desayunoA = MealTemplate(
        id = "t1",
        name = "Desayuno A",
        mealSlotId = "breakfast",
        items = listOf(MealTemplateItem(eggs, 120.0, null), MealTemplateItem(shake, 300.0, null)),
    )

    private val detailsSlot: TemplateDetailsSlot = { modifier, templateId, onClose, onAddFood, picked, consumed ->
        val model = remember { detailsModel(templates, connectivity, templateId = templateId, foods = foods) }
        var closed by remember { mutableStateOf(false) }
        Box(modifier) {
            DetailsHost(model, callbacks, picked, consumed, onAddFood)
            if (callbacks.saved && !closed) {
                closed = true
                onClose()
            }
        }
    }
    private val searchSlot: FoodSearchSlot = { modifier, _, _, onOpenFood, _, _, _, pick ->
        Column(modifier.fillMaxSize().testTag("food_search_screen")) {
            Text("pick=$pick", Modifier.testTag("search_pick_flag"))
            Box(Modifier.size(48.dp).testTag("search_rice").clickable { onOpenFood("rice") })
        }
    }
    private val addFoodSlot: AddFoodSlot = { foodId, _, _, _, _, _, pick, onPick ->
        Column(Modifier.testTag("add_food_sheet")) {
            Text("$foodId|pick=$pick", Modifier.testTag("sheet_args"))
            Box(Modifier.size(48.dp).testTag("sheet_pick").clickable { onPick(foodId, 150.0, null) })
        }
    }

    private fun showEditor() {
        templates.templates += desayunoA
        rule.setContent {
            AppTheme {
                nav = rememberNavController()
                AppScaffold(
                    navController = nav,
                    foodSearch = searchSlot,
                    addFood = addFoodSlot,
                    templateDetails = detailsSlot,
                )
            }
        }
        rule.waitForIdle()
        rule.runOnUiThread { nav.navigate(TemplateDetailsRoute("t1")) }
        rule.waitForIdle()
    }

    private fun click(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun openPickerAndChooseRice() {
        click("template_details_add_food")
        rule.onNodeWithTag("search_pick_flag").assertTextEquals("pick=true")
        click("search_rice")
        rule.onNodeWithTag("sheet_args").assertTextEquals("rice|pick=true")
        click("sheet_pick")
    }

    @Test
    fun addingAFood_throughThePicker_comesBackAsANewRowOfTheDraft_withoutStoringIt() {
        showEditor()

        openPickerAndChooseRice()

        rule.onNodeWithTag("template_details_screen").assertIsDisplayed()
        rule.onNodeWithTag("food_search_screen").assertDoesNotExist()
        rule.onNodeWithTag("add_food_sheet").assertDoesNotExist()
        rule.onNodeWithTag("template_details_item_2").assertTextContains("Arroz")
        rule.onNodeWithTag("template_details_item_2").assertTextContains("150 g")
        assertEquals(2, templates.templates.single().items.size)
        assertEquals(0, templates.saveCalls)
    }

    @Test
    fun guardar_afterAddingAFood_savesNameRemovalAndAdditionTogether_once() {
        showEditor()
        openPickerAndChooseRice()
        rule.onNodeWithTag("template_details_item_1").performClick()
        rule.waitForIdle()
        click("template_food_sheet_remove")

        click("template_details_save")

        val saved = templates.templates.single()
        assertEquals(1, templates.saveCalls)
        assertEquals(listOf("eggs" to 120.0, "rice" to 150.0), saved.items.map { it.food.id to it.amount })
        assertTrue(callbacks.saved)
    }

    @Test
    fun backFromThePickerWithoutChoosing_leavesTheDraftUntouched() {
        showEditor()
        click("template_details_add_food")

        rule.runOnUiThread { nav.popBackStack() }
        rule.waitForIdle()

        rule.onNodeWithTag("template_details_screen").assertIsDisplayed()
        rule.onNodeWithTag("template_details_item_2").assertDoesNotExist()
        assertEquals(0, templates.saveCalls)
    }

    @Test
    fun leavingTheEditorWithoutGuardar_leavesTheComidaUntouched() {
        showEditor()
        openPickerAndChooseRice()

        rule.runOnUiThread { nav.popBackStack() }
        rule.waitForIdle()

        assertEquals(0, templates.saveCalls)
        assertEquals(desayunoA, templates.templates.single())
    }
}
