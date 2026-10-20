package io.github.lopezjavier.appfitness.feature.meals

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.BackInterceptor
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.app.LocalBackInterceptor
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.usecase.BuildDiaryDay
import io.github.lopezjavier.appfitness.domain.usecase.DefaultMealSlot
import io.github.lopezjavier.appfitness.domain.usecase.ResolvePortion
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.domain.usecase.ValidateMealSlots
import io.github.lopezjavier.appfitness.domain.usecase.ValidateQuantity
import io.github.lopezjavier.appfitness.feature.diary.DiaryEvent
import io.github.lopezjavier.appfitness.feature.diary.DiaryScreen
import io.github.lopezjavier.appfitness.feature.diary.DiaryViewModel
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.diary.FakeMealSlotRepository
import io.github.lopezjavier.appfitness.feature.food.AddFoodSheet
import io.github.lopezjavier.appfitness.feature.food.AddFoodViewModel
import io.github.lopezjavier.appfitness.feature.food.FakeFoodRepository
import io.github.lopezjavier.appfitness.feature.food.food
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Today = LocalDate(2026, 10, 19)

/** US-23 end to end with fakes: Diary -> "Comidas" -> edit -> "Guardar" -> Diary (and the add-food meal picker). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class MealSlotsFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val slots = FakeMealSlotRepository()
    private val diary = FakeFoodDiaryRepository()
    private val connectivity = FakeConnectivityObserver()
    private val backInterceptor = BackInterceptor()
    private val sheet = mutableStateOf<AddFoodViewModel?>(null)

    private fun lunchEntry() =
        FoodLogEntry("e1", Today, "lunch", "f", "Lentejas", 300.0, null, "g", 420.0, 20.0, 60.0, 5.0)

    private fun start() {
        val diaryModel = DiaryViewModel(
            slots,
            diary,
            FakeNutritionGoalsRepository(NutritionGoals(GoalStrategy.Maintenance, 0, 2400, MacroSplit(30, 45, 25))),
            BuildDiaryDay(),
            MutableClock(),
            FakeConnectivityObserver(),
            SavedStateHandle(),
        )
        rule.setContent { Flow(diaryModel) }
        rule.waitForIdle()
    }

    @Composable
    private fun Flow(diaryModel: DiaryViewModel) {
        var editor by remember { mutableStateOf<MealSlotsEditorViewModel?>(null) }
        val diaryState by diaryModel.uiState.collectAsState()
        CompositionLocalProvider(LocalBackInterceptor provides backInterceptor) {
            AppTheme {
                val current = editor
                if (current == null) {
                    DiaryScreen(
                        diaryState,
                        {
                            if (it == DiaryEvent.OpenMealSettings) {
                                editor = MealSlotsEditorViewModel(
                                    Today,
                                    SavedStateHandle(),
                                    slots,
                                    diary,
                                    ValidateMealSlots(),
                                    connectivity,
                                )
                            } else {
                                diaryModel.onEvent(it)
                            }
                        },
                    )
                } else {
                    val state by current.uiState.collectAsState()
                    LaunchedEffect(current) {
                        current.effects.collect {
                            editor = null
                            diaryModel.onEvent(DiaryEvent.Refresh)
                        }
                    }
                    MealSlotsScreen(state, current::onEvent)
                }
                sheet.value?.let { model ->
                    val sheetState by model.uiState.collectAsState()
                    AddFoodSheet(sheetState, model::onEvent, { sheet.value = null })
                }
            }
        }
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun type(tag: String, text: String) {
        rule.onNodeWithTag(tag).performTextReplacement(text)
        rule.waitForIdle()
    }

    private fun top(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot().top

    private fun openEditorAndEdit(edit: () -> Unit) {
        tap("diary_meal_settings")
        edit()
        tap("meals_save")
    }

    @Test
    fun renamingAMeal_showsTheNewNameInTheDiaryWithTheSameTotals() {
        diary.server[Today] = listOf(lunchEntry())
        start()

        openEditorAndEdit { type("meals_label_lunch", "Post-entreno") }

        rule.onNodeWithTag("diary_screen").assertExists()
        rule.onNodeWithText("Post-entreno").assertExists()
        rule.onNodeWithText("Comida").assertDoesNotExist()
        rule.onNodeWithTag("diary_entry_e1").assertExists()
        assertEquals("lunch", slots.saved.single().first.first { it.label == "Post-entreno" }.id)
    }

    @Test
    fun addingAMealAfterBreakfast_appearsInThatPositionOfTheDiary() {
        start()

        openEditorAndEdit {
            tap("meals_add_after_breakfast")
            type("meals_label_new_1", "Segundo desayuno")
        }

        val ids = slots.saved.single().first.map { it.label }
        assertEquals(listOf("Desayuno", "Segundo desayuno", "Comida", "Merienda", "Cena"), ids)
        rule.onNodeWithText("Segundo desayuno").assertExists()
        val custom = slots.allResult!!.getOrThrow().first { it.label == "Segundo desayuno" }
        assertTrue(top("diary_section_breakfast") < top("diary_section_${custom.id}"))
        assertTrue(top("diary_section_${custom.id}") < top("diary_section_lunch"))
    }

    @Test
    fun anAddedMeal_isListedAsAnAddTargetInTheFoodSheet() {
        start()
        openEditorAndEdit {
            tap("meals_add")
            type("meals_label_new_1", "Segundo desayuno")
        }

        showFoodSheet()

        rule.onNode(hasText("Segundo desayuno") and hasAnyAncestor(hasTestTag("add_food_meals"))).assertExists()
    }

    @Test
    fun disablingAnEmptyMeal_hidesItFromTheDiaryAndTheAddTargets_andItCanBeEnabledAgain() {
        start()

        openEditorAndEdit { tap("meals_switch_snack") }

        rule.onNodeWithTag("diary_section_snack").assertDoesNotExist()
        rule.onNodeWithTag("diary_section_lunch").assertExists()
        showFoodSheet()
        rule.onNode(hasText("Merienda") and hasAnyAncestor(hasTestTag("add_food_meals"))).assertDoesNotExist()
        rule.onNode(hasText("Comida") and hasAnyAncestor(hasTestTag("add_food_meals"))).assertExists()
    }

    @Test
    fun aDisabledMeal_staysListedInTheEditor_andCanBeEnabledAgain() {
        start()
        openEditorAndEdit { tap("meals_switch_snack") }

        openEditorAndEdit { tap("meals_switch_snack") }

        rule.onNodeWithTag("diary_section_snack").assertExists()
    }

    @Test
    fun aMealWithEntriesOnTheViewedDay_cannotBeDisabled_andShowsTheHint() {
        diary.server[Today] = listOf(lunchEntry())
        start()

        tap("diary_meal_settings")

        rule.onNodeWithTag("meals_switch_lunch").assertIsNotEnabled()
        rule.onNodeWithTag("meals_hint_lunch").assertTextContains("Tiene alimentos hoy", substring = true)
        rule.onNodeWithTag("meals_hint_snack").assertDoesNotExist()
    }

    @Test
    fun movingDinnerUp_showsItAboveSnackInTheDiary_andKeepsTheOrderWhenReopened() {
        start()

        openEditorAndEdit { tap("meals_up_dinner") }

        assertTrue(top("diary_section_dinner") < top("diary_section_snack"))
        tap("diary_meal_settings")
        assertTrue(top("meals_row_dinner") < top("meals_row_snack"))
    }

    @Test
    fun firstMealCannotGoUp_andLastCannotGoDown() {
        start()

        tap("diary_meal_settings")

        rule.onNodeWithTag("meals_up_breakfast").assertIsNotEnabled()
        rule.onNodeWithTag("meals_down_dinner").assertIsNotEnabled()
    }

    private fun pressBack() {
        rule.runOnUiThread { backInterceptor.intercept() }
        rule.waitForIdle()
    }

    @Test
    fun backWithUnsavedChanges_asksBeforeDiscarding_andDiscardingKeepsTheOldMeals() {
        start()
        tap("diary_meal_settings")
        type("meals_label_lunch", "Almuerzo")

        pressBack()
        rule.onNodeWithTag("meals_discard_confirm").assertExists()
        tap("meals_discard_confirm")

        rule.onNodeWithTag("diary_screen").assertExists()
        rule.onNodeWithText("Comida").assertExists()
        assertTrue(slots.saved.isEmpty())
    }

    @Test
    fun backWithUnsavedChanges_canKeepEditing() {
        start()
        tap("diary_meal_settings")
        type("meals_label_lunch", "Almuerzo")

        pressBack()
        tap("meals_discard_cancel")

        rule.onNodeWithTag("meals_label_lunch").assertTextContains("Almuerzo")
    }

    @Test
    fun backWithoutChanges_leavesAtOnce() {
        start()
        tap("diary_meal_settings")

        pressBack()

        rule.onNodeWithTag("diary_screen").assertExists()
    }

    private fun showFoodSheet() {
        val rice = food("rice", "Arroz")
        val foods = FakeFoodRepository().apply { known[rice.id] = rice }
        sheet.value = AddFoodViewModel(
            foodId = rice.id,
            initialSlotId = null,
            date = Today,
            foodRepository = foods,
            foodDiary = diary,
            mealSlots = slots,
            resolvePortion = ResolvePortion(),
            scaleNutrition = ScaleNutrition(),
            validateQuantity = ValidateQuantity(),
            defaultMealSlot = DefaultMealSlot(),
            clock = MutableClock(),
            connectivity = connectivity,
            savedState = SavedStateHandle(),
        )
        rule.waitForIdle()
    }
}
