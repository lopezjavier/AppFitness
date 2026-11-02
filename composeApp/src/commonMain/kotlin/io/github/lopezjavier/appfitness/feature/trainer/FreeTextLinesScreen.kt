package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.free_text_empty
import io.github.lopezjavier.appfitness.composeapp.resources.free_text_intro
import io.github.lopezjavier.appfitness.composeapp.resources.free_text_over_limit
import io.github.lopezjavier.appfitness.composeapp.resources.free_text_section_check_in
import io.github.lopezjavier.appfitness.composeapp.resources.free_text_section_discomfort
import io.github.lopezjavier.appfitness.composeapp.resources.free_text_section_intake
import io.github.lopezjavier.appfitness.composeapp.resources.free_text_section_other
import io.github.lopezjavier.appfitness.composeapp.resources.free_text_section_review
import io.github.lopezjavier.appfitness.composeapp.resources.free_text_state_off
import io.github.lopezjavier.appfitness.composeapp.resources.free_text_state_sent
import io.github.lopezjavier.appfitness.composeapp.resources.free_text_switch_label
import io.github.lopezjavier.appfitness.composeapp.resources.free_text_title
import io.github.lopezjavier.appfitness.composeapp.resources.free_text_toggle_action
import io.github.lopezjavier.appfitness.composeapp.resources.free_text_toggle_failed
import io.github.lopezjavier.appfitness.composeapp.resources.intake_load_error
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppSwitch
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.OfflineState
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.offlineHint
import io.github.lopezjavier.appfitness.domain.model.FreeTextKind
import io.github.lopezjavier.appfitness.domain.model.FreeTextLine
import io.github.lopezjavier.appfitness.domain.model.FreeTextStatus
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.feature.exercises.tagName
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val SKELETON_ROWS = 5

/** Stateful "Lo que el entrenador sabe de ti": plays the rollback message of a switch the server refused. */
@Composable
fun FreeTextLinesRoot(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FreeTextLinesViewModel = koinViewModel(),
    connectivity: ConnectivityObserver = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalAppHaptics.current
    val messages = LocalAppMessages.current
    val latestHaptics by rememberUpdatedState(haptics)
    val failed = stringResource(Res.string.free_text_toggle_failed)
    val offline = offlineHint(stringResource(Res.string.free_text_toggle_action)) + "."
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is FreeTextLinesEffect.ToggleFailed -> {
                    latestHaptics.reject()
                    messages.show(if (effect.offline) offline else failed)
                }
            }
        }
    }
    FreeTextLinesScreen(
        state = state,
        isOnline = connectivity.isOnline,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Every free-text line the coach reads, one section per origin, newest first. A switch leaves a line out (R-42:
 * optimistic, persisted, rolled back with a message when it fails); offline the switches are disabled.
 */
@Composable
fun FreeTextLinesScreen(
    state: FreeTextLinesUiState,
    isOnline: Flow<Boolean>,
    onEvent: (FreeTextLinesEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val online by isOnline.collectAsStateWithLifecycle(initialValue = true)
    AppScreen(
        title = stringResource(Res.string.free_text_title),
        modifier = modifier,
        onBack = onBack,
        testTagPrefix = "free_text",
    ) {
        Box(Modifier.fillMaxSize().testTag("free_text_screen")) {
            when (state) {
                FreeTextLinesUiState.Loading -> FreeTextSkeleton()

                FreeTextLinesUiState.Offline -> OfflineState(isOnline = isOnline, onReload = {
                    onEvent(FreeTextLinesEvent.Retry)
                })

                FreeTextLinesUiState.Error -> ErrorState(
                    message = stringResource(Res.string.intake_load_error),
                    action = AppStateAction(stringResource(Res.string.action_retry)) {
                        onEvent(FreeTextLinesEvent.Retry)
                    },
                    testTagPrefix = "free_text_error",
                    actionTestTag = "free_text_error_retry",
                )

                is FreeTextLinesUiState.Content -> if (state.isEmpty) {
                    EmptyState(
                        message = stringResource(Res.string.free_text_empty),
                        testTagPrefix = "free_text_empty",
                    )
                } else {
                    FreeTextContent(state, online, onEvent)
                }
            }
        }
    }
}

@Composable
private fun FreeTextContent(
    state: FreeTextLinesUiState.Content,
    online: Boolean,
    onEvent: (FreeTextLinesEvent) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        Text(
            text = stringResource(Res.string.free_text_intro),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
        )
        if (!online) OfflineHint(action = stringResource(Res.string.free_text_toggle_action))
        state.sections.forEach { section ->
            Column(Modifier.testTag("free_text_section_${section.kind.tagName()}")) {
                ListSectionHeader(stringResource(section.kind.title()))
                section.lines.forEach { line -> FreeTextLineRow(line, online, onEvent) }
            }
        }
    }
}

@Composable
private fun FreeTextLineRow(line: FreeTextLine, online: Boolean, onEvent: (FreeTextLinesEvent) -> Unit) {
    val kind = line.kind.tagName()
    val sent = line.status == FreeTextStatus.Sent
    val stateText = stringResource(if (sent) Res.string.free_text_state_sent else Res.string.free_text_state_off)
    Column {
        AppListRow(
            headline = line.text,
            modifier = Modifier.testTag("free_text_line_${kind}_${line.sourceId}"),
            supporting = AppDateFormat.title(line.date),
            trailing = {
                AppSwitch(
                    checked = line.status != FreeTextStatus.Excluded,
                    onCheckedChange = { onEvent(FreeTextLinesEvent.Toggle(line.kind, line.sourceId)) },
                    contentDescription = stringResource(Res.string.free_text_switch_label),
                    modifier = Modifier.testTag("free_text_switch_${kind}_${line.sourceId}"),
                    enabled = online,
                    stateDescription = stateText,
                )
            },
        )
        if (line.status == FreeTextStatus.OverLimit) {
            Text(
                text = stringResource(Res.string.free_text_over_limit),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("free_text_over_limit_${line.sourceId}"),
            )
        }
    }
}

private fun FreeTextKind.title(): StringResource = when (this) {
    FreeTextKind.Intake -> Res.string.free_text_section_intake
    FreeTextKind.CheckIn -> Res.string.free_text_section_check_in
    FreeTextKind.Discomfort -> Res.string.free_text_section_discomfort
    FreeTextKind.Review -> Res.string.free_text_section_review
    FreeTextKind.Unknown -> Res.string.free_text_section_other
}

/** Rows while the lines load (R-31). */
@Composable
private fun FreeTextSkeleton() {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.screenMargin).testTag("free_text_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        repeat(SKELETON_ROWS) { SkeletonBox(Modifier.fillMaxWidth().height(Spacing.minTouchTarget)) }
    }
}
