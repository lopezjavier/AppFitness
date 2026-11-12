package io.github.lopezjavier.appfitness.gallery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.feature.exercises.SimilarExerciseRow
import io.github.lopezjavier.appfitness.feature.trainer.AlternativesState
import io.github.lopezjavier.appfitness.feature.trainer.BlockPreviewScreen
import io.github.lopezjavier.appfitness.feature.trainer.BlockPreviewUiState
import io.github.lopezjavier.appfitness.feature.trainer.BlockReplaceSheet
import io.github.lopezjavier.appfitness.feature.trainer.BlockReviewScreen
import io.github.lopezjavier.appfitness.feature.trainer.BlockReviewUiState
import io.github.lopezjavier.appfitness.feature.trainer.CalorieChoice
import io.github.lopezjavier.appfitness.feature.trainer.ExerciseReasonSheet
import io.github.lopezjavier.appfitness.feature.trainer.PlanFailureKind
import io.github.lopezjavier.appfitness.feature.trainer.Replacement
import io.github.lopezjavier.appfitness.feature.trainer.WeekSummaryScreen
import io.github.lopezjavier.appfitness.feature.trainer.WeekSummaryUiState
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val BLOCK_PREVIEW_SCREEN = "Vista previa del bloque"
private const val WEEK_SUMMARY_SCREEN = "Resumen semanal"
private const val BLOCK_REVIEW_SCREEN = "Revisión del bloque"
private const val REASON_SHEET = "Por qué este ejercicio"

/** The saved block preview (BlockPreviewRoute): every week tab, its failures and its replace and reason sheets. */
@Suppress("LongMethod") // A list of fixtures.
private fun blockPreview(): List<GalleryEntry> {
    fun preview(state: String, ui: BlockPreviewUiState, online: Boolean = true, wide: Boolean = false) =
        GalleryEntry(TRAINER_AREA, BLOCK_PREVIEW_SCREEN, state, covers = setOf("BlockPreviewRoute"), wide = wide) {
            BlockPreviewScreen(
                state = ui,
                isOnline = flowOf(online),
                onEvent = {},
                onBack = {},
                onOpenExercise = {},
            )
        }

    val block = BlockPreviewUiState.Preview(
        block = TrainerFixtures.block(saved = false),
        exerciseNames = TrainerFixtures.exerciseNames,
        selectedWeek = 1,
    )
    return listOf(
        preview("loading", BlockPreviewUiState.Loading),
        preview("failed, timeout", BlockPreviewUiState.Failed(PlanFailureKind.Timeout)),
        preview("failed, offline", BlockPreviewUiState.Failed(PlanFailureKind.Offline), online = false),
        preview("weeks, first tab", block),
        preview("weeks, second tab", block.copy(selectedWeek = 2)),
        preview("offline", block, online = false),
        preview("tablet width", block, wide = true),
        preview(
            "replaced exercise",
            block.copy(
                replacements = mapOf(
                    "0/0" to Replacement("fly", "key-fly", Equipment.Dumbbell),
                ),
            ),
        ),
        preview(
            "reason of an exercise",
            block.copy(reasonKey = "1/0/0"),
            wide = false,
        ),
    )
}

/** The sheets of the block preview: the exercise reason and the replace picker, rendered on their own state. */
private fun blockPreviewSheets(): List<GalleryEntry> {
    fun sheet(state: String, covers: String, content: @Composable () -> Unit) =
        GalleryEntry(TRAINER_AREA, BLOCK_PREVIEW_SCREEN, state, GalleryKind.Sheet, covers = setOf(covers)) {
            Box(Modifier.fillMaxSize()) { content() }
        }

    fun replace(state: String, alternatives: AlternativesState) = sheet(state, "BlockReplaceSheet") {
        BlockReplaceSheet(
            state = TrainerFixtures.replaceState(alternatives),
            isOnline = flowOf(true),
            onEvent = {},
            onOpenExercise = {},
        )
    }

    return listOf(
        sheet("reason", "ExerciseReasonSheet") {
            ExerciseReasonSheet(title = REASON_SHEET, reason = "Trabaja el pecho con poca fatiga.", onDismiss = {})
        },
        replace("alternatives loading", AlternativesState.Loading),
        replace("alternatives", AlternativesState.Content(listOf(SimilarExerciseRow(TrainerFixtures.fly, 0.87)))),
        replace("no alternatives", AlternativesState.Empty),
        replace("alternatives offline", AlternativesState.Offline),
        replace("alternatives failed", AlternativesState.Error),
    )
}

/** The week summary (WeekSummaryRoute): data, empty and the failures. */
private fun weekSummary(): List<GalleryEntry> {
    fun summary(state: String, ui: WeekSummaryUiState, online: Boolean = true, wide: Boolean = false) = GalleryEntry(
        TRAINER_AREA,
        WEEK_SUMMARY_SCREEN,
        state,
        covers = setOf("WeekSummaryRoute"),
        wide = wide,
    ) {
        WeekSummaryScreen(state = ui, isOnline = flowOf(online), onEvent = {}, onBack = {})
    }

    val content = WeekSummaryUiState.Content(
        TrainerFixtures.weekSummary(),
        exerciseNames = TrainerFixtures.exerciseNames,
    )
    return listOf(
        summary("loading", WeekSummaryUiState.Loading),
        summary("data", content),
        summary(
            "data, seen",
            WeekSummaryUiState.Content(TrainerFixtures.weekSummary(seen = true), TrainerFixtures.exerciseNames),
        ),
        summary("empty", WeekSummaryUiState.Empty),
        summary("failed", WeekSummaryUiState.Error),
        summary(
            "nutrition, five complete days",
            WeekSummaryUiState.Content(
                TrainerFixtures.weekSummary(nutrition = TrainerFixtures.nutrition(complete = true)),
                TrainerFixtures.exerciseNames,
            ),
        ),
        summary(
            "nutrition, incomplete week",
            WeekSummaryUiState.Content(
                TrainerFixtures.weekSummary(nutrition = TrainerFixtures.nutrition(complete = false)),
                TrainerFixtures.exerciseNames,
            ),
        ),
        summary("offline", WeekSummaryUiState.Offline, online = false),
        summary("tablet width", content, wide = true),
    )
}

/** The block review (BlockReviewRoute): the open decision, the decided and failed states and the calorie card. */
private fun blockReview(): List<GalleryEntry> {
    fun review(state: String, ui: BlockReviewUiState, online: Boolean = true, wide: Boolean = false) = GalleryEntry(
        TRAINER_AREA,
        BLOCK_REVIEW_SCREEN,
        state,
        covers = setOf("BlockReviewRoute"),
        wide = wide,
    ) {
        BlockReviewScreen(
            state = ui,
            isOnline = flowOf(online),
            onEvent = {},
            onBack = {},
            onOpenBeforeStart = { _, _, _ -> },
        )
    }

    fun content(
        decision: PhaseDecision? = null,
        deciding: PhaseDecision? = null,
        decisionFailed: PhaseDecision? = null,
        calorieFailed: CalorieChoice? = null,
        calorie: Boolean = false,
        stalled: Boolean = false,
    ) = BlockReviewUiState.Content(
        review = TrainerFixtures.blockReview(decision = decision, stalled = stalled),
        exerciseNames = TrainerFixtures.exerciseNames,
        calorieProposal = if (calorie) TrainerFixtures.calorieProposal else null,
        deciding = deciding,
        decisionFailed = decisionFailed,
        calorieFailed = calorieFailed,
    )

    return listOf(
        review("loading", BlockReviewUiState.Loading),
        review("empty, no block ended", BlockReviewUiState.Empty),
        review("failed", BlockReviewUiState.Error),
        review("offline", BlockReviewUiState.Offline, online = false),
        review("phase decision open", content()),
        review("phase decision in flight", content(deciding = PhaseDecision.Accepted)),
        review("phase decision failed", content(decisionFailed = PhaseDecision.Kept)),
        review("phase decided", content(decision = PhaseDecision.Accepted)),
        review("calorie proposal", content(decision = PhaseDecision.Kept, calorie = true)),
        review("calorie proposal failed", content(calorie = true, calorieFailed = CalorieChoice.Apply)),
        review("stalled lift, not achieved", content(stalled = true)),
        review("tablet width", content(), wide = true),
    )
}

internal fun trainerBlockEntries(): List<GalleryEntry> =
    blockPreview() + blockPreviewSheets() + weekSummary() + blockReview()

/** Renders the Trainer block, review and week entries (US-131); skipped unless `-Pgallery`. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class TrainerBlockGallery {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rendersTrainerBlock() = rule.renderGallery(trainerBlockEntries().filter { !it.wide && !it.tall })
}
