package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.centerY
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.right
import androidx.compose.ui.test.swipeLeft
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.designsystem.AppMessages
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HapticEvent
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
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
import io.github.lopezjavier.appfitness.feature.food.food
import io.github.lopezjavier.appfitness.feature.meals.defaults.FakeMealDefaultsRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Today = LocalDate(2026, 10, 19)

/** US-19 "Editing an entry's quantity", "Deleting an entry", "Moving an entry to another meal" and US-20 updates. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class DiaryEditFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val diary = FakeFoodDiaryRepository()
    private val foods = FakeFoodRepository()
    private val connectivity = FakeConnectivityObserver()
    private val haptics = RecordingAppHaptics()
    private val shownMessages = mutableListOf<String>()

    /** 220 g of chicken (110 kcal per 100 g): 242 kcal, in the snack. */
    private val chicken =
        FoodLogEntry("e1", Today, "snack", "chicken", "Pechuga de pollo", 220.0, null, "g", 242.0, 22.0, 44.0, 11.0)

    private fun start() {
        foods.known["chicken"] = food("chicken", "Pechuga de pollo", kcal = 110.0)
        diary.server[Today] = listOf(chicken)
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
        val scope = rememberCoroutineScope()
        val messages = remember {
            object : AppMessages(scope) {
                override fun show(text: String) {
                    shownMessages += text
                }
            }
        }
        AppTheme {
            CompositionLocalProvider(LocalAppMessages provides messages, LocalAppHaptics provides haptics) {
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
                        date = Today.toString(),
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
        date = Today,
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

    private fun inEntry(text: String, id: String = "e1") =
        hasText(text) and hasAnyAncestor(hasTestTag("diary_entry_$id"))

    @Test
    fun editingTheAmountTo150Grams_showsTheNewValuesInTheRowAndTheSectionTotal() {
        start()
        rule.onNode(inEntry("220 g")).assertExists()
        rule.onNode(inEntry("242 kcal")).assertExists()

        tap("diary_entry_e1")
        rule.onNodeWithTag("edit_entry_amount").assertTextContains("220")
        rule.onNodeWithTag("edit_entry_amount").performTextReplacement("150")
        rule.waitForIdle()
        rule.onNodeWithTag("edit_entry_preview").assertTextContains("165 kcal", substring = true)
        tap("edit_entry_save")

        rule.onNodeWithTag("edit_entry_sheet").assertDoesNotExist()
        rule.onNode(inEntry("150 g")).assertExists()
        rule.onNode(inEntry("165 kcal")).assertExists()
        rule.onNode(
            hasText("165 kcal") and hasAnyAncestor(hasTestTag("diary_section_snack_toggle")),
            useUnmergedTree = true,
        ).assertExists()
        assertEquals(1, diary.updated.size)
    }

    @Test
    fun movingTheEntryFromMeriendaToCena_showsItUnderCena() {
        start()
        tap("diary_entry_e1")

        tap("edit_entry_slot_dinner")
        tap("edit_entry_save")

        rule.onNode(hasTestTag("diary_entry_e1") and hasAnyAncestor(hasTestTag("diary_section_dinner"))).assertExists()
        rule.onNode(
            hasTestTag("diary_entry_e1") and hasAnyAncestor(hasTestTag("diary_section_snack")),
        ).assertDoesNotExist()
    }

    @Test
    fun anInvalidAmount_disablesSave() {
        start()
        tap("diary_entry_e1")

        rule.onNodeWithTag("edit_entry_amount").performTextReplacement("0")
        rule.waitForIdle()

        rule.onNodeWithTag("edit_entry_save").assertIsNotEnabled()
        rule.onNodeWithTag("edit_entry_amount").performTextReplacement("90")
        rule.waitForIdle()
        rule.onNodeWithTag("edit_entry_save").assertIsEnabled()
    }

    @Test
    fun closingTheSheetWithoutSaving_leavesTheEntryUntouched() {
        start()
        tap("diary_entry_e1")
        rule.onNodeWithTag("edit_entry_amount").performTextReplacement("150")

        tap("edit_entry_close")

        rule.onNodeWithTag("edit_entry_sheet").assertDoesNotExist()
        rule.onNode(inEntry("220 g")).assertExists()
        assertEquals(0, diary.updated.size)
    }

    @Test
    fun swipingAnEntry_deletesItImmediatelyAndUpdatesTheTotals() {
        start()

        rule.onNodeWithTag("diary_entry_e1").performTouchInput { swipeLeft() }
        rule.waitForIdle()

        rule.onNodeWithTag("diary_entry_e1").assertDoesNotExist()
        assertEquals(listOf("e1"), diary.deleted)
        rule.onNode(hasText("Alimento eliminado")).assertDoesNotExist()
        rule.onNode(hasText("Deshacer")).assertDoesNotExist()
    }

    @Test
    fun aShortDragOnAnEntry_snapsBackAndDeletesNothing() {
        start()

        rule.onNodeWithTag("diary_entry_e1").performTouchInput {
            down(Offset(right - 1f, centerY))
            moveTo(Offset(right * 0.6f, centerY))
            up()
        }
        rule.waitForIdle()

        rule.onNodeWithTag("diary_entry_e1").assertExists()
        assertEquals(emptyList<String>(), diary.deleted)
    }

    @Test
    fun deletingFromTheSheet_closesItAndRemovesTheEntryWithNoMessage() {
        start()
        tap("diary_entry_e1")

        tap("edit_entry_delete")

        rule.onNodeWithTag("edit_entry_sheet").assertDoesNotExist()
        rule.onNodeWithTag("diary_entry_e1").assertDoesNotExist()
        assertEquals(listOf("e1"), diary.deleted)
        rule.onNodeWithTag("diary_undo").assertDoesNotExist()
    }

    @Test
    fun aFailedSave_keepsTheSheetOpenWithTheMessageAndTheInput() {
        start()
        tap("diary_entry_e1")
        rule.onNodeWithTag("edit_entry_amount").performTextReplacement("150")
        diary.failUpdate()

        tap("edit_entry_save")

        rule.onNodeWithTag("edit_entry_save_error").assertTextContains("No se pudo guardar. Inténtalo de nuevo.")
        rule.onNodeWithTag("edit_entry_amount").assertTextContains("150")
    }

    @Test
    fun aFailedDelete_showsTheMessageAndKeepsTheEntry() {
        start()
        diary.failDelete()

        rule.onNodeWithTag("diary_entry_e1").performTouchInput { swipeLeft() }
        rule.waitForIdle()

        assertEquals(listOf("Sin conexión: no se puede eliminar"), shownMessages)
        // The swipe itself arms and confirms; the failure then adds the single Reject.
        assertEquals(listOf(HapticEvent.Armed, HapticEvent.Confirm, HapticEvent.Reject), haptics.calls)
        rule.onNode(inEntry("220 g")).assertExists()
    }

    @Test
    fun offline_theSheetExplainsWhySaveAndDeleteAreDisabled() {
        start()
        tap("diary_entry_e1")
        connectivity.state.value = false
        rule.waitForIdle()

        rule.onNodeWithTag("edit_entry_offline").assertExists()
        rule.onNodeWithTag("edit_entry_save").assertIsNotEnabled()
        rule.onNodeWithTag("edit_entry_delete").assertIsNotEnabled()
    }

    @Test
    fun whenTheEntryCannotBeLoaded_theSheetOffersRetryAndRecovers() {
        start()
        diary.failRefresh()
        tap("diary_entry_e1")
        rule.onNodeWithTag("edit_entry_error_state").assertExists()

        diary.refreshResult = Result.success(Unit)
        tap("edit_entry_error_state_action")

        rule.onNodeWithTag("edit_entry_amount").assertExists()
    }
}
