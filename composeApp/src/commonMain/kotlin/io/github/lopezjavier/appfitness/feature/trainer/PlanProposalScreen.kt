package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_accept
import io.github.lopezjavier.appfitness.composeapp.resources.action_discard
import io.github.lopezjavier.appfitness.composeapp.resources.macro_carbs
import io.github.lopezjavier.appfitness.composeapp.resources.macro_fat
import io.github.lopezjavier.appfitness.composeapp.resources.macro_protein
import io.github.lopezjavier.appfitness.composeapp.resources.plan_outcome_deadline
import io.github.lopezjavier.appfitness.composeapp.resources.plan_outcome_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.plan_outcome_help_title
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_accept_error
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_accept_offline
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_accept_reason
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_accepted
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_calories
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_discard_error
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_discard_offline
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_distribution
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_error
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_floor_note
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_grams
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_interrupted
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_kcal_day
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_outcome_e1rm
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_outcome_weight
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_outcomes_header
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_progress
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_progress_cd
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_rate
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_rate_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_rate_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_rate_value
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_confirm_error
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_reask
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.GhostTone
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.InlineNote
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.Macro
import io.github.lopezjavier.appfitness.designsystem.MacroDefaults
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StreamProgressState
import io.github.lopezjavier.appfitness.designsystem.offlineHint
import io.github.lopezjavier.appfitness.domain.model.OutcomeKind
import io.github.lopezjavier.appfitness.domain.model.PlanLimit
import io.github.lopezjavier.appfitness.domain.model.PlanOutcome
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.feature.weight.decimalText
import io.github.lopezjavier.appfitness.feature.weight.signedTenths
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val TITLE_FRACTION = 0.4f
private const val SKELETON_ROWS = 3

/**
 * Stateful plan proposal (US-69): asks the coach once when it opens with no stored plan, plays the one-off effects
 * (haptics, the accept message, leaving the screen) and connects [PlanProposalViewModel] to [PlanProposalScreen].
 */
@Composable
fun PlanProposalRoot(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlanProposalViewModel = koinViewModel(),
    connectivity: ConnectivityObserver = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalAppHaptics.current
    val messages = LocalAppMessages.current
    val latestHaptics by rememberUpdatedState(haptics)
    val latestOnBack by rememberUpdatedState(onBack)
    val accepted = stringResource(Res.string.plan_proposal_accepted)
    val confirmError = stringResource(Res.string.trainer_confirm_error)
    val acceptError = stringResource(Res.string.plan_proposal_accept_error)
    val discardError = stringResource(Res.string.plan_proposal_discard_error)
    val acceptOffline = offlineHint(stringResource(Res.string.plan_proposal_accept_offline)) + "."
    val discardOffline = offlineHint(stringResource(Res.string.plan_proposal_discard_offline)) + "."
    LaunchedEffect(state) {
        if (state == PlanProposalUiState.Idle) viewModel.onEvent(PlanProposalEvent.Request)
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                PlanProposalEffect.ValuesConfirmed -> latestHaptics.confirm()

                PlanProposalEffect.Accepted -> {
                    latestHaptics.confirm()
                    messages.show(accepted)
                    latestOnBack()
                }

                PlanProposalEffect.Discarded -> {
                    latestHaptics.confirm()
                    latestOnBack()
                }

                is PlanProposalEffect.ActionFailed -> {
                    latestHaptics.reject()
                    messages.show(
                        when (effect.action) {
                            PlanAction.Confirm -> confirmError
                            PlanAction.Accept -> if (effect.offline) acceptOffline else acceptError
                            PlanAction.Discard -> if (effect.offline) discardOffline else discardError
                        },
                    )
                }
            }
        }
    }
    PlanProposalScreen(
        state = state,
        isOnline = connectivity.isOnline,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * The nutrition plan proposal on [AppScreen] with back: the derived values behind it (with "Confirmar"), the calories
 * and macros, the weekly rate, the notes and the optional outcomes, and a pinned "Aceptar" / "Descartar" footer. The
 * plan is read-only: the user never enters a target number (ADR-78). Loading the stored plan is a skeleton, waiting
 * for the model is a [StreamProgressState] (R-31).
 */
@Composable
fun PlanProposalScreen(
    state: PlanProposalUiState,
    isOnline: Flow<Boolean>,
    onEvent: (PlanProposalEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val online by isOnline.collectAsStateWithLifecycle(initialValue = true)
    AppScreen(
        title = stringResource(Res.string.plan_proposal_title),
        modifier = modifier,
        onBack = onBack,
        footer = (state as? PlanProposalUiState.Proposal)?.let { { ProposalFooter(it, online, onEvent) } },
        testTagPrefix = "plan_proposal",
    ) {
        Box(Modifier.fillMaxSize().testTag("plan_proposal_screen")) {
            when (state) {
                PlanProposalUiState.Loading, PlanProposalUiState.Idle, PlanProposalUiState.Closed -> ProposalSkeleton()

                PlanProposalUiState.Requesting -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    StreamProgressState(
                        message = stringResource(Res.string.plan_proposal_progress),
                        stateDescription = stringResource(Res.string.plan_proposal_progress_cd),
                        testTag = "plan_proposal_progress",
                    )
                }

                is PlanProposalUiState.Failed -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    FailedContent(state.kind, isOnline, onEvent)
                }

                is PlanProposalUiState.Proposal -> ProposalContent(state, online, onEvent)
            }
        }
    }
}

private val PlanFailureCopy = CoachFailureCopy(
    error = Res.string.plan_proposal_error,
    interrupted = Res.string.plan_proposal_interrupted,
)

@Composable
private fun FailedContent(kind: PlanFailureKind, isOnline: Flow<Boolean>, onEvent: (PlanProposalEvent) -> Unit) =
    CoachFailureContent(kind, isOnline, PlanFailureCopy, "plan_proposal") { onEvent(PlanProposalEvent.Retry) }

@Composable
private fun ProposalContent(
    state: PlanProposalUiState.Proposal,
    online: Boolean,
    onEvent: (PlanProposalEvent) -> Unit,
) {
    val nutrition = state.plan.nutrition
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        DerivedValuesCard(
            values = state.values,
            confirming = state.confirming,
            online = online,
            strategy = nutrition.strategy,
            onEdit = null,
            onConfirm = { onEvent(PlanProposalEvent.Confirm) },
        )
        NutritionCard(state)
        if (nutrition.limitedBy == PlanLimit.Floor) {
            InlineNote(
                text = stringResource(
                    Res.string.plan_proposal_floor_note,
                    AppNumberFormat.integer(nutrition.floorKcal ?: nutrition.targetKcal),
                ),
                testTag = "plan_proposal_floor_note",
            )
        }
        nutrition.distribution?.let {
            InlineNote(
                text = stringResource(Res.string.plan_proposal_distribution, it.perMealG, it.minMeals),
                testTag = "plan_proposal_distribution",
            )
        }
        if (state.plan.outcomes.isNotEmpty()) OutcomesSection(state.plan.outcomes)
    }
}

@Composable
private fun NutritionCard(state: PlanProposalUiState.Proposal) {
    val nutrition = state.plan.nutrition
    AppCard(Modifier.fillMaxWidth().testTag("plan_proposal_nutrition")) {
        Text(
            text = stringResource(Res.string.plan_proposal_calories),
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(Res.string.plan_proposal_kcal_day, AppNumberFormat.integer(nutrition.targetKcal)),
            style = AppTheme.typography.titleLarge,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.testTag("plan_proposal_kcal"),
        )
        MacroRow(Macro.Protein, Res.string.macro_protein, nutrition.proteinG, "plan_proposal_macro_protein")
        MacroRow(Macro.Carbs, Res.string.macro_carbs, nutrition.carbsG, "plan_proposal_macro_carbs")
        MacroRow(Macro.Fat, Res.string.macro_fat, nutrition.fatG, "plan_proposal_macro_fat")
        AppListRow(
            headline = stringResource(Res.string.plan_proposal_rate),
            modifier = Modifier.testTag("plan_proposal_rate"),
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.plan_proposal_rate_value, signedTenths(nutrition.rateKgWeek)),
                        style = AppTheme.typography.bodyLarge,
                        color = AppTheme.colors.textPrimary,
                    )
                    HelpButton(
                        contentDescription = stringResource(Res.string.plan_proposal_rate_help_cd),
                        title = stringResource(Res.string.plan_proposal_rate),
                        body = stringResource(Res.string.plan_proposal_rate_help_body),
                        testTag = "plan_proposal_rate_help",
                    )
                }
            },
        )
    }
}

@Composable
private fun MacroRow(macro: Macro, label: StringResource, grams: Int, tag: String) {
    AppListRow(
        headline = stringResource(label),
        modifier = Modifier.testTag(tag),
        trailing = {
            Text(
                text = stringResource(Res.string.plan_proposal_grams, AppNumberFormat.integer(grams)),
                style = AppTheme.typography.bodyLarge,
                color = MacroDefaults.color(macro, AppTheme.colors),
            )
        },
    )
}

@Composable
private fun OutcomesSection(outcomes: List<PlanOutcome>) {
    Column(Modifier.testTag("plan_proposal_outcomes")) {
        ListSectionHeader(stringResource(Res.string.plan_proposal_outcomes_header))
        outcomes.forEach { outcome ->
            val value = decimalText(outcome.targetValue)
            AppListRow(
                headline = stringResource(
                    if (outcome.kind == OutcomeKind.Weight) {
                        Res.string.plan_proposal_outcome_weight
                    } else {
                        Res.string.plan_proposal_outcome_e1rm
                    },
                    value,
                ),
                modifier = Modifier.testTag("plan_proposal_outcome_${outcome.key}"),
                supporting = stringResource(Res.string.plan_outcome_deadline, AppDateFormat.title(outcome.deadline)),
                trailing = {
                    HelpButton(
                        contentDescription = stringResource(Res.string.plan_outcome_help_cd),
                        title = stringResource(Res.string.plan_outcome_help_title),
                        body = outcome.reason,
                        testTag = "plan_proposal_outcome_help_${outcome.key}",
                    )
                },
            )
        }
    }
}

/** Pinned footer: why "Aceptar" is blocked (if it is), then "Aceptar" and "Descartar". */
@Composable
private fun ProposalFooter(state: PlanProposalUiState.Proposal, online: Boolean, onEvent: (PlanProposalEvent) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        state.acceptBlockedReason?.let { reason ->
            val base = stringResource(Res.string.plan_proposal_accept_reason)
            InlineNote(
                text = if (reason == AcceptBlockedReason.DataChanged) {
                    base + " " + stringResource(Res.string.trainer_reask)
                } else {
                    base
                },
                testTag = "plan_proposal_accept_reason",
            )
        }
        PrimaryButton(
            text = stringResource(Res.string.action_accept),
            onClick = { onEvent(PlanProposalEvent.Accept) },
            modifier = Modifier.fillMaxWidth().testTag("plan_proposal_accept"),
            enabled = state.canAccept && online,
            loading = state.accepting,
        )
        if (!online) OfflineHint(stringResource(Res.string.plan_proposal_accept_offline))
        GhostButton(
            text = stringResource(Res.string.action_discard),
            onClick = { onEvent(PlanProposalEvent.Discard) },
            modifier = Modifier.fillMaxWidth().testTag("plan_proposal_discard"),
            enabled = !state.busy && online,
            tone = GhostTone.Neutral,
            loading = state.discarding,
        )
    }
}

/** Card-shaped placeholder while the stored plan loads (R-31): the values card and the nutrition card. */
@Composable
private fun ProposalSkeleton() {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.screenMargin).testTag("plan_proposal_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        repeat(2) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    SkeletonBox(Modifier.fillMaxWidth(TITLE_FRACTION).height(Spacing.xl))
                    repeat(SKELETON_ROWS) { SkeletonBox(Modifier.fillMaxWidth().height(Spacing.minTouchTarget)) }
                }
            }
        }
    }
}
