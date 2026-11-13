package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_accept
import io.github.lopezjavier.appfitness.composeapp.resources.action_discard
import io.github.lopezjavier.appfitness.composeapp.resources.before_start_confirm_error
import io.github.lopezjavier.appfitness.composeapp.resources.cardio_save_error
import io.github.lopezjavier.appfitness.composeapp.resources.cardio_save_offline
import io.github.lopezjavier.appfitness.composeapp.resources.plan_outcome_deadline
import io.github.lopezjavier.appfitness.composeapp.resources.plan_outcome_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.plan_outcome_help_title
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_accept_error
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_accept_offline
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_accepted
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_calories
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_carbs_rest
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_discard_error
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_discard_offline
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_error
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_fat_min
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_floor_note
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_interrupted
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_kcal_day
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_open_before_start
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_options_error
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_options_offline
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_outcome_e1rm
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_outcome_weight
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_outcomes_header
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_progress
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_progress_cd
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_protein_headline
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_rate
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_rate_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_rate_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_rate_value
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_stale_note
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_title
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_unconfirmed_fallback
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_unconfirmed_note
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_underweight_note
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
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
import io.github.lopezjavier.appfitness.designsystem.NavigationRow
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StreamProgressState
import io.github.lopezjavier.appfitness.designsystem.offlineHint
import io.github.lopezjavier.appfitness.domain.model.OutcomeKind
import io.github.lopezjavier.appfitness.domain.model.PhaseReason
import io.github.lopezjavier.appfitness.domain.model.PlanFixTarget
import io.github.lopezjavier.appfitness.domain.model.PlanLimit
import io.github.lopezjavier.appfitness.domain.model.PlanOutcome
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.usecase.NutritionGoalsChecklist
import io.github.lopezjavier.appfitness.feature.weight.decimalText
import io.github.lopezjavier.appfitness.feature.weight.signedTenths
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val TITLE_FRACTION = 0.4f
private const val SKELETON_ROWS = 3

/**
 * Stateful plan proposal (US-69): asks the coach once when it opens with no stored plan, plays the one-off effects
 * (haptics, the accept message, leaving the screen) and connects [PlanProposalViewModel] to [PlanProposalScreen].
 */
@Suppress("CyclomaticComplexMethod", "LongMethod") // One branch per effect and per failed action.
@Composable
fun PlanProposalRoot(
    onBack: () -> Unit,
    onOpenBeforeStart: (replacingPlanId: String) -> Unit,
    onFix: (PlanFixTarget) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlanProposalViewModel = koinViewModel(),
    connectivity: ConnectivityObserver = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val haptics = LocalAppHaptics.current
    val messages = LocalAppMessages.current
    val latestHaptics by rememberUpdatedState(haptics)
    val latestOnBack by rememberUpdatedState(onBack)
    val accepted = stringResource(Res.string.plan_proposal_accepted)
    val acceptError = stringResource(Res.string.plan_proposal_accept_error)
    val discardError = stringResource(Res.string.plan_proposal_discard_error)
    val acceptOffline = offlineHint(stringResource(Res.string.plan_proposal_accept_offline)) + "."
    val discardOffline = offlineHint(stringResource(Res.string.plan_proposal_discard_offline)) + "."
    val cardioError = stringResource(Res.string.cardio_save_error)
    val cardioOffline = offlineHint(stringResource(Res.string.cardio_save_offline)) + "."
    val confirmError = stringResource(Res.string.before_start_confirm_error)
    val optionsError = stringResource(Res.string.plan_proposal_options_error)
    val optionsOffline = offlineHint(stringResource(Res.string.plan_proposal_options_offline)) + "."
    // Back from an editor: re-read which values still block.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(PlanProposalEvent.Refresh) }
    LaunchedEffect(state) {
        if (state == PlanProposalUiState.Idle) viewModel.onEvent(PlanProposalEvent.Request)
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                PlanProposalEffect.Accepted -> {
                    latestHaptics.confirm()
                    messages.show(accepted)
                    latestOnBack()
                }

                PlanProposalEffect.CardioSaved, PlanProposalEffect.ValuesConfirmed -> latestHaptics.confirm()

                PlanProposalEffect.Discarded -> {
                    latestHaptics.confirm()
                    latestOnBack()
                }

                is PlanProposalEffect.ActionFailed -> {
                    latestHaptics.reject()
                    messages.show(
                        when (effect.action) {
                            PlanAction.Accept -> if (effect.offline) acceptOffline else acceptError
                            PlanAction.Discard -> if (effect.offline) discardOffline else discardError
                            PlanAction.Cardio -> if (effect.offline) cardioOffline else cardioError
                            PlanAction.Options -> if (effect.offline) optionsOffline else optionsError
                            PlanAction.Confirm -> confirmError
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
        onOpenBeforeStart = onOpenBeforeStart,
        modifier = modifier,
        onFix = onFix,
        editor = editor,
        onEditorEvent = viewModel::onEditorEvent,
    )
}

/**
 * The nutrition plan proposal on [AppScreen] with back: the calories
 * and macros, the weekly rate, the notes and the optional outcomes, and a pinned "Aceptar" / "Descartar" footer. The
 * plan is read-only: the user never enters a target number (ADR-78). Loading the stored plan is a skeleton, waiting
 * for the model is a [StreamProgressState] (R-31).
 */
@Suppress("LongParameterList") // State, events and the ways out of the screen.
@Composable
fun PlanProposalScreen(
    state: PlanProposalUiState,
    isOnline: Flow<Boolean>,
    onEvent: (PlanProposalEvent) -> Unit,
    onBack: () -> Unit,
    onOpenBeforeStart: (replacingPlanId: String) -> Unit,
    modifier: Modifier = Modifier,
    onFix: (PlanFixTarget) -> Unit = {},
    editor: IntakeRowEditorState = IntakeRowEditorState(),
    onEditorEvent: (IntakeRowEvent) -> Unit = {},
) {
    val online by isOnline.collectAsStateWithLifecycle(initialValue = true)
    AppScreen(
        title = stringResource(Res.string.plan_proposal_title),
        modifier = modifier,
        onBack = onBack,
        footer = (state as? PlanProposalUiState.Proposal)?.let {
            { ProposalFooter(it, online, onEvent, onOpenBeforeStart) }
        },
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
                    FailedContent(state.kind, isOnline, onEvent, onFix)
                }

                is PlanProposalUiState.Proposal -> ProposalContent(state, online, onEvent, onFix, onEditorEvent)
            }
            if (state is PlanProposalUiState.Proposal) IntakeRowSheetHost(editor, online, onEditorEvent)
        }
    }
}

private val PlanFailureCopy = CoachFailureCopy(
    error = Res.string.plan_proposal_error,
    interrupted = Res.string.plan_proposal_interrupted,
)

@Composable
private fun FailedContent(
    kind: PlanFailureKind,
    isOnline: Flow<Boolean>,
    onEvent: (PlanProposalEvent) -> Unit,
    onFix: (PlanFixTarget) -> Unit,
) = CoachFailureContent(kind, isOnline, PlanFailureCopy, "plan_proposal", onFix) { onEvent(PlanProposalEvent.Retry) }

@Composable
private fun ProposalContent(
    state: PlanProposalUiState.Proposal,
    online: Boolean,
    onEvent: (PlanProposalEvent) -> Unit,
    onFix: (PlanFixTarget) -> Unit,
    onEditorEvent: (IntakeRowEvent) -> Unit,
) {
    val nutrition = state.plan.nutrition
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        PhaseRow(state.plan.phase, nutrition.phaseReason, "plan_proposal")
        if (state.unconfirmedValues.isNotEmpty()) UnconfirmedSection(state, online, onEvent, onFix, onEditorEvent)
        NutritionCard(state)
        if (state.hasCutOptions) {
            CutOptions(
                options = nutrition.phaseOptions,
                saving = state.optionsSaving,
                online = online,
                onRate = { onEvent(PlanProposalEvent.CutRatePicked(it)) },
                onDietBreak = { onEvent(PlanProposalEvent.DietBreakToggled(it)) },
            )
        }
        if (nutrition.limitedBy == PlanLimit.Floor) {
            InlineNote(
                text = stringResource(
                    Res.string.plan_proposal_floor_note,
                    AppNumberFormat.integer(nutrition.floorKcal ?: nutrition.targetKcal),
                ),
                testTag = "plan_proposal_floor_note",
            )
        }
        if (nutrition.phaseReason == PhaseReason.Underweight) {
            InlineNote(
                text = stringResource(Res.string.plan_proposal_underweight_note),
                testTag = "plan_proposal_underweight_note",
            )
        }
        nutrition.activity?.let {
            ActivityCard(it, state.plan.intention, enabled = !state.busy) { onEvent(PlanProposalEvent.OpenCardioEdit) }
        }
        if (state.plan.outcomes.isNotEmpty()) OutcomesSection(state.plan.outcomes)
    }
    state.activitySheet?.let { level ->
        ActivityLevelSheet(
            selected = level,
            saving = state.savingLevel,
            online = online,
            onDismiss = { onEvent(PlanProposalEvent.CloseActivitySheet) },
            onPick = { onEvent(PlanProposalEvent.PickActivityLevel(it)) },
            testTagPrefix = "plan_proposal_activity_sheet",
        )
    }
    state.cardioEdit?.let {
        CardioMinutesSheet(
            edit = it,
            online = online,
            onChange = { minutes -> onEvent(PlanProposalEvent.CardioMinutesChanged(minutes)) },
            onSave = { onEvent(PlanProposalEvent.SaveCardio) },
            onDismiss = { onEvent(PlanProposalEvent.DismissCardioEdit) },
        )
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
        MacroRow(
            Macro.Protein,
            stringResource(Res.string.plan_proposal_protein_headline, AppNumberFormat.integer(nutrition.proteinG)),
            "plan_proposal_macro_protein",
        )
        MacroRow(
            Macro.Fat,
            stringResource(
                Res.string.plan_proposal_fat_min,
                AppNumberFormat.integer(nutrition.fatG),
                AppNumberFormat.integer(NutritionGoalsChecklist.minFatG(nutrition.targetKcal)),
            ),
            "plan_proposal_macro_fat",
        )
        MacroRow(
            Macro.Carbs,
            stringResource(Res.string.plan_proposal_carbs_rest, AppNumberFormat.integer(nutrition.carbsG)),
            "plan_proposal_macro_carbs",
        )
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

/** One macro sentence with its colour dot; the three macros are equal rows (ADR-98, amended T-220). */
@Composable
private fun MacroRow(macro: Macro, text: String, tag: String) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(Modifier.size(Spacing.sm).background(MacroDefaults.color(macro, AppTheme.colors), AppShapes.full))
        Text(
            text = text,
            style = AppTheme.typography.bodyLarge,
            color = AppTheme.colors.textPrimary,
        )
    }
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

/**
 * Pinned footer: a one-line reason "Aceptar" is blocked (it names the values), the way to "Antes de empezar" when the
 * plan must be created again or the values are unknown, then the decisions. The fixes live in the body (R-09, R-59).
 */
@Composable
private fun ProposalFooter(
    state: PlanProposalUiState.Proposal,
    online: Boolean,
    onEvent: (PlanProposalEvent) -> Unit,
    onOpenBeforeStart: (replacingPlanId: String) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        when {
            state.unconfirmedValues.isNotEmpty() -> InlineNote(
                text = stringResource(
                    Res.string.plan_proposal_unconfirmed_note,
                    state.unconfirmedValues.map { it.labelText() }.joinToString(", "),
                ),
                testTag = "plan_proposal_unconfirmed_note",
            )

            state.unconfirmed -> {
                // The server refused and the list is unknown: still one visible way out.
                InlineNote(
                    text = stringResource(Res.string.plan_proposal_unconfirmed_fallback),
                    testTag = "plan_proposal_unconfirmed_note",
                )
                BeforeStartRow(state, onOpenBeforeStart)
            }

            state.stale -> {
                InlineNote(
                    text = stringResource(Res.string.plan_proposal_stale_note),
                    testTag = "plan_proposal_stale_note",
                )
                BeforeStartRow(state, onOpenBeforeStart)
            }
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
            loading = state.discarding,
        )
    }
}

@Composable
private fun BeforeStartRow(state: PlanProposalUiState.Proposal, onOpenBeforeStart: (replacingPlanId: String) -> Unit) {
    NavigationRow(
        label = stringResource(Res.string.plan_proposal_open_before_start),
        onClick = { onOpenBeforeStart(state.plan.id) },
        modifier = Modifier.testTag("plan_proposal_open_your_data"),
    )
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
