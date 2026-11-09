package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.rememberNavController
import io.github.lopezjavier.appfitness.app.AddFoodSlot
import io.github.lopezjavier.appfitness.app.AppScaffold
import io.github.lopezjavier.appfitness.app.DiarySlot
import io.github.lopezjavier.appfitness.app.ExerciseDetailSlot
import io.github.lopezjavier.appfitness.app.ExercisePickerSlot
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.app.FoodSearchSlot
import io.github.lopezjavier.appfitness.app.RoutinesSlot
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.usecase.BuildDiaryDay
import io.github.lopezjavier.appfitness.domain.usecase.DefaultMealSlot
import io.github.lopezjavier.appfitness.domain.usecase.ResolvePortion
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.domain.usecase.ValidateQuantity
import io.github.lopezjavier.appfitness.feature.diary.DiaryRoot
import io.github.lopezjavier.appfitness.feature.diary.DiaryViewModel
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.diary.FakeMealSlotRepository
import io.github.lopezjavier.appfitness.feature.meals.defaults.FakeMealDefaultsRepository
import io.github.lopezjavier.appfitness.feature.notifications.LocalNotificationsEntry
import io.github.lopezjavier.appfitness.feature.notifications.NotificationsEntry
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import io.github.lopezjavier.appfitness.feature.workout.WorkoutEntryHost
import io.github.lopezjavier.appfitness.feature.workout.WorkoutEntryState
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SEARCH_TIMEOUT_MS = 5_000L
private val SearchDay = LocalDate(2026, 10, 19)

/**
 * US-121 (T-263): the magnifier of Diario and Entreno opens the food / exercise search in view-only mode, a result
 * opens its details with no "Añadir" and back returns to the search with the typed text. The real header, search
 * screen and sheet run on the real navigation; the ViewModels use fakes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class QuickSearchFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val foods = FakeFoodRepository()
    private val diary = FakeFoodDiaryRepository()
    private val connectivity = FakeConnectivityObserver()
    private val goals =
        FakeNutritionGoalsRepository(NutritionGoals(GoalStrategy.Maintenance, 0, 2400, MacroSplit(30, 45, 25)))
    private val clock = MutableClock()
    private val chicken = food("chicken", "Pechuga de pollo", kcal = 110.0, isManual = true)

    init {
        foods.known["chicken"] = chicken
        foods.searchResults["pollo"] = Result.success(listOf(chicken))
        foods.recentFoods = Result.success(listOf(chicken))
    }

    private val diarySlot: DiarySlot = { modifier, _, onAddFood, _, edit, onBack, onQuickSearch ->
        val model: DiaryViewModel = viewModel(
            factory = viewModelFactory {
                initializer {
                    DiaryViewModel(
                        FakeMealSlotRepository(),
                        diary,
                        goals,
                        BuildDiaryDay(),
                        clock,
                        FakeConnectivityObserver(),
                        FakeMealDefaultsRepository(),
                        createSavedStateHandle(),
                    )
                }
            },
        )
        Box(modifier.fillMaxSize()) {
            DiaryRoot(
                onOpenPreferences = {},
                onAddToMeal = { _, _ -> },
                onAddFood = { onAddFood(null, it) },
                onSaveTemplate = { _, _ -> },
                editHost = edit,
                onBack = onBack,
                viewModel = model,
                onQuickSearch = onQuickSearch,
            )
            // The real "Añadir" of a meal needs the loaded day; this reaches the same callback.
            Box(Modifier.size(48.dp).testTag("test_add_food").clickable { onAddFood("breakfast", SearchDay) })
        }
    }

    private val routinesSlot: RoutinesSlot = { modifier, _, _, _, _, _, onSearch ->
        WorkoutEntryHost(WorkoutEntryState.Routines, {}, {}, modifier, onOpenSearch = onSearch) { routinesModifier, _ ->
            Box(routinesModifier.fillMaxSize().testTag("routines_screen"))
        }
    }

    private val searchSlot: FoodSearchSlot = { modifier, _, _, onOpenFood, _, _, pick, viewOnly, onBack ->
        val model: FoodSearchViewModel = viewModel(
            factory = viewModelFactory {
                initializer {
                    foodSearchModel(
                        foods,
                        connectivity,
                        FakeBarcodeScanner(),
                        createSavedStateHandle(),
                        pick = pick,
                        viewOnly = viewOnly,
                    )
                }
            },
        )
        val state by model.uiState.collectAsState()
        LaunchedEffect(model) {
            model.effects.collect { if (it is FoodSearchEffect.OpenFood) onOpenFood(it.food.id) }
        }
        FoodSearchScreen(state, model::onEvent, onBack, modifier)
    }

    private val addFoodSlot: AddFoodSlot = { route, _, onDismiss, _, _, _ ->
        val model: AddFoodViewModel = viewModel(
            key = "add_${route.foodId}",
            factory = viewModelFactory {
                initializer {
                    AddFoodViewModel(
                        foodId = route.foodId,
                        initialSlotId = route.slotId,
                        date = LocalDate.parse(route.date),
                        foodRepository = foods,
                        foodDiary = diary,
                        mealSlots = FakeMealSlotRepository(),
                        foodPreferencesRepository = FakeFoodPreferencesRepository(),
                        resolvePortion = ResolvePortion(),
                        scaleNutrition = ScaleNutrition(),
                        validateQuantity = ValidateQuantity(),
                        defaultMealSlot = DefaultMealSlot(),
                        clock = clock,
                        connectivity = connectivity,
                        savedState = createSavedStateHandle(),
                        pick = route.pick,
                        viewOnly = route.viewOnly,
                    )
                }
            },
        )
        val state by model.uiState.collectAsState()
        AddFoodSheet(state, model::onEvent, onDismiss)
    }

    // The exercise screens have their own tests; the stand-ins show which mode the shell hands them.
    private val pickerSlot: ExercisePickerSlot = { modifier, selectMode, onOpenDetail, _, onBack ->
        AppScreen(title = "Ejercicios", modifier = modifier, onBack = onBack, testTagPrefix = "exercise_picker") {
            Box(Modifier.fillMaxSize().testTag("exercise_picker_screen")) {
                Text(if (selectMode) "select" else "browse", Modifier.testTag("picker_mode"))
                Box(Modifier.size(48.dp).testTag("picker_open_detail").clickable { onOpenDetail("ex-1") })
            }
        }
    }
    private val detailSlot: ExerciseDetailSlot = { modifier, exerciseId, selectMode, _, _, onClose, _ ->
        AppScreen(title = "Ejercicio", modifier = modifier, onBack = onClose, testTagPrefix = "exercise_detail") {
            Text("$exerciseId $selectMode", Modifier.testTag("detail_args"))
        }
    }

    @Composable
    private fun Shell() {
        AppTheme {
            CompositionLocalProvider(LocalNotificationsEntry provides NotificationsEntry(0) {}) {
                AppScaffold(
                    navController = rememberNavController(),
                    routines = routinesSlot,
                    diary = diarySlot,
                    foodSearch = searchSlot,
                    addFood = addFoodSlot,
                    exercisePicker = pickerSlot,
                    exerciseDetail = detailSlot,
                )
            }
        }
    }

    private fun start() {
        rule.setContent { Shell() }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun typeAndWaitForChicken() {
        rule.onNodeWithTag("food_search_field").performTextInput("pollo")
        rule.waitUntil(SEARCH_TIMEOUT_MS) { exists("food_search_row_chicken") }
    }

    private fun left(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot().left

    @Test
    fun diarioRoot_showsTheMagnifierBeforeTheCalendar_describedAsBuscarAlimento() {
        start()

        rule.onNodeWithTag("diary_quick_search").assertContentDescriptionEquals("Buscar alimento")
        rule.onNodeWithTag("diary_quick_search").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        assertTrue(left("diary_quick_search") < left("diary_day_picker_open"))
    }

    @Test
    fun diarioMagnifier_opensTheFoodSearchWithoutScanPhotoCreateOrComidas() {
        start()

        tap("diary_quick_search")

        rule.onNodeWithTag("food_search_screen").assertExists()
        rule.onNodeWithTag("food_search_title").assertTextEquals("Buscar alimento")
        rule.onNodeWithTag("food_search_field").assertExists()
        rule.onNodeWithTag("food_search_tab_recent").assertExists()
        assertTrue(!exists("food_search_tab_templates"))
        assertTrue(!exists("food_search_action_scan"))
        assertTrue(!exists("food_search_action_photo"))
        assertTrue(!exists("food_search_action_create"))
    }

    @Test
    fun noResults_inTheQuickSearch_offersNoCreateButton() {
        start()
        tap("diary_quick_search")

        rule.onNodeWithTag("food_search_field").performTextInput("zzz")
        rule.waitUntil(SEARCH_TIMEOUT_MS) { exists("food_search_empty") }

        assertTrue(!exists("food_search_create_button"))
    }

    @Test
    fun aResult_opensItsDetailsWithoutAddOrAmount_andBackKeepsTheTypedText() {
        start()
        tap("diary_quick_search")
        typeAndWaitForChicken()

        tap("food_search_row_chicken")

        rule.onNodeWithTag("food_sheet_title").assertTextEquals("Pechuga de pollo")
        assertTrue(!exists("food_sheet_commit"))
        assertTrue(!exists("food_sheet_amount"))
        assertTrue(!exists("food_sheet_meals"))
        assertTrue(!exists("food_sheet_edit_food"))

        tap("food_sheet_close")
        rule.onNodeWithTag("food_search_field").assertTextContains("pollo")
        rule.onNodeWithTag("food_search_row_chicken").assertExists()
    }

    @Test
    fun openedFromAMeal_theSearchStillOffersTheAddFlow() {
        start()

        tap("test_add_food")
        rule.onNodeWithTag("food_search_action_scan").assertExists()
        rule.onNodeWithTag("food_search_tab_templates").assertExists()
        typeAndWaitForChicken()
        tap("food_search_row_chicken")

        rule.onNodeWithTag("food_sheet_commit").assertExists()
        rule.onNodeWithTag("food_sheet_amount").assertExists()
    }

    @Test
    fun entrenoRoot_showsTheMagnifierLeftOfTheCalendar_describedAsBuscarEjercicio() {
        start()
        tap("nav_tab_workout")

        rule.onNodeWithTag("workout_entry_search").assertContentDescriptionEquals("Buscar ejercicio")
        rule.onNodeWithTag("workout_entry_search").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        assertTrue(left("workout_entry_search") < left("workout_entry_history"))
    }

    @Test
    fun entrenoMagnifier_opensTheExerciseListInBrowseMode_andARowOpensTheDetailNotInSelectMode() {
        start()
        tap("nav_tab_workout")

        tap("workout_entry_search")
        rule.onNodeWithTag("picker_mode").assertTextEquals("browse")
        tap("picker_open_detail")

        rule.onNodeWithTag("detail_args").assertTextEquals("ex-1 false")
        tap("exercise_detail_back")
        rule.onNodeWithTag("exercise_picker_screen").assertIsDisplayed()
        assertEquals(true, exists("picker_open_detail"))
    }
}
