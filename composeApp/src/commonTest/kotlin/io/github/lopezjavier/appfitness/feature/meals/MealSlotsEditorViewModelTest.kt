package io.github.lopezjavier.appfitness.feature.meals

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.MealCode
import io.github.lopezjavier.appfitness.domain.model.MealSlotDraft
import io.github.lopezjavier.appfitness.domain.usecase.MealSlotError
import io.github.lopezjavier.appfitness.domain.usecase.ValidateMealSlots
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.diary.FakeMealSlotRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Suppress("TooManyFunctions") // One method per Gherkin scenario or rule.
class MealSlotsEditorViewModelTest {
    private val today = LocalDate(2026, 10, 20)
    private val slots = FakeMealSlotRepository()
    private val diary = FakeFoodDiaryRepository()
    private val connectivity = FakeConnectivityObserver()
    private val handle = SavedStateHandle()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.editor(savedState: SavedStateHandle = handle): MealSlotsEditorViewModel {
        val vm = MealSlotsEditorViewModel(today, savedState, slots, diary, ValidateMealSlots(), connectivity)
        advanceUntilIdle()
        return vm
    }

    private fun MealSlotsEditorViewModel.form() = assertIs<MealSlotsUiState.Editing>(uiState.value)

    private fun MealSlotsEditorViewModel.labels() = form().rows.map { it.label }

    private fun MealSlotsEditorViewModel.row(label: String) = form().rows.first { it.label == label }

    private fun entry(slotId: String, date: LocalDate = today) =
        FoodLogEntry("e-$slotId-$date", date, slotId, "f", "Food", 100.0, null, "g", 100.0, 0.0, 0.0, 0.0)

    @Test
    fun opening_listsEveryMealIncludingDisabledOnesInOrder() = runTest {
        slots.allResult =
            Result.success(
                slots.result.getOrThrow().map { it } +
                    io.github.lopezjavier.appfitness.domain.model.MealSlot("extra", null, "Extra", 4, enabled = false),
            )

        val vm = editor()

        assertEquals(listOf("Desayuno", "Comida", "Merienda", "Cena", "Extra"), vm.labels())
        assertFalse(vm.row("Extra").enabled)
        assertFalse(vm.form().isDirty)
        assertFalse(vm.form().canSave)
    }

    @Test
    fun loadFailure_showsErrorAndRetryRecovers() = runTest {
        slots.allResult = Result.failure(io.github.lopezjavier.appfitness.domain.AppException(AppError.Network))
        val vm = editor()
        assertEquals(MealSlotsUiState.LoadError, vm.uiState.value)

        slots.allResult = null
        vm.onEvent(MealSlotsEvent.Retry)
        advanceUntilIdle()

        assertEquals(4, vm.form().rows.size)
    }

    @Test
    fun renaming_savesNewLabelKeepingIdAndOrder_thenCloses() = runTest {
        val vm = editor()
        vm.onEvent(MealSlotsEvent.SetLabel("snack", "Post-entreno"))
        assertTrue(vm.form().canSave)

        vm.effects.test {
            vm.onEvent(MealSlotsEvent.Save)
            advanceUntilIdle()

            assertEquals(MealSlotsEffect.Close, awaitItem())
        }

        val (saved, day) = slots.saved.single()
        assertEquals(today, day)
        assertEquals(
            listOf(
                MealSlotDraft("breakfast", MealCode.Breakfast, "Desayuno", true),
                MealSlotDraft("lunch", MealCode.Lunch, "Comida", true),
                MealSlotDraft("snack", MealCode.Snack, "Post-entreno", true),
                MealSlotDraft("dinner", MealCode.Dinner, "Cena", true),
            ),
            saved,
        )
    }

    @Test
    fun save_sendsTrimmedLabels() = runTest {
        val vm = editor()
        vm.onEvent(MealSlotsEvent.SetLabel("snack", "  Post  "))

        vm.onEvent(MealSlotsEvent.Save)
        advanceUntilIdle()

        assertEquals("Post", slots.saved.single().first[2].label)
    }

    @Test
    fun addingAfterARow_insertsBlankCustomMealInThatPositionWithNullCode() = runTest {
        val vm = editor()

        vm.onEvent(MealSlotsEvent.Add(afterIndex = 0))
        val newKey = vm.form().rows[1].key
        vm.onEvent(MealSlotsEvent.SetLabel(newKey, "Segundo desayuno"))
        vm.onEvent(MealSlotsEvent.Save)
        advanceUntilIdle()

        assertEquals(listOf("Desayuno", "Segundo desayuno", "Comida", "Merienda", "Cena"), vm.labels())
        assertEquals(MealSlotDraft(null, null, "Segundo desayuno", true), slots.saved.single().first[1])
    }

    @Test
    fun adding_withoutIndex_appendsAtTheEnd() = runTest {
        val vm = editor()

        vm.onEvent(MealSlotsEvent.Add())

        assertEquals(5, vm.form().rows.size)
        assertTrue(vm.form().rows.last().isNew)
    }

    @Test
    fun newBlankMeal_hasErrorAndBlocksSave() = runTest {
        val vm = editor()

        vm.onEvent(MealSlotsEvent.Add())

        assertEquals(MealSlotError.Blank, vm.form().rows.last().error)
        assertFalse(vm.form().canSave)
    }

    @Test
    fun labelOver50Characters_isFlaggedAndBlocksSave() = runTest {
        val vm = editor()

        vm.onEvent(MealSlotsEvent.SetLabel("snack", "a".repeat(51)))

        assertEquals(MealSlotError.TooLong, vm.row("a".repeat(51)).error)
        assertFalse(vm.form().canSave)
    }

    @Test
    fun disablingAnEmptyMeal_isAllowedAndSaved_thenReEnablingKeepsLabel() = runTest {
        val vm = editor()

        vm.onEvent(MealSlotsEvent.SetEnabled("snack", false))
        assertFalse(vm.row("Merienda").enabled)
        vm.onEvent(MealSlotsEvent.Save)
        advanceUntilIdle()
        assertFalse(slots.saved.single().first[2].enabled)

        val reopened = editor(SavedStateHandle())
        assertEquals("Merienda", reopened.row("Merienda").label)
        reopened.onEvent(MealSlotsEvent.SetEnabled("snack", true))
        assertTrue(reopened.row("Merienda").enabled)
        assertTrue(reopened.form().canSave)
    }

    @Test
    fun mealWithEntriesOnViewedDay_cannotBeDisabledAndShowsHint() = runTest {
        diary.server[today] = listOf(entry("snack"))

        val vm = editor()
        vm.onEvent(MealSlotsEvent.SetEnabled("snack", false))

        val snack = vm.row("Merienda")
        assertFalse(snack.canToggle)
        assertTrue(snack.hasEntriesInViewedDay)
        assertTrue(snack.enabled)
        assertFalse(vm.form().isDirty)
        assertTrue(vm.row("Cena").canToggle)
    }

    @Test
    fun mealWithEntriesOnlyOnAnotherDay_canBeDisabled() = runTest {
        diary.server[today] = emptyList()
        diary.server[today.minusOneDay()] = listOf(entry("snack", today.minusOneDay()))

        val vm = editor()
        vm.onEvent(MealSlotsEvent.SetEnabled("snack", false))

        assertTrue(vm.row("Merienda").canToggle)
        assertFalse(vm.row("Merienda").enabled)
        assertFalse(vm.row("Merienda").hasEntriesInViewedDay)
    }

    @Test
    fun entriesNotReadable_locksDisablingOfExistingMealsWithoutHint() = runTest {
        diary.failRefresh()

        val vm = editor()
        vm.onEvent(MealSlotsEvent.SetEnabled("snack", false))

        assertFalse(vm.row("Merienda").canToggle)
        assertFalse(vm.row("Merienda").hasEntriesInViewedDay)
        assertTrue(vm.row("Merienda").enabled)
        vm.onEvent(MealSlotsEvent.Add())
        assertTrue(vm.form().rows.last().canToggle)
    }

    @Test
    fun disablingEveryMeal_flagsNoneEnabledAndBlocksSave() = runTest {
        val vm = editor()

        listOf("breakfast", "lunch", "snack", "dinner").forEach { vm.onEvent(MealSlotsEvent.SetEnabled(it, false)) }

        assertTrue(vm.form().noneEnabled)
        assertFalse(vm.form().canSave)
    }

    @Test
    fun movingWithTheDragCallback_reordersAndSaveSendsTheNewOrder() = runTest {
        val vm = editor()

        vm.onEvent(MealSlotsEvent.MoveTo(from = 3, to = 2))
        vm.onEvent(MealSlotsEvent.Save)
        advanceUntilIdle()

        assertEquals(listOf("Desayuno", "Comida", "Cena", "Merienda"), vm.labels())
        assertEquals(listOf("breakfast", "lunch", "dinner", "snack"), slots.saved.single().first.map { it.id })
    }

    @Test
    fun upAndDownButtons_moveOneStep_andEdgesCannotMove() = runTest {
        val vm = editor()
        assertFalse(vm.form().rows.first().canMoveUp)
        assertFalse(vm.form().rows.last().canMoveDown)

        vm.onEvent(MealSlotsEvent.MoveUp(3))
        assertEquals(listOf("Desayuno", "Comida", "Cena", "Merienda"), vm.labels())
        vm.onEvent(MealSlotsEvent.MoveDown(2))
        assertEquals(listOf("Desayuno", "Comida", "Merienda", "Cena"), vm.labels())
        vm.onEvent(MealSlotsEvent.MoveUp(0))
        vm.onEvent(MealSlotsEvent.MoveDown(3))
        assertEquals(listOf("Desayuno", "Comida", "Merienda", "Cena"), vm.labels())
        assertFalse(vm.form().isDirty)
    }

    @Test
    fun saveFailure_keepsDraftAndShowsError() = runTest {
        slots.failSave(AppError.Unknown)
        val vm = editor()
        vm.onEvent(MealSlotsEvent.SetLabel("snack", "Post"))

        vm.onEvent(MealSlotsEvent.Save)
        advanceUntilIdle()

        assertEquals(MealSlotsSaveError.Failed, vm.form().saveError)
        assertEquals("Post", vm.row("Post").label)
        assertTrue(vm.form().canSave)
        assertFalse(vm.form().isSaving)
    }

    @Test
    fun saveRejectedByServer_showsRejectedError() = runTest {
        slots.failSave(AppError.Validation)
        val vm = editor()
        vm.onEvent(MealSlotsEvent.SetEnabled("snack", false))

        vm.onEvent(MealSlotsEvent.Save)
        advanceUntilIdle()

        assertEquals(MealSlotsSaveError.Rejected, vm.form().saveError)
    }

    @Test
    fun saveNetworkFailure_showsOfflineError_andEditingClearsIt() = runTest {
        slots.failSave(AppError.Network)
        val vm = editor()
        vm.onEvent(MealSlotsEvent.SetLabel("snack", "Post"))
        vm.onEvent(MealSlotsEvent.Save)
        advanceUntilIdle()
        assertEquals(MealSlotsSaveError.Offline, vm.form().saveError)

        vm.onEvent(MealSlotsEvent.SetLabel("snack", "Post2"))

        assertEquals(null, vm.form().saveError)
    }

    @Test
    fun offline_blocksSave() = runTest {
        val vm = editor()
        vm.onEvent(MealSlotsEvent.SetLabel("snack", "Post"))
        connectivity.state.value = false
        advanceUntilIdle()

        assertFalse(vm.form().isOnline)
        assertFalse(vm.form().canSave)
        vm.onEvent(MealSlotsEvent.Save)
        advanceUntilIdle()
        assertTrue(slots.saved.isEmpty())
    }

    @Test
    fun back_withoutChanges_closesAtOnce() = runTest {
        val vm = editor()

        vm.effects.test {
            vm.onEvent(MealSlotsEvent.Back)
            assertEquals(MealSlotsEffect.Close, awaitItem())
        }
    }

    @Test
    fun back_withChanges_asksToDiscard_andDismissKeepsEditing() = runTest {
        val vm = editor()
        vm.onEvent(MealSlotsEvent.SetLabel("snack", "Post"))

        vm.onEvent(MealSlotsEvent.Back)
        assertTrue(vm.form().showDiscardConfirm)
        vm.onEvent(MealSlotsEvent.DismissDiscard)

        assertFalse(vm.form().showDiscardConfirm)
        vm.effects.test {
            vm.onEvent(MealSlotsEvent.Back)
            vm.onEvent(MealSlotsEvent.ConfirmDiscard)
            assertEquals(MealSlotsEffect.Close, awaitItem())
        }
    }

    @Test
    fun recreatedFromSameSavedState_restoresEditsOrderAndAddedRows() = runTest {
        val vm = editor()
        vm.onEvent(MealSlotsEvent.SetLabel("snack", "Post"))
        vm.onEvent(MealSlotsEvent.SetEnabled("lunch", false))
        vm.onEvent(MealSlotsEvent.MoveTo(3, 0))
        vm.onEvent(MealSlotsEvent.Add(afterIndex = 1))
        vm.onEvent(MealSlotsEvent.SetLabel(vm.form().rows[2].key, "Extra"))
        val before = vm.form().rows

        val restored = editor(handle)

        assertEquals(before, restored.form().rows)
        assertTrue(restored.form().isDirty)
        restored.onEvent(MealSlotsEvent.Add())
        assertTrue(restored.form().rows.map { it.key }.toSet().size == 6)
    }

    private fun LocalDate.minusOneDay(): LocalDate = LocalDate.fromEpochDays(toEpochDays() - 1)
}
