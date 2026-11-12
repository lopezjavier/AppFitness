package io.github.lopezjavier.appfitness.gallery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.PlanLimit
import io.github.lopezjavier.appfitness.domain.model.RoutineFolder
import io.github.lopezjavier.appfitness.feature.trainer.ActivityLevelSheet
import io.github.lopezjavier.appfitness.feature.trainer.BeforeStartMode
import io.github.lopezjavier.appfitness.feature.trainer.BeforeStartScreen
import io.github.lopezjavier.appfitness.feature.trainer.BeforeStartUiState
import io.github.lopezjavier.appfitness.feature.trainer.CardioEdit
import io.github.lopezjavier.appfitness.feature.trainer.CardioMinutesSheet
import io.github.lopezjavier.appfitness.feature.trainer.FolderPickerState
import io.github.lopezjavier.appfitness.feature.trainer.IntakeRowEditorState
import io.github.lopezjavier.appfitness.feature.trainer.IntakeRowSheet
import io.github.lopezjavier.appfitness.feature.trainer.PlanFailureKind
import io.github.lopezjavier.appfitness.feature.trainer.PlanProposalScreen
import io.github.lopezjavier.appfitness.feature.trainer.PlanProposalUiState
import io.github.lopezjavier.appfitness.feature.trainer.StartChoiceStep
import io.github.lopezjavier.appfitness.feature.trainer.YourDataScreen
import io.github.lopezjavier.appfitness.feature.trainer.YourDataUiState
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val PROPOSAL_SCREEN = "Propuesta de plan"
private const val BEFORE_START_SCREEN = "Antes de empezar"
private const val YOUR_DATA_SCREEN = "Tus datos"
private const val ACTIVITY_SHEET = "Nivel de actividad"
private const val CARDIO_SHEET = "Minutos de cardio"

/** The plan proposal (PlanProposalRoute): the proposal, its blocked and failed states and its open sheets. */
@Suppress("LongMethod") // A list of fixtures.
private fun planProposal(): List<GalleryEntry> {
    fun proposal(
        state: String,
        ui: PlanProposalUiState,
        online: Boolean = true,
        kind: GalleryKind = GalleryKind.Screen,
        fontScale: Float = 1f,
        wide: Boolean = false,
        vararg covers: String,
    ) = GalleryEntry(
        TRAINER_AREA,
        PROPOSAL_SCREEN,
        state,
        kind,
        covers = setOf("PlanProposalRoute") + covers,
        wide = wide,
        fontScale = fontScale,
    ) {
        PlanProposalScreen(
            state = ui,
            isOnline = flowOf(online),
            onEvent = {},
            onBack = {},
            onOpenBeforeStart = {},
        )
    }

    return listOf(
        proposal("loading", PlanProposalUiState.Loading),
        proposal("idle, asks for a plan", PlanProposalUiState.Idle),
        proposal("requesting", PlanProposalUiState.Requesting),
        proposal("proposal", TrainerFixtures.proposal()),
        proposal("blocked by an unconfirmed value", TrainerFixtures.proposal(unconfirmed = true)),
        proposal("blocked, data changed", TrainerFixtures.proposal(dataChanged = true)),
        proposal("blocked, level changed (stale)", TrainerFixtures.proposal(stale = true)),
        proposal("accepting", TrainerFixtures.proposal(accepting = true)),
        proposal("calorie floor limits the plan", TrainerFixtures.proposal(limitedBy = PlanLimit.Floor)),
        proposal("offline", TrainerFixtures.proposal(), online = false),
        proposal("large font (1.3)", TrainerFixtures.proposal(), fontScale = 1.3f),
        proposal("tablet width", TrainerFixtures.proposal(), wide = true),
        proposal(
            "activity level sheet",
            TrainerFixtures.proposal(activitySheet = ActivityLevel.Light),
            kind = GalleryKind.Sheet,
            covers = arrayOf("ActivityLevelSheet"),
        ),
        proposal(
            "cardio minutes sheet",
            TrainerFixtures.proposal(cardioEdit = TrainerFixtures.cardioSheet()),
            kind = GalleryKind.Sheet,
            covers = arrayOf("CardioMinutesSheet"),
        ),
        proposal("failed, timeout", PlanProposalUiState.Failed(PlanFailureKind.Timeout)),
        proposal("failed, infeasible", PlanProposalUiState.Failed(PlanFailureKind.Infeasible)),
        proposal("failed, missing data", PlanProposalUiState.Failed(PlanFailureKind.Missing(emptySet()))),
        proposal("failed, block in progress", PlanProposalUiState.Failed(PlanFailureKind.BlockInProgress)),
        proposal("failed, other", PlanProposalUiState.Failed(PlanFailureKind.Other)),
        proposal("failed, offline", PlanProposalUiState.Failed(PlanFailureKind.Offline), online = false),
    )
}

/** "Antes de empezar" (BeforeStartRoute): plan and block modes, its sheets and its failures. */
@Suppress("LongMethod") // A list of fixtures.
private fun beforeStart(): List<GalleryEntry> {
    fun start(
        state: String,
        ui: BeforeStartUiState,
        online: Boolean = true,
        kind: GalleryKind = GalleryKind.Screen,
        wide: Boolean = false,
        vararg covers: String,
    ) = GalleryEntry(
        TRAINER_AREA,
        BEFORE_START_SCREEN,
        state,
        kind,
        covers = setOf("BeforeStartRoute") + covers,
        wide = wide,
    ) {
        BeforeStartScreen(
            state = ui,
            isOnline = flowOf(online),
            onEvent = {},
            onBack = {},
            onOpenYourData = {},
            onFix = {},
        )
    }

    val folders = listOf(RoutineFolder("folder_strength", "Fuerza"))
    val block = TrainerFixtures.beforeStart(mode = BeforeStartMode.Block)
    return listOf(
        start("loading", BeforeStartUiState.Loading),
        start("error", BeforeStartUiState.Error),
        start("offline", BeforeStartUiState.Offline),
        start("plan, complete", TrainerFixtures.beforeStart()),
        start("block, complete", block),
        start(
            "plan, activity level sheet",
            TrainerFixtures.beforeStart(sheet = ActivityLevel.Moderate),
            kind = GalleryKind.Sheet,
            covers = arrayOf("ActivityLevelSheet"),
        ),
        start(
            "block, start choice",
            block.copy(startChoice = StartChoiceStep.Choice),
            kind = GalleryKind.Sheet,
            covers = arrayOf("BlockStartSheets"),
        ),
        start(
            "block, folder picker",
            block.copy(startChoice = StartChoiceStep.Folders, folderPicker = FolderPickerState.Loaded(folders)),
            kind = GalleryKind.Sheet,
            covers = arrayOf("BlockStartSheets"),
        ),
        start("tablet width", TrainerFixtures.beforeStart(), wide = true),
    )
}

/** "Tus datos" (YourDataRoute): the rows, the provisional values, the years and maintenance sheets and the sheet. */
@Suppress("LongMethod") // A list of fixtures.
private fun yourData(): List<GalleryEntry> {
    fun data(
        state: String,
        ui: YourDataUiState,
        online: Boolean = true,
        editor: IntakeRowEditorState = IntakeRowEditorState(),
        kind: GalleryKind = GalleryKind.Screen,
        wide: Boolean = false,
        vararg covers: String,
    ) = GalleryEntry(
        TRAINER_AREA,
        YOUR_DATA_SCREEN,
        state,
        kind,
        covers = setOf("YourDataRoute") + covers,
        wide = wide,
    ) {
        YourDataScreen(
            state = ui,
            isOnline = flowOf(online),
            onEvent = {},
            onBack = {},
            onFix = {},
            editor = editor,
        )
    }

    val complete = TrainerFixtures.yourDataContent()
    return listOf(
        data("loading", YourDataUiState.Loading),
        data("error", YourDataUiState.Error),
        data("offline", YourDataUiState.Offline),
        data("complete", complete),
        data("provisional value", TrainerFixtures.yourDataContent(provisional = true)),
        data("handover note", complete.copy(showHandover = true, handoverKcal = 2_300.0)),
        data(
            "years sheet",
            complete,
            editor = IntakeRowEditorState(sheet = IntakeRowSheet.Years(years = 3.0)),
            kind = GalleryKind.Sheet,
            covers = arrayOf("YearsSheet"),
        ),
        data(
            "maintenance sheet",
            complete,
            editor = IntakeRowEditorState(sheet = IntakeRowSheet.Maintenance(text = "2400")),
            kind = GalleryKind.Sheet,
            covers = arrayOf("MaintenanceSheet"),
        ),
        data(
            "activity level sheet",
            complete.copy(sheet = ActivityLevel.Light),
            kind = GalleryKind.Sheet,
            covers = arrayOf("ActivityLevelSheet"),
        ),
        data("missing fields with fix targets", YourDataUiState.Content(rows = TrainerFixtures.missingRows())),
        data("tablet width", complete, wide = true),
    )
}

/** Overlays rendered directly with their own state: the activity level and the cardio minutes sheets. */
private fun planSheets(): List<GalleryEntry> {
    fun sheet(state: String, screen: String, covers: String, content: @Composable () -> Unit) =
        GalleryEntry(TRAINER_AREA, screen, state, GalleryKind.Sheet, covers = setOf(covers)) {
            Box(Modifier.fillMaxSize()) { content() }
        }

    fun activity(state: String, selected: ActivityLevel?, saving: Boolean = false, online: Boolean = true) =
        sheet(state, ACTIVITY_SHEET, "ActivityLevelSheet") {
            ActivityLevelSheet(
                selected = selected,
                saving = saving,
                online = online,
                onDismiss = {},
                onPick = {},
            )
        }

    fun cardio(state: String, draft: Int, online: Boolean = true) = sheet(state, CARDIO_SHEET, "CardioMinutesSheet") {
        CardioMinutesSheet(
            edit = CardioEdit(draft = draft, saved = 150),
            online = online,
            onChange = {},
            onSave = {},
            onDismiss = {},
        )
    }

    return listOf(
        activity("nothing chosen", selected = null),
        activity("level chosen", selected = ActivityLevel.Moderate),
        activity("saving", selected = ActivityLevel.Moderate, saving = true),
        activity("offline", selected = ActivityLevel.Moderate, online = false),
        cardio("cardio minutes", draft = 150),
        cardio("cardio minutes, changed", draft = 90),
        cardio("cardio minutes offline", draft = 90, online = false),
    )
}

internal fun trainerPlanEntries(): List<GalleryEntry> = planProposal() + beforeStart() + yourData() + planSheets()

/** Renders the Trainer plan, start and data entries (US-131); skipped unless `-Pgallery`. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class TrainerPlanGallery {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rendersTrainerPlan() = rule.renderGallery(trainerPlanEntries().filter { !it.wide && !it.tall })
}
