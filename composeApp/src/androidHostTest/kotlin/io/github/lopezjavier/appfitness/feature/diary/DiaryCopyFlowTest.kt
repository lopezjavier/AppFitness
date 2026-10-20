package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.usecase.BuildDiaryDay
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Today = LocalDate(2026, 10, 19)
private val Yesterday = LocalDate(2026, 10, 18)
private val Tomorrow = LocalDate(2026, 10, 20)

/** US-19 "Copying foods to another meal or day", "Copying a whole meal" and "Repeating yesterday's meal". */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class DiaryCopyFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val diary = FakeFoodDiaryRepository()
    private val connectivity = FakeConnectivityObserver()

    private fun entry(id: String, slot: String, name: String, day: LocalDate = Today) =
        FoodLogEntry(id, day, slot, "food-$id", name, 100.0, null, "g", 100.0, 10.0, 10.0, 5.0)

    private fun start(todayEntries: List<FoodLogEntry>, yesterdayEntries: List<FoodLogEntry> = emptyList()) {
        diary.server[Today] = todayEntries
        diary.server[Yesterday] = yesterdayEntries
        val model = DiaryViewModel(
            FakeMealSlotRepository(),
            diary,
            FakeNutritionGoalsRepository(NutritionGoals(GoalStrategy.Maintenance, 0, 2400, MacroSplit(30, 45, 25))),
            BuildDiaryDay(),
            MutableClock(),
            FakeDiaryPreferences(),
            connectivity,
            SavedStateHandle(),
        )
        rule.setContent {
            AppTheme {
                DiaryRoot(
                    onOpenPreferences = {},
                    onAddToMeal = { _, _ -> },
                    onAddFood = {},
                    onOpenMealSettings = {},
                    editHost = DiaryEditHost({ _, _ -> }, null, {}),
                    viewModel = model,
                )
            }
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun longPress(tag: String) {
        rule.onNodeWithTag(tag).performTouchInput { longClick() }
        rule.waitForIdle()
    }

    private fun inSection(entryId: String, slot: String) =
        hasTestTag("diary_entry_$entryId") and hasAnyAncestor(hasTestTag("diary_section_$slot"))

    private val lunchOfTwo get() = listOf(entry("a", "lunch", "Arroz"), entry("b", "lunch", "Pollo"))

    @Test
    fun longPressingAnEntry_entersSelectionModeWithTheCountAndTheActions() {
        start(lunchOfTwo)

        longPress("diary_entry_a")

        rule.onNodeWithTag("diary_selection_count").assertTextContains("1 seleccionado")
        rule.onNodeWithTag("diary_entry_select_a").assertExists()
        rule.onNodeWithTag("diary_selection_copy").assertIsEnabled()
        tap("diary_entry_b")
        rule.onNodeWithTag("diary_selection_count").assertTextContains("2 seleccionados")
    }

    @Test
    fun cancelling_leavesTheSelectionMode() {
        start(lunchOfTwo)
        longPress("diary_entry_a")

        tap("diary_selection_cancel")

        rule.onNodeWithTag("diary_selection_bar").assertDoesNotExist()
        rule.onNodeWithTag("diary_entry_select_a").assertDoesNotExist()
    }

    @Test
    fun theMealMenuOption_selectsEveryFoodOfTheMeal() {
        start(lunchOfTwo)

        tap("diary_meal_menu_lunch")
        tap("diary_meal_select_lunch")

        rule.onNodeWithTag("diary_selection_count").assertTextContains("2 seleccionados")
    }

    @Test
    fun copyingSelectedFoodsToAnotherMeal_isOneRequestAndTheFoodsAppearThere() {
        start(lunchOfTwo)
        longPress("diary_entry_a")
        tap("diary_entry_b")

        tap("diary_selection_copy")
        tap("copy_entries_slot_dinner")
        tap("copy_entries_confirm")

        assertEquals(1, diary.addedBatches.size)
        assertEquals(listOf("Arroz", "Pollo"), diary.addedBatches.single().map { it.foodName })
        assertEquals(setOf("dinner"), diary.addedBatches.single().map { it.mealSlotId }.toSet())
        rule.onNodeWithTag("copy_entries_sheet").assertDoesNotExist()
        rule.onNodeWithTag("diary_selection_bar").assertDoesNotExist()
        rule.onNode(hasText("Copiado a Cena, hoy")).assertExists()
        rule.onNode(hasTestTag("diary_entry_batch-1-0") and hasAnyAncestor(hasTestTag("diary_section_dinner")))
            .assertExists()
    }

    @Test
    fun copyingToTomorrow_keepsTheVisibleDayAndNamesTheDestination() {
        start(lunchOfTwo)
        longPress("diary_entry_a")

        tap("diary_selection_copy")
        tap("copy_entries_slot_dinner")
        tap("copy_entries_next_day")
        rule.onNodeWithTag("copy_entries_day").assertTextContains("Mañana")
        tap("copy_entries_confirm")

        assertEquals(Tomorrow, diary.addedBatches.single().single().date)
        rule.onNode(hasText("Copiado a Cena, mañana")).assertExists()
        rule.onNodeWithTag("diary_date_label").assertTextContains("Hoy")
        rule.onNodeWithTag("diary_date_next").assertIsNotEnabled()
        rule.onNode(inSection("a", "lunch")).assertExists()
    }

    @Test
    fun copyingAWholeMeal_sendsEveryFoodOfTheMealToTheChosenDay() {
        start(lunchOfTwo + entry("c", "lunch", "Ensalada"))

        tap("diary_meal_menu_lunch")
        tap("diary_meal_copy_lunch")
        tap("copy_entries_next_day")
        tap("copy_entries_confirm")

        assertEquals(1, diary.addedBatches.size)
        assertEquals(listOf("Arroz", "Pollo", "Ensalada"), diary.addedBatches.single().map { it.foodName })
        assertEquals(setOf(Tomorrow), diary.addedBatches.single().map { it.date }.toSet())
    }

    @Test
    fun theCopySheetStartsOnTheMealOfTheFoodsAndTheVisibleDay() {
        start(lunchOfTwo)

        tap("diary_meal_menu_lunch")
        tap("diary_meal_copy_lunch")

        rule.onNodeWithTag("copy_entries_day").assertTextContains("Hoy")
        rule.onNodeWithTag("copy_entries_confirm").assertIsEnabled()
    }

    @Test
    fun offline_theCopyButtonIsDisabledWithTheReason() {
        start(lunchOfTwo)
        tap("diary_meal_menu_lunch")
        tap("diary_meal_copy_lunch")

        connectivity.state.value = false
        rule.waitForIdle()

        rule.onNodeWithTag("copy_entries_offline").assertExists()
        rule.onNodeWithTag("copy_entries_confirm").assertIsNotEnabled()
    }

    @Test
    fun repeatingYesterdaysMeal_logsItInOneRequestAndTheUndoRemovesIt() {
        start(emptyList(), listOf(entry("y1", "lunch", "Lentejas", Yesterday), entry("y2", "lunch", "Pan", Yesterday)))

        tap("diary_repeat_yesterday_lunch")

        assertEquals(1, diary.addedBatches.size)
        assertEquals(Today, diary.addedBatches.single().first().date)
        rule.onNode(hasText("Copiado a Comida, hoy")).assertExists()
        rule.onNodeWithTag("diary_repeat_yesterday_lunch").assertDoesNotExist()

        tap("diary_undo")

        assertEquals(listOf(listOf("batch-1-0", "batch-1-1")), diary.deletedBatches)
        rule.onNodeWithTag("diary_repeat_yesterday_lunch").assertExists()
    }

    @Test
    fun theUndoOfACopy_deletesWhatTheCopyCreated() {
        start(lunchOfTwo)
        longPress("diary_entry_a")
        tap("diary_selection_copy")
        tap("copy_entries_slot_dinner")
        tap("copy_entries_confirm")

        tap("diary_undo")

        assertEquals(listOf(listOf("batch-1-0")), diary.deletedBatches)
        assertEquals(0, diary.restored.size)
        rule.onNode(hasTestTag("diary_entry_batch-1-0")).assertDoesNotExist()
    }

    @Test
    fun repeatYesterdayIsAbsent_whenYesterdaysMealWasEmpty() {
        start(emptyList(), listOf(entry("y1", "dinner", "Sopa", Yesterday)))

        rule.onNodeWithTag("diary_repeat_yesterday_lunch").assertDoesNotExist()
        rule.onNodeWithTag("diary_repeat_yesterday_dinner").assertExists()
    }

    @Test
    fun aFailedCopy_showsTheMessageAndKeepsTheSelection() {
        start(lunchOfTwo)
        longPress("diary_entry_a")
        tap("diary_selection_copy")
        diary.failBatch()

        tap("copy_entries_confirm")

        rule.onNode(hasText("Sin conexión. Inténtalo de nuevo cuando vuelvas a estar conectado.")).assertExists()
        rule.onNodeWithTag("diary_selection_bar").assertExists()
    }
}
