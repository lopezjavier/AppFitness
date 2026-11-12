package io.github.lopezjavier.appfitness.gallery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import io.github.lopezjavier.appfitness.domain.coach.builder.DayTypeOption
import io.github.lopezjavier.appfitness.domain.coach.builder.PickerEntry
import io.github.lopezjavier.appfitness.domain.coach.builder.RoutineType
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.feature.trainer.BlockBuilderScreen
import io.github.lopezjavier.appfitness.feature.trainer.BlockBuilderUiState
import io.github.lopezjavier.appfitness.feature.trainer.BuilderCreateExerciseSheet
import io.github.lopezjavier.appfitness.feature.trainer.BuilderSaveError
import io.github.lopezjavier.appfitness.feature.trainer.BuilderSlotPickerSheet
import io.github.lopezjavier.appfitness.feature.trainer.DayTypeSheet
import io.github.lopezjavier.appfitness.feature.trainer.FolderBlockScreen
import io.github.lopezjavier.appfitness.feature.trainer.FolderBlockUiState
import io.github.lopezjavier.appfitness.feature.trainer.FolderPickerTarget
import io.github.lopezjavier.appfitness.feature.trainer.FolderReviewResultScreen
import io.github.lopezjavier.appfitness.feature.trainer.FolderReviewResultUiState
import io.github.lopezjavier.appfitness.feature.trainer.NoFitInfo
import io.github.lopezjavier.appfitness.feature.trainer.OrderLine
import io.github.lopezjavier.appfitness.feature.trainer.RepLine
import io.github.lopezjavier.appfitness.feature.trainer.ReviewItem
import io.github.lopezjavier.appfitness.feature.trainer.SlotRef
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val BUILDER_SCREEN = "Constructor de bloque"
private const val FOLDER_SCREEN = "Bloque de carpeta"
private const val FOLDER_REVIEW_SCREEN = "Revisión de carpeta"
private const val DAY_TYPE_TITLE = "Tipo de día"

/** The block builder (BlockBuilderRoute): splits, types, targets and the builder day with its sheets. */
@Suppress("LongMethod") // A list of fixtures.
private fun blockBuilder(): List<GalleryEntry> {
    fun builder(
        state: String,
        ui: BlockBuilderUiState,
        kind: GalleryKind = GalleryKind.Screen,
        online: Boolean = true,
        interact: (ComposeContentTestRule.() -> Unit)? = null,
        vararg covers: String,
    ) = GalleryEntry(
        TRAINER_AREA,
        BUILDER_SCREEN,
        state,
        kind,
        covers = setOf("BlockBuilderRoute") + covers,
        interact = interact,
    ) {
        BlockBuilderScreen(
            state = ui,
            isOnline = flowOf(online),
            onEvent = {},
            onOpenExercise = {},
        )
    }

    val fillDay = TrainerFixtures.filledState()
    val full = TrainerFixtures.builderUi(state = fillDay)
    return listOf(
        builder("loading", BlockBuilderUiState.Loading),
        builder("failed", BlockBuilderUiState.Failed(offline = false)),
        builder("failed, offline", BlockBuilderUiState.Failed(offline = true), online = false),
        builder("splits for four days", BlockBuilderUiState.Splits(4, TrainerFixtures.splits(4), 0)),
        builder("types", TrainerFixtures.typesState()),
        builder(
            "types, no fit for the session length",
            TrainerFixtures.typesState(noFit = NoFitInfo(day = 0, neededMinutes = 90, limitMinutes = 60)),
        ),
        builder(
            "types, day sheet open",
            TrainerFixtures.typesState(openDay = 0),
            GalleryKind.Sheet,
            covers = arrayOf("DayTypeSheet"),
        ),
        builder("targets", TrainerFixtures.targetsState()),
        builder(
            "targets, no fit",
            TrainerFixtures.targetsState(noFit = NoFitInfo(day = 0, neededMinutes = 90, limitMinutes = 60)),
        ),
        builder("builder, empty days", TrainerFixtures.builderUi()),
        builder("builder, filled days", full),
        builder("builder, saving", TrainerFixtures.builderUi(state = fillDay, saving = true)),
        builder(
            "builder, save failed",
            TrainerFixtures.builderUi(state = fillDay, saveError = BuilderSaveError.Generic),
        ),
        builder(
            "builder, offline",
            TrainerFixtures.builderUi(state = fillDay, saveError = BuilderSaveError.Offline),
            online = false,
        ),
        builder(
            "builder, discard dialog",
            TrainerFixtures.builderUi(state = fillDay, discardDialog = true),
            GalleryKind.Dialog,
            covers = arrayOf("BlockBuilderContent"),
        ),
        builder(
            "builder, slot picker open",
            TrainerFixtures.builderUi(
                state = fillDay,
                openSlot = SlotRef(0, 0),
                pickerEntries = listOf(PickerEntry(TrainerFixtures.bench)),
            ),
            GalleryKind.Sheet,
            covers = arrayOf("BuilderSlotPickerSheet"),
        ),
    )
}

/** The builder sheets rendered on their own state: day type, slot picker and the create-exercise sheet. */
private fun builderSheets(): List<GalleryEntry> {
    fun sheet(state: String, covers: String, content: @Composable () -> Unit) =
        GalleryEntry(TRAINER_AREA, BUILDER_SCREEN, state, GalleryKind.Sheet, covers = setOf(covers)) {
            Box(Modifier.fillMaxSize()) { content() }
        }

    val options: List<DayTypeOption> = TrainerFixtures.dayTypeOptions(1)[0]
    return listOf(
        sheet("day type", "DayTypeSheet") {
            DayTypeSheet(
                title = DAY_TYPE_TITLE,
                options = options,
                chosen = RoutineType.Upper,
                onPick = {},
                onDismiss = {},
            )
        },
        sheet("slot picker", "BuilderSlotPickerSheet") {
            BuilderSlotPickerSheet(
                state = TrainerFixtures.builderUi(
                    openSlot = SlotRef(0, 0),
                    pickerEntries = listOf(PickerEntry(TrainerFixtures.bench)),
                ),
                slot = SlotRef(0, 0),
                onEvent = {},
                onOpenExercise = {},
            )
        },
        sheet("create exercise", "BuilderCreateExerciseSheet") {
            BuilderCreateExerciseSheet(
                patterns = listOf(MovementPattern.HorizontalPush, MovementPattern.HorizontalPull),
                createFailed = false,
                online = true,
                onEvent = {},
                onDismiss = {},
            )
        },
        sheet("create exercise, failed", "BuilderCreateExerciseSheet") {
            BuilderCreateExerciseSheet(
                patterns = listOf(MovementPattern.HorizontalPush),
                createFailed = true,
                online = false,
                onEvent = {},
                onDismiss = {},
            )
        },
    )
}

/** The folder block (FolderBlockRoute): its loading, refused and empty states. */
private fun folderBlock(): List<GalleryEntry> {
    fun folder(
        state: String,
        ui: FolderBlockUiState,
        online: Boolean = true,
        kind: GalleryKind = GalleryKind.Screen,
        vararg covers: String,
    ) = GalleryEntry(
        TRAINER_AREA,
        FOLDER_SCREEN,
        state,
        kind,
        covers = setOf("FolderBlockRoute", "FolderBlockScreen") + covers,
    ) {
        FolderBlockScreen(state = ui, isOnline = flowOf(online), onEvent = {}, onOpenExercise = {})
    }

    val routineEntry = PickerEntry(TrainerFixtures.row)
    val benchEntry = PickerEntry(TrainerFixtures.bench)
    return listOf(
        folder("loading", FolderBlockUiState.Loading),
        folder("failed", FolderBlockUiState.Failed(offline = false)),
        folder("failed, offline", FolderBlockUiState.Failed(offline = true), online = false),
        folder("empty folder", FolderBlockUiState.Empty(folderName = "Fuerza")),
        folder(
            "refused, days do not match the routines",
            FolderBlockUiState.Refused(
                folderName = "Fuerza",
                days = TrainerFixtures.refusedDays,
                daysPerWeek = 2,
            ),
        ),
        folder("editor, data", TrainerFixtures.folderContent()),
        folder("editor, edited", TrainerFixtures.folderContent(edited = true)),
        folder("editor, saving", TrainerFixtures.folderContent(saving = true)),
        folder("editor, save failed", TrainerFixtures.folderContent(saveError = BuilderSaveError.Generic)),
        folder("editor, offline", TrainerFixtures.folderContent(saveError = BuilderSaveError.Offline), online = false),
        folder(
            "editor, discard dialog",
            TrainerFixtures.folderContent(edited = true, discardDialog = true),
            kind = GalleryKind.Dialog,
        ),
        folder(
            "picker, muscle group",
            TrainerFixtures.folderContent(pickerTarget = FolderPickerTarget.ChooseGroup),
            kind = GalleryKind.Sheet,
            covers = arrayOf("FolderBlockPickers"),
        ),
        folder(
            "picker, routine of a group",
            TrainerFixtures.folderContent(pickerTarget = FolderPickerTarget.ChooseRoutine(MuscleGroup.Back)),
            kind = GalleryKind.Sheet,
            covers = arrayOf("FolderBlockPickers"),
        ),
        folder(
            "picker, add an exercise",
            TrainerFixtures.folderContent(
                pickerTarget = FolderPickerTarget.Add(MuscleGroup.Back, routineIndex = 0),
                pickerEntries = listOf(routineEntry),
            ),
            kind = GalleryKind.Sheet,
            covers = arrayOf("FolderBlockPickers", "ExercisePicker"),
        ),
        folder(
            "picker, swap an exercise",
            TrainerFixtures.folderContent(
                pickerTarget = FolderPickerTarget.Swap(routineIndex = 0, exerciseIndex = 0),
                pickerEntries = listOf(benchEntry),
            ),
            kind = GalleryKind.Sheet,
            covers = arrayOf("FolderBlockPickers", "ExercisePicker"),
        ),
    )
}

/** The folder review result (FolderReviewResultRoute): accepted, with warnings and the failures. */
private fun folderReview(): List<GalleryEntry> {
    fun result(state: String, ui: FolderReviewResultUiState) = GalleryEntry(
        TRAINER_AREA,
        FOLDER_REVIEW_SCREEN,
        state,
        covers = setOf("FolderReviewResultRoute"),
    ) {
        FolderReviewResultScreen(state = ui, onEvent = {}, onBack = {})
    }

    val accepted = FolderReviewResultUiState.Content(
        folderName = "Fuerza",
        score = 9.0,
        strength = true,
        complete = true,
        liftingRoutines = 2,
        items = listOf(
            ReviewItem.Days(days = 2, met = true),
            ReviewItem.Reps(listOf(RepLine("Torso A", "Press de banca", 4, 6))),
        ),
    )
    val warnings = accepted.copy(
        score = 5.5,
        complete = false,
        items = listOf(
            ReviewItem.Days(days = 1, met = false),
            ReviewItem.Order(listOf(OrderLine("Torso A", "Press de banca", "Remo", listOf("Press de banca")))),
        ),
    )
    return listOf(
        result("loading", FolderReviewResultUiState.Loading),
        result("accepted", accepted),
        result("with warnings", warnings),
        result("empty", FolderReviewResultUiState.Empty),
        result("failed", FolderReviewResultUiState.Error),
    )
}

internal fun trainerBuilderEntries(): List<GalleryEntry> =
    blockBuilder() + builderSheets() + folderBlock() + folderReview()

/** Renders the Trainer builder, folder and review entries (US-131); skipped unless `-Pgallery`. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class TrainerBuilderGallery {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rendersTrainerBuilder() = rule.renderGallery(trainerBuilderEntries().filter { !it.wide && !it.tall })
}
