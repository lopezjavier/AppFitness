package io.github.lopezjavier.appfitness.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_chat_empty
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_chat_failure_busy
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_chat_failure_daily_limit
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_chat_failure_offline
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_chat_input_label
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_chat_offline_action
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_chat_progress
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_chat_send_cd
import io.github.lopezjavier.appfitness.composeapp.resources.ic_arrow_upward
import io.github.lopezjavier.appfitness.composeapp.resources.ic_inbox
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ChatBubble
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ExerciseLayoutDefaults
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StreamProgressState
import io.github.lopezjavier.appfitness.designsystem.appAnimateItem
import io.github.lopezjavier.appfitness.domain.model.ExerciseChatContext
import io.github.lopezjavier.appfitness.domain.model.ExerciseDetail
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Longest instruction text sent; the server rejects more (`instructions` 0-4000). */
internal const val CHAT_INSTRUCTIONS_MAX_LENGTH = 4000
private const val CHAT_MUSCLES_MAX = 12
private const val CHAT_MUSCLE_MAX_LENGTH = 60

/**
 * Builds what the trainer chat may know about the exercise: the resolved name, the Spanish muscle labels (never
 * empty: the primary group always counts) and the steps joined by newlines (empty for a custom exercise).
 */
internal fun chatContextOf(detail: ExerciseDetail, muscleLabels: List<String>): ExerciseChatContext =
    ExerciseChatContext(
        name = detail.name,
        muscles = muscleLabels.map { it.take(CHAT_MUSCLE_MAX_LENGTH) }.distinct().take(CHAT_MUSCLES_MAX),
        instructions = detail.info?.instructions.orEmpty().joinToString("\n").take(CHAT_INSTRUCTIONS_MAX_LENGTH),
    )

/**
 * Stateful chat tab: scopes [ExerciseChatViewModel] to the detail destination (key per exercise), so popping the
 * destination clears the conversation while leaving the app keeps it (ADR-40).
 */
@Composable
internal fun ExerciseChatRoot(
    detail: ExerciseDetail,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    viewModel: ExerciseChatViewModel = koinViewModel(key = "chat_${detail.exercise.id}"),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val groups = (listOf(detail.exercise.muscleGroup) + detail.exercise.secondaryMuscles).distinct()
    val labels = groups.map { stringResource(it.label) }
    val context = remember(detail, labels) { chatContextOf(detail, labels) }
    ExerciseChatTab(
        state = state,
        onDraftChange = viewModel::setDraft,
        onSend = { viewModel.send(context) },
        onRetry = { viewModel.retry(context) },
        modifier = modifier,
        listState = listState,
    )
}

/** Stateless chat tab (US-130): the conversation, a pinned input and the states of waiting, offline and failure. */
@Composable
internal fun ExerciseChatTab(
    state: ExerciseChatUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    RejectOnNewFailure(state.messages.lastOrNull())
    LaunchedEffect(state.messages.size, state.sending) {
        val count = state.messages.size + if (state.sending) 1 else 0
        if (count > 0) listState.scrollToItem(count - 1)
    }
    Column(modifier.widthIn(max = ExerciseLayoutDefaults.ContentMaxWidth).imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (state.messages.isEmpty() && !state.sending) {
                EmptyState(
                    icon = Res.drawable.ic_inbox,
                    message = stringResource(Res.string.exercise_chat_empty),
                    testTagPrefix = "exercise_chat_empty",
                )
            } else {
                Conversation(state, onRetry, listState)
            }
        }
        if (!state.isOnline) {
            OfflineHint(
                action = stringResource(Res.string.exercise_chat_offline_action),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenMargin, vertical = Spacing.sm)
                    .testTag("exercise_chat_offline_hint"),
            )
        }
        ChatInput(state, onDraftChange, onSend)
    }
}

/** A buzz when a failure shows up; a failure already there when the tab opens (or is restored) stays silent. */
@Composable
private fun RejectOnNewFailure(last: ChatMessage?) {
    val haptics = LocalAppHaptics.current
    val failureId = (last as? ChatMessage.Failure)?.id
    var seen by remember { mutableStateOf(failureId) }
    LaunchedEffect(failureId) {
        if (failureId != null && failureId != seen) haptics.reject()
        seen = failureId
    }
}

@Composable
private fun Conversation(state: ExerciseChatUiState, onRetry: () -> Unit, listState: LazyListState) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("exercise_chat_list"),
        contentPadding = PaddingValues(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        itemsIndexed(state.messages, key = { _, message -> message.id }) { index, message ->
            val isLast = index == state.messages.lastIndex
            MessageRow(message, index, showRetry = isLast && !state.sending, onRetry, appAnimateItem())
        }
        if (state.sending) {
            item(key = "sending") {
                StreamProgressState(
                    message = stringResource(Res.string.exercise_chat_progress),
                    stateDescription = stringResource(Res.string.exercise_chat_progress),
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "exercise_chat_progress",
                )
            }
        }
    }
}

@Composable
private fun MessageRow(
    message: ChatMessage,
    index: Int,
    showRetry: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (message) {
        is ChatMessage.Question -> ChatBubble(message.text, true, modifier, "exercise_chat_message_$index")
        is ChatMessage.Answer -> ChatBubble(message.text, false, modifier, "exercise_chat_message_$index")
        is ChatMessage.Failure -> FailureRow(message, showRetry && message.canRetry, onRetry, modifier)
    }
}

@Composable
private fun FailureRow(
    failure: ChatMessage.Failure,
    showRetry: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = stringResource(
        when (failure.reason) {
            ChatFailureReason.Busy -> Res.string.exercise_chat_failure_busy
            ChatFailureReason.DailyLimit -> Res.string.exercise_chat_failure_daily_limit
            ChatFailureReason.Offline -> Res.string.exercise_chat_failure_offline
        },
    )
    val tag = if (failure.reason ==
        ChatFailureReason.DailyLimit
    ) {
        "exercise_chat_daily_limit"
    } else {
        "exercise_chat_failure"
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = text,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(tag),
        )
        if (showRetry) {
            GhostButton(
                text = stringResource(Res.string.action_retry),
                onClick = onRetry,
                modifier = Modifier.testTag("exercise_chat_retry"),
            )
        }
    }
}

@Composable
private fun ChatInput(state: ExerciseChatUiState, onDraftChange: (String) -> Unit, onSend: () -> Unit) {
    val sendDescription = stringResource(Res.string.exercise_chat_send_cd)
    AppTextField(
        value = state.draft,
        onValueChange = onDraftChange,
        label = stringResource(Res.string.exercise_chat_input_label),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenMargin, vertical = Spacing.sm),
        maxLength = CHAT_QUESTION_MAX_LENGTH,
        testTagPrefix = "exercise_chat_input",
        imeAction = ImeAction.Send,
        onImeAction = { if (state.canSend) onSend() },
        trailing = {
            AppIconButton(
                contentDescription = sendDescription,
                icon = Res.drawable.ic_arrow_upward,
                testTag = "exercise_chat_send",
                onClick = onSend,
                enabled = state.canSend,
            )
        },
    )
}
