package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.block_replace_invalid
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_proposal_apply_error
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_proposal_decline_error
import io.github.lopezjavier.appfitness.composeapp.resources.calorie_proposal_offline
import io.github.lopezjavier.appfitness.composeapp.resources.cardio_save_error
import io.github.lopezjavier.appfitness.composeapp.resources.cardio_save_offline
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_decide_error
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_decide_not_pending
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_decide_offline
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_range_unavailable
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_undo_conflict
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_undo_error
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_undo_not_found
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_undo_offline
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_undone_skipped
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_cancel_error
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_cancel_offline
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_request_error
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_request_not_available
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_request_offline
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_diet_break_offline
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_end_error
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_end_offline
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_remove_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_remove_error
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_discomfort_change_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_discomfort_save_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_discomfort_save_error
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_diet_break_error
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_swap_applied
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.offlineHint
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.stringResource

/**
 * Plays the one-off [effects] (haptics, failure messages, navigation) and calls [onRefresh] on resume. Both the
 * Trainer root and "Tu plan" and "Tu bloque" use it, so a message or a haptic fires from whichever of them is on top.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod") // One failure message per effect, wired in one place.
@Composable
internal fun TrainerEffectsHost(effects: Flow<TrainerEffect>, onRefresh: () -> Unit, actions: TrainerActions) {
    val haptics = LocalAppHaptics.current
    val messages = LocalAppMessages.current
    val acceptChangeError = stringResource(Res.string.calorie_proposal_apply_error)
    val discardChangeError = stringResource(Res.string.calorie_proposal_decline_error)
    val acceptOffline = offlineHint(stringResource(Res.string.calorie_proposal_offline)) + "."
    val discardOffline = offlineHint(stringResource(Res.string.calorie_proposal_offline)) + "."
    val cardioError = stringResource(Res.string.cardio_save_error)
    val cardioOffline = offlineHint(stringResource(Res.string.cardio_save_offline)) + "."
    val dietBreakError = stringResource(Res.string.trainer_plan_diet_break_error)
    val dietBreakOffline = offlineHint(stringResource(Res.string.plan_proposal_diet_break_offline)) + "."
    val blockEndError = stringResource(Res.string.trainer_block_end_error)
    val blockEndOffline = offlineHint(stringResource(Res.string.trainer_block_end_offline)) + "."
    val removeBlock = listOf(
        stringResource(Res.string.trainer_block_remove_error),
        offlineHint(stringResource(Res.string.trainer_block_remove_action)) + ".",
    )
    val undoOffline = offlineHint(stringResource(Res.string.coach_change_undo_offline)) + "."
    val undoConflict = stringResource(Res.string.coach_change_undo_conflict)
    val undoNotFound = stringResource(Res.string.coach_change_undo_not_found)
    val undoError = stringResource(Res.string.coach_change_undo_error)
    val decideOffline = offlineHint(stringResource(Res.string.coach_change_decide_offline)) + "."
    val decideNotPending = stringResource(Res.string.coach_change_decide_not_pending)
    val decideError = stringResource(Res.string.coach_change_decide_error)
    val rangeUnavailable = stringResource(Res.string.coach_change_range_unavailable)
    val cancelDeloadOffline = offlineHint(stringResource(Res.string.coach_deload_cancel_offline)) + "."
    val cancelDeloadError = stringResource(Res.string.coach_deload_cancel_error)
    val requestDeloadOffline = offlineHint(stringResource(Res.string.coach_deload_request_offline)) + "."
    val requestDeloadError = stringResource(Res.string.coach_deload_request_error)
    val requestDeloadNotAvailable = stringResource(Res.string.coach_deload_request_not_available)
    val swapApplied = stringResource(Res.string.trainer_swap_applied)
    val swapError = stringResource(Res.string.block_replace_invalid)
    val swapOffline = offlineHint(stringResource(Res.string.trainer_discomfort_change_action)) + "."
    val saveError = stringResource(Res.string.trainer_discomfort_save_error)
    val saveOffline = offlineHint(stringResource(Res.string.trainer_discomfort_save_action)) + "."
    val latestHaptics by rememberUpdatedState(haptics)
    val latestActions by rememberUpdatedState(actions)
    val latestRefresh by rememberUpdatedState(onRefresh)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { latestRefresh() }
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                TrainerEffect.CalorieProposalApplied,
                TrainerEffect.CalorieProposalDeclined,
                TrainerEffect.DeloadCancelled,
                TrainerEffect.DeloadRequested,
                TrainerEffect.CardioSaved,
                TrainerEffect.DietBreakSaved,
                TrainerEffect.BlockRemoved,
                TrainerEffect.BlockEndChanged,
                -> latestHaptics.confirm()

                is TrainerEffect.BlockEndFailed -> {
                    latestHaptics.reject()
                    messages.show(if (effect.offline) blockEndOffline else blockEndError)
                }

                is TrainerEffect.DietBreakFailed -> {
                    latestHaptics.reject()
                    messages.show(if (effect.offline) dietBreakOffline else dietBreakError)
                }

                is TrainerEffect.CalorieProposalFailed -> {
                    latestHaptics.reject()
                    messages.show(
                        when {
                            effect.offline -> if (effect.apply) acceptOffline else discardOffline
                            effect.apply -> acceptChangeError
                            else -> discardChangeError
                        },
                    )
                }

                is TrainerEffect.RemoveBlockFailed -> {
                    latestHaptics.reject()
                    messages.show(removeBlock[if (effect.offline) 1 else 0])
                }

                is TrainerEffect.CardioFailed -> {
                    latestHaptics.reject()
                    messages.show(if (effect.offline) cardioOffline else cardioError)
                }

                is TrainerEffect.FlagDecided -> latestHaptics.confirm()

                // The row turns "Deshecho" by itself; only routines edited since need a message (R-13).
                is TrainerEffect.CoachChangeUndone -> {
                    latestHaptics.confirm()
                    if (effect.skipped > 0) {
                        messages.show(
                            getPluralString(Res.plurals.coach_change_undone_skipped, effect.skipped, effect.skipped),
                        )
                    }
                }

                is TrainerEffect.CoachChangeUndoFailed -> {
                    latestHaptics.reject()
                    messages.show(
                        when {
                            effect.offline -> undoOffline
                            effect.kind == CoachChangeUndoFailure.Conflict -> undoConflict
                            effect.kind == CoachChangeUndoFailure.NotFound -> undoNotFound
                            else -> undoError
                        },
                    )
                }

                is TrainerEffect.FlagDecideFailed -> {
                    latestHaptics.reject()
                    messages.show(
                        when {
                            effect.offline -> decideOffline
                            effect.notPending -> decideNotPending
                            else -> decideError
                        },
                    )
                }

                TrainerEffect.RangeUnavailable -> {
                    latestHaptics.reject()
                    messages.show(rangeUnavailable)
                }

                is TrainerEffect.OpenRoutineRange ->
                    latestActions.onOpenRoutineRange(effect.routineId, effect.exerciseId)

                is TrainerEffect.OpenNewBlock ->
                    latestActions.onOpenBeforeStart(BeforeStartMode.NewBlock, effect.planId, null)

                TrainerEffect.OpenMyTraining -> latestActions.onOpenMyTraining()

                TrainerEffect.SwapApplied -> {
                    latestHaptics.confirm()
                    messages.show(swapApplied)
                }

                is TrainerEffect.SwapFailed -> {
                    latestHaptics.reject()
                    messages.show(if (effect.offline) swapOffline else swapError)
                }

                is TrainerEffect.FollowUpActionFailed -> {
                    latestHaptics.reject()
                    messages.show(if (effect.offline) saveOffline else saveError)
                }

                is TrainerEffect.DeloadCancelFailed -> {
                    latestHaptics.reject()
                    messages.show(if (effect.offline) cancelDeloadOffline else cancelDeloadError)
                }

                is TrainerEffect.DeloadRequestFailed -> {
                    latestHaptics.reject()
                    messages.show(
                        when {
                            effect.offline -> requestDeloadOffline
                            effect.notAvailable -> requestDeloadNotAvailable
                            else -> requestDeloadError
                        },
                    )
                }
            }
        }
    }
}
