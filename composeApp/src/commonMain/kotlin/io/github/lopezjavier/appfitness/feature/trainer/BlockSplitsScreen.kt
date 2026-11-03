package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.block_preview_error
import io.github.lopezjavier.appfitness.composeapp.resources.block_preview_interrupted
import io.github.lopezjavier.appfitness.composeapp.resources.block_preview_progress
import io.github.lopezjavier.appfitness.composeapp.resources.block_preview_progress_cd
import io.github.lopezjavier.appfitness.composeapp.resources.block_splits_create
import io.github.lopezjavier.appfitness.composeapp.resources.block_splits_create_action
import io.github.lopezjavier.appfitness.composeapp.resources.block_splits_error
import io.github.lopezjavier.appfitness.composeapp.resources.block_splits_progress
import io.github.lopezjavier.appfitness.composeapp.resources.block_splits_progress_cd
import io.github.lopezjavier.appfitness.composeapp.resources.block_splits_recommended
import io.github.lopezjavier.appfitness.composeapp.resources.block_splits_title
import io.github.lopezjavier.appfitness.composeapp.resources.block_splits_title_recommended
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SelectableCard
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StreamProgressState
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.model.SplitOption
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val OptionsFailureCopy = CoachFailureCopy(
    error = Res.string.block_splits_error,
    interrupted = Res.string.block_preview_interrupted,
)

private val BuildFailureCopy = CoachFailureCopy(
    error = Res.string.block_preview_error,
    interrupted = Res.string.block_preview_interrupted,
)

/**
 * Stateful "Tipo de rutina" (US-93): asks the coach for the options once when it opens, and moves to the block preview
 * when the block is built. Reopening it never asks again by itself after a recreation (the model keeps the options).
 */
@Composable
fun BlockSplitsRoot(
    planId: String,
    onShowBlock: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BlockSplitsViewModel = koinViewModel { parametersOf(planId) },
    connectivity: ConnectivityObserver = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val latestOnShowBlock by rememberUpdatedState(onShowBlock)
    LaunchedEffect(viewModel) { viewModel.onEvent(BlockSplitsEvent.Request) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                BlockSplitsEffect.Built -> latestOnShowBlock()
            }
        }
    }
    BlockSplitsScreen(
        state = state,
        isOnline = connectivity.isOnline,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * "Tipo de rutina" on [AppScreen] with back: one [SelectableCard] per split option (the recommended one carries the
 * word "Recomendada" and starts selected) and a pinned "Crear bloque" footer. Waiting for the coach is a
 * [StreamProgressState] and a failure is [CoachFailureContent] with "Reintentar" (R-31). Leaving stores nothing.
 */
@Composable
fun BlockSplitsScreen(
    state: BlockSplitsUiState,
    isOnline: Flow<Boolean>,
    onEvent: (BlockSplitsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val online by isOnline.collectAsStateWithLifecycle(initialValue = true)
    AppScreen(
        title = stringResource(Res.string.block_splits_title),
        modifier = modifier,
        onBack = onBack,
        footer = (state as? BlockSplitsUiState.Options)?.let { { SplitsFooter(it, online, onEvent) } },
        testTagPrefix = "block_splits",
    ) {
        Box(Modifier.fillMaxSize().testTag("block_splits_screen")) {
            when (state) {
                BlockSplitsUiState.Idle, BlockSplitsUiState.Requesting -> Progress(
                    message = stringResource(Res.string.block_splits_progress),
                    description = stringResource(Res.string.block_splits_progress_cd),
                )

                is BlockSplitsUiState.Options -> if (state.building) {
                    Progress(
                        message = stringResource(Res.string.block_preview_progress),
                        description = stringResource(Res.string.block_preview_progress_cd),
                    )
                } else {
                    OptionList(state, onEvent)
                }

                is BlockSplitsUiState.Failed -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    val copy = if (state.stage == BlockSplitsStage.Options) OptionsFailureCopy else BuildFailureCopy
                    CoachFailureContent(state.kind, isOnline, copy, "block_splits") {
                        onEvent(BlockSplitsEvent.Retry)
                    }
                }
            }
        }
    }
}

@Composable
private fun Progress(message: String, description: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        StreamProgressState(message = message, stateDescription = description, testTag = "block_splits_progress")
    }
}

@Composable
private fun OptionList(state: BlockSplitsUiState.Options, onEvent: (BlockSplitsEvent) -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screenMargin)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
        ) {
            state.options.forEach { option ->
                OptionCard(option, selected = option.id == state.selectedId) {
                    onEvent(BlockSplitsEvent.Pick(option.id))
                }
            }
        }
    }
}

/** A split option: its name (with "Recomendada" as text when it is), the workouts and the coach's reason. */
@Composable
private fun OptionCard(option: SplitOption, selected: Boolean, onClick: () -> Unit) {
    val title = if (option.recommended) {
        stringResource(
            Res.string.block_splits_title_recommended,
            option.name,
            stringResource(Res.string.block_splits_recommended),
        )
    } else {
        option.name
    }
    val description = listOf(option.sessions.joinToString(" · ") { it.name }, option.reason)
        .filter { it.isNotBlank() }
        .joinToString("\n")
    val card: @Composable () -> Unit = {
        SelectableCard(
            title = title,
            selected = selected,
            onClick = onClick,
            description = description,
            modifier = Modifier.testTag("block_splits_option_${option.id}"),
        )
    }
    if (option.recommended) {
        Box(Modifier.testTag("block_splits_recommended_${option.id}")) { card() }
    } else {
        card()
    }
}

/** Pinned footer: "Crear bloque", disabled offline with the reason (R-30) and while the block is being built. */
@Composable
private fun SplitsFooter(state: BlockSplitsUiState.Options, online: Boolean, onEvent: (BlockSplitsEvent) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        PrimaryButton(
            text = stringResource(Res.string.block_splits_create),
            onClick = { onEvent(BlockSplitsEvent.Create) },
            modifier = Modifier.fillMaxWidth().testTag("block_splits_create"),
            enabled = online && !state.building,
        )
        if (!online) OfflineHint(stringResource(Res.string.block_splits_create_action))
    }
}
