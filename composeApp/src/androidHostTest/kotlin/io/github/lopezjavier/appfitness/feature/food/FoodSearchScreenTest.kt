package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Chicken = food("chicken", "Pechuga de pollo", kcal = 110.0).copy(brand = "Hacendado")
private val Milk = food("milk", "Leche", kcal = 45.0, baseUnit = "ml")

/** US-17 search states and rows, rendered from a given state. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class FoodSearchScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<FoodSearchEvent>()

    private fun show(state: FoodSearchUiState, scanFailed: Boolean = false) {
        rule.setContent { AppTheme { FoodSearchScreen(state, { events += it }, scanFailed = scanFailed) } }
        rule.waitForIdle()
    }

    private fun content(
        vararg foods: io.github.lopezjavier.appfitness.domain.model.Food,
        favourites: Set<String> = emptySet(),
    ) = FoodSearchUiState(results = FoodResults.Content(foods.toList()), favouriteIds = favourites)

    @Test
    fun loading_showsSkeletonRows() {
        show(FoodSearchUiState(results = FoodResults.Loading))

        rule.onNodeWithTag("food_search_skeleton").assertExists()
    }

    @Test
    fun resultRow_showsNameBrandAndEnergyPer100Units() {
        show(content(Chicken, Milk))

        rule.onNodeWithText("Pechuga de pollo").assertExists()
        rule.onNodeWithText("Hacendado").assertExists()
        rule.onNodeWithText("110 kcal / 100 g").assertExists()
        rule.onNodeWithText("45 kcal / 100 ml").assertExists()
    }

    @Test
    fun tappingARow_selectsTheFood() {
        show(content(Chicken))

        rule.onNodeWithTag("food_search_result_chicken").performClick()

        assertEquals(listOf<FoodSearchEvent>(FoodSearchEvent.SelectFood(Chicken)), events)
    }

    @Test
    fun star_reflectsFavouritesAndTogglesWithoutOpeningTheFood() {
        show(content(Chicken, Milk, favourites = setOf("milk")))

        rule.onNodeWithTag("food_search_favourite_chicken").assertIsOff()
        rule.onNodeWithTag("food_search_favourite_milk").assertIsOn()
        rule.onNode(hasContentDescription("Quitar de favoritos")).assertExists()
        rule.onNodeWithTag("food_search_favourite_chicken").performClick()

        assertEquals(listOf<FoodSearchEvent>(FoodSearchEvent.ToggleFavourite("chicken")), events)
    }

    @Test
    fun noResults_showsTheMessage_andOffersToCreateTheFood() {
        show(FoodSearchUiState(query = "xyzxyz", results = FoodResults.Empty))

        rule.onNodeWithTag("food_search_empty").assertExists()
        rule.onNodeWithText("No hemos encontrado alimentos").assertExists()
        rule.onNodeWithTag("food_search_create_button").performClick()

        assertEquals(listOf<FoodSearchEvent>(FoodSearchEvent.CreateFromEmpty), events)
    }

    @Test
    fun createIcon_isShownWithAndWithoutAQuery_andEmitsCreate() {
        show(content(Chicken))
        rule.onNodeWithTag("food_search_create_icon").assertExists()
        rule.onNode(hasContentDescription("Crear alimento")).assertExists()
        rule.onNodeWithTag("food_search_create_icon").performClick()
        rule.onNodeWithTag("food_search_create_button").assertDoesNotExist()

        assertEquals(listOf<FoodSearchEvent>(FoodSearchEvent.CreateFromEmpty), events)
    }

    @Test
    fun createIcon_isShownWithAQuery_andAlongsideTheEmptyResultButton() {
        show(FoodSearchUiState(query = "xyz", results = FoodResults.Empty))

        rule.onNodeWithTag("food_search_create_icon").assertExists()
        rule.onNodeWithTag("food_search_create_button").assertExists()
    }

    @Test
    fun createIcon_isDisabledOffline() {
        show(FoodSearchUiState(results = FoodResults.Error, isOffline = true))

        rule.onNodeWithTag("food_search_create_icon").assertIsNotEnabled()
    }

    @Test
    fun scanButton_isDescribed_andEmitsTheScanEvent() {
        show(content(Chicken))

        rule.onNode(hasContentDescription("Escanear código de barras")).assertExists()
        rule.onNodeWithTag("food_search_scan_button").performClick()

        assertEquals(listOf<FoodSearchEvent>(FoodSearchEvent.ScanClicked), events)
    }

    @Test
    fun scanButton_isDisabledOffline_keepingItsDescription() {
        show(FoodSearchUiState(results = FoodResults.Error, isOffline = true))

        rule.onNodeWithTag("food_search_scan_button").assertIsNotEnabled()
        rule.onNode(hasContentDescription("Escanear código de barras")).assertExists()
    }

    @Test
    fun notFoundCard_showsTheCode_andOffersToCreate() {
        show(FoodSearchUiState(results = FoodResults.Empty, notFoundBarcode = "8410000000017"))

        rule.onNodeWithTag("food_search_not_found_card").assertExists()
        rule.onNodeWithText("Producto no encontrado").assertExists()
        rule.onNodeWithTag("food_search_not_found_create_button").performClick()

        assertEquals(listOf<FoodSearchEvent>(FoodSearchEvent.CreateFromNotFound), events)
    }

    @Test
    fun noNotFoundCard_withoutAScannedCode() {
        show(content(Chicken))

        rule.onNodeWithTag("food_search_not_found_card").assertDoesNotExist()
        rule.onNodeWithTag("food_search_scan_error").assertDoesNotExist()
    }

    @Test
    fun scanFailure_showsTheGenericMessage() {
        show(content(Chicken), scanFailed = true)

        rule.onNodeWithTag("food_search_scan_error").assertExists()
        rule.onNodeWithText("No se pudo leer el código. Inténtalo de nuevo.").assertExists()
    }

    @Test
    fun emptyTabs_haveTheirOwnCopy() {
        show(FoodSearchUiState(tab = FoodSearchTab.Favourites, results = FoodResults.Empty))

        rule.onNodeWithText("Aún no tienes favoritos").assertExists()
    }

    @Test
    fun tabs_areShownWhileTheBoxIsEmpty_markTheSelectedOne_andEmitTheChoice() {
        show(FoodSearchUiState(tab = FoodSearchTab.MostEaten, results = FoodResults.Empty))

        rule.onNodeWithTag("food_search_tab_recent").assertExists()
        rule.onNodeWithTag("food_search_tab_most_eaten").assertIsSelected()
        rule.onNodeWithTag("food_search_tab_favourites").performClick()

        assertEquals(listOf<FoodSearchEvent>(FoodSearchEvent.TabSelected(FoodSearchTab.Favourites)), events)
    }

    @Test
    fun tabs_areHiddenWhileAQueryIsTyped() {
        show(FoodSearchUiState(query = "pollo", results = FoodResults.Content(listOf(Chicken))))

        rule.onNodeWithTag("food_search_tab_recent").assertDoesNotExist()
    }

    @Test
    fun typing_andClearing_emitTheQuery() {
        show(FoodSearchUiState(query = "po", results = FoodResults.Loading))

        rule.onNodeWithTag("food_search_input").performTextInput("l")
        rule.onNodeWithTag("food_search_clear").performClick()

        assertEquals(
            listOf<FoodSearchEvent>(FoodSearchEvent.QueryChanged("pol"), FoodSearchEvent.QueryChanged("")),
            events,
        )
    }

    @Test
    fun error_offersARetry() {
        show(FoodSearchUiState(results = FoodResults.Error))

        rule.onNodeWithText("No se pudieron cargar los alimentos").assertExists()
        rule.onNodeWithTag("food_search_retry_action").performClick()

        assertEquals(listOf<FoodSearchEvent>(FoodSearchEvent.Retry), events)
    }

    @Test
    fun offline_disablesTheSearchBox_andSaysWhy() {
        show(FoodSearchUiState(results = FoodResults.Error, isOffline = true))

        rule.onNodeWithTag("food_search_input").assertIsNotEnabled()
        rule.onNodeWithText("Necesitas conexión para buscar alimentos").assertExists()
    }
}
