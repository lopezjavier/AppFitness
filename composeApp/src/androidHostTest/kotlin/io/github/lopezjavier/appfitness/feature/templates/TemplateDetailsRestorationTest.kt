package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.feature.food.food
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-40 for the comida details: the draft (amounts, removals, name) and the open sheets survive recreation. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class TemplateDetailsRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val eggs = food("eggs", "Huevos revueltos", kcal = 100.0)
    private val shake = food("shake", "Batido de proteína", kcal = 50.0)
    private val templates = FakeMealTemplateRepository(mutableMapOf("eggs" to eggs, "shake" to shake))
    private val connectivity = FakeConnectivityObserver()

    init {
        templates.templates += MealTemplate(
            "t1",
            "Desayuno A",
            null,
            listOf(MealTemplateItem(eggs, 120.0, null), MealTemplateItem(shake, 300.0, null)),
        )
    }

    private fun restorable(saved: SavedStateHandle): StateRestorationTester {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            val model = remember { detailsModel(templates, connectivity, saved) }
            DetailsHost(model)
        }
        rule.waitForIdle()
        return tester
    }

    private fun recreate(tester: StateRestorationTester) {
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
    }

    @Test
    fun typedRenameName_survivesRecreation() {
        val tester = restorable(SavedStateHandle())
        rule.onNodeWithTag("template_details_rename").performClick()
        rule.onNodeWithTag("rename_template_name").performTextReplacement("Mi comida")
        rule.waitForIdle()

        recreate(tester)

        rule.onNodeWithTag("rename_template_name").assertTextContains("Mi comida")
    }

    @Test
    fun draftAmountRemovalAndName_surviveRecreation() {
        val saved = SavedStateHandle()
        val tester = restorable(saved)
        rule.onNodeWithTag("template_details_item_0").performClick()
        rule.onNodeWithTag("template_food_sheet_amount").performTextReplacement("180")
        rule.onNodeWithTag("template_food_sheet_close").performClick()
        rule.onNodeWithTag("template_details_rename").performClick()
        rule.onNodeWithTag("rename_template_name").performTextReplacement("Desayuno B")
        rule.onNodeWithTag("rename_template_submit").performClick()
        rule.waitForIdle()

        recreate(tester)

        rule.onNodeWithTag("template_details_name").assertTextContains("Desayuno B")
        rule.onNodeWithTag("template_details_item_0").assertTextContains("180 g")
        rule.onNodeWithTag("template_details_save").assertIsDisplayed()
    }

    @Test
    fun aRemovedFood_staysRemovedAfterRecreation() {
        val tester = restorable(SavedStateHandle())
        rule.onNodeWithTag("template_details_item_1").performClick()
        rule.onNodeWithTag("template_food_sheet_remove").performClick()
        rule.waitForIdle()

        recreate(tester)

        rule.onNodeWithTag("template_details_item_1").assertDoesNotExist()
        rule.onNodeWithTag("template_details_item_0").assertIsDisplayed()
    }

    @Test
    fun openFoodSheetWithItsTypedGrams_survivesRecreation() {
        val tester = restorable(SavedStateHandle())
        rule.onNodeWithTag("template_details_item_0").performClick()
        rule.onNodeWithTag("template_food_sheet_amount").performTextReplacement("175")
        rule.waitForIdle()

        recreate(tester)

        rule.onNodeWithTag("template_food_sheet_amount").assertTextContains("175")
    }

    @Test
    fun openChoiceSheet_survivesRecreation() {
        val tester = restorable(SavedStateHandle())
        rule.onNodeWithTag("template_details_item_0").performClick()
        rule.onNodeWithTag("template_food_sheet_amount").performTextReplacement("150")
        rule.onNodeWithTag("template_food_sheet_close").performClick()
        rule.onNodeWithTag("template_details_save").performClick()
        rule.waitForIdle()

        recreate(tester)

        rule.onNodeWithTag("template_save_overwrite").assertIsDisplayed()
    }
}
