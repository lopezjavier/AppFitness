package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.before_start_confirm_action
import io.github.lopezjavier.appfitness.composeapp.resources.before_start_confirm_error
import io.github.lopezjavier.appfitness.composeapp.resources.before_start_confirm_line_block
import io.github.lopezjavier.appfitness.composeapp.resources.before_start_confirm_line_plan
import io.github.lopezjavier.appfitness.composeapp.resources.before_start_error
import io.github.lopezjavier.appfitness.composeapp.resources.before_start_fix_activity
import io.github.lopezjavier.appfitness.composeapp.resources.before_start_fix_intake
import io.github.lopezjavier.appfitness.composeapp.resources.before_start_fix_personal
import io.github.lopezjavier.appfitness.composeapp.resources.before_start_fix_preferences
import io.github.lopezjavier.appfitness.composeapp.resources.before_start_fix_weight
import io.github.lopezjavier.appfitness.composeapp.resources.before_start_flag
import io.github.lopezjavier.appfitness.composeapp.resources.before_start_note_missing
import io.github.lopezjavier.appfitness.composeapp.resources.before_start_note_replace_accepted
import io.github.lopezjavier.appfitness.composeapp.resources.before_start_note_replace_proposed
import io.github.lopezjavier.appfitness.composeapp.resources.before_start_note_stale_weight
import io.github.lopezjavier.appfitness.composeapp.resources.before_start_reason_block
import io.github.lopezjavier.appfitness.composeapp.resources.before_start_reason_plan
import io.github.lopezjavier.appfitness.composeapp.resources.before_start_summary_chevron_cd
import io.github.lopezjavier.appfitness.composeapp.resources.before_start_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_right
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_create
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_create_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_create
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_create_action
import io.github.lopezjavier.appfitness.composeapp.resources.your_data_title
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.InlineNote
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.NavigationRow
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.OfflineState
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.offlineHint
import io.github.lopezjavier.appfitness.domain.model.CurrentPlan
import io.github.lopezjavier.appfitness.domain.model.PlanFixTarget
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.feature.exercises.tagName
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val SKELETON_LINES = 4
private const val TITLE_FRACTION = 0.4f

/**
 * Stateful "Antes de empezar" (US-78): reloads when the user comes back from an editor, plays the one-off effects
 * (haptics, the confirm failure message, leaving through [onCreate]) and connects [BeforeStartViewModel] to
 * [BeforeStartScreen]. [onFix] opens the editor of a flagged input (the activity sheet is handled here).
 */
@Composable
fun BeforeStartRoot(
    onBack: () -> Unit,
    onCreate: (BeforeStartEffect.Created) -> Unit,
    onOpenYourData: () -> Unit,
    onFix: (PlanFixTarget) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BeforeStartViewModel = koinViewModel(),
    connectivity: ConnectivityObserver = koinInject(),
    onOpenFolderBlock: (BeforeStartEffect.OpenFolderBlock) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val haptics = LocalAppHaptics.current
    val messages = LocalAppMessages.current
    val latestHaptics by rememberUpdatedState(haptics)
    val latestOnCreate by rememberUpdatedState(onCreate)
    val latestOnOpenBlock by rememberUpdatedState(onOpenFolderBlock)
    val failed = stringResource(Res.string.before_start_confirm_error)
    val offline = offlineHint(stringResource(Res.string.before_start_confirm_action)) + "."
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(BeforeStartEvent.Refresh) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is BeforeStartEffect.Created -> {
                    latestHaptics.confirm()
                    latestOnCreate(effect)
                }

                is BeforeStartEffect.OpenFolderBlock -> latestOnOpenBlock(effect)

                is BeforeStartEffect.CreateFailed -> {
                    latestHaptics.reject()
                    messages.show(if (effect.offline) offline else failed)
                }
            }
        }
    }
    BeforeStartScreen(
        state = state,
        isOnline = connectivity.isOnline,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onOpenYourData = onOpenYourData,
        onFix = onFix,
        modifier = modifier,
        editor = editor,
        onEditorEvent = viewModel::onEditorEvent,
    )
}

/**
 * "Antes de empezar" on [AppScreen] with back: flags for what blocks, notes for what is only worth a look, the
 * summary card (opens "Tus datos") and a pinned footer that creates the plan or the block (R-06, R-09). Nothing here
 * writes until the footer is tapped.
 */
@Suppress("LongParameterList") // State, events and the three ways out of the screen.
@Composable
fun BeforeStartScreen(
    state: BeforeStartUiState,
    isOnline: Flow<Boolean>,
    onEvent: (BeforeStartEvent) -> Unit,
    onBack: () -> Unit,
    onOpenYourData: () -> Unit,
    onFix: (PlanFixTarget) -> Unit,
    modifier: Modifier = Modifier,
    editor: IntakeRowEditorState = IntakeRowEditorState(),
    onEditorEvent: (IntakeRowEvent) -> Unit = {},
) {
    val online by isOnline.collectAsStateWithLifecycle(initialValue = true)
    val content = state as? BeforeStartUiState.Content
    AppScreen(
        title = stringResource(Res.string.before_start_title),
        modifier = modifier,
        onBack = onBack,
        footer = content?.let { { BeforeStartFooter(it, online, onEvent) } },
        testTagPrefix = "before_start",
    ) {
        Box(Modifier.fillMaxSize().testTag("before_start_screen")) {
            when (state) {
                BeforeStartUiState.Loading -> BeforeStartSkeleton()

                BeforeStartUiState.Offline -> OfflineState(isOnline = isOnline, onReload = {
                    onEvent(BeforeStartEvent.Retry)
                })

                BeforeStartUiState.Error -> ErrorState(
                    message = stringResource(Res.string.before_start_error),
                    action = AppStateAction(stringResource(Res.string.action_retry)) {
                        onEvent(BeforeStartEvent.Retry)
                    },
                    testTagPrefix = "before_start_error",
                    actionTestTag = "before_start_error_retry",
                )

                is BeforeStartUiState.Content -> BeforeStartContent(
                    state,
                    onEvent,
                    onEditorEvent,
                    onOpenYourData,
                    onFix,
                )
            }
            if (content != null) IntakeRowSheetHost(editor, online, onEditorEvent)
            if (content != null) BlockStartSheets(content, onEvent)
            if (content?.sheet != null) {
                ActivityLevelSheet(
                    selected = content.sheet,
                    saving = content.savingLevel,
                    online = online,
                    onDismiss = { onEvent(BeforeStartEvent.CloseSheet) },
                    onPick = { onEvent(BeforeStartEvent.PickLevel(it)) },
                    testTagPrefix = "before_start_activity_sheet",
                )
            }
        }
    }
}

@Composable
private fun BeforeStartContent(
    state: BeforeStartUiState.Content,
    onEvent: (BeforeStartEvent) -> Unit,
    onEditorEvent: (IntakeRowEvent) -> Unit,
    onOpenYourData: () -> Unit,
    onFix: (PlanFixTarget) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        state.flags.forEach { row ->
            NavigationRow(
                label = stringResource(Res.string.before_start_flag, row.labelText()),
                value = row.fixTarget?.let { stringResource(it.destinationLabel()) },
                onClick = {
                    when (row.fixTarget) {
                        null -> onOpenYourData()

                        PlanFixTarget.ActivitySheet -> onEvent(BeforeStartEvent.OpenSheet)

                        PlanFixTarget.TrainingYears ->
                            openIntakeRow(IntakeRowKind.TrainingYears, state.intake, onEditorEvent, onFix)

                        PlanFixTarget.Maintenance ->
                            openIntakeRow(IntakeRowKind.Maintenance, state.intake, onEditorEvent, onFix)

                        else -> onFix(row.fixTarget)
                    }
                },
                modifier = Modifier.testTag("before_start_flag_${row.key.tagName()}"),
            )
        }
        state.missingNotes.forEach { row ->
            InlineNote(
                text = stringResource(Res.string.before_start_note_missing, row.labelText()),
                testTag = "before_start_note_missing_${row.key.tagName()}",
            )
        }
        state.staleWeight?.date?.let { date ->
            InlineNote(
                text = stringResource(Res.string.before_start_note_stale_weight, AppDateFormat.title(date)),
                testTag = "before_start_note_stale_weight",
            )
        }
        state.replaces?.let { current ->
            InlineNote(
                text = stringResource(
                    if (current == CurrentPlan.Accepted) {
                        Res.string.before_start_note_replace_accepted
                    } else {
                        Res.string.before_start_note_replace_proposed
                    },
                ),
                testTag = "before_start_note_replace",
            )
        }
        SummaryCard(state, onOpenYourData)
        if (state.intakeRows.isNotEmpty()) {
            // Never a flag, "Sin indicar" when nothing was said; each row opens only its own editor (R-55).
            IntakeRows(
                intake = state.intake,
                kinds = state.intakeRows,
                screen = "before_start",
                onOpen = { openIntakeRow(it, state.intake, onEditorEvent, onFix) },
                modifier = Modifier.testTag("before_start_intake_block"),
            )
        }
    }
}

@Composable
private fun SummaryCard(state: BeforeStartUiState.Content, onOpenYourData: () -> Unit) {
    val chevronDescription = stringResource(Res.string.before_start_summary_chevron_cd)
    AppCard(
        Modifier.fillMaxWidth()
            .testTag("before_start_summary")
            .clickable(role = Role.Button, onClick = onOpenYourData),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = stringResource(Res.string.your_data_title),
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Icon(
                painter = painterResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                tint = AppTheme.colors.textSecondary,
                modifier = Modifier.size(Spacing.xl)
                    .testTag("before_start_summary_chevron")
                    .semantics { contentDescription = chevronDescription },
            )
        }
        state.mode.kind.summaryLines().forEach { keys ->
            val rows = keys.mapNotNull { key -> state.rows.firstOrNull { it.key == key } }
            if (rows.isNotEmpty()) {
                Text(
                    text = summaryLineText(rows),
                    style = AppTheme.typography.bodyLarge,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.padding(top = Spacing.sm)
                        .testTag("before_start_row_${rows.first().key.tagName()}"),
                )
            }
        }
    }
}

/** Pinned footer: what the footer does, why it is disabled (if it is) and the create button. */
@Composable
private fun BeforeStartFooter(
    state: BeforeStartUiState.Content,
    online: Boolean,
    onEvent: (BeforeStartEvent) -> Unit,
) {
    val isPlan = state.mode == BeforeStartMode.Plan
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (state.hasProvisional) {
            Text(
                text = stringResource(
                    if (isPlan) {
                        Res.string.before_start_confirm_line_plan
                    } else {
                        Res.string.before_start_confirm_line_block
                    },
                ),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("before_start_confirm_line"),
            )
        }
        if (state.hasBlocking) {
            Text(
                text = stringResource(
                    if (isPlan) Res.string.before_start_reason_plan else Res.string.before_start_reason_block,
                ),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("before_start_footer_reason"),
            )
        }
        PrimaryButton(
            text = stringResource(if (isPlan) Res.string.trainer_plan_create else Res.string.trainer_block_create),
            onClick = { onEvent(BeforeStartEvent.Create) },
            modifier = Modifier.fillMaxWidth().testTag("before_start_create"),
            enabled = state.canCreate && online,
            loading = state.creating,
        )
        if (!online) {
            Box(Modifier.testTag("before_start_offline_hint")) {
                OfflineHint(
                    stringResource(
                        if (isPlan) Res.string.trainer_plan_create_action else Res.string.trainer_block_create_action,
                    ),
                )
            }
        }
    }
}

/** Where a flagged input is fixed, as the flag row's value. */
internal fun PlanFixTarget.destinationLabel() = when (this) {
    PlanFixTarget.MyTraining -> Res.string.before_start_fix_preferences

    PlanFixTarget.PersonalData -> Res.string.before_start_fix_personal

    PlanFixTarget.Weight -> Res.string.before_start_fix_weight

    PlanFixTarget.ActivitySheet -> Res.string.before_start_fix_activity

    PlanFixTarget.TrainingYears, PlanFixTarget.Maintenance, PlanFixTarget.FreeText ->
        Res.string.before_start_fix_intake
}

/** Card-shaped placeholder while the inputs load (R-31): a title bar and one bar per summary line. */
@Composable
private fun BeforeStartSkeleton() {
    AppCard(Modifier.fillMaxWidth().padding(Spacing.screenMargin).testTag("before_start_loading")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SkeletonBox(Modifier.fillMaxWidth(TITLE_FRACTION).height(Spacing.xl))
            repeat(SKELETON_LINES) { SkeletonBox(Modifier.fillMaxWidth().height(Spacing.minTouchTarget)) }
        }
    }
}
