package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
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
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.food.FakeFoodRepository
import io.github.lopezjavier.appfitness.feature.food.food
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ADR-40 for the comida editor: the draft (name, amounts, removals, added foods), the open food sheet and a picked food
 * on its way back from the picker survive recreation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class TemplateDetailsRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val eggs = food("eggs", "Huevos revueltos", kcal = 100.0)
    private val shake = food("shake", "Batido de proteína", kcal = 50.0)
    private val rice = food("rice", "Arroz", kcal = 130.0)
    private val templates = FakeMealTemplateRepository(mutableMapOf("eggs" to eggs, "shake" to shake, "rice" to rice))
    private val foods = FakeFoodRepository().apply { known["rice"] = rice }
    private var model: TemplateDetailsViewModel? = null
    private val diary = FakeFoodDiaryRepository()
    private val connectivity = FakeConnectivityObserver()

    init {
        templates.templates += MealTemplate(
            "t1",
            "Desayuno A",
            null,
            listOf(MealTemplateItem(eggs, 120.0, null), MealTemplateItem(shake, 300.0, null)),
        )
    }

    private fun restorable(saved: SavedStateHandle, pickedFrom: SavedStateHandle? = null): StateRestorationTester {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            val current = remember { detailsModel(templates, connectivity, saved, foods = foods, diary = diary) }
            model = current
            val picked = pickedFrom?.getStateFlow<ArrayList<String>?>(PICKED_FOOD_KEY, null)?.collectAsState()?.value
            DetailsHost(current, pickedFood = picked, onConsumePickedFood = { pickedFrom?.set(PICKED_FOOD_KEY, null) })
        }
        rule.waitForIdle()
        return tester
    }

    private fun recreate(tester: StateRestorationTester) {
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
    }

    @Test
    fun typedName_survivesRecreation() {
        val tester = restorable(SavedStateHandle())
        rule.onNodeWithTag("template_edit_name").performTextReplacement("Mi comida")
        rule.waitForIdle()

        recreate(tester)

        rule.onNodeWithTag("template_edit_name").assertTextContains("Mi comida")
    }

    @Test
    fun draftAmountRemovalAndName_surviveRecreation() {
        val saved = SavedStateHandle()
        val tester = restorable(saved)
        rule.onNodeWithTag("template_edit_item_0").performClick()
        rule.onNodeWithTag("food_sheet_amount").performTextReplacement("180")
        rule.onNodeWithTag("food_sheet_commit").performClick()
        rule.onNodeWithTag("template_edit_name").performTextReplacement("Desayuno B")
        rule.waitForIdle()

        recreate(tester)

        rule.onNodeWithTag("template_edit_name").assertTextContains("Desayuno B")
        rule.assertRowText("template_edit_item_0", "180 g")
        rule.onNodeWithTag("template_edit_save").assertIsDisplayed()
    }

    @Test
    fun anAddedFood_survivesRecreation() {
        val tester = restorable(SavedStateHandle())
        rule.runOnUiThread { model?.onEvent(TemplateDetailsEvent.FoodPicked("rice", 150.0, null)) }
        rule.waitForIdle()
        rule.assertRowText("template_edit_item_2", "Arroz")

        recreate(tester)

        rule.assertRowText("template_edit_item_2", "Arroz")
        rule.assertRowText("template_edit_item_2", "150 g")
    }

    @Test
    fun aPickedFoodWaitingInTheBackStack_isAppliedOnce_evenAcrossRecreation() {
        val navHandle = SavedStateHandle()
        navHandle[PICKED_FOOD_KEY] = pickedFoodResult("rice", 150.0, null)
        val tester = restorable(SavedStateHandle(), pickedFrom = navHandle)
        rule.waitForIdle()
        rule.assertRowText("template_edit_item_2", "Arroz")

        recreate(tester)

        rule.assertRowText("template_edit_item_2", "Arroz")
        rule.onNodeWithTag("template_edit_item_3").assertDoesNotExist()
    }

    @Test
    fun aRemovedFood_staysRemovedAfterRecreation() {
        val tester = restorable(SavedStateHandle())
        rule.onNodeWithTag("template_edit_item_1").performClick()
        rule.onNodeWithTag("food_sheet_remove").performClick()
        rule.waitForIdle()

        recreate(tester)

        rule.onNodeWithTag("template_edit_item_1").assertDoesNotExist()
        rule.onNodeWithTag("template_edit_item_0").assertIsDisplayed()
    }

    @Test
    fun openFoodSheetWithItsTypedGrams_survivesRecreation() {
        val tester = restorable(SavedStateHandle())
        rule.onNodeWithTag("template_edit_item_0").performClick()
        rule.onNodeWithTag("food_sheet_amount").performTextReplacement("175")
        rule.waitForIdle()

        recreate(tester)

        rule.onNodeWithTag("food_sheet_amount").assertTextContains("175")
        rule.onNodeWithTag("food_sheet_commit").performClick()
        rule.waitForIdle()
        rule.assertRowText("template_edit_item_0", "175 g")
    }

    @Test
    fun typedFoodGramsNotYetSaved_survivesRecreation_andTheDraftKeepsTheOldValue() {
        val tester = restorable(SavedStateHandle())
        rule.onNodeWithTag("template_edit_item_0").performClick()
        rule.onNodeWithTag("food_sheet_amount").performTextReplacement("175")
        rule.waitForIdle()

        recreate(tester)

        rule.onNodeWithTag("food_sheet_amount").assertTextContains("175")
        rule.onNodeWithTag("food_sheet_close").performClick()
        rule.waitForIdle()
        rule.assertRowText("template_edit_item_0", "120 g")
    }

    @Test
    fun theEditorStaysOpen_withTheStagedDraft_afterRecreation_andStoresNothing() {
        val tester = restorable(SavedStateHandle())
        rule.onNodeWithTag("template_edit_item_0").performClick()
        rule.onNodeWithTag("food_sheet_amount").performTextReplacement("180")
        rule.onNodeWithTag("food_sheet_commit").performClick()
        rule.waitForIdle()

        recreate(tester)

        rule.assertRowText("template_edit_item_0", "180 g")
        rule.onNodeWithTag("template_edit_save").assertIsEnabled()
        assertEquals(0, templates.saveCalls)
        assertEquals(emptyList<Any>(), diary.addedBatches)
    }

    @Test
    fun theComidaDetails_stayOnTheSameRouteArguments_afterRecreation() {
        val saved = SavedStateHandle()
        val tester = restorable(saved)

        recreate(tester)

        rule.onNodeWithTag("template_edit_name").assertTextContains("Desayuno A")
        rule.onNodeWithTag("template_edit_save").assertIsDisplayed()
    }
}
