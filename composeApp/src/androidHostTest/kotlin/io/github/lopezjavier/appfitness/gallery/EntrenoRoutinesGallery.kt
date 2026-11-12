package io.github.lopezjavier.appfitness.gallery

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.usecase.SupersetLabel
import io.github.lopezjavier.appfitness.feature.routines.FolderSheetMode
import io.github.lopezjavier.appfitness.feature.routines.FolderSheetState
import io.github.lopezjavier.appfitness.feature.routines.RoutineBuilderScreen
import io.github.lopezjavier.appfitness.feature.routines.RoutineBuilderUiState
import io.github.lopezjavier.appfitness.feature.routines.RoutineDetailsScreen
import io.github.lopezjavier.appfitness.feature.routines.RoutineDetailsUiState
import io.github.lopezjavier.appfitness.feature.routines.RoutinesScreen
import io.github.lopezjavier.appfitness.feature.routines.RoutinesUiState
import io.github.lopezjavier.appfitness.feature.routines.TypePickerUi
import io.github.lopezjavier.appfitness.feature.workout.CalculatorDraft
import io.github.lopezjavier.appfitness.feature.workout.CalculatorKind
import io.github.lopezjavier.appfitness.feature.workout.CalculatorUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

internal const val ENTRENO_AREA = "Entreno"
private const val ROUTINES_SCREEN = "Rutinas"
private const val DETAILS_SCREEN = "Detalles de rutina"
private const val BUILDER_SCREEN = "Constructor de rutina"

/** The "Mis rutinas" list (WorkoutHome) with its folder sheets and actions sheets. */
@Suppress("LongMethod") // A list of fixtures.
private fun routinesHome(): List<GalleryEntry> {
    fun home(
        state: String,
        ui: RoutinesUiState,
        kind: GalleryKind = GalleryKind.Screen,
        wide: Boolean = false,
        interact: (ComposeContentTestRule.() -> Unit)? = null,
        vararg covers: String,
    ) = GalleryEntry(
        ENTRENO_AREA,
        ROUTINES_SCREEN,
        state,
        kind,
        covers = setOf("WorkoutHome") + covers,
        wide = wide,
        interact = interact,
    ) { RoutinesScreen(ui, onEvent = {}) }

    val fixtures = EntrenoFixtures
    val strength = fixtures.strengthFolder
    return listOf(
        home("loading", RoutinesUiState.Loading),
        home("load failed", RoutinesUiState.Error),
        home("empty", RoutinesUiState.Content(routines = emptyList())),
        home("folders and routines", fixtures.listContent()),
        home("folder collapsed", fixtures.listContent(collapsed = setOf(strength.id))),
        home("offline", fixtures.listContent(isOnline = false)),
        home("tablet width", fixtures.listContent(), wide = true),
        home(
            "routine menu",
            fixtures.listContent(menuFor = fixtures.upperRoutine),
            GalleryKind.Sheet,
            covers = arrayOf("RoutineMenuSheet"),
        ),
        home(
            "routine menu offline",
            fixtures.listContent(isOnline = false, menuFor = fixtures.upperRoutine),
            GalleryKind.Sheet,
            covers = arrayOf("RoutineMenuSheet"),
        ),
        home(
            "folder menu with routines (delete blocked)",
            fixtures.listContent(folderMenuFor = strength),
            GalleryKind.Sheet,
            covers = arrayOf("FolderMenuSheet"),
        ),
        home(
            "folder menu empty folder",
            fixtures.listContent(folderMenuFor = fixtures.hypertrophyFolder),
            GalleryKind.Sheet,
            covers = arrayOf("FolderMenuSheet"),
        ),
        home(
            "folder menu offline",
            fixtures.listContent(isOnline = false, folderMenuFor = fixtures.hypertrophyFolder),
            GalleryKind.Sheet,
            covers = arrayOf("FolderMenuSheet"),
        ),
        home(
            "move routine to folder",
            fixtures.listContent(moveFor = fixtures.fullBodyRoutine),
            GalleryKind.Sheet,
            covers = arrayOf("MoveSheet"),
        ),
        home(
            "new folder sheet",
            fixtures.listContent(folderSheet = FolderSheetState(FolderSheetMode.Create)),
            GalleryKind.Sheet,
            covers = arrayOf("FolderSheet"),
        ),
        home(
            "rename folder sheet",
            fixtures.listContent(folderSheet = FolderSheetState(FolderSheetMode.Rename, strength.id)),
            GalleryKind.Sheet,
            covers = arrayOf("FolderSheet"),
        ),
        home(
            "rename folder sheet saving",
            fixtures.listContent(
                folderSheet = FolderSheetState(FolderSheetMode.Rename, strength.id),
                folderSaving = true,
            ),
            GalleryKind.Sheet,
            covers = arrayOf("FolderSheet"),
        ),
    )
}

/** Routine details (RoutineDetailsRoute): the read-only routine with its sets and "Empezar". */
private fun routineDetails(): List<GalleryEntry> {
    fun details(state: String, ui: RoutineDetailsUiState, wide: Boolean = false) =
        GalleryEntry(ENTRENO_AREA, DETAILS_SCREEN, state, covers = setOf("RoutineDetailsRoute"), wide = wide) {
            RoutineDetailsScreen(
                state = ui,
                onEvent = {},
                onEdit = {},
                onOpenExercise = {},
                onBack = {},
            )
        }

    val data = RoutineDetailsUiState.Content(
        name = "Torso A",
        exercises = EntrenoFixtures.routineDetailsExercises,
        lastDone = GalleryFixtures.now,
    )
    return listOf(
        details("loading", RoutineDetailsUiState.Loading),
        details("load failed", RoutineDetailsUiState.Error),
        details("never done", data.copy(lastDone = null)),
        details("done before", data),
        details("offline", data.copy(isOnline = false)),
        details("tablet width", data, wide = true),
    )
}

/** Routine builder (RoutineBuilderRoute) and the sheets it opens from its state or its row menu. */
@Suppress("LongMethod") // A list of fixtures.
private fun routineBuilder(): List<GalleryEntry> {
    fun builder(
        state: String,
        ui: RoutineBuilderUiState,
        kind: GalleryKind = GalleryKind.Screen,
        wide: Boolean = false,
        interact: (ComposeContentTestRule.() -> Unit)? = null,
        vararg covers: String,
    ) = GalleryEntry(
        ENTRENO_AREA,
        BUILDER_SCREEN,
        state,
        kind,
        covers = setOf("RoutineBuilderRoute") + covers,
        wide = wide,
        interact = interact,
    ) { RoutineBuilderScreen(ui, onEvent = {}, onAddExercise = {}, onOpenExercise = {}) }

    val rows = EntrenoFixtures.draftRows
    val editing = RoutineBuilderUiState.Editing(isEditMode = true, name = "Torso A", rows = rows)
    val rowMenu: ComposeContentTestRule.() -> Unit = { onNodeWithTag("routine_builder_menu_0").performClick() }
    return listOf(
        builder("loading", RoutineBuilderUiState.Loading),
        builder("load failed", RoutineBuilderUiState.LoadError),
        builder(
            "new routine, empty",
            RoutineBuilderUiState.Editing(isEditMode = false, name = "", rows = emptyList()),
        ),
        builder("data with exercises", editing),
        builder("blank name (save disabled)", editing.copy(name = "")),
        builder("name taken", editing.copy(nameTaken = true)),
        builder("unsaved changes", editing.copy(isDirty = true)),
        builder("saving", editing.copy(isSaving = true)),
        builder("offline", editing.copy(isOnline = false)),
        builder("tablet width", editing, wide = true),
        builder(
            "discard changes dialog",
            editing.copy(isDirty = true),
            GalleryKind.Dialog,
            interact = { onNodeWithTag("routine_builder_back").performClick() },
        ),
        builder(
            "row menu",
            editing,
            GalleryKind.Sheet,
            interact = rowMenu,
            covers = arrayOf("BuilderRowMenu"),
        ),
        builder(
            "set type sheet",
            editing.copy(typePicker = TypePickerUi(rowIndex = 0, setIndex = 0, current = SetType.Working)),
            GalleryKind.Sheet,
            covers = arrayOf("BuilderSheets"),
        ),
        builder(
            "warm-up calculator sheet",
            editing.copy(calculator = routineCalculator(CalculatorKind.WarmUp)),
            GalleryKind.Sheet,
            covers = arrayOf("WarmUpCalculatorSheet"),
        ),
        builder(
            "disc loader sheet",
            editing.copy(
                calculator = routineCalculator(CalculatorKind.Discs, listOf(20.0, 10.0, 5.0, 2.5, 1.25)),
            ),
            GalleryKind.Sheet,
            covers = arrayOf("DiscLoaderSheet"),
        ),
        builder(
            "superset rows",
            editing.copy(
                rows = listOf(
                    rows[0].copy(supersetLabel = SupersetLabel('A', 1)),
                    rows[1].copy(supersetLabel = SupersetLabel('A', 2)),
                ),
            ),
        ),
    )
}

private fun routineCalculator(kind: CalculatorKind, discs: List<Double> = emptyList()) = CalculatorUiState(
    draft = CalculatorDraft(position = 0, kind = kind),
    weightKg = 60.0,
    barKg = 20.0,
    roundingKg = 2.5,
    availableDiscs = discs,
)

internal fun entrenoRoutinesEntries(): List<GalleryEntry> = routinesHome() + routineDetails() + routineBuilder()

/** Renders the Entreno routine entries (US-131); skipped unless `-Pgallery`. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class EntrenoRoutinesGallery {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rendersRoutines() = rule.renderGallery(entrenoRoutinesEntries().filter { !it.wide && !it.tall })
}
