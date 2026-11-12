package io.github.lopezjavier.appfitness.gallery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary
import io.github.lopezjavier.appfitness.feature.workout.ChooserRoutines
import io.github.lopezjavier.appfitness.feature.workout.EditWorkoutScreen
import io.github.lopezjavier.appfitness.feature.workout.EditWorkoutUiState
import io.github.lopezjavier.appfitness.feature.workout.FormExercise
import io.github.lopezjavier.appfitness.feature.workout.FormSet
import io.github.lopezjavier.appfitness.feature.workout.HistoryUiState
import io.github.lopezjavier.appfitness.feature.workout.PastWorkoutChooserSheet
import io.github.lopezjavier.appfitness.feature.workout.PastWorkoutChooserStep
import io.github.lopezjavier.appfitness.feature.workout.PastWorkoutChooserUiState
import io.github.lopezjavier.appfitness.feature.workout.PastWorkoutScreen
import io.github.lopezjavier.appfitness.feature.workout.PastWorkoutUiState
import io.github.lopezjavier.appfitness.feature.workout.WorkoutForm
import io.github.lopezjavier.appfitness.feature.workout.WorkoutFormView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val EDIT_SCREEN = "Editar entreno"
private const val PAST_SCREEN = "Entreno pasado"
private const val CHOOSER_SCREEN = "Añadir entreno"

/** A valid form (no issues) with one exercise and two sets, the shape the edit and past forms show. */
private fun formView(name: String = "Torso A"): WorkoutFormView = WorkoutFormView(
    form = WorkoutForm(
        date = GalleryFixtures.today,
        name = name,
        duration = "55",
        exercises = listOf(
            FormExercise(
                rowId = 1,
                exerciseId = "bench",
                metricType = ExerciseMetricType.WeightReps,
                group = null,
                restSeconds = 120,
                sets = listOf(
                    FormSet(setId = 1, weight = "60", reps = "8"),
                    FormSet(setId = 2, weight = "60", reps = "8", rpe = 8.0),
                ),
            ),
        ),
    ),
    names = mapOf("bench" to "Press de banca"),
    issues = emptyList(),
)

/** The edit screen of a finished workout (EditWorkoutRoute) and its form pickers. */
@Suppress("LongMethod") // A list of fixtures.
private fun editWorkout(): List<GalleryEntry> {
    fun edit(
        state: String,
        ui: EditWorkoutUiState,
        kind: GalleryKind = GalleryKind.Screen,
        vararg covers: String,
        wide: Boolean = false,
        interact: (ComposeContentTestRule.() -> Unit)? = null,
    ) = GalleryEntry(
        ENTRENO_AREA,
        EDIT_SCREEN,
        state,
        kind,
        covers = setOf("EditWorkoutRoute") + covers,
        wide = wide,
        interact = interact,
    ) {
        EditWorkoutScreen(ui, onEvent = {}, onFormEvent = {}, onAddExercise = {}, onClose = {})
    }

    val content = EditWorkoutUiState.Content(view = formView())
    return listOf(
        edit("loading", EditWorkoutUiState.Loading),
        edit("load failed", EditWorkoutUiState.Error),
        edit("data", content),
        edit("offline", content.copy(isOnline = false)),
        edit("saving", content.copy(busy = true)),
        edit("unsaved changes", content.copy(isDirty = true)),
        edit("tablet width", content, wide = true),
        edit(
            "discard changes dialog",
            content.copy(isDirty = true),
            GalleryKind.Dialog,
            interact = { onNodeWithTag("edit_workout_back").performClick() },
        ),
        edit(
            "set type picker",
            content,
            GalleryKind.Sheet,
            "FormPickers",
            interact = { onNodeWithTag("edit_workout_set_0_0_type").performClick() },
        ),
        edit(
            "rpe picker",
            content,
            GalleryKind.Sheet,
            "FormPickers",
            interact = { onNodeWithTag("edit_workout_set_0_1_rpe").performClick() },
        ),
    )
}

/** The form to log a workout of a past date (PastWorkoutRoute). */
private fun pastWorkout(): List<GalleryEntry> {
    fun past(
        state: String,
        ui: PastWorkoutUiState,
        kind: GalleryKind = GalleryKind.Screen,
        vararg covers: String,
        wide: Boolean = false,
        interact: (ComposeContentTestRule.() -> Unit)? = null,
    ) = GalleryEntry(
        ENTRENO_AREA,
        PAST_SCREEN,
        state,
        kind,
        covers = setOf("PastWorkoutRoute", "PastWorkoutScreen") + covers,
        wide = wide,
        interact = interact,
    ) {
        PastWorkoutScreen(ui, onEvent = {}, onFormEvent = {}, onAddExercise = {}, onClose = {})
    }

    val content = PastWorkoutUiState.Content(view = formView(), today = GalleryFixtures.today)
    return listOf(
        past("loading", PastWorkoutUiState.Loading),
        past("load failed", PastWorkoutUiState.Error),
        past("form", content),
        past("offline", content.copy(isOnline = false)),
        past("saving", content.copy(busy = true)),
        past("unsaved changes", content.copy(isDirty = true)),
        past("tablet width", content, wide = true),
        past(
            "date dialog",
            content,
            GalleryKind.Dialog,
            interact = { onNodeWithTag("past_workout_date").performClick() },
        ),
    )
}

/** The "Añadir entreno" chooser (PastWorkoutChooserSheet): its options, routines and sessions steps. */
private fun pastWorkoutChooser(): List<GalleryEntry> {
    fun chooser(state: String, ui: PastWorkoutChooserUiState, sessions: HistoryUiState = HistoryUiState.Empty) =
        GalleryEntry(
            ENTRENO_AREA,
            CHOOSER_SCREEN,
            state,
            GalleryKind.Sheet,
            covers = setOf("PastWorkoutChooserSheet"),
        ) {
            Box(Modifier.fillMaxSize()) {
                PastWorkoutChooserSheet(ui, onEvent = {}, sessions = { sessions })
            }
        }

    val routines = listOf(
        RoutineSummary("routine_upper", "Torso A", 5),
        RoutineSummary("routine_lower", "Pierna A", 4),
    )
    return listOf(
        chooser("options", PastWorkoutChooserUiState(step = PastWorkoutChooserStep.Options)),
        chooser(
            "routines loading",
            PastWorkoutChooserUiState(step = PastWorkoutChooserStep.Routines, routines = ChooserRoutines.Loading),
        ),
        chooser(
            "routines",
            PastWorkoutChooserUiState(
                step = PastWorkoutChooserStep.Routines,
                routines = ChooserRoutines.Content(routines),
            ),
        ),
        chooser(
            "routines empty",
            PastWorkoutChooserUiState(step = PastWorkoutChooserStep.Routines, routines = ChooserRoutines.Empty),
        ),
        chooser(
            "routines failed",
            PastWorkoutChooserUiState(step = PastWorkoutChooserStep.Routines, routines = ChooserRoutines.Error),
        ),
        chooser(
            "sessions",
            PastWorkoutChooserUiState(step = PastWorkoutChooserStep.Sessions),
            sessions = HistoryUiState.Content(EntrenoFixtures.dayRows),
        ),
        chooser(
            "sessions empty",
            PastWorkoutChooserUiState(step = PastWorkoutChooserStep.Sessions),
        ),
    )
}

internal fun entrenoWorkoutFormEntries(): List<GalleryEntry> = editWorkout() + pastWorkout() + pastWorkoutChooser()

/** Renders the Entreno form entries (US-131); skipped unless `-Pgallery`. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class EntrenoWorkoutFormsGallery {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rendersWorkoutForms() = rule.renderGallery(entrenoWorkoutFormEntries().filter { !it.wide && !it.tall })
}
