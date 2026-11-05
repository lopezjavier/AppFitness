package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-93 "Desde una carpeta": one test per Gherkin scenario, over the real ViewModel and fakes. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One scenario per test.
class FolderBlockScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val fixture = FolderBlockFixture()
    private val connectivity = FakeConnectivityObserver()
    private var saves = 0
    private var closes = 0
    private val opened = mutableListOf<String>()

    private fun show() {
        val model = fixture.newModel()
        rule.setContent {
            AppTheme {
                FolderBlockRoot(
                    planId = "plan-1",
                    folderId = "f",
                    onSave = { saves++ },
                    onClose = { closes++ },
                    onOpenExercise = { opened += it },
                    viewModel = model,
                    connectivity = connectivity,
                )
            }
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun assertMissing(tag: String) = rule.onAllNodesWithTag(tag).assertCountEquals(0)

    @Test
    fun aFolderAt100Percent_savesAsANewFolder_andTheSourceIsNeverWritten() {
        fixture.folder()
        show()

        assertMissing("folder_block_intro")
        assertMissing("folder_block_save_helper")
        rule.onNodeWithTag("folder_block_score").assertTextContains("100", substring = true)
        rule.onNodeWithTag("folder_block_save").assertIsEnabled()
        tap("folder_block_save")

        assertEquals("Fuerza · bloque", fixture.blocks.builtSaves.single().third)
        assertEquals(1, saves)
        assertTrue(fixture.routines.saved.isEmpty())
        assertTrue(fixture.routines.renamedFolders.isEmpty())
    }

    @Test
    fun aFolderNameInUse_getsTheNextFreeSuffix() {
        fixture.folder(existingBlock = true)
        show()

        tap("folder_block_save")

        assertEquals("Fuerza · bloque 2", fixture.blocks.builtSaves.single().third)
    }

    @Test
    fun aRoutineCountThatDiffersFromTheDays_isRefusedNamingBothNumbers() {
        fixture.folder(days = 3)
        show()

        rule.onNodeWithTag("folder_block_refused_note").assertTextContains(
            "Tu carpeta tiene 2 rutinas de fuerza y entrenas 3 días a la semana.",
            substring = true,
        )
        rule.onNodeWithTag("folder_block_save").assertIsNotEnabled()
        assertMissing("folder_block_routine_0")
    }

    @Test
    fun aOneRoutineFolderWithTwoDays_asksForASecondRoutine() {
        fixture.folder(days = 2, routineCount = 1)
        show()

        rule.onNodeWithTag("folder_block_refused_note").assertTextContains(
            "Tu carpeta tiene 1 rutina de fuerza y entrenas 2 días a la semana. Añade otra rutina",
            substring = true,
        )
        rule.onNodeWithTag("folder_block_save").assertIsNotEnabled()
    }

    @Test
    fun fixingAFolderOnACopy_addsAnExerciseRaisingTheScoreUntilItCanBeSaved() {
        fixture.folder(skip = MuscleGroup.Hamstrings)
        show()

        rule.onNodeWithTag("folder_block_intro").assertTextContains("Tu carpeta está al ", substring = true)
        rule.onNodeWithTag("folder_block_intro").assertTextContains(" %. Complétala para guardarla", substring = true)
        rule.onNodeWithTag("folder_block_save").assertIsNotEnabled()
        rule.onNodeWithTag("folder_block_save_helper").assertTextContains("al 100 %", substring = true)
        rule.onNodeWithTag("folder_block_missing_row_hamstrings").assertExists()
        rule.onNodeWithTag("folder_block_missing_row_hamstrings_help").assertExists()

        addHamstringsTo(0)
        rule.onNodeWithTag("folder_block_save").assertIsNotEnabled()
        addHamstringsTo(1)

        assertMissing("folder_block_intro")
        rule.onNodeWithTag("folder_block_save").assertIsEnabled()
        tap("folder_block_save")
        assertEquals(1, fixture.blocks.builtSaves.size)
        assertTrue(fixture.routines.saved.isEmpty())
    }

    private fun addHamstringsTo(routine: Int) {
        tap("folder_block_add")
        if (routine >= 0) tap("folder_block_routine_option_$routine")
        tap("folder_block_pick_hamstrings-iso1")
    }

    @Test
    fun anAddedRow_canBeRemoved_andTheFolderOwnRowsCannot() {
        fixture.folder(skip = MuscleGroup.Hamstrings)
        show()

        addHamstringsTo(0)
        assertMissing("folder_block_remove_0_0")
        rule.onNodeWithTag("folder_block_remove_0_5").assertExists()
        tap("folder_block_remove_0_5")

        assertMissing("folder_block_remove_0_5")
        rule.onNodeWithTag("folder_block_intro").assertExists()
    }

    @Test
    fun swappingARow_listsTheSameGroupAndKeepsTheFolderAt100() {
        fixture.folder()
        show()

        tap("folder_block_swap_0_0")
        tap("folder_block_pick_chest-iso3")

        rule.onNodeWithTag("folder_block_row_0_0").assertTextContains("Nombre chest-iso3", substring = true)
        rule.onNodeWithTag("folder_block_save").assertIsEnabled()
    }

    @Test
    fun theRowName_opensTheExerciseDetails() {
        fixture.folder()
        show()

        tap("folder_block_row_0_0")

        assertEquals(listOf("chest-iso1"), opened)
    }

    @Test
    fun aStrengthMainLiftAbove8Reps_offersTheRepFixAndApplyingItRemovesTheAction() {
        fixture.folder(intention = TrainingIntention.Strength, mainChest = true)
        show()
        val tag = "folder_block_rep_fix_0_${fixture.chestIndex}"

        rule.onNodeWithTag(tag).assertTextContains("Cambiar a 6-8 repeticiones", substring = true)
        tap(tag)

        assertMissing(tag)
    }

    @Test
    fun anExerciseOverTenSets_blocksTheSaveWithANote() {
        fixture.folder(overSets = true)
        show()

        rule.onNodeWithTag("folder_block_sets_over_note")
            .assertTextContains("Tiene más de 10 series: quita series o el ejercicio en la carpeta.", substring = true)
        rule.onNodeWithTag("folder_block_save").assertIsNotEnabled()
    }

    @Test
    fun routinesWithoutLiftingExercises_areListedAsLeftOut() {
        fixture.folder(stretchRoutine = true)
        show()

        rule.onNodeWithTag("folder_block_left_out_note").assertTextContains("Rutina s", substring = true)
        rule.onNodeWithTag("folder_block_save").assertIsEnabled()
        assertMissing("folder_block_routine_2")
    }

    @Test
    fun leavingWithoutEdits_closesSilently() {
        fixture.folder()
        show()

        tap("folder_block_back")

        assertEquals(1, closes)
        assertMissing("folder_block_discard_dialog")
    }

    @Test
    fun leavingAfterEdits_asksToDiscard_andNothingIsSaved() {
        fixture.folder()
        show()
        tap("folder_block_swap_0_0")
        tap("folder_block_pick_chest-iso3")

        tap("folder_block_back")
        rule.onNodeWithTag("folder_block_discard_dialog").assertExists()
        tap("folder_block_discard_confirm")

        assertEquals(1, closes)
        assertTrue(fixture.blocks.builtSaves.isEmpty())
        assertTrue(fixture.routines.saved.isEmpty())
    }

    @Test
    fun keepEditing_closesTheDialogAndStaysOnTheScreen() {
        fixture.folder()
        show()
        tap("folder_block_swap_0_0")
        tap("folder_block_pick_chest-iso3")
        tap("folder_block_back")

        tap("folder_block_discard_dialog_cancel")

        assertMissing("folder_block_discard_dialog")
        assertEquals(0, closes)
    }

    @Test
    fun offline_theSaveStaysDisabled() {
        fixture.folder()
        connectivity.state.value = false
        show()

        rule.onNodeWithTag("folder_block_save").assertIsNotEnabled()
    }

    @Test
    fun aFolderWithNoRoutines_showsTheEmptyStateAndADisabledSave() {
        fixture.folder(routineCount = 0)
        show()

        rule.onNodeWithTag("folder_block_empty").assertExists()
        rule.onNodeWithTag("folder_block_save").assertIsNotEnabled()
    }
}
