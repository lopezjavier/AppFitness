package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.domain.usecase.ValidateQuantity
import io.github.lopezjavier.appfitness.domain.usecase.ValidateTemplateName
import io.github.lopezjavier.appfitness.feature.food.food
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * US-21 "Editing a comida": the editor changes amounts and removes foods; "Guardar" asks to overwrite the comida or to
 * save a new one, which needs its own name (a taken name is rejected inline).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class TemplateEditorScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val eggs = food("eggs", "Huevos revueltos", kcal = 100.0)
    private val shake = food("shake", "Batido de proteína", kcal = 50.0)
    private val templates = FakeMealTemplateRepository(mutableMapOf("eggs" to eggs, "shake" to shake))
    private val connectivity = FakeConnectivityObserver()
    private val desayunoA = MealTemplate(
        id = "t1",
        name = "Desayuno A",
        mealSlotId = "breakfast",
        items = listOf(MealTemplateItem(eggs, 120.0, null), MealTemplateItem(shake, 300.0, "1 vaso")),
    )

    private fun editor(saved: SavedStateHandle = SavedStateHandle()) = TemplateEditorViewModel(
        templateId = "t1",
        mealTemplates = templates,
        validateQuantity = ValidateQuantity(),
        validateTemplateName = ValidateTemplateName(),
        connectivity = connectivity,
        savedState = saved,
    )

    @Composable
    private fun EditorHost(model: TemplateEditorViewModel) {
        val state by model.uiState.collectAsState()
        AppTheme { TemplateEditorScreen(state, model::onEvent) }
    }

    @Test
    fun guardar_afterChangingAnAmountAndRemovingAFood_asksWhetherToOverwriteOrSaveAsNew() {
        templates.templates += desayunoA
        rule.setContent { EditorHost(editor()) }
        rule.waitForIdle()

        rule.onNodeWithTag("template_editor_item_0_amount").performTextReplacement("150")
        rule.onNodeWithTag("template_editor_item_1_remove").performClick()
        rule.onNodeWithTag("template_editor_save").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("template_editor_overwrite").assertIsDisplayed()
        rule.onNodeWithTag("template_editor_save_as_new").assertIsDisplayed()
    }

    @Test
    fun sobrescribir_replacesTheFoodsAndQuantities_andKeepsTheName() {
        templates.templates += desayunoA
        rule.setContent { EditorHost(editor()) }
        rule.waitForIdle()
        rule.onNodeWithTag("template_editor_item_0_amount").performTextReplacement("150")
        rule.onNodeWithTag("template_editor_item_1_remove").performClick()

        rule.onNodeWithTag("template_editor_save").performClick()
        rule.onNodeWithTag("template_editor_overwrite").performClick()
        rule.waitForIdle()

        val saved = templates.templates.single()
        assertEquals("Desayuno A", saved.name)
        assertEquals(listOf("eggs" to 150.0), saved.items.map { it.food.id to it.amount })
    }

    @Test
    fun guardarComoNueva_withANewName_createsASecondComida_andLeavesTheOriginalUntouched() {
        templates.templates += desayunoA
        rule.setContent { EditorHost(editor()) }
        rule.waitForIdle()
        rule.onNodeWithTag("template_editor_item_0_amount").performTextReplacement("200")

        rule.onNodeWithTag("template_editor_save").performClick()
        rule.onNodeWithTag("template_editor_save_as_new").performClick()
        rule.onNodeWithTag("template_editor_new_name").performTextReplacement("Desayuno B")
        rule.onNodeWithTag("template_editor_new_name_submit").performClick()
        rule.waitForIdle()

        assertEquals(listOf("Desayuno A", "Desayuno B"), templates.templates.map { it.name }.sorted())
        assertEquals(120.0, templates.templates.first { it.name == "Desayuno A" }.items.first().amount, 0.0)
    }

    @Test
    fun guardarComoNueva_withATakenName_rejectsItInline_andSavesNothing() {
        templates.templates += desayunoA
        rule.setContent { EditorHost(editor()) }
        rule.waitForIdle()

        rule.onNodeWithTag("template_editor_save").performClick()
        rule.onNodeWithTag("template_editor_save_as_new").performClick()
        rule.onNodeWithTag("template_editor_new_name").performTextReplacement("desayuno a")
        rule.onNodeWithTag("template_editor_new_name_submit").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("template_editor_new_name_error").assertTextEquals("Ya existe una comida con ese nombre")
        assertEquals(1, templates.templates.size)
    }

    @Test
    fun offline_theChoiceDoesNotOpen_becauseSavingNeedsTheServer() {
        templates.templates += desayunoA
        connectivity.state.value = false
        rule.setContent { EditorHost(editor()) }
        rule.waitForIdle()

        rule.onNodeWithTag("template_editor_save").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("template_editor_overwrite").assertDoesNotExist()
    }

    @Test
    fun theLastFood_cannotBeRemoved() {
        templates.templates += desayunoA
        rule.setContent { EditorHost(editor()) }
        rule.waitForIdle()
        rule.onNodeWithTag("template_editor_item_1_remove").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("template_editor_item_0_remove").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("template_editor_item_0_amount").assertIsDisplayed()
        rule.onNodeWithTag("template_editor_item_0_remove").assertIsDisplayed()
    }

    private val twiceEggs = MealTemplate(
        id = "t1",
        name = "Doble huevo",
        mealSlotId = "breakfast",
        items = listOf(MealTemplateItem(eggs, 100.0, null), MealTemplateItem(eggs, 50.0, "1 huevo")),
    )

    @Test
    fun editor_withTheSameFoodTwice_opensWithoutCrashing() {
        templates.templates += twiceEggs
        rule.setContent { EditorHost(editor()) }
        rule.waitForIdle()

        rule.onNodeWithTag("template_editor_item_0_amount").assertTextContains("100")
        rule.onNodeWithTag("template_editor_item_1_amount").assertTextContains("50")
    }

    @Test
    fun sobrescribir_afterEditingOneOfTwoRowsOfTheSameFood_keepsTheOtherRowUntouched() {
        templates.templates += twiceEggs
        rule.setContent { EditorHost(editor()) }
        rule.waitForIdle()

        rule.onNodeWithTag("template_editor_item_1_amount").performTextReplacement("80")
        rule.onNodeWithTag("template_editor_save").performClick()
        rule.onNodeWithTag("template_editor_overwrite").performClick()
        rule.waitForIdle()

        val saved = templates.templates.single()
        assertEquals(listOf("eggs" to 100.0, "eggs" to 80.0), saved.items.map { it.food.id to it.amount })
    }
}
