package io.github.lopezjavier.appfitness.gallery

import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.feature.exercises.CreateExerciseForm
import io.github.lopezjavier.appfitness.feature.exercises.EditExerciseForm
import io.github.lopezjavier.appfitness.feature.exercises.ExerciseChatTab
import io.github.lopezjavier.appfitness.feature.exercises.ExerciseChatUiState
import io.github.lopezjavier.appfitness.feature.exercises.ExerciseDetailScreen
import io.github.lopezjavier.appfitness.feature.exercises.ExerciseDetailTab
import io.github.lopezjavier.appfitness.feature.exercises.ExerciseDetailUiState
import io.github.lopezjavier.appfitness.feature.exercises.ExercisePickerScreen
import io.github.lopezjavier.appfitness.feature.exercises.ExercisePickerUiState
import io.github.lopezjavier.appfitness.feature.exercises.HistoryState
import io.github.lopezjavier.appfitness.feature.exercises.RecordsState
import io.github.lopezjavier.appfitness.feature.exercises.SimilarState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val PICKER_SCREEN = "Seleccionar ejercicio"
private const val DETAIL_SCREEN = "Detalle de ejercicio"

/** The exercise picker (ExercisePickerRoute), its states, the filters and the create sheet. */
@Suppress("LongMethod") // A list of fixtures.
private fun exercisePicker(): List<GalleryEntry> {
    fun picker(
        state: String,
        ui: ExercisePickerUiState,
        selectMode: Boolean = false,
        kind: GalleryKind = GalleryKind.Screen,
        wide: Boolean = false,
        fontScale: Float = 1f,
        vararg covers: String,
    ) = GalleryEntry(
        ENTRENO_AREA,
        PICKER_SCREEN,
        state,
        kind,
        covers = setOf("ExercisePickerRoute") + covers,
        alsoOpenedFrom = listOf("Trainer"),
        wide = wide,
        fontScale = fontScale,
    ) {
        EntrenoFixtures.WithoutMedia {
            ExercisePickerScreen(ui, onEvent = {}, selectMode = selectMode)
        }
    }

    val all = EntrenoFixtures.pickerCatalog
    val results = ExercisePickerUiState.Content(visible = all, totalCount = all.size)
    return listOf(
        picker("loading", ExercisePickerUiState.Loading),
        picker("load failed", ExercisePickerUiState.Error),
        picker("catalog empty", ExercisePickerUiState.Content(visible = emptyList(), totalCount = 0)),
        picker("results", results),
        picker(
            "filters on (search, muscle, equipment)",
            ExercisePickerUiState.Content(
                visible = listOf(all[0]),
                totalCount = all.size,
                query = "press",
                muscleGroup = MuscleGroup.Chest,
                equipment = Equipment.Barbell,
            ),
        ),
        picker(
            "search without results",
            ExercisePickerUiState.Content(visible = emptyList(), totalCount = all.size, query = "zzz"),
        ),
        picker("favorites first", results.copy(favoriteIds = setOf("row"))),
        picker("offline", results.copy(isOnline = false)),
        picker("select mode", results, selectMode = true),
        picker("large font (1.3)", results, fontScale = 1.3f),
        picker("tablet width", results, wide = true),
        picker(
            "create sheet",
            results.copy(
                createForm = CreateExerciseForm(
                    name = "Remo a una mano",
                    muscleGroup = MuscleGroup.Back,
                    equipment = Equipment.Dumbbell,
                ),
            ),
            kind = GalleryKind.Sheet,
            covers = arrayOf("ExerciseFormSheet", "MovementPatternField"),
        ),
    )
}

/** The exercise detail (ExerciseDetailRoute) with its tabs, the states of each one and the edit sheet. */
@Suppress("LongMethod") // A list of fixtures.
private fun exerciseDetail(): List<GalleryEntry> {
    fun detail(
        state: String,
        ui: ExerciseDetailUiState,
        selectMode: Boolean = false,
        kind: GalleryKind = GalleryKind.Screen,
        wide: Boolean = false,
        chat: ExerciseChatUiState? = null,
        vararg covers: String,
    ) = GalleryEntry(
        ENTRENO_AREA,
        DETAIL_SCREEN,
        state,
        kind,
        covers = setOf("ExerciseDetailRoute") + covers,
        alsoOpenedFrom = listOf("Trainer"),
        wide = wide,
    ) {
        EntrenoFixtures.WithoutMedia {
            ExerciseDetailScreen(
                ui,
                selectMode,
                onSelect = {},
                onRetry = {},
                today = EntrenoFixtures.today,
                chatTab = { _, listState, modifier ->
                    ExerciseChatTab(
                        state = chat ?: ExerciseChatUiState(),
                        onDraftChange = {},
                        onSend = {},
                        onRetry = {},
                        modifier = modifier,
                        listState = listState,
                    )
                },
            )
        }
    }

    val benchDetail = EntrenoFixtures.benchDetail
    fun loaded(
        tab: ExerciseDetailTab = ExerciseDetailTab.Summary,
        history: HistoryState = HistoryState.Empty,
        similar: SimilarState = SimilarState.Loading,
        isOnline: Boolean = true,
        editForm: EditExerciseForm? = null,
        records: RecordsState = RecordsState.Empty,
    ) = ExerciseDetailUiState.Loaded(
        detail = benchDetail,
        tab = tab,
        records = records,
        history = history,
        similar = similar,
        editForm = editForm,
        isOnline = isOnline,
    )

    val historyContent = HistoryState.Content(rows = EntrenoFixtures.historyRows, endReached = true)
    return listOf(
        detail("loading", ExerciseDetailUiState.Loading),
        detail("load failed", ExerciseDetailUiState.Error),
        detail("summary", loaded(similar = SimilarState.Empty)),
        detail(
            "similar exercises",
            loaded(tab = ExerciseDetailTab.Similar, similar = SimilarState.Content(EntrenoFixtures.similarRows)),
        ),
        detail("similar offline", loaded(tab = ExerciseDetailTab.Similar, similar = SimilarState.Offline)),
        detail("similar failed", loaded(tab = ExerciseDetailTab.Similar, similar = SimilarState.Error)),
        detail("history with sessions", loaded(tab = ExerciseDetailTab.History, history = historyContent)),
        detail("history empty", loaded(tab = ExerciseDetailTab.History, history = HistoryState.Empty)),
        detail("how to", loaded(tab = ExerciseDetailTab.HowTo)),
        detail("select mode", loaded(similar = SimilarState.Empty), selectMode = true),
        detail("offline", loaded(similar = SimilarState.Empty, isOnline = false)),
        detail("tablet width", loaded(similar = SimilarState.Empty), wide = true),
        detail("records", loaded(similar = SimilarState.Empty, records = EntrenoFixtures.records)),
        detail("records failed", loaded(similar = SimilarState.Empty, records = RecordsState.Error)),
        detail("chat empty", loaded(tab = ExerciseDetailTab.Chat, similar = SimilarState.Empty)),
        detail(
            "chat asking",
            loaded(tab = ExerciseDetailTab.Chat, similar = SimilarState.Empty),
            chat = EntrenoFixtures.chatAsking,
        ),
        detail(
            "chat answered",
            loaded(tab = ExerciseDetailTab.Chat, similar = SimilarState.Empty),
            chat = EntrenoFixtures.chatAnswered,
        ),
        detail(
            "chat failed",
            loaded(tab = ExerciseDetailTab.Chat, similar = SimilarState.Empty),
            chat = EntrenoFixtures.chatFailed,
        ),
        detail(
            "chat offline",
            loaded(tab = ExerciseDetailTab.Chat, similar = SimilarState.Empty, isOnline = false),
            chat = EntrenoFixtures.chatFailed.copy(isOnline = false),
        ),
        detail(
            "edit sheet",
            loaded(similar = SimilarState.Empty, editForm = EntrenoFixtures.editForm),
            kind = GalleryKind.Sheet,
            covers = arrayOf("ExerciseFormSheet"),
        ),
    )
}

internal fun entrenoExercisesEntries(): List<GalleryEntry> = exercisePicker() + exerciseDetail()

/** Renders the Entreno exercise entries (US-131); skipped unless `-Pgallery`. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class EntrenoExercisesGallery {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rendersExercises() = rule.renderGallery(entrenoExercisesEntries().filter { !it.wide && !it.tall })
}
