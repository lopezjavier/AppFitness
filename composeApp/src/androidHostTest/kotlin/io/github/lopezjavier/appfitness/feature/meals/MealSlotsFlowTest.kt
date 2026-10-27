package io.github.lopezjavier.appfitness.feature.meals

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
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
import io.github.lopezjavier.appfitness.feature.food.FakeFoodPreferencesRepository
import io.github.lopezjavier.appfitness.feature.food.FakeFoodRepository
import io.github.lopezjavier.appfitness.feature.food.food
import io.github.lopezjavier.appfitness.feature.meals.defaults.FakeMealDefaultsRepository
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

/** US-56 end to end with fakes: meals editor over the Diary, staged edits, "Guardar", then Diary and picker. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class MealSlotsFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val slots = FakeMealSlotRepository()
    private val diary = FakeFoodDiaryRepository()
    private val connectivity = FakeConnectivityObserver()
    private val sheet = mutableStateOf<AddFoodViewModel?>(null)
    private val editorModel = mutableStateOf<MealSlotsEditorViewModel?>(null)

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
            FakeMealDefaultsRepository(),
            SavedStateHandle(),
        )
        rule.setContent { Flow(diaryModel) }
        rule.waitForIdle()
    }

    @Composable
    private fun Flow(diaryModel: DiaryViewModel) {
        var editor by editorModel
        val diaryState by diaryModel.uiState.collectAsState()
        AppTheme {
            val current = editor
            if (current == null) {
                DiaryScreen(diaryState, diaryModel::onEvent)
            } else {
                val state by current.uiState.collectAsState()
                MealSlotsScreen(
                    state,
                    current::onEvent,
                    onClose = {
                        editor = null
                        diaryModel.onEvent(DiaryEvent.Refresh)
                    },
                )
            }
            sheet.value?.let { model ->
                val sheetState by model.uiState.collectAsState()
                AddFoodSheet(sheetState, model::onEvent, { sheet.value = null })
            }
        }
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun top(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot().top

    private fun openEditor() {
        rule.runOnUiThread {
            editorModel.value = MealSlotsEditorViewModel(SavedStateHandle(), slots, ValidateMealSlots(), connectivity)
        }
        rule.waitForIdle()
    }

    private fun rename(key: String, name: String) {
        tap("meals_name_$key")
        rule.onNodeWithTag("meal_name_sheet_input").performTextReplacement(name)
        tap("meal_name_sheet_commit")
    }

    private fun addMeal(name: String) {
        tap("meals_add")
        rule.onNodeWithTag("meal_name_sheet_input").performTextInput(name)
        tap("meal_name_sheet_commit")
    }

    @Test
    fun renamingAMeal_showsTheNewNameInTheDiaryWithTheSameTotals() {
        diary.server[Today] = listOf(lunchEntry())
        start()
        openEditor()

        rename("lunch", "Post-entreno")
        tap("meals_save")

        rule.onNodeWithTag("diary_screen").assertExists()
        rule.onNodeWithText("Post-entreno").assertExists()
        rule.onNodeWithText("Comida").assertDoesNotExist()
        rule.onNodeWithTag("diary_entry_e1").assertExists()
        assertEquals("lunch", slots.saved.single().first { it.label == "Post-entreno" }.id)
    }

    @Test
    fun addingAMeal_appendsItAtTheEndOfTheDiary() {
        start()
        openEditor()

        addMeal("Pre-entreno")
        tap("meals_save")

        assertEquals(
            listOf("Desayuno", "Comida", "Merienda", "Cena", "Pre-entreno"),
            slots.saved.single().map { it.label },
        )
        val custom = slots.allResult!!.getOrThrow().first { it.label == "Pre-entreno" }
        assertTrue(top("diary_section_dinner") < top("diary_section_${custom.id}"))
    }

    @Test
    fun anAddedMeal_isListedAsAnAddTargetInTheFoodSheet() {
        start()
        openEditor()
        addMeal("Segundo desayuno")
        tap("meals_save")

        showFoodSheet()

        rule.onNode(hasText("Segundo desayuno") and hasAnyAncestor(hasTestTag("food_sheet_meals"))).assertExists()
    }

    @Test
    fun movingDinnerUp_showsItAboveSnackInTheDiary_andKeepsTheOrderWhenReopened() {
        start()
        openEditor()

        rule.runOnUiThread { editorModel.value!!.onEvent(MealSlotsEvent.MoveUp(3)) }
        rule.waitForIdle()
        tap("meals_save")

        assertTrue(top("diary_section_dinner") < top("diary_section_snack"))
        openEditor()
        assertTrue(top("meals_row_dinner") < top("meals_row_snack"))
    }

    @Test
    fun removingAMeal_isStagedUntilGuardar_thenSentInOneCall() {
        start()
        openEditor()

        tap("meals_delete_snack")

        rule.onNodeWithTag("meals_row_snack").assertDoesNotExist()
        assertTrue("nothing reaches the server before Guardar", slots.saved.isEmpty())
        tap("meals_save")
        assertEquals(1, slots.saved.size)
        assertEquals(listOf(listOf("snack")), slots.savedRemoved)
        rule.onNodeWithTag("diary_screen").assertExists()
        rule.onNodeWithTag("diary_section_snack").assertDoesNotExist()
        showFoodSheet()
        rule.onNode(hasText("Merienda") and hasAnyAncestor(hasTestTag("food_sheet_meals"))).assertDoesNotExist()
    }

    @Test
    fun backAfterStagingARemoval_asks_andDiscardingKeepsTheMeal() {
        start()
        openEditor()
        tap("meals_delete_snack")

        tap("meals_back")
        rule.onNodeWithTag("meals_discard_dialog").assertExists()
        tap("meals_discard_confirm")

        rule.onNodeWithTag("diary_screen").assertExists()
        rule.onNodeWithTag("diary_section_snack").assertExists()
        assertTrue(slots.saved.isEmpty())
    }

    @Test
    fun backWithUnsavedRename_canKeepEditing() {
        start()
        openEditor()
        rename("lunch", "Almuerzo")

        tap("meals_back")
        tap("meals_keep_editing")

        rule.onNodeWithTag("meals_name_lunch").assertTextContains("Almuerzo")
    }

    @Test
    fun backWithoutChanges_leavesAtOnce() {
        start()
        openEditor()

        tap("meals_back")

        rule.onNodeWithTag("diary_screen").assertExists()
    }

    @Test
    fun aMealWithEntries_showsItsReasonBeforeSaving_andCannotBeRemoved() {
        slots.inUse = setOf("lunch")
        start()
        openEditor()

        rule.onNodeWithTag("meals_hint_lunch").assertTextContains("Tiene alimentos registrados", substring = true)
        rule.onNodeWithTag("meals_delete_lunch").assertIsNotEnabled()
        assertTrue(slots.saved.isEmpty())
    }

    @Test
    fun theDefaultsRow_isBlockedWhileDirty_andOpensAfterSaving() {
        start()
        openEditor()

        rename("lunch", "Almuerzo")

        rule.onNodeWithTag("meal_slots_defaults_hint").assertExists()
        rule.onNodeWithTag("meal_slots_defaults_button").assertIsNotEnabled()
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
            foodPreferencesRepository = FakeFoodPreferencesRepository(),
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
