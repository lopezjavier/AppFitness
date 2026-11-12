package io.github.lopezjavier.appfitness.gallery

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import io.github.lopezjavier.appfitness.feature.trainer.BlockSummary
import io.github.lopezjavier.appfitness.feature.trainer.TrainerBox
import io.github.lopezjavier.appfitness.feature.trainer.TrainerBoxScreen
import io.github.lopezjavier.appfitness.feature.trainer.TrainerScreen
import io.github.lopezjavier.appfitness.feature.trainer.TrainerUiState
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

internal const val TRAINER_AREA = "Trainer"
private const val TRAINER_SCREEN = "Trainer"
private const val PLAN_BOX_SCREEN = "Tu plan"
private const val BLOCK_BOX_SCREEN = "Tu bloque"

/** The Trainer hub (TrainerHome): every state the root can show, with the plan and block entries. */
@Suppress("LongMethod") // A list of fixtures.
private fun trainerHome(): List<GalleryEntry> {
    fun home(state: String, ui: TrainerUiState, online: Boolean = true, wide: Boolean = false, fontScale: Float = 1f) =
        GalleryEntry(
            TRAINER_AREA,
            TRAINER_SCREEN,
            state,
            covers = setOf("TrainerHome"),
            wide = wide,
            fontScale = fontScale,
        ) {
            TrainerScreen(state = ui, isOnline = flowOf(online), onEvent = {})
        }

    return listOf(
        home("loading", TrainerUiState.Loading),
        home("no plan yet", TrainerFixtures.contentWithoutPlan()),
        home("proposal waiting for a decision", TrainerFixtures.contentWithProposal()),
        home("plan and block active", TrainerFixtures.contentWithBlock()),
        home("block paused", TrainerFixtures.contentWithBlock(pause = TrainerFixtures.pausedBlock)),
        home("block review due", TrainerFixtures.contentWithBlock(blockReviewDue = true)),
        home("calorie proposal", TrainerFixtures.contentWithBlock(proposal = TrainerFixtures.calorieProposal)),
        home("offline", TrainerFixtures.contentWithBlock(), online = false),
        home("large font (1.3)", TrainerFixtures.contentWithBlock(), fontScale = 1.3f),
        home("tablet width", TrainerFixtures.contentWithBlock(), wide = true),
    )
}

/** The "Tu plan" and "Tu bloque" boxes (TrainerPlanRoute, TrainerBlockRoute) opened from the hub. */
@Suppress("LongMethod") // A list of fixtures.
private fun trainerBoxes(): List<GalleryEntry> {
    @Composable
    fun TrainerBoxEntry(box: TrainerBox, ui: TrainerUiState, online: Boolean = true) {
        TrainerBoxScreen(box = box, state = ui, isOnline = flowOf(online), onEvent = {}, onBack = {})
    }

    fun plan(state: String, ui: TrainerUiState, online: Boolean = true) = GalleryEntry(
        TRAINER_AREA,
        PLAN_BOX_SCREEN,
        state,
        covers = setOf("TrainerPlanRoute"),
    ) { TrainerBoxEntry(TrainerBox.Plan, ui, online) }

    fun block(
        state: String,
        ui: TrainerUiState,
        online: Boolean = true,
        kind: GalleryKind = GalleryKind.Screen,
        vararg covers: String,
    ) = GalleryEntry(
        TRAINER_AREA,
        BLOCK_BOX_SCREEN,
        state,
        kind,
        covers = setOf("TrainerBlockRoute") + covers,
    ) { TrainerBoxEntry(TrainerBox.Block, ui, online) }

    return listOf(
        plan("accepted plan", TrainerFixtures.contentWithBlock()),
        plan("accepted plan offline", TrainerFixtures.contentWithBlock(), online = false),
        plan("proposal pending", TrainerFixtures.contentWithProposal()),
        plan("loading", TrainerUiState.Loading),
        plan("diet break switch saving", TrainerFixtures.contentWithBlock().copy(dietBreakSaving = true)),
        block("saved block", TrainerFixtures.contentWithBlock()),
        block("current week paused", TrainerFixtures.contentWithBlock(pause = TrainerFixtures.pausedBlock)),
        block("current week extended", TrainerFixtures.contentWithBlock(pause = TrainerFixtures.extendedBlock)),
        block(
            "block not saved",
            TrainerFixtures.contentWithBlock(block = BlockSummary.Proposed(TrainerFixtures.block(saved = false))),
        ),
        block(
            "week end picker open",
            TrainerFixtures.contentWithBlock(pause = TrainerFixtures.extendedBlock)
                .copy(extendWeekPickerOpen = true),
            kind = GalleryKind.Sheet,
            covers = arrayOf("ExtendWeekPicker"),
        ),
        block(
            "remove block confirmation",
            TrainerFixtures.contentWithBlock().copy(removeBlockConfirm = true),
            kind = GalleryKind.Dialog,
            covers = arrayOf("SavedBlockActions"),
        ),
        block("offline", TrainerFixtures.contentWithBlock(), online = false),
        block("loading", TrainerUiState.Loading),
    )
}

/** The swap picker of a flagged exercise, opened from the hub's discomfort follow-up (US-108). */
private fun swapPicker(): List<GalleryEntry> = listOf(
    GalleryEntry(
        TRAINER_AREA,
        TRAINER_SCREEN,
        "swap picker, candidates",
        GalleryKind.Sheet,
        covers = setOf("TrainerHome", "SwapPickerSheet"),
    ) {
        TrainerScreen(
            state = TrainerFixtures.contentWithBlock().copy(swapPicker = TrainerFixtures.swapPicker()),
            isOnline = flowOf(true),
            onEvent = {},
        )
    },
    GalleryEntry(
        TRAINER_AREA,
        TRAINER_SCREEN,
        "swap picker, loading",
        GalleryKind.Sheet,
        covers = setOf("TrainerHome", "SwapPickerSheet"),
    ) {
        TrainerScreen(
            state = TrainerFixtures.contentWithBlock().copy(swapPicker = TrainerFixtures.swapPicker(loading = true)),
            isOnline = flowOf(true),
            onEvent = {},
        )
    },
)

internal fun trainerRootEntries(): List<GalleryEntry> = trainerHome() + trainerBoxes() + swapPicker()

/** Renders the Trainer hub and its boxes (US-131); skipped unless `-Pgallery`. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class TrainerRootGallery {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rendersTrainerRoot() = rule.renderGallery(trainerRootEntries().filter { !it.wide && !it.tall })
}
