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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.app.IntakeHost
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.intake_free_text_counter
import io.github.lopezjavier.appfitness.composeapp.resources.intake_free_text_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.intake_free_text_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.intake_free_text_label
import io.github.lopezjavier.appfitness.composeapp.resources.intake_load_error
import io.github.lopezjavier.appfitness.composeapp.resources.intake_save_failed
import io.github.lopezjavier.appfitness.composeapp.resources.intake_title
import io.github.lopezjavier.appfitness.designsystem.AppEditorScreen
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.InlineNote
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.OfflineState
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val SKELETON_FIELDS = 5
private const val FREE_TEXT_MIN_LINES = 4

/** Stateful "Para conocerte" (US-81): connects [IntakeViewModel] and leaves through [IntakeHost]. */
@Composable
fun IntakeRoot(
    host: IntakeHost,
    modifier: Modifier = Modifier,
    viewModel: IntakeViewModel = koinViewModel(),
    connectivity: ConnectivityObserver = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val online by connectivity.isOnline.collectAsStateWithLifecycle(initialValue = true)
    var saved by rememberSaveable { mutableStateOf(false) }
    val latestHost by rememberUpdatedState(host)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                IntakeEffect.Saved -> saved = true
                IntakeEffect.Close -> latestHost.onClose()
            }
        }
    }
    IntakeScreen(
        state = state,
        isOnline = online,
        isOnlineFlow = connectivity.isOnline,
        saved = saved,
        onEvent = viewModel::onEvent,
        onClose = { if (saved) latestHost.onDone() else latestHost.onClose() },
        modifier = modifier,
    )
}

/** "Para conocerte" on [AppEditorScreen]: one multi-line field. Nothing is stored until "Guardar". */
@Suppress("LongParameterList") // State, connectivity and the ways out.
@Composable
fun IntakeScreen(
    state: IntakeUiState,
    isOnline: Boolean,
    isOnlineFlow: Flow<Boolean>,
    saved: Boolean,
    onEvent: (IntakeEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state !is IntakeUiState.Content) {
        IntakePlaceholder(state, isOnlineFlow, onEvent, onClose, modifier)
        return
    }
    val haptics = LocalAppHaptics.current
    LaunchedEffect(state.notice) {
        if (state.notice != null) haptics.reject()
    }
    AppEditorScreen(
        title = stringResource(Res.string.intake_title),
        isDirty = state.dirty,
        isSaving = state.saving,
        isOnline = isOnline,
        onSave = { onEvent(IntakeEvent.Save) },
        onClose = onClose,
        modifier = modifier,
        saved = saved,
        canSave = state.canSave,
        footerNotice = { SaveNotice(state.notice) },
        testTagPrefix = "intake",
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg)
                .testTag("intake_screen"),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            AppTextField(
                value = state.text,
                onValueChange = { onEvent(IntakeEvent.EditFreeText(it)) },
                label = stringResource(Res.string.intake_free_text_label),
                maxLength = IntakeLimits.MAX_FREE_TEXT,
                testTagPrefix = "intake_free_text",
                imeAction = ImeAction.Default,
                singleLine = false,
                minLines = FREE_TEXT_MIN_LINES,
                enabled = !state.saving,
            )
            HelpAndCounter(state.text.length)
        }
    }
}

/** The "?" (what to write, R-46) beside the "0/500" counter. */
@Composable
private fun HelpAndCounter(length: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HelpButton(
            contentDescription = stringResource(Res.string.intake_free_text_help_cd),
            title = stringResource(Res.string.intake_title),
            body = stringResource(Res.string.intake_free_text_help_body),
            testTag = "intake_free_text_help",
        )
        Text(
            text = stringResource(Res.string.intake_free_text_counter, length, IntakeLimits.MAX_FREE_TEXT),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).testTag("intake_free_text_counter"),
        )
    }
}

@Composable
private fun SaveNotice(notice: IntakeNotice?) {
    // Offline the pinned "Guardar" already carries the hint: one offline message only (R-32).
    if (notice == IntakeNotice.SaveFailed) {
        InlineNote(text = stringResource(Res.string.intake_save_failed), testTag = "intake_save_error")
    }
}

/** Loading, offline and error before the form exists: a plain screen with back (nothing to discard yet). */
@Composable
private fun IntakePlaceholder(
    state: IntakeUiState,
    isOnline: Flow<Boolean>,
    onEvent: (IntakeEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScreen(
        title = stringResource(Res.string.intake_title),
        modifier = modifier,
        onBack = onClose,
        testTagPrefix = "intake",
    ) {
        Box(Modifier.fillMaxSize().testTag("intake_screen")) {
            when (state) {
                IntakeUiState.Offline -> OfflineState(isOnline = isOnline, onReload = { onEvent(IntakeEvent.Retry) })

                IntakeUiState.Error -> ErrorState(
                    message = stringResource(Res.string.intake_load_error),
                    action = AppStateAction(stringResource(Res.string.action_retry)) { onEvent(IntakeEvent.Retry) },
                    testTagPrefix = "intake_error",
                    actionTestTag = "intake_error_retry",
                )

                else -> Column(
                    Modifier.fillMaxSize().padding(Spacing.screenMargin).testTag("intake_loading"),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    repeat(SKELETON_FIELDS) {
                        SkeletonBox(Modifier.fillMaxWidth().height(Spacing.minTouchTarget))
                    }
                }
            }
        }
    }
}
