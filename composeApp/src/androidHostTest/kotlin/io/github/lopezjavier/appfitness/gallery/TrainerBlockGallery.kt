package io.github.lopezjavier.appfitness.gallery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.feature.exercises.SimilarExerciseRow
import io.github.lopezjavier.appfitness.feature.trainer.AlternativesState
import io.github.lopezjavier.appfitness.feature.trainer.BlockPreviewScreen
import io.github.lopezjavier.appfitness.feature.trainer.BlockPreviewUiState
import io.github.lopezjavier.appfitness.feature.trainer.BlockReplaceSheet
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

/** The week summary (WeekSummaryRoute): loading, empty and the failures; data states: TrainerWeekReportGallery. */
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

    return listOf(
        summary("loading", WeekSummaryUiState.Loading),
        summary("empty", WeekSummaryUiState.Empty),
        summary("failed", WeekSummaryUiState.Error),
        summary("offline", WeekSummaryUiState.Offline, online = false),
    )
}

internal fun trainerBlockEntries(): List<GalleryEntry> =
    blockPreview() + blockPreviewSheets() + weekSummary() + trainerWeekReportEntries() + blockReviewEntries()

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
