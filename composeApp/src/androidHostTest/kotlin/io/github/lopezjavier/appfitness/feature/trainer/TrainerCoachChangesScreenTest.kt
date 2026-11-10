package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachChangeDecision
import io.github.lopezjavier.appfitness.domain.model.CoachChangeException
import io.github.lopezjavier.appfitness.domain.model.CoachChangeFailure
import io.github.lopezjavier.appfitness.domain.model.CoachChangeFigures
import io.github.lopezjavier.appfitness.domain.model.CoachChangeKind
import io.github.lopezjavier.appfitness.domain.model.CoachChangeStatus
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.ProgressionFlagDecision
import io.github.lopezjavier.appfitness.domain.model.UndoResult
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.routines.RecordingMessages
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-97 and US-104 (UI side): the "Cambios del entrenador" section of the Trainer tab. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class TrainerCoachChangesScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<TrainerEvent>()
    private val messages = RecordingMessages()
    private val changes = FakeCoachChangesRepository()

    private fun row(
        id: String,
        kind: CoachChangeKind,
        status: CoachChangeStatus = CoachChangeStatus.Applied,
        figures: CoachChangeFigures = CoachChangeFigures(),
        decision: CoachChangeDecision? = null,
    ) = coachChange(id = id, kind = kind, status = status, figures = figures, decision = decision).toRowUi()

    private fun show(
        rows: List<CoachChangeRowUi>,
        busyId: String? = null,
        online: Boolean = true,
        onOpenExercise: (String) -> Unit = {},
    ) {
        rule.setContent {
            AppTheme {
                TrainerWithBoxes(
                    state = TrainerUiState.Content(coachChanges = rows, busyChangeId = busyId),
                    isOnline = flowOf(online),
                    onEvent = { events += it },
                    actions = TrainerActions(onOpenExercise = onOpenExercise),
                    exerciseNames = mapOf("bench" to "Press de banca"),
                )
            }
        }
        rule.waitForIdle()
        // Pending flags stay on the root; the applied changes live in "Tu bloque".
        if (rows.isNotEmpty() && rows.none { it.status == CoachChangeStatus.Pending }) rule.openTrainerBlock()
    }

    private fun showRoute(
        model: TrainerViewModel,
        details: Boolean = true,
        onRange: (String, String) -> Unit = { _, _ -> },
    ) {
        val resolver = ExerciseNameResolver(
            FakeExerciseRepository(listOf(exerciseRow("bench", "bench"))),
            FakeExerciseLibrary(mapOf("bench" to exerciseInfo("bench", "Press de banca"))),
        )
        val actions = TrainerActions(onOpenRoutineRange = onRange)
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalAppMessages provides messages) {
                    if (details) {
                        TrainerBoxRoot(
                            box = TrainerBox.Block,
                            actions = actions,
                            viewModel = model,
                            connectivity = FakeConnectivityObserver(),
                            nameResolver = resolver,
                        )
                    } else {
                        TrainerRoute(
                            viewModel = model,
                            connectivity = FakeConnectivityObserver(),
                            nameResolver = resolver,
                            actions = actions,
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun model(): TrainerViewModel {
        val plans = FakeCoachPlanRepository()
        plans.current = Result.success(coachPlan(id = "plan-1", status = PlanStatus.Accepted))
        return TrainerViewModel(
            FakeCoachRepository(),
            MutableClock(),
            plans,
            FakeCoachBlockRepository(),
            FakeCoachWeeksRepository(),
            androidx.lifecycle.SavedStateHandle(),
            coachChanges = changes,
        )
    }

    private val raise = CoachChangeFigures(weightFromKg = 60.0, weightToKg = 62.5, rpeMax = 8.0)

    @Test
    fun noRows_hidesTheSection() {
        show(emptyList())

        rule.onNodeWithTag("trainer_coach_changes_section").assertDoesNotExist()
    }

    @Test
    fun appliedRaise_showsNameReasonAndDeshacer_thatSendsTheEvent() {
        show(listOf(row("c1", CoachChangeKind.LoadRaise, figures = raise)))

        rule.onNodeWithText("Cambios del entrenador").assertExists()
        rule.onNodeWithText("Press de banca").assertExists()
        rule.onNodeWithText(
            "Subida de 60 kg a 62,5 kg: completaste todas las series en el máximo del rango con RPE 8 o menos.",
        ).assertExists()
        rule.onNodeWithTag("trainer_coach_change_undo_c1").performClick()

        assertEquals(listOf<TrainerEvent>(TrainerEvent.UndoCoachChange("c1")), events)
    }

    @Test
    fun appliedDrop_readsTheFivePercentReasonWithTheSessions() {
        show(
            listOf(
                row(
                    "c2",
                    CoachChangeKind.LoadDrop,
                    figures = CoachChangeFigures(weightFromKg = 80.0, weightToKg = 76.0, sessions = 2),
                ),
            ),
        )

        rule.onNodeWithText(
            "Bajada del 5 % de 80 kg a 76 kg: 2 sesiones seguidas sin llegar al mínimo del rango.",
        ).assertExists()
    }

    @Test
    fun keptByDiscomfort_showsTheNeutralReasonWithoutActions() {
        show(listOf(row("c3", CoachChangeKind.LoadKept)))

        rule.onNodeWithText("El peso se mantiene mientras tengas una molestia abierta en este ejercicio.")
            .assertExists()
        rule.onNodeWithTag("trainer_coach_change_undo_c3").assertDoesNotExist()
    }

    @Test
    fun undoneRow_readsDeshechoWithTheOldWeight_andNoAction() {
        show(listOf(row("c4", CoachChangeKind.LoadRaise, CoachChangeStatus.Undone, raise)))

        rule.onNodeWithTag("trainer_coach_change_row_c4_status", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Deshecho").assertExists()
        rule.onNodeWithText("Vuelve a 60 kg.").assertExists()
        rule.onNodeWithTag("trainer_coach_change_undo_c4").assertDoesNotExist()
    }

    @Test
    fun flaggedRaise_offersBothOptions_eachSendingItsDecision() {
        val flag = CoachChangeFigures(currentKg = 20.0, nextKg = 22.5, percent = 12.5)
        show(listOf(row("c5", CoachChangeKind.LoadFlag, CoachChangeStatus.Pending, flag)))

        rule.onNodeWithText(
            "La siguiente subida es de 22,5 kg (+12,5 %), más de lo recomendable de una vez. " +
                "Puedes subir igualmente o mantener el peso y sumar repeticiones.",
        ).assertExists()
        rule.onNodeWithText("Subir a 22,5 kg").assertExists()
        rule.onNodeWithTag("trainer_coach_change_raise_c5").performClick()
        rule.onNodeWithTag("trainer_coach_change_keep_c5").performClick()

        assertEquals(
            listOf<TrainerEvent>(
                TrainerEvent.DecideFlag("c5", ProgressionFlagDecision.Raise),
                TrainerEvent.DecideFlag("c5", ProgressionFlagDecision.Keep),
            ),
            events,
        )
    }

    @Test
    fun decidedFlag_readsTheChoice_withoutOptions() {
        show(
            listOf(
                row(
                    "c6",
                    CoachChangeKind.LoadFlag,
                    CoachChangeStatus.Decided,
                    CoachChangeFigures(nextKg = 22.5, percent = 12.5),
                    CoachChangeDecision.Keep,
                ),
            ),
        )

        rule.onNodeWithText("Peso mantenido").assertExists()
        rule.onNodeWithTag("trainer_coach_change_raise_c6").assertDoesNotExist()
    }

    @Test
    fun noRangeFlag_offersAnadirUnRango_thatSendsOpenRange() {
        show(listOf(row("c7", CoachChangeKind.NoRangeFlag, CoachChangeStatus.Pending)))

        rule.onNodeWithText(
            "Este ejercicio no tiene un rango de repeticiones, así que el entrenador no puede subirlo solo.",
        ).assertExists()
        rule.onNodeWithTag("trainer_coach_change_add_range_c7").performClick()

        assertEquals(listOf<TrainerEvent>(TrainerEvent.OpenRange("c7")), events)
    }

    @Test
    fun offline_disablesUndoAndExplainsWhy() {
        show(listOf(row("c1", CoachChangeKind.LoadRaise, figures = raise)), online = false)

        rule.onNodeWithTag("trainer_coach_change_undo_c1").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede deshacer el cambio").assertExists()
    }

    @Test
    fun anotherChangeBusy_disablesTheOtherRowsActions() {
        show(
            listOf(
                row("c1", CoachChangeKind.LoadRaise, figures = raise),
                row("c8", CoachChangeKind.LoadFlag, CoachChangeStatus.Pending, CoachChangeFigures(nextKg = 22.5)),
            ),
            busyId = "c1",
        )

        rule.onNodeWithTag("trainer_coach_change_raise_c8").assertIsNotEnabled()
        rule.onNodeWithTag("trainer_coach_change_keep_c8").assertIsNotEnabled()
    }

    @Test
    fun tappingTheName_opensTheExercise() {
        val opened = mutableListOf<String>()
        show(listOf(row("c1", CoachChangeKind.LoadRaise, figures = raise)), onOpenExercise = { opened += it })

        rule.onNodeWithTag("trainer_coach_change_row_c1_headline").performClick()

        assertEquals(listOf("bench"), opened)
    }

    @Test
    fun undoWithEditedRoutines_showsTheSkippedMessage() {
        changes.changes = Result.success(
            listOf(coachChange("c1", figures = raise)),
        )
        changes.undoResult = Result.success(UndoResult(restored = 1, skipped = 2))
        showRoute(model())

        rule.onNodeWithTag("trainer_coach_change_undo_c1").performClick()
        // The plural is read from the resources off the main thread.
        rule.waitUntil(timeoutMillis = RESOURCE_TIMEOUT_MS) { messages.shown.isNotEmpty() }

        assertEquals(listOf("Deshecho, salvo en 2 rutinas que ya habías editado."), messages.shown)
    }

    @Test
    fun undoRefusedBecauseTheRoutineChanged_showsTheMessage_andKeepsTheRow() {
        changes.changes = Result.success(listOf(coachChange("c1", figures = raise)))
        changes.undoResult = Result.failure(CoachChangeException(CoachChangeFailure.Conflict))
        showRoute(model())

        rule.onNodeWithTag("trainer_coach_change_undo_c1").performClick()
        rule.waitForIdle()

        assertEquals(listOf("Ya no se puede deshacer: has cambiado esa rutina."), messages.shown)
        rule.onNodeWithTag("trainer_coach_change_undo_c1").assertExists()
    }

    @Test
    fun undoFailing_showsTheErrorAsAMessage_withoutARetryButton() {
        changes.changes = Result.success(listOf(coachChange("c1", figures = raise)))
        changes.undoResult = Result.failure(AppException(AppError.Unknown))
        showRoute(model())

        rule.onNodeWithTag("trainer_coach_change_undo_c1").performClick()
        rule.waitForIdle()

        assertEquals(listOf("No se pudo deshacer el cambio. Inténtalo de nuevo."), messages.shown)
        rule.onNodeWithText("Reintentar").assertDoesNotExist()
    }

    @Test
    fun decidingAFlag_failing_showsTheChoiceError() {
        changes.changes = Result.success(
            listOf(
                coachChange(
                    "c5",
                    CoachChangeKind.LoadFlag,
                    CoachChangeStatus.Pending,
                    figures = CoachChangeFigures(nextKg = 22.5, percent = 12.5),
                ),
            ),
        )
        changes.decideResult = Result.failure(AppException(AppError.Unknown))
        showRoute(model(), details = false)

        rule.onNodeWithTag("trainer_coach_change_keep_c5").performClick()
        rule.waitForIdle()

        assertEquals(listOf("No se pudo guardar tu elección. Inténtalo de nuevo."), messages.shown)
    }

    @Test
    fun anadirUnRango_opensTheRoutineEditorOnThatExercise() {
        changes.changes = Result.success(
            listOf(
                coachChange(
                    "c7",
                    CoachChangeKind.NoRangeFlag,
                    CoachChangeStatus.Pending,
                    exerciseId = "bench",
                    routineId = "routine-9",
                ),
            ),
        )
        val opened = mutableListOf<Pair<String, String>>()
        showRoute(model(), details = false) { routineId, exerciseId -> opened += routineId to exerciseId }

        rule.onNodeWithTag("trainer_coach_change_add_range_c7").performClick()
        rule.waitForIdle()

        assertTrue(opened == listOf("routine-9" to "bench"))
    }
}

private const val RESOURCE_TIMEOUT_MS = 5_000L
