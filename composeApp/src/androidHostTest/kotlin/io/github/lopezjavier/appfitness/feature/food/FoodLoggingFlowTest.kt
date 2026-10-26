package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.FoodPortion
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.usecase.BuildDiaryDay
import io.github.lopezjavier.appfitness.domain.usecase.DefaultMealSlot
import io.github.lopezjavier.appfitness.domain.usecase.ResolvePortion
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.domain.usecase.ValidateQuantity
import io.github.lopezjavier.appfitness.feature.diary.DiaryEvent
import io.github.lopezjavier.appfitness.feature.diary.DiaryScreen
import io.github.lopezjavier.appfitness.feature.diary.DiaryUiState
import io.github.lopezjavier.appfitness.feature.diary.DiaryViewModel
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.diary.FakeMealSlotRepository
import io.github.lopezjavier.appfitness.feature.meals.defaults.FakeMealDefaultsRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SEARCH_TIMEOUT_MS = 5_000L

private val Chicken = food("chicken", "Pechuga de pollo", kcal = 110.0)
private val Rice = food("rice", "Arroz", kcal = 130.0)
private val Milk = food("milk", "Leche", kcal = 45.0, baseUnit = "ml")
private val Egg = food("egg", "Huevo", kcal = 200.0, portions = listOf(FoodPortion("p1", "huevo", 55.0)))

/** US-17, US-22 and US-20 "Immediate update" end to end: Diary -> search -> sheet -> "Añadir" -> Diary, with fakes. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class FoodLoggingFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val foods = FakeFoodRepository()
    private val diary = FakeFoodDiaryRepository()
    private val connectivity = FakeConnectivityObserver()
    private var taps = 0

    private fun start(vararg recents: Food, slotId: String? = null) {
        foods.recentFoods = Result.success(recents.toList())
        recents.forEach { foods.known[it.id] = it }
        val diaryModel = DiaryViewModel(
            FakeMealSlotRepository(),
            diary,
            FakeNutritionGoalsRepository(NutritionGoals(GoalStrategy.Maintenance, 0, 2400, MacroSplit(30, 45, 25))),
            BuildDiaryDay(),
            MutableClock(),
            FakeConnectivityObserver(),
            FakeMealDefaultsRepository(),
            SavedStateHandle(),
        )
        val searchModel = foodSearchModel(foods, connectivity, FakeBarcodeScanner(), SavedStateHandle())
        rule.setContent { Flow(diaryModel, searchModel, slotId) }
        rule.waitForIdle()
    }

    @Composable
    private fun Flow(diaryModel: DiaryViewModel, searchModel: FoodSearchViewModel, slotId: String?) {
        var searching by remember { mutableStateOf(false) }
        var openFoodId by remember { mutableStateOf<String?>(null) }
        val diaryState by diaryModel.uiState.collectAsState()
        val searchState by searchModel.uiState.collectAsState()
        LaunchedEffect(searchModel) {
            searchModel.effects.collect { if (it is FoodSearchEffect.OpenFood) openFoodId = it.food.id }
        }
        val date = (diaryState as? DiaryUiState.Content)?.content?.date
        AppTheme {
            if (!searching) {
                DiaryScreen(diaryState, {
                    if (it is DiaryEvent.AddToMeal) searching = true else diaryModel.onEvent(it)
                })
            } else {
                FoodSearchScreen(searchState, searchModel::onEvent)
                val id = openFoodId
                if (id != null && date != null) {
                    val addModel = remember(id) { addFoodModel(id, slotId, date) }
                    val addState by addModel.uiState.collectAsState()
                    LaunchedEffect(addModel) {
                        addModel.effects.collect {
                            if (it == AddFoodEffect.Added) {
                                searching = false
                                openFoodId = null
                            }
                        }
                    }
                    AddFoodSheet(addState, addModel::onEvent, { openFoodId = null })
                }
            }
        }
    }

    private fun addFoodModel(foodId: String, slotId: String?, date: LocalDate) = AddFoodViewModel(
        foodId = foodId,
        initialSlotId = slotId,
        date = date,
        foodRepository = foods,
        foodDiary = diary,
        mealSlots = FakeMealSlotRepository(),
        foodPreferencesRepository = FakeFoodPreferencesRepository(),
        resolvePortion = ResolvePortion(),
        scaleNutrition = ScaleNutrition(),
        validateQuantity = ValidateQuantity(),
        defaultMealSlot = DefaultMealSlot(),
        clock = MutableClock(),
        connectivity = connectivity,
        savedState = SavedStateHandle(),
    )

    private fun tap(tag: String) {
        taps++
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun openSearchAndPick(foodId: String) {
        tap("diary_section_breakfast_add")
        tap("food_search_result_$foodId")
    }

    private fun setAmount(text: String) {
        rule.onNodeWithTag("add_food_amount").performTextReplacement(text)
        rule.waitForIdle()
    }

    private fun assertPreview(text: String) =
        rule.onNodeWithTag("add_food_preview").assertTextContains(text, substring = true)

    private fun submitAndAssertDiaryShows(kcal: String) {
        tap("add_food_submit")
        rule.onNodeWithTag("diary_screen").assertExists()
        rule.onNodeWithTag("diary_entry_new-1").assertExists()
        rule.onNode(hasText("$kcal kcal") and hasAnyAncestor(hasTestTag("diary_entry_new-1"))).assertExists()
    }

    @Test
    fun scalingPer100_220GramsOf110KcalFood_logs242Kcal_andTheDiaryUpdatesInPlace() {
        start(Chicken)
        openSearchAndPick("chicken")

        setAmount("220")
        assertPreview("242 kcal")
        submitAndAssertDiaryShows("242")

        assertEquals(220.0, diary.added.single().amount, 0.0)
    }

    @Test
    fun customAmount_180GramsOfRice_logs234Kcal() {
        start(Rice)
        openSearchAndPick("rice")

        setAmount("180")
        assertPreview("234 kcal")
        submitAndAssertDiaryShows("234")
    }

    @Test
    fun drink_250MillilitresOf45KcalPer100_logs113Kcal() {
        start(Milk)
        openSearchAndPick("milk")

        setAmount("250")
        assertPreview("113 kcal")
        submitAndAssertDiaryShows("113")
    }

    @Test
    fun namedPortion_twoEggs_resolveTo110Grams_andKeepThePortionLabel() {
        start(Egg)
        openSearchAndPick("egg")

        tap("add_food_portion_option_p1")
        setAmount("2")
        assertPreview("220 kcal")
        submitAndAssertDiaryShows("220")

        val entry = diary.added.single()
        assertEquals(110.0, entry.amount, 0.0)
        assertEquals("2 × huevo", entry.portionLabel)
    }

    @Test
    fun preselectedMeal_fromTheSectionButton_isKept() {
        start(Rice, slotId = "dinner")
        openSearchAndPick("rice")

        rule.onNodeWithTag("add_food_meal_dinner").assertExists()
        tap("add_food_submit")

        assertEquals("dinner", diary.added.single().mealSlotId)
    }

    @Test
    fun addingARecentFood_takesAtMostThreeTaps() {
        start(Rice)
        taps = 0

        tap("diary_section_breakfast_add")
        tap("food_search_result_rice")
        tap("add_food_submit")

        assertEquals(3, taps)
        assertEquals(100.0, diary.added.single().amount, 0.0)
    }

    @Test
    fun debouncedSearch_typingFindsFoodsWithOneRequest() {
        foods.searchResults["pollo"] = Result.success(listOf(Chicken))
        start()
        tap("diary_section_breakfast_add")

        rule.onNodeWithTag("food_search_input").performTextInput("pollo")
        rule.waitUntil(SEARCH_TIMEOUT_MS) {
            rule.onAllNodes(hasTestTag("food_search_result_chicken")).fetchSemanticsNodes().isNotEmpty()
        }

        assertEquals(listOf("pollo"), foods.searches)
    }

    @Test
    fun markingAFavourite_fillsTheStar_andAsksTheServer() {
        start(Chicken)
        tap("diary_section_breakfast_add")

        tap("food_search_favourite_chicken")

        assertEquals(listOf("chicken" to true), foods.favouriteCalls)
    }
}
