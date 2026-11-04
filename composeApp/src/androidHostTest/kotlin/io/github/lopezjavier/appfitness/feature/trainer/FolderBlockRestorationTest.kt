package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ADR-40: "Desde una carpeta" survives recreation (a new ViewModel on the same `SavedStateHandle` replays the ops and
 * restores the picker, the discard dialog and a refusal) and the picker search survives a `StateRestorationTester`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class FolderBlockRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val fixture = FolderBlockFixture()
    private val saved = SavedStateHandle()
    private var current by mutableStateOf<FolderBlockViewModel?>(null)

    private fun showRoot() {
        current = fixture.newModel(saved)
        rule.setContent {
            AppTheme {
                FolderBlockRoot("plan-1", "f", {
                }, {}, {}, viewModel = checkNotNull(current), connectivity = FakeConnectivityObserver())
            }
        }
        rule.waitForIdle()
    }

    private fun recreate() {
        current = fixture.newModel(saved)
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun theAppliedOps_surviveRecreation_withTheSameScore() {
        fixture.folder()
        showRoot()
        tap("folder_block_swap_0_0")
        tap("folder_block_pick_chest-iso3")

        recreate()

        rule.onNodeWithTag("folder_block_row_0_0").assertTextContains("Nombre chest-iso3", substring = true)
        rule.onNodeWithTag("folder_block_score").assertTextContains("100", substring = true)
    }

    @Test
    fun anAddedRow_andTheScore_surviveRecreation() {
        fixture.folder(skip = MuscleGroup.Hamstrings)
        showRoot()
        tap("folder_block_add")
        tap("folder_block_routine_option_0")
        tap("folder_block_pick_hamstrings-iso1")

        recreate()

        rule.onNodeWithTag("folder_block_remove_0_5").assertExists()
        rule.onNodeWithTag("folder_block_intro").assertExists()
    }

    @Test
    fun theOpenSwapPicker_survivesRecreation() {
        fixture.folder()
        showRoot()
        tap("folder_block_swap_0_0")

        recreate()

        rule.onNodeWithTag("folder_block_pick_chest-iso3").assertExists()
    }

    @Test
    fun theOpenRoutineChooser_survivesRecreation() {
        fixture.folder(skip = MuscleGroup.Hamstrings)
        showRoot()
        tap("folder_block_add")

        recreate()

        rule.onNodeWithTag("folder_block_routine_option_1").assertExists()
    }

    @Test
    fun theDiscardDialog_survivesRecreation() {
        fixture.folder()
        showRoot()
        tap("folder_block_swap_0_0")
        tap("folder_block_pick_chest-iso3")
        tap("folder_block_back")
        rule.onNodeWithTag("folder_block_discard_dialog").assertExists()

        recreate()

        rule.onNodeWithTag("folder_block_discard_dialog").assertExists()
    }

    @Test
    fun aRefusedFolder_isStillRefusedAfterRecreation() {
        fixture.folder(days = 3)
        showRoot()

        recreate()

        rule.onNodeWithTag("folder_block_refused_note").assertExists()
        rule.onAllNodesWithTag("folder_block_routine_0").assertCountEquals(0)
    }

    @Test
    fun thePickerSearch_survivesStateRestoration() {
        fixture.folder()
        val tester = StateRestorationTester(rule)
        val model = fixture.newModel()
        tester.setContent {
            AppTheme {
                FolderBlockRoot("plan-1", "f", {}, {}, {}, viewModel = model, connectivity = FakeConnectivityObserver())
            }
        }
        rule.waitForIdle()
        tap("folder_block_swap_0_0")
        rule.onNodeWithTag("folder_block_picker_search").performTextInput("iso3")

        tester.emulateSavedInstanceStateRestore()

        rule.onAllNodesWithTag("folder_block_pick_chest-iso2").assertCountEquals(0)
        rule.onNodeWithTag("folder_block_pick_chest-iso3").assertExists()
    }
}
