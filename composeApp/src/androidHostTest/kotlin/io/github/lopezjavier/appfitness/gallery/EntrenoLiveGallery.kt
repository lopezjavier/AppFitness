package io.github.lopezjavier.appfitness.gallery

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import io.github.lopezjavier.appfitness.domain.model.DeloadExerciseSuggestion
import io.github.lopezjavier.appfitness.domain.model.DiscomfortLevel
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
import io.github.lopezjavier.appfitness.domain.model.ProgressionAction
import io.github.lopezjavier.appfitness.domain.model.ProgressionReason
import io.github.lopezjavier.appfitness.domain.model.ProgressionSuggestion
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.SetUsed
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseSummary
import io.github.lopezjavier.appfitness.domain.usecase.SessionSummary
import io.github.lopezjavier.appfitness.feature.workout.CalculatorDraft
import io.github.lopezjavier.appfitness.feature.workout.CalculatorKind
import io.github.lopezjavier.appfitness.feature.workout.CalculatorUiState
import io.github.lopezjavier.appfitness.feature.workout.FinishSheet
import io.github.lopezjavier.appfitness.feature.workout.LiveDiscomfort
import io.github.lopezjavier.appfitness.feature.workout.LiveExercise
import io.github.lopezjavier.appfitness.feature.workout.LiveNoteSheet
import io.github.lopezjavier.appfitness.feature.workout.LiveSessionScreen
import io.github.lopezjavier.appfitness.feature.workout.LiveSessionUiState
import io.github.lopezjavier.appfitness.feature.workout.LiveSetRow
import io.github.lopezjavier.appfitness.feature.workout.LiveSummary
import io.github.lopezjavier.appfitness.feature.workout.RoutineChangeSummary
import io.github.lopezjavier.appfitness.feature.workout.RpePickerTarget
import io.github.lopezjavier.appfitness.feature.workout.TimerBarUi
import io.github.lopezjavier.appfitness.feature.workout.TypePickerTarget
import io.github.lopezjavier.appfitness.feature.workout.UpdateRoutineSheet
import io.github.lopezjavier.appfitness.feature.workout.WorkoutEntryHost
import io.github.lopezjavier.appfitness.feature.workout.WorkoutEntryState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val LIVE_SCREEN = "Sesión en vivo"
private const val ELAPSED_SECONDS = 1_820L
private const val SUMMARY_DURATION_SECONDS = 3_300L

private fun set(
    index: Int,
    weight: String,
    reps: String,
    completed: Boolean,
    type: SetType = SetType.Working,
    rpe: String? = null,
    previous: String? = null,
    pendingSave: Boolean = false,
) = LiveSetRow(
    setIndex = index,
    weight = weight,
    reps = reps,
    completed = completed,
    type = type,
    rpe = rpe,
    previous = previous,
    pendingSave = pendingSave,
)

private fun exercise(
    position: Int,
    id: String,
    name: String,
    sets: List<LiveSetRow>,
    metricType: ExerciseMetricType = ExerciseMetricType.WeightReps,
    cardio: Boolean = false,
) = LiveExercise(
    position = position,
    exerciseId = id,
    name = name,
    supersetLabel = null,
    sets = sets,
    targetRpeText = null,
    metricType = metricType,
    equipment = Equipment.Barbell,
    cardio = cardio,
)

private val bench = exercise(
    0,
    "bench",
    "Press de banca",
    listOf(
        set(1, "60", "8", completed = true, rpe = "8", previous = "60 kg × 8"),
        set(2, "60", "8", completed = true),
        set(3, "60", "8", completed = false),
    ),
).copy(targetRpeText = "8")

private val row = exercise(1, "row", "Remo con barra", listOf(set(1, "50", "10", completed = false)))

private fun active(
    exercises: List<LiveExercise> = listOf(bench, row),
    isOnline: Boolean = true,
    timerBar: TimerBarUi? = null,
    pausedAt: kotlin.time.Instant? = null,
    finishSheet: FinishSheet? = null,
    discardConfirm: Boolean = false,
    typePicker: TypePickerTarget? = null,
    rpePicker: RpePickerTarget? = null,
    noteSheet: LiveNoteSheet? = null,
    progressionHelpPosition: Int? = null,
    calculator: CalculatorUiState? = null,
) = LiveSessionUiState.Active(
    name = "Torso A",
    startedAt = GalleryFixtures.now,
    exercises = exercises,
    isOnline = isOnline,
    finishSheet = finishSheet,
    discardConfirm = discardConfirm,
    timerBar = timerBar,
    typePicker = typePicker,
    rpePicker = rpePicker,
    noteSheet = noteSheet,
    progressionHelpPosition = progressionHelpPosition,
    pausedAt = pausedAt,
    calculator = calculator,
)

private fun summaryState(withRecords: Boolean): LiveSessionUiState.Finished {
    val exercises = listOf(
        ExerciseSummary(
            0,
            "bench",
            4_250.0,
            isPersonalRecord = withRecords,
            records = if (withRecords) setOf(PersonalRecordType.HeaviestLift) else emptySet(),
        ),
        ExerciseSummary(1, "row", 1_500.0, isPersonalRecord = false),
    )
    return LiveSessionUiState.Finished(
        LiveSummary(
            SessionSummary(SUMMARY_DURATION_SECONDS, completedSets = 14, exercises = exercises),
            exerciseNames = mapOf(0 to "Press de banca", 1 to "Remo con barra"),
        ),
    )
}

private val tapMenu: ComposeContentTestRule.() -> Unit = { onNodeWithTag("live_exercise_0_menu").performClick() }

private fun menuThen(tag: String): ComposeContentTestRule.() -> Unit = {
    onNodeWithTag("live_exercise_0_menu").performClick()
    waitForIdle()
    onNodeWithTag(tag).performClick()
}

private fun calculatorOf(kind: CalculatorKind, discs: List<Double> = emptyList()) = CalculatorUiState(
    draft = CalculatorDraft(position = 0, kind = kind),
    weightKg = 60.0,
    barKg = 20.0,
    roundingKg = 2.5,
    availableDiscs = discs,
)

/** One live session entry: the stateless body of [LiveSessionRoute] with a fake state. */
private fun live(
    state: String,
    ui: LiveSessionUiState,
    kind: GalleryKind = GalleryKind.Screen,
    vararg covers: String,
    wide: Boolean = false,
    fontScale: Float = 1f,
    interact: (ComposeContentTestRule.() -> Unit)? = null,
) = GalleryEntry(
    ENTRENO_AREA,
    LIVE_SCREEN,
    state,
    kind,
    covers = setOf("LiveSessionRoute") + covers,
    wide = wide,
    fontScale = fontScale,
    interact = interact,
) {
    LiveSessionScreen(ui, elapsedSeconds = ELAPSED_SECONDS, onEvent = {}, onBack = {}, onOpenExercise = {})
}

@Suppress("LongMethod") // A list of fixtures.
private fun liveSession(): List<GalleryEntry> {
    val allDone =
        listOf(
            bench.copy(
                sets = bench.sets.map {
                    it.copy(completed = true)
                },
            ),
            row.copy(sets = row.sets.map { it.copy(completed = true) }),
        )
    val pending = listOf(bench.copy(sets = bench.sets.map { it.copy(completed = false) }), row)
    val cardio = exercise(
        0,
        "run",
        "Cinta",
        listOf(
            LiveSetRow(setIndex = 1, weight = "", reps = "", completed = false, plannedDurationMinutes = 20),
        ),
        metricType = ExerciseMetricType.Duration,
        cardio = true,
    )
    val mixedTypes = bench.copy(
        sets = listOf(
            set(1, "40", "10", completed = true, type = SetType.WarmUp),
            set(2, "60", "8", completed = true),
            set(3, "65", "6", completed = true, type = SetType.Failure),
            set(4, "50", "6", completed = false, type = SetType.Drop),
        ),
    )
    val timed = exercise(
        0,
        "plank",
        "Plancha",
        listOf(LiveSetRow(setIndex = 1, weight = "", reps = "", completed = true, durationSeconds = 60)),
        metricType = ExerciseMetricType.Duration,
    )
    val walk = exercise(
        2,
        "walk",
        "Caminata",
        listOf(
            LiveSetRow(
                setIndex = 1,
                weight = "",
                reps = "",
                completed = true,
                distance = "3,2",
                durationSeconds = 1_200,
            ),
        ),
        metricType = ExerciseMetricType.DistanceDuration,
    )
    val suggestion = ProgressionSuggestion(
        exerciseId = "bench",
        action = ProgressionAction.RaiseWeight,
        weightKg = 62.5,
        goalReps = 8,
        reason = ProgressionReason.AllSetsAtMax,
        setsUsed = listOf(SetUsed(GalleryFixtures.today, 60.0, 8, 8.0)),
    )
    val withSuggestion = listOf(bench.copy(suggestion = suggestion), row)
    val restTimer = TimerBarUi.Rest(remaining = 90.seconds, total = 120.seconds)
    val cardioTimer = TimerBarUi.Cardio(0, 1, "Cinta", elapsed = 5.minutes, target = 20.minutes, paused = false)
    val rpe = RpePickerTarget(0, 2, completesSet = true)

    return listOf(
        live("loading", LiveSessionUiState.Loading),
        live("load failed", LiveSessionUiState.Error),
        live("no session", LiveSessionUiState.NoSession),
        live("no sets done yet", active(exercises = pending)),
        live("mid session with done, pending and current sets", active()),
        live("rest timer running", active(timerBar = restTimer)),
        live("cardio timer running", active(exercises = listOf(cardio), timerBar = cardioTimer)),
        live("target reached", active(exercises = allDone)),
        live("paused", active(pausedAt = GalleryFixtures.now)),
        live(
            "discomfort flagged",
            active(exercises = listOf(bench.copy(discomfort = LiveDiscomfort(DiscomfortLevel.Mild)), row)),
        ),
        live(
            "deload week",
            active(exercises = listOf(bench, row.copy(deload = DeloadExerciseSuggestion("row", 3, 2)))),
        ),
        live("progression hint", active(exercises = withSuggestion)),
        live("metric types", active(exercises = listOf(timed, walk, row))),
        live("set type chips", active(exercises = listOf(mixedTypes))),
        live("offline", active(isOnline = false)),
        live("tablet width", active(), wide = true),
        live("large font (1.3)", active(), fontScale = 1.3f),
        live("exercise menu", active(), GalleryKind.Sheet, "ExerciseMenuSheet", interact = tapMenu),
        live(
            "rest sheet",
            active(),
            GalleryKind.Sheet,
            "ExerciseRestSheet",
            interact = menuThen("live_exercise_0_menu_rest"),
        ),
        live(
            "discomfort sheet",
            active(),
            GalleryKind.Sheet,
            "DiscomfortSheet",
            interact = menuThen("live_exercise_0_menu_discomfort"),
        ),
        live(
            "body weight sheet",
            active(exercises = listOf(bench.copy(metricType = ExerciseMetricType.Bodyweight), row)),
            GalleryKind.Sheet,
            "OptionSheet",
            interact = menuThen("live_exercise_0_menu_body_weight"),
        ),
        live(
            "menu sheet",
            active(),
            GalleryKind.Sheet,
            "LiveDialogs",
            interact = { onNodeWithTag("live_menu_button").performClick() },
        ),
        live(
            "finish sheet",
            active(finishSheet = FinishSheet(pendingCount = 2)),
            GalleryKind.Sheet,
            "FinishSessionSheet",
        ),
        live("discard dialog", active(discardConfirm = true), GalleryKind.Dialog, "LiveDialogs"),
        live("set type picker", active(typePicker = TypePickerTarget(0, 0)), GalleryKind.Sheet, "LiveTypePicker"),
        live("rpe picker", active(rpePicker = rpe), GalleryKind.Sheet, "LiveRpePicker"),
        live(
            "set note sheet",
            active(noteSheet = LiveNoteSheet(0, 1, kind = null, draft = "", originalKind = null, original = null)),
            GalleryKind.Sheet,
        ),
        live(
            "progression help",
            active(exercises = withSuggestion, progressionHelpPosition = 0),
            GalleryKind.Sheet,
            "ProgressionHelpSheet",
        ),
        live(
            "warm-up calculator",
            active(calculator = calculatorOf(CalculatorKind.WarmUp)),
            GalleryKind.Sheet,
            "WarmUpCalculatorSheet",
        ),
        live(
            "disc loader",
            active(calculator = calculatorOf(CalculatorKind.Discs, listOf(20.0, 10.0, 5.0, 2.5, 1.25))),
            GalleryKind.Sheet,
            "DiscLoaderSheet",
        ),
        live("summary with records", summaryState(withRecords = true)),
        live("summary without records", summaryState(withRecords = false)),
        live(
            "update routine sheet",
            LiveSessionUiState.Finished(
                summaryState(withRecords = false).summary,
                updateRoutineSheet = UpdateRoutineSheet(
                    sessionId = "session_1",
                    summary = RoutineChangeSummary(setsChanged = 2, restsChanged = 1),
                ),
            ),
            GalleryKind.Sheet,
            "UpdateRoutineBottomSheet",
        ),
        live(
            "update routine sheet, no changes",
            LiveSessionUiState.Finished(
                summaryState(withRecords = false).summary,
                updateRoutineSheet = UpdateRoutineSheet(
                    sessionId = "session_1",
                    summary = RoutineChangeSummary(setsChanged = 0, restsChanged = 0),
                ),
            ),
            GalleryKind.Sheet,
            "UpdateRoutineBottomSheet",
        ),
    )
}

/** The entry point that shows the workout card and the discard confirmation (WorkoutEntryHost). */
private fun workoutEntry(): List<GalleryEntry> {
    fun entry(state: String, discardAsked: Boolean = false, kind: GalleryKind = GalleryKind.Screen) = GalleryEntry(
        ENTRENO_AREA,
        "Entreno",
        state,
        kind,
        covers = setOf("WorkoutEntryHost"),
    ) {
        WorkoutEntryHost(
            state = WorkoutEntryState.Routines,
            onOpenLiveSession = {},
            onOpenHistory = {},
            discardAsked = discardAsked,
            routines = { _, _ -> },
        )
    }
    return listOf(
        entry("default"),
        entry("discard asked", discardAsked = true, kind = GalleryKind.Dialog),
    )
}

internal fun entrenoLiveEntries(): List<GalleryEntry> = liveSession() + workoutEntry()

/** Renders the Entreno live session entries (US-131); skipped unless `-Pgallery`. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class EntrenoLiveGallery {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rendersLiveSession() = rule.renderGallery(entrenoLiveEntries().filter { !it.wide && !it.tall })
}
