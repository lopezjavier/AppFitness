package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.food.FakeBarcodeScanner
import io.github.lopezjavier.appfitness.feature.food.FakeFoodRepository
import io.github.lopezjavier.appfitness.feature.food.FoodSearchScreen
import io.github.lopezjavier.appfitness.feature.food.FoodSearchViewModel
import io.github.lopezjavier.appfitness.feature.food.food
import io.github.lopezjavier.appfitness.feature.food.foodSearchModel
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * US-21 "Renaming and deleting a comida" from the Comidas tab: the row's options sheet, the rename sheet with the
 * duplicate-name error, the confirmed delete, and the entries already in the Diary keeping their values.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class TemplateManageScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val day = LocalDate(2026, 10, 19)
    private val eggs = food("eggs", "Huevos revueltos", kcal = 100.0)
    private val shake = food("shake", "Batido de proteína", kcal = 50.0)
    private val foods = FakeFoodRepository()
    private val templates = FakeMealTemplateRepository(mutableMapOf("eggs" to eggs, "shake" to shake))
    private val diary = FakeFoodDiaryRepository()
    private val connectivity = FakeConnectivityObserver()
    private val desayunoA = MealTemplate(
        id = "t1",
        name = "Desayuno A",
        mealSlotId = "breakfast",
        items = listOf(MealTemplateItem(eggs, 120.0, null), MealTemplateItem(shake, 300.0, "1 vaso")),
    )

    private fun search(saved: SavedStateHandle = SavedStateHandle()): FoodSearchViewModel = foodSearchModel(
        repository = foods,
        connectivity = connectivity,
        scanner = FakeBarcodeScanner(),
        state = saved,
        slotId = "lunch",
        templates = templates,
        diary = diary,
    )

    @Composable
    private fun SearchHost(model: FoodSearchViewModel) {
        val state by model.uiState.collectAsState()
        AppTheme { FoodSearchScreen(state, model::onEvent) }
    }

    private fun openRowMenu(id: String) {
        rule.onNodeWithTag("food_search_tab_templates").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("food_search_template_${id}_menu").performClick()
        rule.waitForIdle()
    }

    @Test
    fun renamingAComida_savesTheNewName_andTheRowShowsIt() {
        templates.templates += desayunoA
        rule.setContent { SearchHost(search()) }
        rule.waitForIdle()
        openRowMenu("t1")

        rule.onNodeWithTag("food_search_template_menu_rename").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("rename_template_name").performTextReplacement("Desayuno B")
        rule.onNodeWithTag("rename_template_submit").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("food_search_template_t1").assertIsDisplayed()
        assertEquals("Desayuno B", templates.templates.single().name)
        rule.onNodeWithTag("rename_template_name").assertDoesNotExist()
    }

    @Test
    fun renamingToATakenName_showsTheDuplicateError_andKeepsTheSheetOpen() {
        templates.templates += desayunoA
        templates.templates += desayunoA.copy(id = "t2", name = "Comida 2")
        rule.setContent { SearchHost(search()) }
        rule.waitForIdle()
        openRowMenu("t1")
        rule.onNodeWithTag("food_search_template_menu_rename").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("rename_template_name").performTextReplacement("comida 2")
        rule.onNodeWithTag("rename_template_submit").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("rename_template_name_error").assertTextEquals("Ya existe una comida con ese nombre")
        rule.onNodeWithTag("rename_template_name").assertIsDisplayed()
    }

    @Test
    fun deleteConfirmed_removesTheRow() {
        templates.templates += desayunoA
        rule.setContent { SearchHost(search()) }
        rule.waitForIdle()
        openRowMenu("t1")
        rule.onNodeWithTag("food_search_template_menu_delete").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("template_delete_confirm").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("food_search_template_t1").assertDoesNotExist()
        rule.onNodeWithTag("food_search_templates_empty").assertIsDisplayed()
    }

    @Test
    fun deleteDismissed_keepsTheRow() {
        templates.templates += desayunoA
        rule.setContent { SearchHost(search()) }
        rule.waitForIdle()
        openRowMenu("t1")
        rule.onNodeWithTag("food_search_template_menu_delete").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("template_delete_cancel").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("food_search_template_t1").assertIsDisplayed()
        assertEquals(1, templates.templates.size)
    }

    @Test
    fun deletingAComida_leavesTheEntriesAlreadyInTheDiary_withTheirValues() {
        templates.templates += desayunoA
        val logged = FoodLogEntry(
            id = "e1",
            logDate = day,
            mealSlotId = "lunch",
            foodId = "eggs",
            foodName = "Huevos revueltos",
            amount = 120.0,
            portionLabel = null,
            baseUnit = "g",
            kcal = 100.0,
            proteinG = 10.0,
            carbsG = 10.0,
            fatG = 5.0,
        )
        diary.server[day] = listOf(logged)
        rule.setContent { SearchHost(search()) }
        rule.waitForIdle()
        openRowMenu("t1")
        rule.onNodeWithTag("food_search_template_menu_delete").performClick()
        rule.onNodeWithTag("template_delete_confirm").performClick()
        rule.waitForIdle()

        assertEquals(listOf(logged), diary.server[day])
    }

    @Test
    fun offline_theRenameAndDeleteRowsOfTheMenuAreDisabled() {
        templates.templates += desayunoA
        rule.setContent { SearchHost(search()) }
        rule.waitForIdle()
        openRowMenu("t1")
        connectivity.state.value = false
        rule.waitForIdle()

        rule.onNodeWithTag("food_search_template_menu_rename").assertIsNotEnabled()
        rule.onNodeWithTag("food_search_template_menu_delete").assertIsNotEnabled()
    }
}
