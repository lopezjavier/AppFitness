package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.domain.usecase.ValidateQuantity
import io.github.lopezjavier.appfitness.domain.usecase.ValidateTemplateName
import io.github.lopezjavier.appfitness.feature.food.food
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ADR-40 for the comida editor: the typed amounts, the removed foods, the open "Guardar" choice and the typed new name
 * survive the recreation of the screen and its ViewModel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class TemplateEditorRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val eggs = food("eggs", "Huevos revueltos", kcal = 100.0)
    private val shake = food("shake", "Batido de proteína", kcal = 50.0)
    private val templates = FakeMealTemplateRepository(mutableMapOf("eggs" to eggs, "shake" to shake))
    private val connectivity = FakeConnectivityObserver()

    init {
        templates.templates += MealTemplate(
            id = "t1",
            name = "Desayuno A",
            mealSlotId = "breakfast",
            items = listOf(MealTemplateItem(eggs, 120.0, null), MealTemplateItem(shake, 300.0, "1 vaso")),
        )
    }

    @Composable
    private fun EditorHost(saved: SavedStateHandle) {
        val model = remember {
            TemplateEditorViewModel(
                templateId = "t1",
                mealTemplates = templates,
                validateQuantity = ValidateQuantity(),
                validateTemplateName = ValidateTemplateName(),
                connectivity = connectivity,
                savedState = saved,
            )
        }
        val state by model.uiState.collectAsState()
        AppTheme { TemplateEditorScreen(state, model::onEvent) }
    }

    @Test
    fun typedAmount_andRemovedFood_surviveRecreation() {
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { EditorHost(saved) }
        rule.waitForIdle()
        rule.onNodeWithTag("template_editor_item_0_amount").performTextReplacement("150")
        rule.onNodeWithTag("template_editor_item_1_remove").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("template_editor_item_0_amount").assertTextContains("150")
        rule.onNodeWithTag("template_editor_item_1_amount").assertDoesNotExist()
    }

    @Test
    fun openChoice_andTypedNewName_surviveRecreation() {
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { EditorHost(saved) }
        rule.waitForIdle()
        rule.onNodeWithTag("template_editor_save").performClick()
        rule.onNodeWithTag("template_editor_save_as_new").performClick()
        rule.onNodeWithTag("template_editor_new_name").performTextReplacement("Mi comida")
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("template_editor_new_name").assertTextContains("Mi comida")
        rule.onNodeWithTag("template_editor_new_name_submit").assertIsDisplayed()
    }

    @Test
    fun duplicateFoodComida_typedAmountsAndRemovedRow_surviveRecreationByIndex() {
        templates.templates.clear()
        templates.templates += MealTemplate(
            id = "t1",
            name = "Doble huevo",
            mealSlotId = null,
            items = listOf(
                MealTemplateItem(eggs, 100.0, null),
                MealTemplateItem(eggs, 50.0, null),
                MealTemplateItem(shake, 300.0, null),
            ),
        )
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { EditorHost(saved) }
        rule.waitForIdle()
        rule.onNodeWithTag("template_editor_item_1_amount").performTextReplacement("75")
        rule.onNodeWithTag("template_editor_item_2_remove").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("template_editor_item_0_amount").assertTextContains("100")
        rule.onNodeWithTag("template_editor_item_1_amount").assertTextContains("75")
        rule.onNodeWithTag("template_editor_item_2_amount").assertDoesNotExist()
    }
}
