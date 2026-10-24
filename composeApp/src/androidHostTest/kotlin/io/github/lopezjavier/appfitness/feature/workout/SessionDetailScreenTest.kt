package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.usecase.SessionStats
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun detail(
    isOnline: Boolean = true,
    volumeKg: Double = 2_120.0,
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
            records = if (firstRecord) setOf(PersonalRecordType.Best1Rm, PersonalRecordType.MaxVolume) else emptySet(),
        ),
        DetailExercise(1, "e2", "Remo", listOf(DetailSet(1, SetType.Working, "87,5 kg × 8")), isPersonalRecord = false),
    ),
    isOnline = isOnline,
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

        rule.onNodeWithTag("session_detail_record_0_best_1rm").assert(hasContentDescription("Récord: 1RM estimado"))
        rule.onNodeWithTag("session_detail_record_0_max_volume").assert(hasContentDescription("Récord: Volumen máximo"))
        rule.onNodeWithTag("session_detail_record_0_best_set").assertDoesNotExist()
        rule.onNodeWithTag("session_detail_records_1").assertDoesNotExist()
    }

    @Test
    fun durationAndDistanceRecords_showTheirLabels() {
        val records = setOf(PersonalRecordType.BestDuration, PersonalRecordType.BestDistance)
        show(
            SessionDetailUiState.Content(
                name = "Cardio",
                label = DateLabel.Yesterday,
                stats = SessionStats(durationSeconds = 1_800, completedSets = 1, volumeKg = 0.0),
                exercises = listOf(DetailExercise(0, "e1", "Cinta", emptyList(), false, records)),
                isOnline = true,
                busy = false,
            ),
        )

        rule.onNode(hasText("Mejor tiempo"), useUnmergedTree = true).assertExists()
        rule.onNode(hasText("Mayor distancia"), useUnmergedTree = true).assertExists()
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
    fun recalculatedVolume_isShownFromTheState() {
        show(detail(volumeKg = 2_032.5))

        rule.onNodeWithTag("session_detail_volume").assertTextContains("2.033 kg")
    }

    @Test
    fun deleteAction_isInTheTopBarRow_andDeletesAtOnceWithoutADialog() {
        show(detail())

        rule.onNodeWithTag("session_detail_delete").assert(hasContentDescription("Eliminar"))
        rule.onNodeWithTag("session_detail_delete").performClick()

        assertEquals(listOf<SessionDetailEvent>(SessionDetailEvent.Delete), events)
        rule.onNodeWithTag("session_detail_delete_confirm").assertDoesNotExist()
    }

    @Test
    fun editAction_opensTheEditScreen_andThereIsNoCopyAction() {
        var edits = 0
        rule.setContent {
            AppTheme {
                SessionDetailScreen(detail(), { events += it }, snackbarHost = snackbarHost, onEdit = { edits++ })
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("session_detail_edit").assert(hasContentDescription("Editar")).performClick()

        assertEquals(1, edits)
        rule.onNode(hasContentDescription("Copiar")).assertDoesNotExist()
        rule.onNode(hasText("Copiar")).assertDoesNotExist()
    }

    @Test
    fun saveAsRoutineAction_emitsOpenSaveAsRoutine() {
        show(detail())

        rule.onNodeWithTag("session_detail_save_as_routine").assert(hasContentDescription("Guardar como rutina"))
        rule.onNodeWithTag("session_detail_save_as_routine").performClick()

        assertEquals(listOf<SessionDetailEvent>(SessionDetailEvent.OpenSaveAsRoutine), events)
    }

    @Test
    fun noSetEditSheetExists_tappingASetDoesNothing() {
        show(detail())

        rule.onNodeWithTag("session_detail_set_0_2").performClick()

        assertEquals(emptyList<SessionDetailEvent>(), events)
        rule.onNodeWithTag("session_edit_save").assertDoesNotExist()
    }

    @Test
    fun offline_disablesTheActions_andShowsTheReason() {
        show(detail(isOnline = false))

        rule.onNodeWithTag("session_detail_delete").assertIsNotEnabled()
        rule.onNodeWithTag("session_detail_edit").assertIsNotEnabled()
        rule.onNodeWithTag("session_detail_save_as_routine").assertIsNotEnabled()
        rule.onNodeWithTag("session_detail_offline_reason")
            .assertTextContains("Sin conexión: no puedes editar ni eliminar entrenos")
    }

    @Test
    fun online_enablesTheActions_withoutReason() {
        show(detail())

        rule.onNodeWithTag("session_detail_delete").assertIsEnabled()
        rule.onNodeWithTag("session_detail_edit").assertIsEnabled()
        rule.onNodeWithTag("session_detail_offline_reason").assertDoesNotExist()
    }

    @Test
    fun saveAsRoutineSheet_isPrefilled_confirmEmitsEvent_andTypingEmitsTheName() {
        show(detail().copy(saveAsRoutine = SaveAsRoutineState(name = "Torso", tooManySets = false)))

        rule.onNodeWithTag("save_as_routine_name").assertTextContains("Torso")
        rule.onNodeWithTag("save_as_routine_name").performTextInput("2")
        rule.onNodeWithTag("save_as_routine_confirm").assertIsEnabled().performClick()

        assertEquals(SessionDetailEvent.SaveAsRoutineNameChanged("Torso2"), events.first())
        assertEquals(SessionDetailEvent.ConfirmSaveAsRoutine, events.last())
    }

    @Test
    fun saveAsRoutineSheet_closeDiscards() {
        show(detail().copy(saveAsRoutine = SaveAsRoutineState(name = "Torso", tooManySets = false)))

        rule.onNodeWithTag("save_as_routine_close").performClick()

        assertEquals(listOf<SessionDetailEvent>(SessionDetailEvent.DismissSaveAsRoutine), events)
    }

    @Test
    fun saveAsRoutineSheet_tooManySets_explainsAndBlocksSaving() {
        show(detail().copy(saveAsRoutine = SaveAsRoutineState(name = "Torso", tooManySets = true)))

        rule.onNodeWithTag("save_as_routine_too_many_sets")
            .assertTextContains("Un ejercicio tiene más de 10 series; no se puede guardar como rutina")
        rule.onNodeWithTag("save_as_routine_confirm").assertIsNotEnabled()
    }

    @Test
    fun saveAsRoutineSheet_failure_showsTheInlineMessage_andBlankNameBlocksSaving() {
        show(detail().copy(saveAsRoutine = SaveAsRoutineState(name = " ", tooManySets = false, failed = true)))

        rule.onNodeWithTag("save_as_routine_failed").assertExists()
        rule.onNodeWithTag("save_as_routine_confirm").assertIsNotEnabled()
    }
}
