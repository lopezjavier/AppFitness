package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.usecase.BuildTemplateItems
import io.github.lopezjavier.appfitness.domain.usecase.ValidateTemplateName
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.food.food
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-40: the name typed in the save-as-comida sheet and its duplicate-name error survive recreation. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class SaveTemplateRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val day = LocalDate(2026, 10, 19)
    private val diary = FakeFoodDiaryRepository()
    private val templates = FakeMealTemplateRepository(mutableMapOf("eggs" to food("eggs", "Huevos revueltos")))
    private val connectivity = FakeConnectivityObserver()

    init {
        diary.server[day] = listOf(
            FoodLogEntry(
                id = "e1",
                logDate = day,
                mealSlotId = "breakfast",
                foodId = "eggs",
                foodName = "Huevos revueltos",
                amount = 120.0,
                portionLabel = null,
                baseUnit = "g",
                kcal = 100.0,
                proteinG = 10.0,
                carbsG = 10.0,
                fatG = 5.0,
            ),
        )
    }

    private fun model(saved: SavedStateHandle) = SaveTemplateViewModel(
        slotId = "breakfast",
        date = day,
        foodDiary = diary,
        buildTemplateItems = BuildTemplateItems(),
        validateTemplateName = ValidateTemplateName(),
        mealTemplates = templates,
        connectivity = connectivity,
        savedState = saved,
    )

    @Composable
    private fun SheetHost(saved: SavedStateHandle) {
        val viewModel = remember { model(saved) }
        val state by viewModel.uiState.collectAsState()
        AppTheme { SaveTemplateSheet(state, viewModel::onEvent, onDismiss = {}) }
    }

    @Test
    fun typedName_surviveRecreation() {
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { SheetHost(saved) }
        rule.waitForIdle()
        rule.onNodeWithTag("save_template_name").performTextReplacement("Desayuno B")
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("save_template_name").assertTextContains("Desayuno B")
    }

    @Test
    fun duplicateNameError_surviveRecreation_withTheTypedName() {
        templates.templates += MealTemplate("t0", "Desayuno A", null, emptyList())
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { SheetHost(saved) }
        rule.waitForIdle()
        rule.onNodeWithTag("save_template_name").performTextReplacement("Desayuno A")
        rule.waitForIdle()
        rule.onNodeWithTag("save_template_submit").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("save_template_name_error").assertTextEquals("Ya existe una comida con ese nombre")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("save_template_name").assertTextContains("Desayuno A")
        rule.onNodeWithTag("save_template_name_error").assertIsDisplayed()
        assertEquals(1, templates.templates.size)
    }
}
