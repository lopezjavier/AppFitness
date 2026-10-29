package io.github.lopezjavier.appfitness.feature.workout

import androidx.activity.ComponentDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseSummary
import io.github.lopezjavier.appfitness.domain.usecase.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

private val SUMMARY = LiveSummary(
    SessionSummary(60, 1, listOf(ExerciseSummary(0, "e1", 100.0, false))),
    mapOf(0 to "Press banca"),
)

private fun sheet(
    summary: RoutineChangeSummary = RoutineChangeSummary(setsAdded = 2, restsChanged = 1),
    loading: Boolean = false,
    failed: Boolean = false,
) = UpdateRoutineSheet("s1", summary, loading, failed)

/** US-36 "Actualizar rutina" sheet over the summary of a finished session. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class LiveSessionUpdateRoutineScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<LiveSessionEvent>()

    private fun show(state: LiveSessionUiState) {
        rule.setContent {
            AppTheme { LiveSessionScreen(state, 0, { events += it }, {}, onOpenExercise = {}) }
        }
        rule.waitForIdle()
    }

    /** The sheet lives in its own dialog window: back goes to the dialog, not to the activity. */
    private fun pressBack() {
        rule.runOnUiThread {
            (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed()
        }
        rule.waitForIdle()
    }

    @Test
    fun sameValues_showsNoSheet() {
        show(LiveSessionUiState.Finished(SUMMARY, updateRoutineSheet = null))

        rule.onNodeWithTag("summary_screen").assertExists()
        rule.onNodeWithTag("live_updateRoutine_sheet").assertDoesNotExist()
    }

    @Test
    fun changedValues_showsTheTitleSummaryAndBothButtons() {
        show(LiveSessionUiState.Finished(SUMMARY, sheet()))

        rule.onNodeWithTag("live_updateRoutine_sheet").assertExists()
        rule.onNodeWithText("2 series añadidas").assertExists()
        rule.onNodeWithText("1 descanso modificado").assertExists()
        rule.onNodeWithTag("live_updateRoutine_update").assertIsSelected()
        rule.onNodeWithTag("live_updateRoutine_keep").assertIsNotSelected()
        rule.onNodeWithTag("live_updateRoutine_sheet_commit").assertIsEnabled().assertTextEquals("Guardar")
        rule.onNodeWithText("Mantener la rutina").assertExists()
        rule.onNodeWithTag("live_updateRoutine_error").assertDoesNotExist()
    }

    @Test
    fun changedRoutineNotes_showTheirOwnSummaryLine() {
        show(LiveSessionUiState.Finished(SUMMARY, sheet(RoutineChangeSummary(notesChanged = 2))))

        rule.onNodeWithText("2 notas modificadas").assertExists()
    }

    @Test
    fun choicesAndCommit_have48dpTargets() {
        show(LiveSessionUiState.Finished(SUMMARY, sheet()))

        rule.onNodeWithTag("live_updateRoutine_update").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("live_updateRoutine_keep").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("live_updateRoutine_sheet_commit").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun choosingARow_appliesNothingUntilTheCommit() {
        show(LiveSessionUiState.Finished(SUMMARY, sheet()))

        rule.onNodeWithTag("live_updateRoutine_keep").performClick()

        rule.onNodeWithTag("live_updateRoutine_keep").assertIsSelected()
        assertTrue(events.isEmpty())
    }

    @Test
    fun commitWithTheDefaultChoice_sendsUpdateRoutine() {
        show(LiveSessionUiState.Finished(SUMMARY, sheet()))

        rule.onNodeWithTag("live_updateRoutine_sheet_commit").performClick()

        assertEquals(listOf<LiveSessionEvent>(LiveSessionEvent.UpdateRoutine), events)
    }

    @Test
    fun commitAfterChoosingMantener_sendsKeepRoutine_andNeverUpdate() {
        show(LiveSessionUiState.Finished(SUMMARY, sheet()))

        rule.onNodeWithTag("live_updateRoutine_keep").performClick()
        rule.onNodeWithTag("live_updateRoutine_sheet_commit").performClick()

        assertEquals(listOf<LiveSessionEvent>(LiveSessionEvent.KeepRoutine), events)
    }

    @Test
    fun back_dismissesAsKeepRoutine_andNeverUpdate() {
        show(LiveSessionUiState.Finished(SUMMARY, sheet()))

        pressBack()

        assertEquals(listOf<LiveSessionEvent>(LiveSessionEvent.KeepRoutine), events)
    }

    @Test
    fun whileLoading_buttonsAreDisabled_andBackIsIgnored() {
        show(LiveSessionUiState.Finished(SUMMARY, sheet(loading = true)))

        rule.onNodeWithTag("live_updateRoutine_sheet_commit").assertIsNotEnabled()
        pressBack()

        assertTrue(events.isEmpty())
    }

    @Test
    fun failedUpdate_showsTheErrorLine_withButtonsEnabled() {
        show(LiveSessionUiState.Finished(SUMMARY, sheet(failed = true)))

        rule.onNodeWithTag("live_updateRoutine_error").assertExists()
        rule.onNodeWithText("No se pudo actualizar la rutina. Inténtalo de nuevo.").assertExists()
        rule.onNodeWithTag("live_updateRoutine_sheet_commit").assertIsEnabled()
    }

    @Test
    fun manyChanges_foldTheRestIntoAMoreLine() {
        val all = RoutineChangeSummary(
            setsAdded = 1,
            setsRemoved = 2,
            setsChanged = 3,
            restsChanged = 4,
            exercisesAdded = 1,
            orderOrSupersetChanged = true,
        )
        show(LiveSessionUiState.Finished(SUMMARY, sheet(all)))

        rule.onNodeWithText("1 serie añadida").assertExists()
        rule.onNodeWithText("3 series modificadas").assertExists()
        rule.onNodeWithText("4 descansos modificados").assertDoesNotExist()
        rule.onNodeWithText("y 6 cambios más").assertExists()
    }

    @Test
    fun orderOrSupersetChange_hasItsOwnLine() {
        show(LiveSessionUiState.Finished(SUMMARY, sheet(RoutineChangeSummary(orderOrSupersetChanged = true))))

        rule.onNodeWithText("Orden o superseries modificados").assertExists()
    }

    @Test
    fun theSheet_comesBackWithItsSummary_afterRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            val saved = rememberSaveable { mutableStateOf(sheet().encoded()) }
            val restored = decodeUpdateRoutineSheet(saved.value)
            AppTheme {
                LiveSessionScreen(
                    LiveSessionUiState.Finished(SUMMARY, restored),
                    0,
                    { events += it },
                    {},
                    onOpenExercise = {},
                )
            }
        }
        rule.onNodeWithText("2 series añadidas").assertExists()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("live_updateRoutine_sheet").assertExists()
        rule.onNodeWithText("2 series añadidas").assertExists()
        rule.onNodeWithText("1 descanso modificado").assertExists()
    }
}
