package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.feature.food.food
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * US-56 on the comida screens: the read-only details (title, top-bar actions, totals, section header, rows without
 * swipe, "Añadir alimento", the pinned primary action, no "Se añadirá…" hint) and the editor's discard dialog.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class TemplateDetailsReadOnlyTest {
    @get:Rule
    val rule = createComposeRule()

    private val eggs = food("eggs", "Huevos revueltos", kcal = 100.0)
    private val shake = food("shake", "Batido de proteína", kcal = 50.0)
    private val templates = FakeMealTemplateRepository(mutableMapOf("eggs" to eggs, "shake" to shake))
    private val connectivity = FakeConnectivityObserver()
    private val callbacks = DetailsCallbacks()
    private val desayunoA = MealTemplate(
        id = "t1",
        name = "Desayuno A",
        mealSlotId = "breakfast",
        items = listOf(MealTemplateItem(eggs, 120.0, null), MealTemplateItem(shake, 300.0, "1 vaso")),
    )

    private fun show(startInEditor: Boolean = false) {
        templates.templates += desayunoA
        rule.setContent {
            DetailsHost(detailsModel(templates, connectivity), callbacks, startInEditor = startInEditor)
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun title_isTheComidaName_andTheTopBarHasEditarAndEliminar() {
        show()

        rule.onNodeWithTag("template_details_title").assertTextEquals("Desayuno A")
        rule.onNodeWithTag("template_details_edit").assertIsDisplayed()
        rule.onNodeWithTag("template_details_delete").assertIsDisplayed()
        rule.onNodeWithTag("template_details_back").assertIsDisplayed()
    }

    @Test
    fun header_showsTheTotals_andTheSectionTitle() {
        show()

        rule.onNodeWithTag("template_details_kcal").assertTextContains("270 kcal")
        rule.onNodeWithText("Alimentos en esta comida").assertIsDisplayed()
        rule.assertRowText("template_details_item_0", "Huevos revueltos")
        rule.assertRowText("template_details_item_1", "1 vaso (300 g)")
    }

    @Test
    fun addButton_closesTheList_readingAnadirAlimento_andThereIsNoUnsavedHint() {
        show()

        rule.onNodeWithTag("template_details_add_item").assertTextEquals("Añadir alimento")
        rule.onNodeWithText("Se añadirá tal como lo ves; Guardar para conservar los cambios").assertDoesNotExist()
        rule.onNodeWithTag("template_details_primary").assertTextEquals("Añadir")
    }

    @Test
    fun aRowTap_showsTheFoodReadOnly_withNothingToApply() {
        show()

        tap("template_details_item_0")

        rule.onNodeWithTag("template_item_sheet_amount").assertTextEquals("120 g")
        rule.onNodeWithTag("food_sheet_commit").assertDoesNotExist()
        rule.onNodeWithTag("food_sheet_remove").assertDoesNotExist()
        rule.onNodeWithTag("template_item_sheet_close").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("template_item_sheet_amount").assertDoesNotExist()
    }

    @Test
    fun rowsAreNotSwipeable_inTheReadOnlyDetails() {
        show()

        rule.onNodeWithTag("template_edit_remove_0").assertDoesNotExist()
        rule.onNodeWithTag("template_details_remove_0").assertDoesNotExist()
    }

    @Test
    fun editor_backWithoutChanges_leavesAtOnce_andAsksNothing() {
        show(startInEditor = true)

        tap("template_edit_back")

        rule.onNodeWithTag("template_edit_discard_dialog").assertDoesNotExist()
        rule.onNodeWithTag("template_details_title").assertTextEquals("Desayuno A")
    }

    @Test
    fun editor_backAfterAChange_asksToDiscard_keepEditingStays_andDescartarDropsTheDraft() {
        show(startInEditor = true)
        rule.onNodeWithTag("template_edit_name").performTextReplacement("Desayuno B")
        rule.waitForIdle()

        tap("template_edit_back")
        rule.onNodeWithTag("template_edit_discard_dialog").assertIsDisplayed()
        rule.onNodeWithText("¿Descartar los cambios?").assertIsDisplayed()
        tap("template_edit_keep_editing")
        rule.onNodeWithTag("template_edit_name").assertTextContains("Desayuno B")

        tap("template_edit_back")
        tap("template_edit_discard_confirm")

        rule.onNodeWithTag("template_details_title").assertTextEquals("Desayuno A")
        assertEquals(0, templates.saveCalls)
        assertEquals(desayunoA, templates.templates.single())
    }

    @Test
    fun editor_stagedRemoval_isNotAppliedUntilGuardar_andDiscardingBringsTheRowBack() {
        show(startInEditor = true)
        tap("template_edit_item_1")
        tap("food_sheet_remove")
        rule.onNodeWithTag("template_edit_item_1").assertDoesNotExist()
        assertEquals(2, templates.templates.single().items.size)

        tap("template_edit_back")
        tap("template_edit_discard_confirm")
        tap("template_details_edit")

        rule.onNodeWithTag("template_edit_item_1").assertIsDisplayed()
        rule.onNodeWithTag("template_edit_save").assertIsNotEnabled()
    }
}
