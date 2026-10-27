package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.usecase.SessionStats
import kotlinx.datetime.LocalDate
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
    date = LocalDate(2026, 10, 18),
    today = LocalDate(2026, 10, 19),
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

    private fun show(state: SessionDetailUiState) {
        rule.setContent { AppTheme { SessionDetailScreen(state, { events += it }) } }
        rule.waitForIdle()
    }

    private fun openOverflow() {
        rule.onNodeWithTag("session_detail_more").performClick()
        rule.waitForIdle()
    }

    @Test
    fun exerciseName_opensTheExerciseDetails_andThereIsNoProgressLink() {
        val opened = mutableListOf<String>()
        rule.setContent {
            AppTheme { SessionDetailScreen(detail(), { events += it }, onOpenExercise = { opened += it }) }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("session_detail_list").performScrollToNode(hasTestTag("session_detail_exercise_1"))
        rule.onNodeWithTag("session_detail_exercise_1").performClick()

        assertEquals(listOf("e2"), opened)
        rule.onNodeWithText("Ver progreso").assertDoesNotExist()
    }

    @Test
    fun title_isTheWorkoutNameAndItsDate_withEditAndDeleteInlineAndSaveAsRoutineInTheOverflow() {
        show(detail())

        rule.onNodeWithTag("session_detail_title").assertTextEquals("Torso · Ayer")
        rule.onNodeWithTag("session_detail_edit").assertExists()
        rule.onNodeWithTag("session_detail_delete").assertExists()
        rule.onNodeWithTag("session_detail_save_as_routine").assertDoesNotExist()
        openOverflow()
        rule.onNodeWithText("Guardar como rutina").assertExists()
    }

    @Test
    fun title_usesTheShortDateForOlderWorkouts() {
        show(detail().copy(date = LocalDate(2026, 10, 12)))

        rule.onNodeWithTag("session_detail_title").assertTextEquals("Torso · lun, 12 oct")
    }

    @Test
    fun noSnackbarHostIsDrawn() {
        show(detail())

        rule.onNodeWithTag("session_detail_snackbar").assertDoesNotExist()
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
                date = LocalDate(2026, 10, 18),
                today = LocalDate(2026, 10, 19),
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
        rule.setContent { AppTheme { SessionDetailScreen(detail(), { events += it }, onEdit = { edits++ }) } }
        rule.waitForIdle()

        rule.onNodeWithTag("session_detail_edit").assert(hasContentDescription("Editar")).performClick()

        assertEquals(1, edits)
        rule.onNode(hasContentDescription("Copiar")).assertDoesNotExist()
        rule.onNode(hasText("Copiar")).assertDoesNotExist()
    }

    @Test
    fun saveAsRoutineAction_inTheOverflow_emitsOpenSaveAsRoutine() {
        show(detail())

        openOverflow()
        rule.onNodeWithText("Guardar como rutina").performClick()

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
    fun offline_disablesTheActions_andShowsTheHint() {
        show(detail(isOnline = false))

        rule.onNodeWithTag("session_detail_delete").assertIsNotEnabled()
        rule.onNodeWithTag("session_detail_edit").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede editar").assertExists()
    }

    @Test
    fun online_enablesTheActions_withoutTheHint() {
        show(detail())

        rule.onNodeWithTag("session_detail_delete").assertIsEnabled()
        rule.onNodeWithTag("session_detail_edit").assertIsEnabled()
        rule.onNodeWithText("Sin conexión: no se puede editar").assertDoesNotExist()
    }

    @Test
    fun saveAsRoutineSheet_isPrefilled_andGuardarEmitsTheTypedName() {
        show(detail().copy(saveAsRoutine = SaveAsRoutineState()))

        rule.onNodeWithTag("save_as_routine_sheet_input").assertTextContains("Torso")
        rule.onNodeWithTag("save_as_routine_sheet_input").performTextInput("2")
        rule.onNodeWithTag("save_as_routine_sheet_commit").assertIsEnabled().performClick()

        assertEquals(SessionDetailEvent.ConfirmSaveAsRoutine("Torso2"), events.last())
    }

    @Test
    fun saveAsRoutineSheet_closeDiscards() {
        show(detail().copy(saveAsRoutine = SaveAsRoutineState()))

        rule.onNodeWithTag("save_as_routine_sheet_close").performClick()
        rule.waitForIdle()

        assertEquals(SessionDetailEvent.DismissSaveAsRoutine, events.last())
        assertEquals(false, events.any { it is SessionDetailEvent.ConfirmSaveAsRoutine })
    }

    @Test
    fun saveAsRoutineSheet_aTakenName_isFlaggedAndBlocksGuardar() {
        show(detail().copy(saveAsRoutine = SaveAsRoutineState(existingNames = listOf("Torso"))))

        rule.onNodeWithTag("save_as_routine_sheet_commit").assertIsNotEnabled()
        rule.onNodeWithTag("save_as_routine_sheet_input").performTextClearance()
        rule.onNodeWithTag("save_as_routine_sheet_input").performTextInput("torso")

        rule.onNodeWithText("Ya existe una rutina con ese nombre").assertExists()
        rule.onNodeWithTag("save_as_routine_sheet_commit").assertIsNotEnabled()
    }

    @Test
    fun saveAsRoutineSheet_tooManySets_explainsAndBlocksSaving() {
        show(detail().copy(saveAsRoutine = SaveAsRoutineState(tooManySets = true)))

        rule.onNodeWithTag("save_as_routine_too_many_sets")
            .assertTextContains("Un ejercicio tiene más de 10 series; no se puede guardar como rutina")
        rule.onNodeWithTag("save_as_routine_sheet_commit").assertIsNotEnabled()
    }

    @Test
    fun saveAsRoutineSheet_blankNameBlocksSaving() {
        show(detail().copy(saveAsRoutine = SaveAsRoutineState()))

        rule.onNodeWithTag("save_as_routine_sheet_input").performTextClearance()

        rule.onNodeWithTag("save_as_routine_sheet_commit").assertIsNotEnabled()
    }
}
