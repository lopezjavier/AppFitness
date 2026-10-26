package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.centerY
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.right
import androidx.compose.ui.test.swipeLeft
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.food.FakeBarcodeScanner
import io.github.lopezjavier.appfitness.feature.food.FakeFoodRepository
import io.github.lopezjavier.appfitness.feature.food.FoodSearchRoot
import io.github.lopezjavier.appfitness.feature.food.FoodSearchScreen
import io.github.lopezjavier.appfitness.feature.food.FoodSearchViewModel
import io.github.lopezjavier.appfitness.feature.food.food
import io.github.lopezjavier.appfitness.feature.food.foodSearchModel
import io.github.lopezjavier.appfitness.feature.food.photo.FakePhotoCapture
import io.github.lopezjavier.appfitness.feature.food.photo.foodPhotoModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Eggs = food("eggs", "Huevos revueltos", kcal = 100.0)
private val Shake = food("shake", "Batido de proteína", kcal = 50.0)
private val DesayunoA = MealTemplate(
    id = "t1",
    name = "Desayuno A",
    mealSlotId = "breakfast",
    items = listOf(MealTemplateItem(Eggs, 120.0, null), MealTemplateItem(Shake, 300.0, "1 vaso")),
)

/**
 * US-21 / US-53 Comidas tab: the tab lists the saved meals with their totals, a row only opens the details of the
 * comida (no pencil, no add), and it shows the empty and error states.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class TemplatesTabScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val foods = FakeFoodRepository()
    private val templates = FakeMealTemplateRepository(mutableMapOf("eggs" to Eggs, "shake" to Shake))
    private val diary = FakeFoodDiaryRepository()
    private val connectivity = FakeConnectivityObserver()

    private fun search() = foodSearchModel(
        repository = foods,
        connectivity = connectivity,
        scanner = FakeBarcodeScanner(),
        state = SavedStateHandle(),
        templates = templates,
    )

    @Composable
    private fun SearchHost(model: FoodSearchViewModel) {
        val state by model.uiState.collectAsState()
        AppTheme { FoodSearchScreen(state, model::onEvent, {}) }
    }

    private fun openComidasTab() {
        rule.onNodeWithTag("food_search_tab_templates").performClick()
        rule.waitForIdle()
    }

    @Test
    fun comidasTab_listsTheComidaWithItsFoodCountAndTotalKcal() {
        templates.templates += DesayunoA
        val model = search()
        rule.setContent { SearchHost(model) }
        rule.waitForIdle()

        openComidasTab()

        rule.onNodeWithTag("food_search_tab_templates").assertIsSelected()
        rule.onNodeWithTag("food_search_template_t1").assertIsDisplayed()
        rule.onNodeWithTag("food_search_template_t1").assertTextContains("Desayuno A", substring = true)
        rule.onNodeWithTag("food_search_template_t1").assertTextContains("2 alimentos · 270 kcal", substring = true)
    }

    @Test
    fun comidasTab_withNoComidas_showsTheEmptyState() {
        rule.setContent { SearchHost(search()) }
        rule.waitForIdle()

        openComidasTab()

        rule.onNodeWithTag("food_search_templates_empty").assertIsDisplayed()
    }

    @Test
    fun comidasTab_whenTheListFails_showsTheErrorState_andRetryLoadsIt() {
        templates.listFailure = AppError.Network
        templates.templates += DesayunoA
        rule.setContent { SearchHost(search()) }
        rule.waitForIdle()
        openComidasTab()

        rule.onNodeWithTag("food_search_templates_retry_action").assertIsDisplayed()
        templates.listFailure = null
        rule.onNodeWithTag("food_search_templates_retry_action").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("food_search_template_t1").assertIsDisplayed()
    }

    @Suppress("ViewModelForwarding") // Test host: the Root takes the view model the test built.
    @Composable
    private fun RootHost(model: FoodSearchViewModel, onOpenTemplate: (String) -> Unit = {}) {
        AppTheme {
            FoodSearchRoot(
                slotId = "lunch",
                date = "2026-10-19",
                onOpenFood = {},
                onCreateFood = { _, _, _ -> },
                onOpenTemplate = onOpenTemplate,
                onBack = {},
                viewModel = model,
                photoViewModel = foodPhotoModel(connectivity = connectivity),
                photoCapture = FakePhotoCapture(),
            )
        }
    }

    @Test
    fun comidasTab_row_hasNoPencilAndNoAddButton() {
        templates.templates += DesayunoA
        rule.setContent { SearchHost(search()) }
        rule.waitForIdle()

        openComidasTab()

        rule.onNodeWithTag("food_search_template_t1").assertIsDisplayed()
        rule.onNodeWithTag("food_search_template_t1_edit").assertDoesNotExist()
        rule.onNodeWithTag("food_search_template_t1_add").assertDoesNotExist()
        rule.onNodeWithTag("food_search_template_t1_menu").assertDoesNotExist()
        rule.onNodeWithTag("food_search_template_t1_delete").assertDoesNotExist()
        rule.onNodeWithContentDescription("Editar Desayuno A").assertDoesNotExist()
    }

    @Test
    fun tappingTheRow_opensTheDetailsOfTheComida_andAddsNothing() {
        templates.templates += DesayunoA
        var opened: String? = null
        rule.setContent { RootHost(search()) { opened = it } }
        rule.waitForIdle()
        openComidasTab()

        rule.onNodeWithTag("food_search_template_t1").performClick()
        rule.waitForIdle()

        assertEquals("t1", opened)
        assertEquals(emptyList<Any>(), diary.addedBatches)
    }

    @Test
    fun offline_tappingTheRow_stillOpensTheDetails_andAddsNothing() {
        templates.templates += DesayunoA
        var opened: String? = null
        rule.setContent { RootHost(search()) { opened = it } }
        rule.waitForIdle()
        openComidasTab()
        connectivity.state.value = false
        rule.waitForIdle()

        rule.onNodeWithTag("food_search_template_t1").performClick()
        rule.waitForIdle()

        assertEquals("t1", opened)
        assertEquals(emptyList<Any>(), diary.addedBatches)
    }

    @Test
    fun pickMode_hidesTheComidasTab_andKeepsTheOthers() {
        rule.setContent { SearchHost(foodSearchModel(foods, connectivity, FakeBarcodeScanner(), pick = true)) }
        rule.waitForIdle()

        rule.onNodeWithTag("food_search_tab_templates").assertDoesNotExist()
        rule.onNodeWithTag("food_search_tab_recent").assertIsDisplayed()
        rule.onNodeWithTag("food_search_tab_favourites").assertIsDisplayed()
    }

    @Test
    fun swipingARowToTheEnd_deletesItAtOnce_withNoMessageAndNoDeshacer() {
        templates.templates += DesayunoA
        rule.setContent { RootHost(search()) }
        rule.waitForIdle()
        openComidasTab()

        rule.onNodeWithTag("food_search_template_t1_swipe").performTouchInput { swipeLeft() }
        rule.waitForIdle()

        rule.onNodeWithTag("food_search_template_t1").assertDoesNotExist()
        rule.onNodeWithText("Comida eliminada").assertDoesNotExist()
        rule.onNodeWithText("Deshacer").assertDoesNotExist()
        assertEquals(0, templates.templates.size)
    }

    @Test
    fun aShortDrag_restoresTheRow_andDeletesNothing() {
        templates.templates += DesayunoA
        rule.setContent { RootHost(search()) }
        rule.waitForIdle()
        openComidasTab()

        rule.onNodeWithTag("food_search_template_t1_swipe").performTouchInput {
            down(Offset(right - 1f, centerY))
            moveTo(Offset(right * 0.6f, centerY))
            up()
        }
        rule.waitForIdle()

        rule.onNodeWithTag("food_search_template_t1").assertIsDisplayed()
        assertEquals(1, templates.templates.size)
    }

    @Test
    fun offline_swipingARow_doesNotDeleteIt() {
        templates.templates += DesayunoA
        rule.setContent { RootHost(search()) }
        rule.waitForIdle()
        openComidasTab()
        connectivity.state.value = false
        rule.waitForIdle()

        rule.onNodeWithTag("food_search_template_t1_swipe").performTouchInput { swipeLeft() }
        rule.waitForIdle()

        rule.onNodeWithTag("food_search_template_t1").assertIsDisplayed()
        rule.onNodeWithText("Comida eliminada").assertDoesNotExist()
    }
}
