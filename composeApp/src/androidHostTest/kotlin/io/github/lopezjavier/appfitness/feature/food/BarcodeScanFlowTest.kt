package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.Micronutrient
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.MicronutrientUnit
import io.github.lopezjavier.appfitness.domain.model.Micronutrients
import io.github.lopezjavier.appfitness.domain.port.ScanResult
import io.github.lopezjavier.appfitness.domain.usecase.DefaultMealSlot
import io.github.lopezjavier.appfitness.domain.usecase.ResolvePortion
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.domain.usecase.ValidateQuantity
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.diary.FakeMealSlotRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val CODE = "8410000000017"
private const val SEARCH_TIMEOUT_MS = 5_000L

/** US-18 and US-17 "No results": search -> scan or "Crear alimento" -> form -> sheet of the new food, with fakes. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class BarcodeScanFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val foods = FakeFoodRepository()
    private val diary = FakeFoodDiaryRepository()
    private val connectivity = FakeConnectivityObserver()
    private val scanner = FakeBarcodeScanner()

    private fun start() {
        val searchModel = foodSearchModel(foods, connectivity, scanner, SavedStateHandle())
        rule.setContent { Flow(searchModel) }
        rule.waitForIdle()
    }

    @Composable
    private fun Flow(searchModel: FoodSearchViewModel) {
        var form by remember { mutableStateOf<FoodSearchEffect.OpenCreateFood?>(null) }
        var openFoodId by remember { mutableStateOf<String?>(null) }
        val searchState by searchModel.uiState.collectAsState()
        LaunchedEffect(searchModel) {
            searchModel.effects.collect {
                when (it) {
                    is FoodSearchEffect.OpenFood -> openFoodId = it.food.id
                    is FoodSearchEffect.OpenCreateFood -> form = it
                    else -> Unit
                }
            }
        }
        AppTheme {
            val open = form
            when {
                open == null -> FoodSearchScreen(searchState, searchModel::onEvent)

                else -> {
                    val model = remember { CreateFoodViewModel(open.barcode, open.name, foods, connectivity) }
                    val createState by model.uiState.collectAsState()
                    LaunchedEffect(model) {
                        model.effects.collect {
                            if (it is CreateFoodEffect.Saved) {
                                openFoodId = it.foodId
                                form = null
                            }
                        }
                    }
                    CreateFoodScreen(createState, model::onEvent)
                }
            }
            openFoodId?.let { id -> Sheet(id) { openFoodId = null } }
        }
    }

    @Composable
    private fun Sheet(foodId: String, onDismiss: () -> Unit) {
        val model = remember(foodId) {
            AddFoodViewModel(
                foodId = foodId,
                initialSlotId = null,
                date = LocalDate(2026, 10, 19),
                foodRepository = foods,
                foodDiary = diary,
                mealSlots = FakeMealSlotRepository(),
                resolvePortion = ResolvePortion(),
                scaleNutrition = ScaleNutrition(),
                validateQuantity = ValidateQuantity(),
                defaultMealSlot = DefaultMealSlot(),
                clock = MutableClock(),
                connectivity = connectivity,
                savedState = SavedStateHandle(),
            )
        }
        val state by model.uiState.collectAsState()
        AddFoodSheet(state, model::onEvent, onDismiss)
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun type(tag: String, text: String) {
        rule.onNodeWithTag(tag).performTextInput(text)
        rule.waitForIdle()
    }

    private fun scanned(food: Food?) {
        scanner.result = ScanResult.Code(CODE)
        food?.let {
            foods.known[it.id] = it
            foods.barcodeResults[CODE] = Result.success(it)
        }
    }

    @Test
    fun productFound_opensTheSheetWithItsNutrition_andAddLogsIt() {
        scanned(food("yogur", "Yogur natural", kcal = 60.0))
        start()

        tap("food_search_scan_button")

        rule.onNodeWithTag("add_food_sheet").assertExists()
        rule.onNodeWithText("Yogur natural").assertExists()
        tap("add_food_submit")
        assertEquals("yogur", diary.added.single().foodId)
        assertEquals(listOf(CODE), foods.barcodeLookups)
    }

    @Test
    fun productFound_showsItsMicronutrientsInTheSheet() {
        scanned(food("yogur", "Yogur natural", kcal = 60.0))
        foods.micronutrientResults["yogur"] = Result.success(
            Micronutrients(listOf(Micronutrient(MicronutrientKind.Fiber, 2.5, MicronutrientUnit.G))),
        )
        start()

        tap("food_search_scan_button")

        rule.onNodeWithTag("add_food_label_row_fiber").assertExists()
    }

    @Test
    fun drinkScanned_showsItsNutritionPer100Millilitres() {
        scanned(food("cola", "Cola", kcal = 42.0, baseUnit = "ml"))
        start()

        tap("food_search_scan_button")

        rule.onNodeWithTag("add_food_sheet").assertExists()
        rule.onNodeWithText("Por 100 ml").assertExists()
    }

    @Test
    fun productNotFound_offersToCreateIt_withTheCodeFilledIn_andTheNewFoodOpensItsSheet() {
        scanned(null)
        start()

        tap("food_search_scan_button")
        rule.onNodeWithText("Producto no encontrado").assertExists()
        tap("food_search_not_found_create_button")
        rule.onNodeWithTag("create_food_barcode_text").assertTextContains(CODE, substring = true)
        type("create_food_name_field", "Tortilla de patatas casera")
        type("create_food_kcal_field", "190")
        type("create_food_protein_field", "7")
        type("create_food_carbs_field", "15")
        type("create_food_fat_field", "11")
        tap("create_food_save_button")

        assertEquals(CODE, foods.created.single().barcode)
        rule.onNodeWithTag("add_food_sheet").assertExists()
        rule.onNodeWithText("Tortilla de patatas casera").assertExists()
    }

    @Test
    fun scanCancelled_leavesTheSearchUntouched_withoutAnError() {
        scanner.result = ScanResult.Cancelled
        start()

        tap("food_search_scan_button")

        rule.onNodeWithTag("food_search_screen").assertExists()
        rule.onNodeWithTag("food_search_not_found_card").assertDoesNotExist()
        rule.onNodeWithTag("food_search_scan_error").assertDoesNotExist()
        rule.onNodeWithTag("add_food_sheet").assertDoesNotExist()
        assertEquals(emptyList<String>(), foods.barcodeLookups)
    }

    @Test
    fun noResults_createFood_opensTheFormPrefilledWithTheQuery() {
        start()

        type("food_search_input", "tortilla")
        rule.waitUntil(SEARCH_TIMEOUT_MS) {
            rule.onAllNodesWithTagExists("food_search_create_button")
        }
        tap("food_search_create_button")

        rule.onNodeWithTag("create_food_name_field").assertTextContains("tortilla")
        rule.onNodeWithTag("create_food_barcode_text").assertDoesNotExist()
    }

    @Test
    fun createIcon_withoutScanning_opensTheFormWithTheTypedNameAndNoBarcode() {
        start()

        type("food_search_input", "tortilla")
        tap("food_search_create_icon")

        rule.onNodeWithTag("create_food_name_field").assertTextContains("tortilla")
        rule.onNodeWithTag("create_food_barcode_text").assertDoesNotExist()
        assertEquals(0, scanner.scans)
    }

    @Test
    fun createIcon_withoutQuery_opensAnEmptyForm() {
        start()

        tap("food_search_create_icon")

        rule.onNodeWithTag("create_food_save_button").assertExists()
        rule.onNodeWithTag("create_food_barcode_text").assertDoesNotExist()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagExists(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
}
