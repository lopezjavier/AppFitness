package io.github.lopezjavier.appfitness.feature.meals

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.NameSheetDefaults
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.MealCode
import io.github.lopezjavier.appfitness.domain.model.MealSlotDraft
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

    private fun MealSlotsEditorViewModel.addMeal(name: String) {
        onEvent(MealSlotsEvent.Add)
        onEvent(MealSlotsEvent.SubmitName(name))
    }

    private fun TestScope.save(vm: MealSlotsEditorViewModel) {
        vm.onEvent(MealSlotsEvent.Save)
        advanceUntilIdle()
    }

    @Test
    fun opening_listsEveryMealInOrder_andIsClean() = runTest {
        val vm = editor()

        assertEquals(listOf("Desayuno", "Comida", "Merienda", "Cena"), vm.labels())
        assertFalse(vm.form().isDirty)
        assertFalse(vm.form().canSave)
        assertFalse(vm.form().saved)
        assertTrue(vm.form().canNavigateToDefaults)
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
    fun add_opensTheCreateSheet_andDoesNotChangeTheDraft() = runTest {
        val vm = editor()

        vm.onEvent(MealSlotsEvent.Add)

        assertEquals(MealNameSheet.Create, vm.form().sheet)
        assertEquals(4, vm.form().rows.size)
        assertFalse(vm.form().isDirty)
    }

    @Test
    fun submitName_inCreate_appendsANewRowAndClosesTheSheet() = runTest {
        val vm = editor()

        vm.addMeal("  Pre-entreno ")

        assertEquals(listOf("Desayuno", "Comida", "Merienda", "Cena", "Pre-entreno"), vm.labels())
        assertTrue(vm.form().rows.last().isNew)
        assertEquals(null, vm.form().sheet)
        assertTrue(vm.form().isDirty)
        assertTrue(vm.form().canSave)
    }

    @Test
    fun dismissSheet_closesItWithoutChanges() = runTest {
        val vm = editor()
        vm.onEvent(MealSlotsEvent.Add)

        vm.onEvent(MealSlotsEvent.DismissSheet)

        assertEquals(null, vm.form().sheet)
        assertFalse(vm.form().isDirty)
    }

    @Test
    fun rename_opensTheSheetWithTheCurrentName_andSubmitStagesTheNewName() = runTest {
        val vm = editor()

        vm.onEvent(MealSlotsEvent.Rename("snack"))
        assertEquals(MealNameSheet.Rename("snack", "Merienda"), vm.form().sheet)
        vm.onEvent(MealSlotsEvent.SubmitName("Post-entreno"))

        assertEquals(listOf("Desayuno", "Comida", "Post-entreno", "Cena"), vm.labels())
        assertEquals(null, vm.form().sheet)
        assertTrue(vm.form().isDirty)
        assertTrue(slots.saved.isEmpty())
    }

    @Test
    fun rename_keepingOnlyTheCaseOfItsOwnName_isAllowed() = runTest {
        val vm = editor()

        vm.onEvent(MealSlotsEvent.Rename("snack"))
        vm.onEvent(MealSlotsEvent.SubmitName("merienda"))

        assertEquals("merienda", vm.form().rows[2].label)
    }

    @Test
    fun submitName_duplicateOfAnotherRowIsRefused_ignoringCaseAndSpaces() = runTest {
        val vm = editor()
        vm.onEvent(MealSlotsEvent.Add)

        vm.onEvent(MealSlotsEvent.SubmitName("  cena "))

        assertEquals(4, vm.form().rows.size)
        assertEquals(MealNameSheet.Create, vm.form().sheet)
        assertFalse(vm.form().isDirty)
    }

    @Test
    fun submitName_renameToAnotherRowsName_isRefused() = runTest {
        val vm = editor()
        vm.onEvent(MealSlotsEvent.Rename("snack"))

        vm.onEvent(MealSlotsEvent.SubmitName("COMIDA"))

        assertEquals("Merienda", vm.form().rows[2].label)
        assertIs<MealNameSheet.Rename>(vm.form().sheet)
    }

    @Test
    fun submitName_blankOrOver50Characters_isRefused() = runTest {
        val vm = editor()
        vm.onEvent(MealSlotsEvent.Add)

        vm.onEvent(MealSlotsEvent.SubmitName("   "))
        vm.onEvent(MealSlotsEvent.SubmitName("a".repeat(51)))

        assertEquals(4, vm.form().rows.size)
        vm.onEvent(MealSlotsEvent.SubmitName("a".repeat(50)))
        assertEquals(5, vm.form().rows.size)
    }

    @Test
    fun duplicateRule_isConsistentWithTheNameSheet() {
        val existing = listOf("Torso", "Torso A")
        val candidates = listOf("  torso ", "Torso  A", "torso 2", "TORSO A")
        candidates.forEach {
            assertEquals(
                NameSheetDefaults.isDuplicate(it, existing),
                ValidateMealSlots.isDuplicate(it, existing),
                it,
            )
        }
    }

    @Test
    fun deletingASavedRow_stagesTheRemoval_andCallsNothingOnTheRepository() = runTest {
        val vm = editor()

        vm.onEvent(MealSlotsEvent.Delete("snack"))
        advanceUntilIdle()

        assertEquals(listOf("Desayuno", "Comida", "Cena"), vm.labels())
        assertTrue(vm.form().isDirty)
        assertTrue(vm.form().canSave)
        assertTrue(slots.saved.isEmpty())
    }

    @Test
    fun deletingANewRow_dropsItAndTheEditorIsCleanAgain() = runTest {
        val vm = editor()
        vm.addMeal("Extra")

        vm.onEvent(MealSlotsEvent.Delete(vm.form().rows.last().key))

        assertEquals(4, vm.form().rows.size)
        assertFalse(vm.form().isDirty)
    }

    @Test
    fun removingAMealAndAddingOneWithTheSameName_staysDirty() = runTest {
        val vm = editor()

        vm.onEvent(MealSlotsEvent.Delete("snack"))
        vm.addMeal("Merienda")

        assertTrue(vm.form().isDirty)
        save(vm)
        assertEquals(listOf("snack"), slots.savedRemoved.single())
        assertEquals(null, slots.saved.single().last().id)
    }

    @Test
    fun renamingBackToTheOriginalName_isNotDirty() = runTest {
        val vm = editor()
        vm.onEvent(MealSlotsEvent.Rename("snack"))
        vm.onEvent(MealSlotsEvent.SubmitName("Post"))
        assertTrue(vm.form().isDirty)

        vm.onEvent(MealSlotsEvent.Rename("snack"))
        vm.onEvent(MealSlotsEvent.SubmitName("Merienda"))

        assertFalse(vm.form().isDirty)
    }

    @Test
    fun save_sendsOneCallWithDraftsInOrderAndTheRemovedIds() = runTest {
        val vm = editor()
        vm.onEvent(MealSlotsEvent.Delete("lunch"))
        vm.onEvent(MealSlotsEvent.Rename("dinner"))
        vm.onEvent(MealSlotsEvent.SubmitName("Cena tarde"))
        vm.addMeal("Pre-entreno")
        vm.onEvent(MealSlotsEvent.MoveTo(from = 3, to = 0))

        save(vm)

        assertEquals(
            listOf(
                MealSlotDraft(null, null, "Pre-entreno"),
                MealSlotDraft("breakfast", MealCode.Breakfast, "Desayuno"),
                MealSlotDraft("snack", MealCode.Snack, "Merienda"),
                MealSlotDraft("dinner", MealCode.Dinner, "Cena tarde"),
            ),
            slots.saved.single(),
        )
        assertEquals(listOf("lunch"), slots.savedRemoved.single())
    }

    @Test
    fun save_success_marksSaved_andIsClean() = runTest {
        val vm = editor()
        vm.onEvent(MealSlotsEvent.Delete("snack"))

        save(vm)

        assertTrue(vm.form().saved)
        assertFalse(vm.form().isDirty)
        assertFalse(vm.form().isSaving)
        save(vm)
        assertEquals(1, slots.saved.size)
    }

    @Test
    fun inUseMeal_isFlagged_andItsRemovalIsRefusedLocally() = runTest {
        slots.inUse = setOf("lunch")
        val vm = editor()

        assertTrue(vm.row("Comida").inUse)
        assertFalse(vm.row("Comida").canDelete)
        assertTrue(vm.row("Cena").canDelete)
        vm.onEvent(MealSlotsEvent.Delete("lunch"))

        assertEquals(4, vm.form().rows.size)
        assertFalse(vm.form().isDirty)
    }

    @Test
    fun inUseLoadFailure_isNotFatal_andNoHintShows() = runTest {
        slots.inUseResult = Result.failure(AppException(AppError.Network))
        val vm = editor()

        assertEquals(4, vm.form().rows.size)
        assertTrue(vm.form().rows.none { it.inUse })
    }

    @Test
    fun lastRemainingMeal_isFlagged_andCannotBeRemoved() = runTest {
        slots.allResult = Result.success(slots.result.getOrThrow().take(1))
        val vm = editor()

        assertTrue(vm.form().rows.single().isLastMeal)
        assertFalse(vm.form().rows.single().canDelete)
        vm.onEvent(MealSlotsEvent.Delete("breakfast"))

        assertEquals(1, vm.form().rows.size)
        assertFalse(vm.form().isDirty)
    }

    @Test
    fun removingDownToOneMeal_thenTheLastOneIsProtected() = runTest {
        val vm = editor()
        listOf("lunch", "snack", "dinner").forEach { vm.onEvent(MealSlotsEvent.Delete(it)) }

        assertEquals(listOf("Desayuno"), vm.labels())
        assertTrue(vm.form().rows.single().isLastMeal)
        vm.onEvent(MealSlotsEvent.Delete("breakfast"))
        assertEquals(1, vm.form().rows.size)
    }

    @Test
    fun movingWithTheDragCallback_reordersAndSaveSendsTheNewOrder() = runTest {
        val vm = editor()

        vm.onEvent(MealSlotsEvent.MoveTo(from = 3, to = 2))
        save(vm)

        assertEquals(listOf("breakfast", "lunch", "dinner", "snack"), slots.saved.single().map { it.id })
    }

    @Test
    fun upAndDownActions_moveOneStep_andEdgesCannotMove() = runTest {
        val vm = editor()
        assertFalse(vm.form().rows.first().canMoveUp)
        assertFalse(vm.form().rows.last().canMoveDown)

        vm.onEvent(MealSlotsEvent.MoveUp(3))
        assertEquals(listOf("Desayuno", "Comida", "Cena", "Merienda"), vm.labels())
        vm.onEvent(MealSlotsEvent.MoveDown(2))
        assertEquals(listOf("Desayuno", "Comida", "Merienda", "Cena"), vm.labels())
        vm.onEvent(MealSlotsEvent.MoveUp(0))
        vm.onEvent(MealSlotsEvent.MoveDown(3))
        assertFalse(vm.form().isDirty)
    }

    @Test
    fun saveFailure_keepsEveryEditAndShowsFailed() = runTest {
        slots.failSave(AppError.Unknown)
        val vm = editor()
        vm.onEvent(MealSlotsEvent.Delete("snack"))
        vm.onEvent(MealSlotsEvent.Rename("lunch"))
        vm.onEvent(MealSlotsEvent.SubmitName("Almuerzo"))

        save(vm)

        assertEquals(MealSlotsSaveError.Failed, vm.form().saveError)
        assertEquals(listOf("Desayuno", "Almuerzo", "Cena"), vm.labels())
        assertTrue(vm.form().canSave)
        assertFalse(vm.form().saved)
        assertFalse(vm.form().isSaving)
    }

    @Test
    fun saveRejectedByServer_showsRejected_keepsEdits_andRefreshesInUse() = runTest {
        slots.failSave(AppError.Validation)
        val vm = editor()
        vm.onEvent(MealSlotsEvent.Delete("snack"))
        slots.inUse = setOf("lunch")

        save(vm)

        assertEquals(MealSlotsSaveError.Rejected, vm.form().saveError)
        assertEquals(3, vm.form().rows.size)
        assertTrue(vm.row("Comida").inUse)
    }

    @Test
    fun saveNetworkFailure_showsOffline_andEditingClearsIt() = runTest {
        slots.failSave(AppError.Network)
        val vm = editor()
        vm.onEvent(MealSlotsEvent.Delete("snack"))
        save(vm)
        assertEquals(MealSlotsSaveError.Offline, vm.form().saveError)

        vm.onEvent(MealSlotsEvent.Delete("dinner"))

        assertEquals(null, vm.form().saveError)
    }

    @Test
    fun offline_blocksSave() = runTest {
        val vm = editor()
        vm.onEvent(MealSlotsEvent.Delete("snack"))
        connectivity.state.value = false
        advanceUntilIdle()

        assertFalse(vm.form().isOnline)
        assertFalse(vm.form().canSave)
        save(vm)
        assertTrue(slots.saved.isEmpty())
    }

    @Test
    fun canNavigateToDefaults_isFalseWhileDirty() = runTest {
        val vm = editor()
        vm.onEvent(MealSlotsEvent.Delete("snack"))

        assertFalse(vm.form().canNavigateToDefaults)
    }

    @Test
    fun recreatedFromSameSavedState_restoresStagedRemovalDraftAndSheet() = runTest {
        val vm = editor()
        vm.onEvent(MealSlotsEvent.Delete("snack"))
        vm.addMeal("Extra")
        vm.onEvent(MealSlotsEvent.Rename("lunch"))
        val before = vm.form()

        val restored = editor(handle)

        assertEquals(before.rows, restored.form().rows)
        assertEquals(MealNameSheet.Rename("lunch", "Comida"), restored.form().sheet)
        assertTrue(restored.form().isDirty)
        restored.onEvent(MealSlotsEvent.DismissSheet)
        save(restored)
        assertEquals(listOf("snack"), slots.savedRemoved.single())
        restored.addMeal("Otra")
        assertEquals(5, restored.form().rows.size)
        assertEquals(restored.form().rows.map { it.key }.toSet().size, restored.form().rows.size)
    }

    @Test
    fun savedDraftFromAnOlderVersion_stillRestores() = runTest {
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
