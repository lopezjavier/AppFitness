package io.github.lopezjavier.appfitness.feature.meals

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.MealCode
import io.github.lopezjavier.appfitness.domain.model.MealSlotDraft
import io.github.lopezjavier.appfitness.domain.usecase.MealSlotError
import io.github.lopezjavier.appfitness.domain.usecase.ValidateMealSlots
import io.github.lopezjavier.appfitness.feature.diary.FakeMealSlotRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Suppress("TooManyFunctions") // One method per Gherkin scenario or rule.
class MealSlotsEditorViewModelTest {
    private val slots = FakeMealSlotRepository()
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
        val vm = MealSlotsEditorViewModel(savedState, slots, ValidateMealSlots(), connectivity)
        advanceUntilIdle()
        return vm
    }

    private fun MealSlotsEditorViewModel.form() = assertIs<MealSlotsUiState.Editing>(uiState.value)

    private fun MealSlotsEditorViewModel.labels() = form().rows.map { it.label }

    private fun MealSlotsEditorViewModel.row(label: String) = form().rows.first { it.label == label }

    @Test
    fun opening_listsEveryMealInOrder() = runTest {
        val vm = editor()

        assertEquals(listOf("Desayuno", "Comida", "Merienda", "Cena"), vm.labels())
        assertFalse(vm.form().isDirty)
        assertFalse(vm.form().canSave)
    }

    @Test
    fun loadFailure_showsErrorAndRetryRecovers() = runTest {
        slots.allResult = Result.failure(AppException(AppError.Network))
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

        assertEquals(
            listOf(
                MealSlotDraft("breakfast", MealCode.Breakfast, "Desayuno"),
                MealSlotDraft("lunch", MealCode.Lunch, "Comida"),
                MealSlotDraft("snack", MealCode.Snack, "Post-entreno"),
                MealSlotDraft("dinner", MealCode.Dinner, "Cena"),
            ),
            slots.saved.single(),
        )
    }

    @Test
    fun save_sendsTrimmedLabels() = runTest {
        val vm = editor()
        vm.onEvent(MealSlotsEvent.SetLabel("snack", "  Post  "))

        vm.onEvent(MealSlotsEvent.Save)
        advanceUntilIdle()

        assertEquals("Post", slots.saved.single()[2].label)
    }

    @Test
    fun adding_appendsABlankCustomMealWithNullCode_andSavesIt() = runTest {
        val vm = editor()

        vm.onEvent(MealSlotsEvent.Add)
        val newKey = vm.form().rows.last().key
        vm.onEvent(MealSlotsEvent.SetLabel(newKey, "Pre-entreno"))
        vm.onEvent(MealSlotsEvent.Save)
        advanceUntilIdle()

        assertEquals(listOf("Desayuno", "Comida", "Merienda", "Cena", "Pre-entreno"), vm.labels())
        assertEquals(MealSlotDraft(null, null, "Pre-entreno"), slots.saved.single().last())
    }

    @Test
    fun addedMeal_canBeMovedAfterBreakfast() = runTest {
        val vm = editor()
        vm.onEvent(MealSlotsEvent.Add)
        vm.onEvent(MealSlotsEvent.SetLabel(vm.form().rows.last().key, "Segundo desayuno"))

        vm.onEvent(MealSlotsEvent.MoveTo(from = 4, to = 1))

        assertEquals(listOf("Desayuno", "Segundo desayuno", "Comida", "Merienda", "Cena"), vm.labels())
    }

    @Test
    fun addedMeal_startsInEditModeAndIsNew() = runTest {
        val vm = editor()

        vm.onEvent(MealSlotsEvent.Add)

        assertEquals(5, vm.form().rows.size)
        assertTrue(vm.form().rows.last().isNew)
        assertTrue(vm.form().rows.last().isEditing)
    }

    @Test
    fun newBlankMeal_hasErrorAndBlocksSave() = runTest {
        val vm = editor()

        vm.onEvent(MealSlotsEvent.Add)

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
    fun startingAndStoppingEdit_marksOnlyThatRow_andIsNotAnUnsavedChange() = runTest {
        val vm = editor()

        vm.onEvent(MealSlotsEvent.Edit("snack"))
        assertTrue(vm.row("Merienda").isEditing)
        assertFalse(vm.row("Cena").isEditing)
        assertFalse(vm.form().isDirty)

        vm.onEvent(MealSlotsEvent.StopEdit)
        assertFalse(vm.row("Merienda").isEditing)
    }

    @Test
    fun deletingAnUnsavedRow_removesItWithoutCallingTheRepository() = runTest {
        val vm = editor()
        vm.onEvent(MealSlotsEvent.Add)
        val newKey = vm.form().rows.last().key

        vm.onEvent(MealSlotsEvent.Delete(newKey))
        advanceUntilIdle()

        assertEquals(4, vm.form().rows.size)
        assertTrue(slots.deleted.isEmpty())
        assertFalse(vm.form().isDirty)
    }

    @Test
    fun deletingAnUnusedSavedRow_callsTheRepositoryOnce_andIsNotAnUnsavedEdit() = runTest {
        val vm = editor()

        vm.onEvent(MealSlotsEvent.Delete("snack"))
        advanceUntilIdle()

        assertEquals(listOf("snack"), slots.deleted)
        assertEquals(listOf("Desayuno", "Comida", "Cena"), vm.labels())
        assertFalse(vm.form().isDirty)
        assertFalse(vm.form().canSave)
    }

    @Test
    fun deletingASavedRow_keepsOtherPendingEdits_andSavingSendsTheRemainingIdsInOrder() = runTest {
        val vm = editor()
        vm.onEvent(MealSlotsEvent.SetLabel("dinner", "Cena tarde"))

        vm.onEvent(MealSlotsEvent.Delete("lunch"))
        advanceUntilIdle()
        assertTrue(vm.form().isDirty)
        vm.onEvent(MealSlotsEvent.Save)
        advanceUntilIdle()

        assertEquals(
            listOf(
                MealSlotDraft("breakfast", MealCode.Breakfast, "Desayuno"),
                MealSlotDraft("snack", MealCode.Snack, "Merienda"),
                MealSlotDraft("dinner", MealCode.Dinner, "Cena tarde"),
            ),
            slots.saved.single(),
        )
    }

    @Test
    fun deletedRow_doesNotComeBackWhenTheViewModelIsRebuiltFromTheSameSavedState() = runTest {
        val vm = editor()
        vm.onEvent(MealSlotsEvent.Delete("snack"))
        advanceUntilIdle()

        val restored = editor(handle)

        assertEquals(listOf("Desayuno", "Comida", "Cena"), restored.labels())
        assertFalse(restored.form().isDirty)
    }

    @Test
    fun deleteRefusedByServer_keepsTheRowAndFlagsItUntilItIsEdited() = runTest {
        slots.failDelete(AppError.Validation)
        val vm = editor()

        vm.onEvent(MealSlotsEvent.Delete("lunch"))
        advanceUntilIdle()

        assertEquals(4, vm.form().rows.size)
        assertTrue(vm.row("Comida").deleteBlocked)
        assertFalse(vm.row("Cena").deleteBlocked)
        assertEquals(null, vm.form().saveError)

        vm.onEvent(MealSlotsEvent.SetLabel("lunch", "Almuerzo"))
        assertFalse(vm.row("Almuerzo").deleteBlocked)
    }

    @Test
    fun deleteFailure_keepsTheRowAndShowsTheSaveError() = runTest {
        slots.failDelete(AppError.Network)
        val vm = editor()

        vm.onEvent(MealSlotsEvent.Delete("lunch"))
        advanceUntilIdle()

        assertEquals(4, vm.form().rows.size)
        assertFalse(vm.row("Comida").deleteBlocked)
        assertEquals(MealSlotsSaveError.Offline, vm.form().saveError)
    }

    @Test
    fun lastRemainingMeal_cannotBeDeleted() = runTest {
        slots.allResult = Result.success(slots.result.getOrThrow().take(1))
        val vm = editor()

        assertTrue(vm.form().onlyOneMeal)
        assertFalse(vm.form().rows.single().canDelete)
        vm.onEvent(MealSlotsEvent.Delete("breakfast"))
        advanceUntilIdle()

        assertEquals(1, vm.form().rows.size)
        assertTrue(slots.deleted.isEmpty())
    }

    @Test
    fun deleting_isDisabledWhileOffline() = runTest {
        val vm = editor()
        connectivity.state.value = false
        advanceUntilIdle()

        assertFalse(vm.form().rows.any { it.canDelete })
        vm.onEvent(MealSlotsEvent.Delete("snack"))
        advanceUntilIdle()

        assertEquals(4, vm.form().rows.size)
        assertTrue(slots.deleted.isEmpty())
    }

    @Test
    fun withSeveralMeals_everyRowCanBeDeleted() = runTest {
        val vm = editor()

        assertFalse(vm.form().onlyOneMeal)
        assertTrue(vm.form().rows.all { it.canDelete })
    }

    @Test
    fun movingWithTheDragCallback_reordersAndSaveSendsTheNewOrder() = runTest {
        val vm = editor()

        vm.onEvent(MealSlotsEvent.MoveTo(from = 3, to = 2))
        vm.onEvent(MealSlotsEvent.Save)
        advanceUntilIdle()

        assertEquals(listOf("Desayuno", "Comida", "Cena", "Merienda"), vm.labels())
        assertEquals(listOf("breakfast", "lunch", "dinner", "snack"), slots.saved.single().map { it.id })
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
        vm.onEvent(MealSlotsEvent.SetLabel("snack", "Post"))

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
    fun recreatedFromSameSavedState_restoresEditsOrderAddedRowsAndEditingKey() = runTest {
        val vm = editor()
        vm.onEvent(MealSlotsEvent.SetLabel("snack", "Post"))
        vm.onEvent(MealSlotsEvent.MoveTo(3, 0))
        vm.onEvent(MealSlotsEvent.Add)
        vm.onEvent(MealSlotsEvent.SetLabel(vm.form().rows.last().key, "Extra"))
        vm.onEvent(MealSlotsEvent.Edit("lunch"))
        val before = vm.form().rows

        val restored = editor(handle)

        assertEquals(before, restored.form().rows)
        assertTrue(restored.form().isDirty)
        assertTrue(restored.row("Comida").isEditing)
        restored.onEvent(MealSlotsEvent.Add)
        assertTrue(restored.form().rows.map { it.key }.toSet().size == 6)
    }

    @Test
    fun savedDraftFromBeforeDisablingWasRemoved_stillRestores() = runTest {
        val old =
            """{"rows":[{"key":"breakfast","id":"breakfast","code":"Breakfast",""" +
                """"label":"Desayuno","enabled":false}],"nextNewId":1}"""
        handle["meals_draft"] = old
        handle["meals_baseline"] = old

        val vm = editor(handle)

        assertEquals(listOf("Desayuno"), vm.labels())
        assertFalse(vm.form().isDirty)
    }
}
