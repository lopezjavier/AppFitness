package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_not_now
import io.github.lopezjavier.appfitness.composeapp.resources.block_replace_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_swap
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_discomfort_change_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_discomfort_note_days14
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_discomfort_note_sessions3
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_discomfort_offer_change
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_discomfort_offer_reason
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_discomfort_offer_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_swap_picker_empty
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_swap_picker_hint
import io.github.lopezjavier.appfitness.designsystem.AppPickerSheet
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.CoachProposalCard
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ExerciseRow
import io.github.lopezjavier.appfitness.designsystem.InlineNote
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.DiscomfortNoteReason
import org.jetbrains.compose.resources.stringResource

private const val SKELETON_ROWS = 3

/**
 * Swap offers and health notes of the persistent level 4-5 discomforts (US-108), on the Trainer root below the
 * welcome-back card. Both also show outside a block as far as the server says so. One note and one offer per
 * exercise; the tags carry no exercise id because a single flagged exercise is the normal case.
 */
@Composable
internal fun DiscomfortFollowUpsSection(
    state: TrainerUiState.Content,
    online: Boolean,
    onEvent: (TrainerEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    state.discomfortFollowUps.forEach { item ->
        if (item.showNote) DiscomfortNote(item, onEvent)
        if (item.offerSwap) SwapOfferCard(item, online, onEvent)
    }
    state.swapPicker?.let { SwapPickerSheet(it, state.busyFollowUpExerciseId != null, onEvent, onOpenExercise) }
}

@Composable
private fun DiscomfortNote(item: DiscomfortFollowUpUi, onEvent: (TrainerEvent) -> Unit) {
    val text = when (item.noteReason) {
        DiscomfortNoteReason.Sessions3 -> stringResource(
            Res.string.trainer_discomfort_note_sessions3,
            item.exerciseName,
        )

        else -> stringResource(Res.string.trainer_discomfort_note_days14, item.exerciseName)
    }
    InlineNote(
        text = text,
        testTag = "trainer_discomfort_note",
        onDismiss = { onEvent(TrainerEvent.DismissDiscomfortNote(item.exerciseId)) },
        dismissTestTag = "trainer_discomfort_note_close",
    )
}

@Composable
private fun SwapOfferCard(item: DiscomfortFollowUpUi, online: Boolean, onEvent: (TrainerEvent) -> Unit) {
    CoachProposalCard(
        acceptText = stringResource(Res.string.trainer_discomfort_offer_change),
        discardText = stringResource(Res.string.action_not_now),
        onAccept = { onEvent(TrainerEvent.OpenSwapPicker(item.exerciseId)) },
        onDiscard = { onEvent(TrainerEvent.DeclineSwap(item.exerciseId)) },
        testTagPrefix = "trainer_discomfort_offer_card",
        acceptEnabled = online,
        footer = if (online) null else ({ OfflineHint(stringResource(Res.string.trainer_discomfort_change_action)) }),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = stringResource(Res.string.trainer_discomfort_offer_title, item.exerciseName),
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(Res.string.trainer_discomfort_offer_reason, item.exerciseName),
                    style = AppTheme.typography.bodyLarge,
                    color = AppTheme.colors.textPrimary,
                )
            }
        },
    )
}

/** The candidates in the server's order (favourites first); a tap applies the swap and the caller closes the sheet. */
@Composable
private fun SwapPickerSheet(
    picker: SwapPickerUi,
    busy: Boolean,
    onEvent: (TrainerEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    AppPickerSheet(
        title = stringResource(Res.string.block_replace_title),
        onDismiss = { onEvent(TrainerEvent.DismissSwapPicker) },
        testTagPrefix = "trainer_swap_picker",
    ) {
        Column(
            Modifier.padding(horizontal = Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(Res.string.trainer_swap_picker_hint),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("trainer_swap_picker_hint"),
            )
            when {
                picker.loading -> Column(
                    Modifier.fillMaxWidth().testTag("trainer_swap_picker_loading"),
                    verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
                ) {
                    repeat(SKELETON_ROWS) { SkeletonBox(Modifier.fillMaxWidth().padding(vertical = Spacing.lg)) }
                }

                picker.candidates.isEmpty() -> EmptyState(
                    icon = Res.drawable.ic_swap,
                    message = stringResource(Res.string.trainer_swap_picker_empty),
                    testTagPrefix = "trainer_swap_picker_empty",
                )

                else -> picker.candidates.forEach { candidate ->
                    ExerciseRow(
                        name = candidate.name,
                        supporting = null,
                        onOpen = { onOpenExercise(candidate.exerciseId) },
                        tagPrefix = "trainer_swap_picker_row_${candidate.exerciseId}",
                        onSelect = { onEvent(TrainerEvent.PickSwap(candidate.exerciseId)) },
                        favorite = candidate.isFavorite,
                        enabled = !busy,
                    )
                }
            }
        }
    }
}
