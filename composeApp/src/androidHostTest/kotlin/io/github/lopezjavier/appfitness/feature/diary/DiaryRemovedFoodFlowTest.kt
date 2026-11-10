package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HapticEvent
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.RecordingAppHaptics
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.usecase.BuildDiaryDay
import io.github.lopezjavier.appfitness.domain.usecase.RecalculateEntry
import io.github.lopezjavier.appfitness.domain.usecase.ResolvePortion
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.domain.usecase.ValidateQuantity
import io.github.lopezjavier.appfitness.feature.food.FakeFoodRepository
import io.github.lopezjavier.appfitness.feature.meals.defaults.FakeMealDefaultsRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Day = LocalDate(2026, 10, 19)

/** US-133 "Past days do not change": an entry of a removed food opens view or delete only (ADR-105). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class DiaryRemovedFoodFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val diary = FakeFoodDiaryRepository()
    private val foods = FakeFoodRepository()
    private val connectivity = FakeConnectivityObserver()
    private val haptics = RecordingAppHaptics()

    /** 220 g of a food that no longer exists: 242 kcal, in the snack. */
    private val removed =
        FoodLogEntry("e1", Day, "snack", null, "Pechuga de pollo", 220.0, null, "g", 242.0, 22.0, 44.0, 11.0)

    private fun start() {
        diary.server[Day] = listOf(removed)
        val diaryModel = DiaryViewModel(
            FakeMealSlotRepository(),
            diary,
            FakeNutritionGoalsRepository(NutritionGoals(GoalStrategy.Maintenance, 0, 2400, MacroSplit(30, 45, 25))),
            BuildDiaryDay(),
            MutableClock(),
            connectivity,
            FakeMealDefaultsRepository(),
            SavedStateHandle(),
        )
        rule.setContent { Host(diaryModel) }
        rule.waitForIdle()
    }

    @Composable
    @Suppress("ViewModelForwarding") // A test host wires the real ViewModels.
    private fun Host(diaryModel: DiaryViewModel) {
        var editing by remember { mutableStateOf<String?>(null) }
        var pendingDelete by remember { mutableStateOf<String?>(null) }
        AppTheme {
            CompositionLocalProvider(LocalAppHaptics provides haptics) {
                DiaryRoot(
                    onOpenPreferences = {},
                    onAddToMeal = { _, _ -> },
                    onAddFood = {},
                    onSaveTemplate = { _, _ -> },
                    editHost = DiaryEditHost(
                        onEditEntry = { id, _ -> editing = id },
                        pendingDeleteEntryId = pendingDelete,
                        onConsumeDelete = { pendingDelete = null },
                    ),
                    viewModel = diaryModel,
                )
                editing?.let { id ->
                    val editModel = remember(id) { editModel(id) }
                    EditEntryRoot(
                        entryId = id,
                        date = Day.toString(),
                        onSave = { editing = null },
                        onDelete = {
                            editing = null
                            pendingDelete = it
                        },
                        onDismiss = { editing = null },
                        viewModel = editModel,
                    )
                }
            }
        }
    }

    private fun editModel(entryId: String) = EditEntryViewModel(
        entryId = entryId,
        date = Day,
        foodRepository = foods,
        foodDiary = diary,
        mealSlots = FakeMealSlotRepository(),
        recalculateEntry = RecalculateEntry(ResolvePortion(), ScaleNutrition(), ValidateQuantity()),
        connectivity = connectivity,
        savedState = SavedStateHandle(),
    )

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun tappingTheEntryRow_opensTheViewOnlySheetFromTheSnapshot() {
        start()

        tap("diary_entry_e1")

        rule.onNodeWithTag("food_sheet_title").assertTextContains("Pechuga de pollo")
        rule.onNodeWithTag("food_sheet_removed_note")
            .assertTextContains("Este alimento ya no está en tu lista. Puedes ver el registro o eliminarlo.")
        rule.onNodeWithTag("food_sheet_snapshot_amount").assertTextContains("220 g")
        rule.onNodeWithTag("food_sheet_snapshot_meal").assertTextContains("19 oct", substring = true)
        rule.onNodeWithTag("food_sheet_totals").assertTextContains("242 kcal", substring = true)
    }

    @Test
    fun theViewOnlySheet_hasNoSaveNoAmountFieldNoMealChipsAndNoFoodActions() {
        start()

        tap("diary_entry_e1")

        rule.onNodeWithTag("food_sheet_delete").assertExists()
        rule.onNodeWithTag("food_sheet_commit").assertDoesNotExist()
        rule.onNodeWithTag("food_sheet_amount").assertDoesNotExist()
        rule.onNodeWithTag("food_sheet_slots").assertDoesNotExist()
        rule.onNodeWithTag("food_sheet_favourite").assertDoesNotExist()
        rule.onNodeWithTag("food_sheet_edit_food").assertDoesNotExist()
    }

    @Test
    fun delete_removesTheEntryAtOnceWithNoDialogAndAConfirmHaptic() {
        start()
        tap("diary_entry_e1")

        tap("food_sheet_delete")

        rule.onNodeWithTag("food_sheet").assertDoesNotExist()
        rule.onNodeWithTag("diary_entry_e1").assertDoesNotExist()
        assertEquals(listOf("e1"), diary.deleted)
        assertEquals(listOf(HapticEvent.Confirm), haptics.calls)
        rule.onNode(hasText("Deshacer")).assertDoesNotExist()
    }

    @Test
    fun offline_deleteIsDisabledWithTheDeleteOnlyHint() {
        start()
        tap("diary_entry_e1")

        connectivity.state.value = false
        rule.waitForIdle()

        rule.onNodeWithTag("food_sheet_delete").assertIsNotEnabled()
        rule.onNodeWithTag("food_sheet_commit_hint").assertTextContains("Sin conexión: no se puede eliminar")
    }

    @Test
    fun closingTheSheet_leavesTheEntryUntouched() {
        start()
        tap("diary_entry_e1")

        tap("food_sheet_close")

        rule.onNodeWithTag("food_sheet").assertDoesNotExist()
        rule.onNodeWithTag("diary_entry_e1").assertExists()
        assertEquals(emptyList<String>(), diary.deleted)
        assertEquals(emptyList<HapticEvent>(), haptics.calls)
    }
}
