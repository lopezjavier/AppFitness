package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_applied
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_applied_unnamed
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_apply
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_apply_action
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_apply_failed
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_block_failed
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_changed
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_create_block
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_create_block_action
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_error
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_interrupted
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_needs_changes
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_progress
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_progress_cd
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_reason_fixable
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_reason_unfixable
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_section_changes
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_section_comments
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_section_falta
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_info
import io.github.lopezjavier.appfitness.composeapp.resources.ic_warning
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ChecklistRow
import io.github.lopezjavier.appfitness.designsystem.InlineNote
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StreamProgressState
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.designsystem.offlineHint
import io.github.lopezjavier.appfitness.domain.model.ReviewPurpose
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private val ReviewFailureCopy = CoachFailureCopy(
    error = Res.string.folder_review_error,
    interrupted = Res.string.folder_review_interrupted,
)

private val ChangedFailureCopy = CoachFailureCopy(
    error = Res.string.folder_review_changed,
    interrupted = Res.string.folder_review_interrupted,
)

/**
 * Stateful "Revisar carpeta" (US-95, US-93): asks the coach once when it opens (a paid call, never again by itself
 * after a recreation) and plays the one-off results: the confirmation of the copy (a message and a Confirm haptic)
 * before leaving through [onFinishCopy], the failure message with a Reject haptic, and [onFinishBlock] once the block
 * is stored.
 */
@Composable
fun FolderReviewRoot(
    onFinishCopy: () -> Unit,
    onFinishBlock: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FolderReviewViewModel = koinViewModel(),
    connectivity: ConnectivityObserver = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalAppHaptics.current
    val messages = LocalAppMessages.current
    val latestOnFinishCopy by rememberUpdatedState(onFinishCopy)
    val latestOnFinishBlock by rememberUpdatedState(onFinishBlock)
    val block = (state as? FolderReviewUiState.Content)?.purpose == ReviewPurpose.Block
    val applyFailed = stringResource(Res.string.folder_review_apply_failed)
    val blockFailed = stringResource(Res.string.folder_review_block_failed)
    val applyOffline = offlineHint(stringResource(Res.string.folder_review_apply_action)) + "."
    val blockOffline = offlineHint(stringResource(Res.string.folder_review_create_block_action)) + "."
    val unnamed = stringResource(Res.string.folder_review_applied_unnamed)
    LaunchedEffect(viewModel) { viewModel.onEvent(FolderReviewEvent.Request) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is FolderReviewEffect.Applied -> {
                    haptics.confirm()
                    messages.show(effect.folderName?.let { getString(Res.string.folder_review_applied, it) } ?: unnamed)
                    latestOnFinishCopy()
                }

                FolderReviewEffect.BlockReady -> {
                    haptics.confirm()
                    latestOnFinishBlock()
                }

                is FolderReviewEffect.ActionFailed -> {
                    haptics.reject()
                    messages.show(
                        when {
                            effect.offline && block -> blockOffline
                            effect.offline -> applyOffline
                            block -> blockFailed
                            else -> applyFailed
                        },
                    )
                }
            }
        }
    }
    FolderReviewScreen(
        state = state,
        isOnline = connectivity.isOnline,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * "Revisar carpeta" on [AppScreen] with back: the coach's message, "Falta", the comments and the checklist of proposed
 * changes (all checked), with a pinned footer: "Aplicar en una copia" (review) or "Crear bloque" (block). Waiting is a
 * [StreamProgressState]; a failure is [CoachFailureContent] with "Reintentar" (R-31). Leaving writes nothing.
 */
@Composable
fun FolderReviewScreen(
    state: FolderReviewUiState,
    isOnline: Flow<Boolean>,
    onEvent: (FolderReviewEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val online by isOnline.collectAsStateWithLifecycle(initialValue = true)
    val content = state as? FolderReviewUiState.Content
    AppScreen(
        title = stringResource(Res.string.folder_review_title),
        modifier = modifier,
        onBack = onBack,
        footer = content?.takeIf { it.showsFooter }?.let { { ReviewFooter(it, online, onEvent) } },
        testTagPrefix = "folder_review",
    ) {
        Box(Modifier.fillMaxSize().testTag("folder_review_screen")) {
            when (state) {
                FolderReviewUiState.Idle,
                FolderReviewUiState.Requesting,
                -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    StreamProgressState(
                        message = stringResource(Res.string.folder_review_progress),
                        stateDescription = stringResource(Res.string.folder_review_progress_cd),
                        testTag = "folder_review_progress",
                    )
                }

                is FolderReviewUiState.Content -> ReviewContent(state, onEvent)

                is FolderReviewUiState.Failed -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CoachFailureContent(
                        kind = state.kind,
                        isOnline = isOnline,
                        copy = if (state.changed) ChangedFailureCopy else ReviewFailureCopy,
                        tagPrefix = "folder_review",
                    ) { onEvent(FolderReviewEvent.Retry) }
                }
            }
        }
    }
}

@Composable
private fun ReviewContent(state: FolderReviewUiState.Content, onEvent: (FolderReviewEvent) -> Unit) {
    val falta = state.falta.ifEmpty { state.blockedFalta }
    Box(Modifier.fillMaxSize(), Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = state.review.message,
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.testTag("folder_review_message"),
            )
            if (state.blockedFalta.isNotEmpty()) {
                InlineNote(
                    text = stringResource(Res.string.folder_review_needs_changes),
                    testTag = "folder_review_needs_changes",
                )
            }
            if (falta.isNotEmpty()) FaltaSection(falta)
            if (state.review.comments.isNotEmpty()) CommentsSection(state)
            if (state.rows.isNotEmpty()) ChangesSection(state, onEvent)
        }
    }
}

@Composable
private fun FaltaSection(falta: List<FaltaItem>) {
    Column(Modifier.testTag("folder_review_section_falta")) {
        ListSectionHeader(stringResource(Res.string.folder_review_section_falta))
        falta.forEachIndexed { index, item ->
            AppListRow(
                headline = stringResource(faltaMessage(item.falta.code)),
                supporting = item.detail(),
                leading = {
                    Icon(
                        painter = painterResource(Res.drawable.ic_warning),
                        contentDescription = null,
                        tint = AppTheme.colors.error,
                        modifier = Modifier.size(Spacing.xl),
                    )
                },
                modifier = Modifier.testTag("folder_review_falta_${index + 1}"),
            )
        }
    }
}

@Composable
private fun CommentsSection(state: FolderReviewUiState.Content) {
    Column(Modifier.testTag("folder_review_section_comments")) {
        ListSectionHeader(stringResource(Res.string.folder_review_section_comments))
        state.review.comments.forEachIndexed { index, comment ->
            AppListRow(
                headline = comment.text,
                leading = {
                    Icon(
                        painter = painterResource(Res.drawable.ic_info),
                        contentDescription = null,
                        tint = AppTheme.colors.textSecondary,
                        modifier = Modifier.size(Spacing.xl),
                    )
                },
                modifier = Modifier.testTag("folder_review_comment_${index + 1}"),
            )
        }
    }
}

@Composable
private fun ChangesSection(state: FolderReviewUiState.Content, onEvent: (FolderReviewEvent) -> Unit) {
    Column(Modifier.testTag("folder_review_section_changes")) {
        ListSectionHeader(stringResource(Res.string.folder_review_section_changes))
        state.rows.forEach { row ->
            ChecklistRow(
                checked = row.checked,
                onCheckedChange = { onEvent(FolderReviewEvent.Toggle(row.id)) },
                title = row.line.text(),
                supporting = row.reason,
                enabled = !state.working,
                testTag = "folder_review_change_${row.id}",
            )
        }
    }
}

/** Pinned footer: the action of the purpose, why it is disabled (block with an open Falta) and the offline hint. */
@Composable
private fun ReviewFooter(state: FolderReviewUiState.Content, online: Boolean, onEvent: (FolderReviewEvent) -> Unit) {
    val block = state.purpose == ReviewPurpose.Block
    Column(
        Modifier.fillMaxWidth().padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (block && state.falta.isNotEmpty()) {
            Text(
                text = stringResource(
                    if (state.hasUnfixableFalta) {
                        Res.string.folder_review_reason_unfixable
                    } else {
                        Res.string.folder_review_reason_fixable
                    },
                ),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("folder_review_disabled_reason"),
            )
        }
        PrimaryButton(
            text = stringResource(
                if (block) Res.string.folder_review_create_block else Res.string.folder_review_apply,
            ),
            onClick = { onEvent(if (block) FolderReviewEvent.Continue else FolderReviewEvent.Apply) },
            modifier = Modifier.fillMaxWidth()
                .testTag(if (block) "folder_review_create_block" else "folder_review_apply"),
            enabled = online && (if (block) state.canCreateBlock else state.canApply),
            loading = state.working,
        )
        if (!online) {
            OfflineHint(
                stringResource(
                    if (block) Res.string.folder_review_create_block_action else Res.string.folder_review_apply_action,
                ),
            )
        }
    }
}
