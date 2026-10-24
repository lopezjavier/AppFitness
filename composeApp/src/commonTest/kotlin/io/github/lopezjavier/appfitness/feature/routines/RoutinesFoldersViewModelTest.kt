package io.github.lopezjavier.appfitness.feature.routines

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.RoutineFolder
import io.github.lopezjavier.appfitness.feature.workout.FakeWorkoutSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutinesFoldersViewModelTest {
    private val repository = FakeRoutineRepository(
        listOf(
            routineOf("r1", "Torso", "a"),
            routineOf("r2", "Pierna", "a"),
            routineOf("r3", "Empuje", "a"),
            routineOf("r4", "Tirón", "a"),
        ),
    )
    private val connectivity = FakeConnectivityObserver()
    private val savedState = SavedStateHandle()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(state: SavedStateHandle = savedState) =
        RoutinesViewModel(repository, FakeWorkoutSessionRepository(), connectivity, state)

    private fun RoutinesViewModel.content() = assertIs<RoutinesUiState.Content>(uiState.value)

    private fun RoutinesViewModel.ids(folderId: String?) =
        content().routines.filter { it.folderId == folderId }.map { it.id }

    private fun withFolder(id: String = "f1", name: String = "Pierna", vararg routineIds: String) {
        repository.folders += RoutineFolder(id, name)
        routineIds.forEach { repository.folderOf[it] = id }
    }

    private suspend fun kotlinx.coroutines.test.TestScope.started(): RoutinesViewModel {
        val vm = viewModel()
        advanceUntilIdle()
        return vm
    }

    // ---- Creating a folder ----

    @Test
    fun creatingAFolder_thenMovingTwoRoutinesIn_groupsThem() = runTest {
        val vm = started()

        vm.onEvent(RoutinesEvent.NewFolder)
        vm.onEvent(RoutinesEvent.FolderNameChanged("  Piernas "))
        vm.onEvent(RoutinesEvent.SaveFolder)
        advanceUntilIdle()
        val folderId = vm.content().folders.single().id
        vm.onEvent(RoutinesEvent.MoveToFolder("r1", folderId))
        vm.onEvent(RoutinesEvent.MoveToFolder("r3", folderId))
        advanceUntilIdle()

        assertEquals(listOf("Piernas"), vm.content().folders.map { it.name })
        assertNull(vm.content().folderSheet)
        val groups = vm.content().groups
        assertEquals(listOf("r1", "r3"), groups[0].routines.map { it.id })
        assertEquals(listOf("r2", "r4"), groups[1].routines.map { it.id })
    }

    @Test
    fun aBlankFolderName_isRejected_andTheSheetStaysOpen() = runTest {
        val vm = started()

        vm.onEvent(RoutinesEvent.NewFolder)
        assertTrue(!vm.content().folderSheet!!.canSave)
        vm.onEvent(RoutinesEvent.FolderNameChanged("   "))
        assertTrue(vm.content().folderSheet!!.showBlankError)
        vm.onEvent(RoutinesEvent.SaveFolder)
        advanceUntilIdle()

        assertTrue(repository.createdFolders.isEmpty())
        assertEquals(FolderSheetMode.Create, vm.content().folderSheet?.mode)
    }

    @Test
    fun dismissingTheFolderSheet_savesNothing() = runTest {
        val vm = started()

        vm.onEvent(RoutinesEvent.NewFolder)
        vm.onEvent(RoutinesEvent.FolderNameChanged("Brazos"))
        vm.onEvent(RoutinesEvent.DismissFolderSheet)
        advanceUntilIdle()

        assertTrue(repository.createdFolders.isEmpty())
        assertNull(vm.content().folderSheet)
    }

    @Test
    fun renamingAFolder_prefillsTheNameAndSaves() = runTest {
        withFolder("f1", "Pierna")
        val vm = started()

        vm.onEvent(RoutinesEvent.RenameFolder("f1"))
        assertEquals("Pierna", vm.content().folderSheet?.name)
        vm.onEvent(RoutinesEvent.FolderNameChanged("Piernas"))
        vm.onEvent(RoutinesEvent.SaveFolder)
        advanceUntilIdle()

        assertEquals(listOf("Piernas"), vm.content().folders.map { it.name })
        assertNull(vm.content().folderSheet)
    }

    @Test
    fun aFolderSaveFailure_keepsTheSheetAndItsDraft_andSendsAMessage() = runTest {
        val vm = started()
        vm.onEvent(RoutinesEvent.NewFolder)
        vm.onEvent(RoutinesEvent.FolderNameChanged("Brazos"))
        repository.saveFailure = AppError.Network

        vm.effects.test {
            vm.onEvent(RoutinesEvent.SaveFolder)
            advanceUntilIdle()
            assertEquals(RoutinesEffect.ActionFailed, awaitItem())
        }
        assertEquals("Brazos", vm.content().folderSheet?.name)
    }

    // ---- Unique folder names ----

    @Test
    fun creatingWithAnExistingName_isRejected_andTheSheetStaysOpen() = runTest {
        withFolder("f1", "Push")
        val vm = started()

        vm.onEvent(RoutinesEvent.NewFolder)
        vm.onEvent(RoutinesEvent.FolderNameChanged("Push"))
        vm.onEvent(RoutinesEvent.SaveFolder)
        advanceUntilIdle()

        assertTrue(vm.content().folderSheet!!.showDuplicateError)
        assertTrue(!vm.content().folderSheet!!.canSave)
        assertTrue(repository.createdFolders.isEmpty())
    }

    @Test
    fun creatingWithAnExistingNameInOtherCaseAndSpaces_isRejected() = runTest {
        withFolder("f1", "Push")
        val vm = started()

        vm.onEvent(RoutinesEvent.NewFolder)
        vm.onEvent(RoutinesEvent.FolderNameChanged("  PUSH "))
        vm.onEvent(RoutinesEvent.SaveFolder)
        advanceUntilIdle()

        assertTrue(vm.content().folderSheet!!.showDuplicateError)
        assertTrue(repository.createdFolders.isEmpty())
    }

    @Test
    fun renamingToAnotherFoldersName_isRejected() = runTest {
        withFolder("f1", "Push")
        withFolder("f2", "Pull")
        val vm = started()

        vm.onEvent(RoutinesEvent.RenameFolder("f2"))
        vm.onEvent(RoutinesEvent.FolderNameChanged("push"))
        vm.onEvent(RoutinesEvent.SaveFolder)
        advanceUntilIdle()

        assertTrue(vm.content().folderSheet!!.showDuplicateError)
        assertEquals(listOf("Push", "Pull"), vm.content().folders.map { it.name })
    }

    @Test
    fun renamingToItsOwnName_orChangingOnlyItsCase_isAllowed() = runTest {
        withFolder("f1", "Push")
        withFolder("f2", "Pull")
        val vm = started()

        vm.onEvent(RoutinesEvent.RenameFolder("f1"))
        assertTrue(vm.content().folderSheet!!.canSave)
        vm.onEvent(RoutinesEvent.FolderNameChanged("PUSH"))
        assertTrue(!vm.content().folderSheet!!.showDuplicateError)
        vm.onEvent(RoutinesEvent.SaveFolder)
        advanceUntilIdle()

        assertEquals(listOf("PUSH", "Pull"), vm.content().folders.map { it.name })
        assertNull(vm.content().folderSheet)
    }

    @Test
    fun typingADifferentName_clearsTheDuplicateError_andSaves() = runTest {
        withFolder("f1", "Push")
        val vm = started()

        vm.onEvent(RoutinesEvent.NewFolder)
        vm.onEvent(RoutinesEvent.FolderNameChanged("Push"))
        vm.onEvent(RoutinesEvent.FolderNameChanged("Legs"))
        assertTrue(!vm.content().folderSheet!!.showDuplicateError)
        vm.onEvent(RoutinesEvent.SaveFolder)
        advanceUntilIdle()

        assertEquals(listOf("Legs"), repository.createdFolders)
    }

    @Test
    fun aRestoredSheetWithADuplicateName_stillShowsTheError() = runTest {
        withFolder("f1", "Push")
        val first = started()
        first.onEvent(RoutinesEvent.NewFolder)
        first.onEvent(RoutinesEvent.FolderNameChanged("push"))

        val second = viewModel(savedState)
        advanceUntilIdle()

        assertTrue(second.content().folderSheet!!.showDuplicateError)
    }

    // ---- Deleting a folder ----

    @Test
    fun deletingAFolderWithThreeRoutines_confirmsWithTheCount_thenDeletesAllOfThem() = runTest {
        withFolder("f1", "Pierna", "r1", "r2", "r3")
        withFolder("f2", "Empuje", "r4")
        val vm = started()
        vm.onEvent(RoutinesEvent.ToggleFolder("f1")) // collapsed: its routines are not on screen

        vm.onEvent(RoutinesEvent.RequestDeleteFolder("f1"))
        assertEquals("f1", vm.content().deleteFolderFor?.id)
        assertEquals(3, vm.content().deleteFolderRoutineCount)
        assertTrue(repository.deletedFolders.isEmpty())

        vm.onEvent(RoutinesEvent.ConfirmDeleteFolder)
        advanceUntilIdle()

        assertEquals(listOf("f1"), repository.deletedFolders)
        assertEquals(listOf("f2"), vm.content().folders.map { it.id })
        assertEquals(listOf("r4"), vm.content().routines.map { it.id })
        assertNull(vm.content().deleteFolderFor)
        assertTrue(vm.content().collapsedFolderIds.isEmpty())
    }

    @Test
    fun cancellingTheFolderDeletion_deletesNothing() = runTest {
        withFolder("f1", "Pierna", "r1", "r2")
        val vm = started()

        vm.onEvent(RoutinesEvent.RequestDeleteFolder("f1"))
        vm.onEvent(RoutinesEvent.DismissDeleteFolder)
        advanceUntilIdle()

        assertTrue(repository.deletedFolders.isEmpty())
        assertNull(vm.content().deleteFolderFor)
        assertEquals(4, vm.content().routines.size)
    }

    @Test
    fun anEmptyFolder_isDeletedAtOnceWithoutConfirmation() = runTest {
        withFolder("f1", "Vacía")
        val vm = started()

        vm.onEvent(RoutinesEvent.RequestDeleteFolder("f1"))
        advanceUntilIdle()

        assertNull(vm.content().deleteFolderFor)
        assertEquals(listOf("f1"), repository.deletedFolders)
        assertTrue(vm.content().folders.isEmpty())
    }

    @Test
    fun aFolderDeletionFailure_keepsEverything_andSendsAMessage() = runTest {
        withFolder("f1", "Pierna", "r1")
        val vm = started()
        vm.onEvent(RoutinesEvent.RequestDeleteFolder("f1"))
        repository.saveFailure = AppError.Network

        vm.effects.test {
            vm.onEvent(RoutinesEvent.ConfirmDeleteFolder)
            advanceUntilIdle()
            assertEquals(RoutinesEffect.ActionFailed, awaitItem())
        }
        assertEquals(1, vm.content().folders.size)
        assertEquals(4, vm.content().routines.size)
    }

    @Test
    fun foldersWithoutRoutines_areNotTheEmptyState() = runTest {
        repository.routines.clear()
        withFolder("f1", "Pierna")
        val vm = started()

        assertTrue(!vm.content().isEmpty)
    }

    // ---- Move ----

    @Test
    fun moveSheet_opensForARoutine_andMovingToNoFolderClosesIt() = runTest {
        withFolder("f1", "Pierna", "r1")
        val vm = started()

        vm.onEvent(RoutinesEvent.OpenMove("r1"))
        assertEquals("r1", vm.content().moveFor?.id)
        vm.onEvent(RoutinesEvent.MoveToFolder("r1", null))
        advanceUntilIdle()

        assertNull(vm.content().moveFor)
        assertEquals(listOf<Pair<String, String?>>("r1" to null), repository.movedToFolder)
        assertNull(vm.content().routines.last().folderId)
    }

    // ---- Next workout ----

    @Test
    fun setNext_marksOne_anotherMovesIt_andClearRemovesIt() = runTest {
        val vm = started()

        vm.onEvent(RoutinesEvent.SetNext("r1"))
        advanceUntilIdle()
        assertEquals(listOf("r1"), vm.content().routines.filter { it.isNext }.map { it.id })

        vm.onEvent(RoutinesEvent.SetNext("r3"))
        advanceUntilIdle()
        assertEquals(listOf("r3"), vm.content().routines.filter { it.isNext }.map { it.id })
        assertEquals("r3", repository.nextId)

        vm.onEvent(RoutinesEvent.ClearNext)
        advanceUntilIdle()
        assertTrue(vm.content().routines.none { it.isNext })
        assertEquals(listOf("r1", "r3", null), repository.nextCalls)
    }

    @Test
    fun setNext_appliesAtOnce_andRevertsWithAMessageOnFailure() = runTest {
        val vm = started()
        repository.saveFailure = AppError.Network

        vm.effects.test {
            vm.onEvent(RoutinesEvent.SetNext("r1"))
            assertTrue(vm.content().routines.first { it.id == "r1" }.isNext)
            advanceUntilIdle()
            assertEquals(RoutinesEffect.ActionFailed, awaitItem())
        }
        assertTrue(vm.content().routines.none { it.isNext })
    }

    // ---- Order ----

    @Test
    fun aDragResult_isShownAndPersistedWithTheOrderedIds() = runTest {
        val vm = started()

        vm.onEvent(RoutinesEvent.Reorder(null, listOf("r4", "r3", "r2", "r1")))
        assertEquals(listOf("r4", "r3", "r2", "r1"), vm.ids(null))
        advanceUntilIdle()

        assertEquals(
            listOf<Pair<String?, List<String>>>(null to listOf("r4", "r3", "r2", "r1")),
            repository.savedOrders,
        )
    }

    @Test
    fun reordering_oneGroup_leavesTheOtherGroupsUntouched() = runTest {
        withFolder("f1", "Pierna", "r1", "r2")
        val vm = started()

        vm.onEvent(RoutinesEvent.Reorder("f1", listOf("r2", "r1")))
        advanceUntilIdle()

        assertEquals(listOf("r2", "r1"), vm.ids("f1"))
        assertEquals(listOf("r3", "r4"), vm.ids(null))
        assertEquals("f1", repository.savedOrders.single().first)
    }

    @Test
    fun moveUpAndDown_swapWithTheNeighbourInTheSameGroup() = runTest {
        val vm = started()

        vm.onEvent(RoutinesEvent.MoveDown("r1"))
        advanceUntilIdle()
        assertEquals(listOf("r2", "r1", "r3", "r4"), vm.ids(null))

        vm.onEvent(RoutinesEvent.MoveUp("r4"))
        advanceUntilIdle()
        assertEquals(listOf("r2", "r1", "r4", "r3"), vm.ids(null))
        assertEquals(2, repository.savedOrders.size)
    }

    @Test
    fun moveUpOnTheFirstAndMoveDownOnTheLast_doNothing() = runTest {
        val vm = started()

        vm.onEvent(RoutinesEvent.MoveUp("r1"))
        vm.onEvent(RoutinesEvent.MoveDown("r4"))
        advanceUntilIdle()

        assertTrue(repository.savedOrders.isEmpty())
        assertEquals(listOf("r1", "r2", "r3", "r4"), vm.ids(null))
    }

    @Test
    fun anOrderFailure_revertsTheList_andSendsAMessage() = runTest {
        val vm = started()
        repository.saveFailure = AppError.Network

        vm.effects.test {
            vm.onEvent(RoutinesEvent.MoveDown("r1"))
            assertEquals(listOf("r2", "r1", "r3", "r4"), vm.ids(null))
            advanceUntilIdle()
            assertEquals(RoutinesEffect.ActionFailed, awaitItem())
        }
        assertEquals(listOf("r1", "r2", "r3", "r4"), vm.ids(null))
    }

    @Test
    fun aDuplicatedRoutine_appearsAtTheEndOfItsGroup() = runTest {
        val vm = started()

        vm.onEvent(RoutinesEvent.Duplicate("r1"))
        advanceUntilIdle()

        assertEquals("Torso (copia)", vm.content().routines.last().name)
    }

    // ---- Offline ----

    @Test
    fun offline_blocksEveryFolderOrderAndNextWrite() = runTest {
        withFolder("f1", "Pierna", "r1")
        withFolder("f2", "Vacía")
        val vm = started()
        connectivity.state.value = false
        advanceUntilIdle()

        vm.onEvent(RoutinesEvent.NewFolder)
        vm.onEvent(RoutinesEvent.RenameFolder("f1"))
        vm.onEvent(RoutinesEvent.RequestDeleteFolder("f1"))
        vm.onEvent(RoutinesEvent.RequestDeleteFolder("f2"))
        vm.onEvent(RoutinesEvent.OpenMove("r2"))
        vm.onEvent(RoutinesEvent.MoveToFolder("r2", "f1"))
        vm.onEvent(RoutinesEvent.SetNext("r2"))
        vm.onEvent(RoutinesEvent.MoveDown("r2"))
        vm.onEvent(RoutinesEvent.Reorder(null, listOf("r4", "r3", "r2")))
        advanceUntilIdle()

        val content = vm.content()
        assertNull(content.folderSheet)
        assertNull(content.deleteFolderFor)
        assertNull(content.moveFor)
        assertTrue(repository.deletedFolders.isEmpty())
        assertTrue(repository.movedToFolder.isEmpty())
        assertTrue(repository.nextCalls.isEmpty())
        assertTrue(repository.savedOrders.isEmpty())
        assertEquals(listOf("r2", "r3", "r4"), vm.ids(null))
    }

    @Test
    fun offline_collapsingAFolder_stillWorks() = runTest {
        withFolder("f1", "Pierna", "r1")
        val vm = started()
        connectivity.state.value = false
        advanceUntilIdle()

        vm.onEvent(RoutinesEvent.ToggleFolder("f1"))

        assertEquals(setOf("f1"), vm.content().collapsedFolderIds)
    }

    // ---- Restoration (ADR-40) ----

    @Test
    fun folderSheetDraftAndCollapsedFolders_surviveARecreatedViewModel() = runTest {
        withFolder("f1", "Pierna", "r1")
        val first = started()
        first.onEvent(RoutinesEvent.ToggleFolder("f1"))
        first.onEvent(RoutinesEvent.NewFolder)
        first.onEvent(RoutinesEvent.FolderNameChanged("Brazos"))

        val second = viewModel(savedState)
        advanceUntilIdle()

        assertEquals(setOf("f1"), second.content().collapsedFolderIds)
        assertEquals(
            FolderSheetState(FolderSheetMode.Create, null, "Brazos", edited = true),
            second.content().folderSheet,
        )
    }

    @Test
    fun aRenameSheet_isRestoredWithItsFolderAndMode() = runTest {
        withFolder("f1", "Pierna")
        val first = started()
        first.onEvent(RoutinesEvent.RenameFolder("f1"))
        first.onEvent(RoutinesEvent.FolderNameChanged("Piernas"))

        val second = viewModel(savedState)
        advanceUntilIdle()

        assertEquals(FolderSheetMode.Rename, second.content().folderSheet?.mode)
        assertEquals("f1", second.content().folderSheet?.folderId)
        assertEquals("Piernas", second.content().folderSheet?.name)
    }

    @Test
    fun aClosedSheet_isNotRestored() = runTest {
        val first = started()
        first.onEvent(RoutinesEvent.NewFolder)
        first.onEvent(RoutinesEvent.FolderNameChanged("Brazos"))
        first.onEvent(RoutinesEvent.DismissFolderSheet)

        val second = viewModel(savedState)
        advanceUntilIdle()

        assertNull(second.content().folderSheet)
    }
}
