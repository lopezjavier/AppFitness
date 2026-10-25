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
 * US-53 on the comida details: it opens straight into the editable screen, "Añadir" logs the foods as shown without
 * storing the comida (ADR-63), "Guardar" stores, and neither a pencil nor an "Editar" button exists.
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

    private fun show() {
        templates.templates += desayunoA
        rule.setContent {
            DetailsHost(
                detailsModel(templates, connectivity, diary = diary, foods = FakeFoodRepository()),
                callbacks,
                onAdd = { addedCalls++ },
            )
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun setFirstFoodTo(grams: String) {
        tap("template_details_item_0")
        rule.onNodeWithTag("template_food_sheet_amount").performTextReplacement(grams)
        tap("template_food_sheet_save")
    }

    @Test
    fun opening_showsTheEditableDetails_withNothingAdded_andNoEditarNorPencil() {
        show()

        rule.onNodeWithTag("template_details_name_field").assertTextContains("Desayuno A")
        rule.onNodeWithTag("template_details_kcal").assertTextContains("270 kcal")
        rule.onNodeWithTag("template_details_add").assertIsEnabled()
        rule.onNodeWithTag("template_details_save").assertIsNotEnabled()
        rule.onNodeWithTag("template_preview_edit").assertDoesNotExist()
        rule.onNodeWithTag("template_details_rename").assertDoesNotExist()
        rule.onNodeWithTag("template_details_unsaved_hint").assertDoesNotExist()
        assertEquals(emptyList<Any>(), diary.addedBatches)
    }

    @Test
    fun anadir_withAnUntouchedDraft_logsTheSavedFoods_andReportsAdded() {
        show()

        tap("template_details_add")

        val batch = diary.addedBatches.single()
        assertEquals(listOf("eggs" to 120.0, "shake" to 300.0), batch.map { it.foodId to it.amount })
        assertEquals(setOf("lunch"), batch.map { it.mealSlotId }.toSet())
        assertEquals(1, addedCalls)
    }

    @Test
    fun editingTheName_keepsTheTextAndEnablesGuardar() {
        show()

        rule.onNodeWithTag("template_details_name_field").performTextReplacement("Desayuno B")
        rule.waitForIdle()

        rule.onNodeWithTag("template_details_name_field").assertTextContains("Desayuno B")
        rule.onNodeWithTag("template_details_save").assertIsEnabled()
    }

    @Test
    fun editingAnAmount_updatesTheRowAndTheTotals_andShowsTheUnsavedHint() {
        show()

        setFirstFoodTo("200")

        rule.onNodeWithTag("template_details_item_0").assertTextContains("200 g")
        rule.onNodeWithTag("template_details_kcal").assertTextContains("350 kcal")
        rule.onNodeWithTag("template_details_unsaved_hint").assertIsDisplayed()
    }

    @Test
    fun removingAFood_dropsTheRowAndUpdatesTheTotals_andTheLastOneCannotBeRemoved() {
        show()

        tap("template_details_item_1")
        tap("template_food_sheet_remove")

        rule.onNodeWithTag("template_details_item_1").assertDoesNotExist()
        rule.onNodeWithTag("template_details_kcal").assertTextContains("120 kcal")
        tap("template_details_item_0")
        rule.onNodeWithTag("template_food_sheet_remove").assertDoesNotExist()
    }

    @Test
    fun addFoodTap_asksForThePicker() {
        show()

        assertEquals(0, callbacks.pickerOpened)
        tap("template_details_add_food")

        assertEquals(1, callbacks.pickerOpened)
    }

    @Test
    fun anadir_withEditedAmount_logsTheDraftAsShown_andStoresNothing() {
        show()
        setFirstFoodTo("200")

        tap("template_details_add")

        val batch = diary.addedBatches.single()
        assertEquals(listOf("eggs" to 200.0, "shake" to 300.0), batch.map { it.foodId to it.amount })
        assertEquals(0, templates.saveCalls)
        assertEquals(desayunoA, templates.templates.single())
        assertEquals(1, addedCalls)
    }

    @Test
    fun guardar_storesTheEditsOnce_andReportsSaved_withoutLoggingAnything() {
        show()
        setFirstFoodTo("200")

        tap("template_details_save")

        assertEquals(1, templates.saveCalls)
        assertEquals(true, callbacks.saved)
        assertEquals(emptyList<Any>(), diary.addedBatches)
    }

    @Test
    fun offline_showsTheBanner_andDisablesAnadir() {
        connectivity.state.value = false
        show()

        rule.onNodeWithTag("template_details_offline").assertIsDisplayed()
        rule.onNodeWithTag("template_details_add").assertIsNotEnabled()
        rule.onNodeWithTag("template_details_delete").assertIsNotEnabled()
    }

    @Test
    fun failedAdd_showsTheFailureText_logsNothing_andAnadirCanBePressedAgain() {
        diary.failBatch()
        show()

        tap("template_details_add")

        rule.onNodeWithTag("template_details_add_failed").assertIsDisplayed()
        rule.onNodeWithTag("template_details_add").assertIsEnabled()
        assertEquals(0, addedCalls)
        assertEquals(emptyList<Any>(), diary.addedBatches)
    }

    @Test
    fun delete_isATrashIconInTheHeader_andReportsDeleted() {
        show()

        tap("template_details_delete")

        assertEquals(true, callbacks.deleted)
    }
}
