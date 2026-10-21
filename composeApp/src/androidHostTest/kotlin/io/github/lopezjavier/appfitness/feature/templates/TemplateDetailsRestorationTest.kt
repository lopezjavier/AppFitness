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

/** ADR-40 for the comida details: the rename sheet (with its typed name) and the delete dialog survive recreation. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class TemplateDetailsRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val eggs = food("eggs", "Huevos revueltos", kcal = 100.0)
    private val templates = FakeMealTemplateRepository(mutableMapOf("eggs" to eggs))
    private val connectivity = FakeConnectivityObserver()

    init {
        templates.templates += MealTemplate("t1", "Desayuno A", null, listOf(MealTemplateItem(eggs, 120.0, null)))
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

    @Test
    fun typedRenameName_survivesRecreation() {
        val tester = restorable(SavedStateHandle())
        rule.onNodeWithTag("template_details_rename").performClick()
        rule.onNodeWithTag("rename_template_name").performTextReplacement("Mi comida")
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("rename_template_name").assertTextContains("Mi comida")
    }

    @Test
    fun openDeleteDialog_survivesRecreation() {
        val tester = restorable(SavedStateHandle())
        rule.onNodeWithTag("template_details_delete").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("template_delete_confirm").assertIsDisplayed()
    }
}
