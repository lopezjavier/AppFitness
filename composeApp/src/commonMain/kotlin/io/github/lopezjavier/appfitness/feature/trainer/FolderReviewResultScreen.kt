package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_error
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_complete
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_complete_strength
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_empty_body
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_empty_title
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_help_title
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_met
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_score_label
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_score_state
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_summary
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_summary_plain
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_title
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_unmet
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import io.github.lopezjavier.appfitness.composeapp.resources.ic_warning
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.ScoreRing
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val SKELETON_ROWS = 4

/** Stateful "Revisar tus rutinas" (US-95): the score and checklist of a folder, computed on the device. */
@Composable
fun FolderReviewResultRoot(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FolderReviewResultViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FolderReviewResultScreen(state, viewModel::onEvent, onBack, modifier)
}

/**
 * "Revisar tus rutinas" on [AppScreen] with back: a [ScoreRing] card (with the "how it is calculated" help) and one
 * read-only [AppListRow] per checklist row, met or to improve. Nothing is tappable, nothing is written or blocked.
 * Loading is a skeleton, an empty folder an [EmptyState] and a failed load an [ErrorState] with "Reintentar" (R-31).
 */
@Composable
fun FolderReviewResultScreen(
    state: FolderReviewResultUiState,
    onEvent: (FolderReviewResultEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScreen(
        title = stringResource(Res.string.folder_review_result_title),
        modifier = modifier,
        onBack = onBack,
        testTagPrefix = "folder_review_result",
    ) {
        Box(Modifier.fillMaxSize().testTag("folder_review_result_screen")) {
            when (state) {
                FolderReviewResultUiState.Loading -> ResultSkeleton()

                FolderReviewResultUiState.Empty -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    EmptyState(
                        message = stringResource(Res.string.folder_review_result_empty_body),
                        title = stringResource(Res.string.folder_review_result_empty_title),
                        testTagPrefix = "folder_review_result_empty",
                    )
                }

                FolderReviewResultUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    ErrorState(
                        message = stringResource(Res.string.folder_review_error),
                        action = AppStateAction(stringResource(Res.string.action_retry)) {
                            onEvent(FolderReviewResultEvent.Retry)
                        },
                        testTagPrefix = "folder_review_result_error",
                        actionTestTag = "folder_review_result_retry",
                    )
                }

                is FolderReviewResultUiState.Content -> ResultContent(state)
            }
        }
    }
}

@Composable
private fun ResultContent(state: FolderReviewResultUiState.Content) {
    Box(Modifier.fillMaxSize(), Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            ScoreCard(state)
            Column(Modifier.testTag("folder_review_result_rows")) {
                state.items.forEach { item -> ReviewRow(item) }
            }
        }
    }
}

@Composable
private fun ScoreCard(state: FolderReviewResultUiState.Content) {
    val band = stringResource(state.band.label)
    AppCard(Modifier.fillMaxWidth(), highlighted = state.band == ScoreBand.Complete) {
        Box(Modifier.fillMaxWidth()) {
            HelpButton(
                contentDescription = stringResource(Res.string.folder_review_result_help_cd),
                title = stringResource(Res.string.folder_review_result_help_title),
                body = stringResource(Res.string.folder_review_result_help_body),
                testTag = "folder_review_result_score_help",
                modifier = Modifier.align(Alignment.TopEnd),
            )
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ScoreRing(
                    score = state.score,
                    bandLabel = band,
                    description = stringResource(Res.string.folder_review_result_score_label),
                    stateText = stringResource(
                        Res.string.folder_review_result_score_state,
                        AppNumberFormat.oneDecimal(state.score),
                        band.lowercase(),
                    ),
                    modifier = Modifier.testTag("folder_review_result_score"),
                    valueTag = "folder_review_result_score_value",
                    bandTag = "folder_review_result_score_band",
                )
                Text(
                    text = summary(state),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = Spacing.md)
                        .testTag(
                            if (state.complete) "folder_review_result_complete" else "folder_review_result_summary",
                        ),
                )
            }
        }
    }
}

@Composable
private fun summary(state: FolderReviewResultUiState.Content): String = when {
    state.complete -> {
        val base = stringResource(Res.string.folder_review_result_complete, state.liftingRoutines)
        if (state.strength) base + " " + stringResource(Res.string.folder_review_result_complete_strength) else base
    }

    state.folderName != null -> stringResource(Res.string.folder_review_result_summary, state.folderName)

    else -> stringResource(Res.string.folder_review_result_summary_plain)
}

/** A read-only row: lime check when met, error warning when not; its state is spoken ("Cumplido" / "Por mejorar"). */
@Composable
private fun ReviewRow(item: ReviewItem) {
    val stateText = stringResource(
        if (item.met) Res.string.folder_review_result_met else Res.string.folder_review_result_unmet,
    )
    AppListRow(
        headline = item.headline(),
        supporting = item.supporting(),
        leading = {
            Icon(
                painter = painterResource(if (item.met) Res.drawable.ic_check else Res.drawable.ic_warning),
                contentDescription = null,
                tint = if (item.met) AppTheme.colors.neonLime else AppTheme.colors.error,
                modifier = Modifier.size(Spacing.xl),
            )
        },
        modifier = Modifier
            .testTag("folder_review_result_row_${item.tagSuffix}")
            .semantics { stateDescription = stateText },
    )
}

@Composable
private fun ResultSkeleton() {
    AppCard(Modifier.fillMaxWidth().padding(Spacing.screenMargin).testTag("folder_review_result_loading")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SkeletonBox(Modifier.fillMaxWidth().height(Spacing.minTouchTarget))
            repeat(SKELETON_ROWS) { SkeletonBox(Modifier.fillMaxWidth().height(Spacing.minTouchTarget)) }
        }
    }
}
