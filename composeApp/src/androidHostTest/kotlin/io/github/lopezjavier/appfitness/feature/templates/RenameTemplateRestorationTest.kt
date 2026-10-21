package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.food.FakeBarcodeScanner
import io.github.lopezjavier.appfitness.feature.food.FakeFoodRepository
import io.github.lopezjavier.appfitness.feature.food.FoodSearchScreen
import io.github.lopezjavier.appfitness.feature.food.food
import io.github.lopezjavier.appfitness.feature.food.foodSearchModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-40 for the rename sheet of a comida: the typed name survives the recreation of the search. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class RenameTemplateRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val eggs = food("eggs", "Huevos revueltos", kcal = 100.0)
    private val foods = FakeFoodRepository()
    private val templates = FakeMealTemplateRepository(mutableMapOf("eggs" to eggs))
    private val diary = FakeFoodDiaryRepository()
    private val connectivity = FakeConnectivityObserver()

    init {
        templates.templates += MealTemplate("t1", "Desayuno A", null, listOf(MealTemplateItem(eggs, 120.0, null)))
    }

    @Composable
    private fun SearchHost(saved: SavedStateHandle) {
        val model = remember {
            foodSearchModel(
                repository = foods,
                connectivity = connectivity,
                scanner = FakeBarcodeScanner(),
                state = saved,
                templates = templates,
                diary = diary,
            )
        }
        val state by model.uiState.collectAsState()
        AppTheme { FoodSearchScreen(state, model::onEvent) }
    }

    @Test
    fun typedRenameName_surviveRecreation() {
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { SearchHost(saved) }
        rule.waitForIdle()
        rule.onNodeWithTag("food_search_tab_templates").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("food_search_template_t1_menu").performClick()
        rule.onNodeWithTag("food_search_template_menu_rename").performClick()
        rule.onNodeWithTag("rename_template_name").performTextReplacement("Mi comida")
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("rename_template_name").assertTextContains("Mi comida")
    }
}
