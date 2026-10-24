package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.food.food
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ADR-40 for the comida details: the route arguments are the whole state, so a view model recreated for the same
 * comida shows identical content, and recreation neither adds the comida nor loses the screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class TemplatePreviewRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val rice = food("rice", "Arroz", kcal = 200.0)
    private val templates = FakeMealTemplateRepository(mutableMapOf("rice" to rice))
    private val diary = FakeFoodDiaryRepository()
    private val connectivity = FakeConnectivityObserver()

    init {
        templates.templates += MealTemplate("t1", "Comida A", "lunch", listOf(MealTemplateItem(rice, 100.0, null)))
    }

    @Test
    fun theComidaIsStillShown_afterRecreation_andNothingWasAdded() {
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { PreviewHost(remember { previewModel(templates, diary, connectivity, saved) }) }
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("template_preview_name").assertTextContains("Comida A")
        rule.onNodeWithTag("template_preview_item_0").assertTextContains("Arroz", substring = true)
        assertEquals(emptyList<Any>(), diary.addedBatches)
    }

    @Test
    fun aViewModelRecreatedFromTheSameHandle_yieldsTheSameContent() {
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        var current: TemplatePreviewViewModel? = null
        tester.setContent {
            val model = remember { previewModel(templates, diary, connectivity, saved) }
            current = model
            PreviewHost(model)
        }
        rule.waitForIdle()
        val before = current!!.uiState.value

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        assertEquals(before, current!!.uiState.value)
    }

    @Test
    fun anAddInProgressIsNotPersisted_soRecreationOffersAnadirAgain() {
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { PreviewHost(remember { previewModel(templates, diary, connectivity, saved) }) }
        rule.waitForIdle()
        rule.onNodeWithTag("template_preview_add").performClick()
        rule.waitForIdle()
        val adds = diary.addedBatches.size

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        assertEquals(adds, diary.addedBatches.size)
        rule.onNodeWithTag("template_preview_add").assertExists()
    }
}
