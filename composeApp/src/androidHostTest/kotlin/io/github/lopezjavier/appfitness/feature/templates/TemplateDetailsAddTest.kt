package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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
 * US-53 on the comida screens: the read-only details log the foods with the pinned "Añadir" and delete from the top
 * bar; "Editar" opens the editor, whose "Guardar" stores everything once and never logs anything.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class TemplateDetailsAddTest {
    @get:Rule
    val rule = createComposeRule()

    private val eggs = food("eggs", "Huevos revueltos", kcal = 100.0)
    private val shake = food("shake", "Batido de proteína", kcal = 50.0)
    private val templates = FakeMealTemplateRepository(mutableMapOf("eggs" to eggs, "shake" to shake))
    private val diary = FakeFoodDiaryRepository()
    private val connectivity = FakeConnectivityObserver()
    private val callbacks = DetailsCallbacks()
    private var addedCalls = 0
    private val desayunoA = MealTemplate(
        id = "t1",
        name = "Desayuno A",
        mealSlotId = "breakfast",
        items = listOf(MealTemplateItem(eggs, 120.0, null), MealTemplateItem(shake, 300.0, "1 vaso")),
    )

    private fun show(startInEditor: Boolean) {
        templates.templates += desayunoA
        rule.setContent {
            DetailsHost(
                detailsModel(templates, connectivity, diary = diary, foods = FakeFoodRepository()),
                callbacks,
                onAdd = { addedCalls++ },
                startInEditor = startInEditor,
            )
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun setFirstFoodTo(grams: String) {
        tap("template_edit_item_0")
        rule.onNodeWithTag("food_sheet_amount").performTextReplacement(grams)
        tap("food_sheet_commit")
    }

    @Test
    fun opening_showsTheReadOnlyDetails_withNothingAdded() {
        show(startInEditor = false)

        rule.onNodeWithTag("template_details_title").assertTextContains("Desayuno A")
        rule.onNodeWithTag("template_details_kcal").assertTextContains("270 kcal")
        rule.onNodeWithTag("template_details_primary").assertIsEnabled()
        rule.onNodeWithTag("template_edit_name").assertDoesNotExist()
        rule.onNodeWithTag("template_details_unsaved_hint").assertDoesNotExist()
        assertEquals(emptyList<Any>(), diary.addedBatches)
    }

    @Test
    fun anadir_logsTheSavedFoods_andReportsAdded() {
        show(startInEditor = false)

        tap("template_details_primary")

        val batch = diary.addedBatches.single()
        assertEquals(listOf("eggs" to 120.0, "shake" to 300.0), batch.map { it.foodId to it.amount })
        assertEquals(setOf("lunch"), batch.map { it.mealSlotId }.toSet())
        assertEquals(1, addedCalls)
    }

    @Test
    fun offline_disablesAnadir_withTheHint_andTheTopBarDeleteDoesNothing() {
        connectivity.state.value = false
        show(startInEditor = false)

        rule.onNodeWithTag("template_details_primary").assertIsNotEnabled()
        rule.onNodeWithTag("offline_hint").assertIsDisplayed()
        tap("template_details_delete")
        assertEquals(false, callbacks.deleted)
    }

    @Test
    fun failedAdd_isReported_logsNothing_andAnadirCanBePressedAgain() {
        diary.failBatch()
        show(startInEditor = false)

        tap("template_details_primary")

        assertEquals(1, callbacks.addFailed)
        rule.onNodeWithTag("template_details_primary").assertIsEnabled()
        assertEquals(0, addedCalls)
        assertEquals(emptyList<Any>(), diary.addedBatches)
    }

    @Test
    fun delete_isATopBarAction_andReportsDeletedAtOnce() {
        show(startInEditor = false)

        tap("template_details_delete")

        assertEquals(true, callbacks.deleted)
    }

    @Test
    fun editar_opensTheEditor_withGuardarDisabledUntilSomethingChanges() {
        show(startInEditor = false)

        tap("template_details_edit")

        rule.onNodeWithTag("template_edit_name").assertTextContains("Desayuno A")
        rule.onNodeWithTag("template_edit_kcal").assertTextContains("270 kcal")
        rule.onNodeWithTag("template_edit_save").assertIsNotEnabled()
        rule.onNodeWithTag("template_edit_primary").assertDoesNotExist()
    }

    @Test
    fun editingTheName_keepsTheTextAndEnablesGuardar() {
        show(startInEditor = true)

        rule.onNodeWithTag("template_edit_name").performTextReplacement("Desayuno B")
        rule.waitForIdle()

        rule.onNodeWithTag("template_edit_name").assertTextContains("Desayuno B")
        rule.onNodeWithTag("template_edit_save").assertIsEnabled()
    }

    @Test
    fun editingAnAmount_updatesTheRowAndTheTotals_withoutStoringAnything() {
        show(startInEditor = true)

        setFirstFoodTo("200")

        rule.assertRowText("template_edit_item_0", "200 g")
        rule.onNodeWithTag("template_edit_kcal").assertTextContains("350 kcal")
        rule.onNodeWithTag("template_edit_save").assertIsEnabled()
        assertEquals(0, templates.saveCalls)
        assertEquals(desayunoA, templates.templates.single())
    }

    @Test
    fun removingAFood_dropsTheRowAndUpdatesTheTotals_andTheLastOneCannotBeRemoved() {
        show(startInEditor = true)

        tap("template_edit_item_1")
        tap("food_sheet_remove")

        rule.onNodeWithTag("template_edit_item_1").assertDoesNotExist()
        rule.onNodeWithTag("template_edit_kcal").assertTextContains("120 kcal")
        assertEquals(desayunoA, templates.templates.single())
        tap("template_edit_item_0")
        rule.onNodeWithTag("food_sheet_remove").assertIsNotEnabled()
    }

    @Test
    fun addFoodTap_asksForThePicker() {
        show(startInEditor = true)

        assertEquals(0, callbacks.pickerOpened)
        tap("template_edit_add_item")

        assertEquals(1, callbacks.pickerOpened)
    }

    @Test
    fun addFoodFromTheDetails_startsEditing_andAsksForThePicker() {
        show(startInEditor = false)

        tap("template_details_add_item")

        assertEquals(1, callbacks.pickerOpened)
        rule.onNodeWithTag("template_edit_name").assertIsDisplayed()
    }

    @Test
    fun guardar_storesTheEditsOnce_andReportsSaved_withoutLoggingAnything() {
        show(startInEditor = true)
        setFirstFoodTo("200")

        tap("template_edit_save")

        assertEquals(1, templates.saveCalls)
        assertEquals(true, callbacks.saved)
        assertEquals(emptyList<Any>(), diary.addedBatches)
    }
}
