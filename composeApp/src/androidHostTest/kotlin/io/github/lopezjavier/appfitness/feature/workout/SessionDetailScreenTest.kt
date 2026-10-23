package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.usecase.SessionStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

private fun detail(
    isOnline: Boolean = true,
    volumeKg: Double = 2_120.0,
    edit: EditSetState? = null,
    busy: Boolean = false,
    firstRecord: Boolean = true,
) = SessionDetailUiState.Content(
    name = "Torso",
    label = DateLabel.Yesterday,
    stats = SessionStats(durationSeconds = 3_725, completedSets = 3, volumeKg = volumeKg),
    exercises = listOf(
        DetailExercise(
            position = 0,
            exerciseId = "e1",
            name = "Press banca",
            sets = listOf(
                DetailSet(1, SetType.Working, "60 kg × 12"),
                DetailSet(2, SetType.Working, "87,5 kg × 8"),
            ),
            isPersonalRecord = firstRecord,
        ),
        DetailExercise(1, "e2", "Remo", listOf(DetailSet(1, SetType.Working, "87,5 kg × 8")), isPersonalRecord = false),
    ),
    isOnline = isOnline,
    edit = edit,
    busy = busy,
)

/** US-14 session detail: exercises, sets, records, correcting a set and deleting the session. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class SessionDetailScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<SessionDetailEvent>()
    private val snackbarHost = SnackbarHostState()

    private fun show(state: SessionDetailUiState) {
        rule.setContent { AppTheme { SessionDetailScreen(state, { events += it }, snackbarHost = snackbarHost) } }
        rule.waitForIdle()
    }

    @Test
    fun progressLink_opensTheProgressOfThatExercise() {
        val opened = mutableListOf<String>()
        rule.setContent {
            AppTheme {
                SessionDetailScreen(detail(), { events += it }, snackbarHost = snackbarHost, onOpenProgress = {
                    opened +=
                        it
                })
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("session_detail_list").performScrollToNode(hasTestTag("session_detail_progress_e2"))
        rule.onNodeWithTag("session_detail_progress_e2").performClick()

        assertEquals(listOf("e2"), opened)
    }

    @Test
    fun content_showsHeaderStats_exercises_andSets() {
        show(detail())

        rule.onNodeWithTag("session_detail_screen").assertExists()
        rule.onNodeWithTag("session_detail_duration").assertTextContains("1:02:05")
        rule.onNodeWithTag("session_detail_sets").assertTextContains("3")
        rule.onNodeWithTag("session_detail_volume").assertTextContains("2.120 kg")
        rule.onNodeWithText("Press banca").assertExists()
        rule.onNodeWithTag("session_detail_set_0_1").assertTextContains("60 kg × 12")
        rule.onNodeWithTag("session_detail_set_0_2").assertTextContains("87,5 kg × 8")
    }

    @Test
    fun personalRecord_isMarkedOnlyOnTheRecordExercise_withAnAccessibleLabel() {
        show(detail())

        rule.onNodeWithTag("session_detail_pr_0").assert(hasContentDescription("Récord personal"))
        rule.onNodeWithTag("session_detail_pr_1").assertDoesNotExist()
    }

    @Test
    fun loading_showsSkeleton() {
        show(SessionDetailUiState.Loading)

        rule.onNodeWithTag("session_detail_loading").assertExists()
    }

    @Test
    fun error_showsMessage_andRetryEmitsRetry() {
        show(SessionDetailUiState.Error)

        rule.onNodeWithText("No se pudo cargar el entreno").assertExists()
        rule.onNodeWithTag("session_detail_error_action").performClick()

        assertEquals(listOf<SessionDetailEvent>(SessionDetailEvent.Retry), events)
    }

    @Test
    fun tappingASet_emitsEditSet() {
        show(detail())

        rule.onNodeWithTag("session_detail_set_0_2").performClick()

        assertEquals(listOf<SessionDetailEvent>(SessionDetailEvent.EditSet(0, 2)), events)
    }

    @Test
    fun editSheet_isPrefilled_andSaveEmitsSaveSet() {
        show(detail(edit = EditSetState(0, 2, weight = "87,5", reps = "8")))

        rule.onNodeWithTag("session_edit_weight").assertTextContains("87,5")
        rule.onNodeWithTag("session_edit_reps").assertTextContains("8")
        rule.onNodeWithTag("session_edit_save").performClick()

        assertEquals(listOf<SessionDetailEvent>(SessionDetailEvent.SaveSet), events)
    }

    @Test
    fun editSheet_changingReps_emitsTheTypedText() {
        show(detail(edit = EditSetState(0, 2, weight = "87,5", reps = "8")))

        rule.onNodeWithTag("session_edit_reps").performTextClearance()
        rule.onNodeWithTag("session_edit_reps").performTextInput("7")

        assertEquals(SessionDetailEvent.RepsChanged("7"), events.last())
    }

    @Test
    fun editSheet_bothFieldsHaveUsableWidth() {
        show(detail(edit = EditSetState(0, 2, weight = "87,5", reps = "8")))

        assertBothFieldsUsable()
    }

    @Test
    fun editSheet_labelsSitInsideTheirFields_at360dp() {
        show(detail(edit = EditSetState(0, 2, weight = "87,5", reps = "8")))

        assertLabelsSitInsideTheirFields()
    }

    @Test
    fun editSheet_atLargestFontScale_fieldsAndLabelsStayUsable() {
        showScaled(2f)

        assertBothFieldsUsable()
        assertLabelsSitInsideTheirFields()
    }

    @Test
    @Config(qualifiers = "w720dp-h1000dp-xxhdpi")
    fun editSheet_atTabletWidth_fieldsAndLabelsStayUsable() {
        show(detail(edit = EditSetState(0, 2, weight = "87,5", reps = "8")))

        assertBothFieldsUsable()
        assertLabelsSitInsideTheirFields()
    }

    @Test
    fun editSheet_tappingReps_thenTyping_emitsTheTypedText() {
        show(detail(edit = EditSetState(0, 2, weight = "87,5", reps = "8")))

        rule.onNodeWithTag("session_edit_reps").performClick()
        rule.onNodeWithTag("session_edit_reps").performTextClearance()
        rule.onNodeWithTag("session_edit_reps").performTextInput("7")

        assertEquals(SessionDetailEvent.RepsChanged("7"), events.last())
    }

    private fun showScaled(fontScale: Float) {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                AppTheme {
                    SessionDetailScreen(
                        detail(edit = EditSetState(0, 2, weight = "87,5", reps = "8")),
                        { events += it },
                        snackbarHost = snackbarHost,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun assertBothFieldsUsable() {
        val weight = rule.onNodeWithTag("session_edit_weight").getBoundsInRoot()
        val reps = rule.onNodeWithTag("session_edit_reps").getBoundsInRoot()
        listOf(weight, reps).forEach {
            assertTrue("field too narrow: ${it.width}", it.width >= 48.dp)
            assertTrue("field too short: ${it.height}", it.height >= 48.dp)
        }
        assertTrue("fields differ in width", abs((weight.width - reps.width).value) <= 4f)
    }

    /** Robolectric measures glyphs unreliably, so the label is checked to sit inside its own, wide field. */
    private fun assertLabelsSitInsideTheirFields() {
        mapOf("Kilos" to "session_edit_weight", "Repeticiones" to "session_edit_reps").forEach { (label, tag) ->
            val field = rule.onNodeWithTag(tag).getBoundsInRoot()
            val text = rule.onNodeWithText(label, ignoreCase = true, useUnmergedTree = true).getBoundsInRoot()
            assertTrue("label '$label' starts outside its field", text.left >= field.left)
            assertTrue("label '$label' ends outside its field", text.right <= field.right)
            assertTrue("label '$label' is not above the input", text.bottom <= field.bottom)
        }
    }

    @Test
    fun editSheet_invalidDraft_showsInlineError() {
        show(detail(edit = EditSetState(0, 2, weight = "87,5", reps = "", invalid = true)))

        rule.onNodeWithTag("session_edit_invalid").assertExists()
    }

    @Test
    fun editSheet_cancelEmitsDismissEdit() {
        show(detail(edit = EditSetState(0, 2, weight = "87,5", reps = "8")))

        rule.onNodeWithTag("session_edit_cancel").performClick()

        assertEquals(listOf<SessionDetailEvent>(SessionDetailEvent.DismissEdit), events)
    }

    @Test
    fun recalculatedVolume_isShownFromTheState() {
        show(detail(volumeKg = 2_032.5))

        rule.onNodeWithTag("session_detail_volume").assertTextContains("2.033 kg")
    }

    @Test
    fun saveError_showsTheMessageInASnackbar_andTheSheetStaysOpen() {
        rule.setContent {
            AppTheme {
                LaunchedEffect(Unit) {
                    snackbarHost.showSnackbar("No se pudo completar la acción. Inténtalo de nuevo.")
                }
                SessionDetailScreen(
                    detail(edit = EditSetState(0, 2, weight = "87,5", reps = "7")),
                    { events += it },
                    snackbarHost = snackbarHost,
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("No se pudo completar la acción. Inténtalo de nuevo.").assertExists()
        rule.onNodeWithTag("session_edit_reps").assertTextContains("7")
    }

    @Test
    fun deleteButton_isAtTheEndOfTheScroll_andDeletesAtOnceWithoutADialog() {
        show(detail())

        rule.onNodeWithTag("session_detail_list").performScrollToNode(hasTestTag("session_detail_delete"))
        rule.onNodeWithTag("session_detail_delete").assertTextContains("Eliminar sesión")
        rule.onNodeWithTag("session_detail_delete").performClick()

        assertEquals(listOf<SessionDetailEvent>(SessionDetailEvent.Delete), events)
        rule.onNodeWithTag("session_detail_delete_confirm").assertDoesNotExist()
    }

    @Test
    fun offline_disablesEditAndDelete_andShowsTheReason() {
        show(detail(isOnline = false))

        rule.onNodeWithTag("session_detail_list").performScrollToNode(hasTestTag("session_detail_delete"))
        rule.onNodeWithTag("session_detail_delete").assertIsNotEnabled()
        rule.onNodeWithTag("session_detail_set_0_2").assertIsNotEnabled()
        rule.onNodeWithTag("session_detail_offline_reason")
            .assertTextContains("Sin conexión: no puedes corregir ni eliminar sesiones")
    }

    @Test
    fun online_enablesEditAndDelete_withoutReason() {
        show(detail())

        rule.onNodeWithTag("session_detail_list").performScrollToNode(hasTestTag("session_detail_delete"))
        rule.onNodeWithTag("session_detail_delete").assertIsEnabled()
        rule.onNodeWithTag("session_detail_offline_reason").assertDoesNotExist()
    }
}
