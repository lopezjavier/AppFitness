package io.github.lopezjavier.appfitness.gallery

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.feature.trainer.BlockReportUi
import io.github.lopezjavier.appfitness.feature.trainer.BlockReviewScreen
import io.github.lopezjavier.appfitness.feature.trainer.BlockReviewUiState
import io.github.lopezjavier.appfitness.feature.trainer.CalorieChoice
import kotlinx.coroutines.flow.flowOf

private const val BLOCK_REVIEW_SCREEN = "Revisión del bloque"

/**
 * The progress-first block review (BlockReviewRoute, T-318): the states of the shell, every card state (first block,
 * deload last week, records, weight gates, waist), each goal order, the outcome chips and the decisions. The stored
 * review is in TrainerReportsGallery ("Informe pasado").
 */
@Suppress("LongMethod") // A list of fixtures.
internal fun blockReviewEntries(): List<GalleryEntry> {
    val r = BlockReportFixtures

    fun review(
        state: String,
        ui: BlockReviewUiState,
        online: Boolean = true,
        wide: Boolean = false,
        fontScale: Float = 1f,
        tall: Boolean = false,
        interact: (ComposeContentTestRule.() -> Unit)? = null,
    ) = GalleryEntry(
        TRAINER_AREA,
        BLOCK_REVIEW_SCREEN,
        state,
        covers = setOf("BlockReviewRoute"),
        wide = wide,
        tall = tall,
        fontScale = fontScale,
        interact = interact,
    ) {
        BlockReviewScreen(
            state = ui,
            isOnline = flowOf(online),
            onEvent = {},
            onBack = {},
            onOpenBeforeStart = { _, _, _ -> },
        )
    }

    // C-192: a state below the first screen is rendered on the tall window so the image shows it.
    fun card(
        state: String,
        report: BlockReportUi,
        wide: Boolean = false,
        fontScale: Float = 1f,
        tall: Boolean = !wide &&
            fontScale == 1f,
    ) = review(state, r.content(report), wide = wide, fontScale = fontScale, tall = tall)

    return listOf(
        review("loading", BlockReviewUiState.Loading),
        review("empty, no block ended", BlockReviewUiState.Empty),
        review("failed", BlockReviewUiState.Error),
        review("offline", BlockReviewUiState.Offline, online = false),
        review("all states, phase decision open (muscle order)", r.content(r.report()), tall = true),
        review("phase decision in flight", r.content(r.report(), deciding = PhaseDecision.Accepted), tall = true),
        review("phase decision failed", r.content(r.report(), decisionFailed = PhaseDecision.Kept), tall = true),
        review("phase decided, create next", r.content(r.createNext, decision = PhaseDecision.Accepted), tall = true),
        review(
            "calorie proposal pending",
            r.content(r.calorieAction, decision = PhaseDecision.Kept, calorie = true),
            tall = true,
        ),
        review(
            "stall pending, coach points to it",
            r.content(r.stallAction, decision = PhaseDecision.Kept, stalled = true),
            tall = true,
        ),
        card("first block (own baseline, weight gate)", r.firstBlock),
        card("deload in the last week", r.deloadLast),
        card("no records", r.noRecords),
        card("records beyond the cap (3 of 7)", r.manyRecords),
        card("weight, under the gate", r.gate),
        card("weight, no weigh-ins", r.noWeighIns),
        card("weight, same as the start", r.sameAsStart),
        card("waist", r.withWaist),
        card("order, fat loss (weight first, Mantienes)", r.cut),
        card("order, strength", r.order(TrainingIntention.Strength)),
        card("order, maintenance (strength, waist, weight)", r.order(TrainingIntention.Maintenance, waist = true)),
        card("order, recomposition (strength, waist, weight)", r.order(TrainingIntention.Recomposition, waist = true)),
        card("v1 review without progress", r.v1Live),
        review(
            "group sheet open",
            r.content(r.report()),
            interact = { onNodeWithTag("block_review_strength_group_chest").performClick() },
        ),
        review(
            "calorie proposal failed",
            r.content(r.calorieAction, calorie = true).copy(calorieFailed = CalorieChoice.Apply),
            tall = true,
        ),
        card("large font (1.3)", r.report(), fontScale = 1.3f),
        card("large font (2.0)", r.report(), fontScale = 2.0f),
        card("tablet width", r.report(), wide = true),
    )
}
