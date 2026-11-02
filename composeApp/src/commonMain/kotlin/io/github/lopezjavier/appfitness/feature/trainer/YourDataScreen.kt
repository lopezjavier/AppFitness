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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.before_start_error
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_provisional
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_value_supporting
import io.github.lopezjavier.appfitness.composeapp.resources.your_data_handover_note
import io.github.lopezjavier.appfitness.composeapp.resources.your_data_intake_provisional
import io.github.lopezjavier.appfitness.composeapp.resources.your_data_intake_row
import io.github.lopezjavier.appfitness.composeapp.resources.your_data_save_action
import io.github.lopezjavier.appfitness.composeapp.resources.your_data_save_error
import io.github.lopezjavier.appfitness.composeapp.resources.your_data_situation_row
import io.github.lopezjavier.appfitness.composeapp.resources.your_data_title
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.InlineNote
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.NavigationRow
import io.github.lopezjavier.appfitness.designsystem.OfflineState
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StatusChip
import io.github.lopezjavier.appfitness.designsystem.StatusTone
import io.github.lopezjavier.appfitness.designsystem.offlineHint
import io.github.lopezjavier.appfitness.domain.model.PlanFixTarget
import io.github.lopezjavier.appfitness.domain.model.Situation
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.feature.exercises.tagName
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

private const val SKELETON_ROWS = 5
private const val TITLE_FRACTION = 0.4f

/** Stateful "Tus datos": reloads when the user comes back from an editor and plays the save-failure message. */
@Composable
fun YourDataRoot(
    onBack: () -> Unit,
    onFix: (PlanFixTarget) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YourDataViewModel = koinViewModel(),
    connectivity: ConnectivityObserver = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalAppHaptics.current
    val messages = LocalAppMessages.current
    val latestHaptics by rememberUpdatedState(haptics)
    val failed = stringResource(Res.string.your_data_save_error)
    val offline = offlineHint(stringResource(Res.string.your_data_save_action)) + "."
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(YourDataEvent.Refresh) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is YourDataEffect.SaveFailed -> {
                    latestHaptics.reject()
                    messages.show(if (effect.offline) offline else failed)
                }
            }
        }
    }
    YourDataScreen(
        state = state,
        isOnline = connectivity.isOnline,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onFix = onFix,
        modifier = modifier,
    )
}

/**
 * "Tus datos" on [AppScreen] with back, no footer: every input the coach reads, grouped by origin. A row with an
 * editor opens it (the activity level opens its sheet); derived values are plain rows. Provisional ones carry a chip.
 */
@Composable
fun YourDataScreen(
    state: YourDataUiState,
    isOnline: Flow<Boolean>,
    onEvent: (YourDataEvent) -> Unit,
    onBack: () -> Unit,
    onFix: (PlanFixTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val online by isOnline.collectAsStateWithLifecycle(initialValue = true)
    AppScreen(
        title = stringResource(Res.string.your_data_title),
        modifier = modifier,
        onBack = onBack,
        testTagPrefix = "your_data",
    ) {
        Box(Modifier.fillMaxSize().testTag("your_data_screen")) {
            when (state) {
                YourDataUiState.Loading -> YourDataSkeleton()

                YourDataUiState.Offline -> OfflineState(isOnline = isOnline, onReload = {
                    onEvent(YourDataEvent.Retry)
                })

                YourDataUiState.Error -> ErrorState(
                    message = stringResource(Res.string.before_start_error),
                    action = AppStateAction(stringResource(Res.string.action_retry)) { onEvent(YourDataEvent.Retry) },
                    testTagPrefix = "your_data_error",
                    actionTestTag = "your_data_error_retry",
                )

                is YourDataUiState.Content -> {
                    YourDataContent(state, onEvent, onFix)
                    if (state.sheet != null) {
                        ActivityLevelSheet(
                            selected = state.sheet,
                            saving = state.savingLevel,
                            online = online,
                            onDismiss = { onEvent(YourDataEvent.CloseSheet) },
                            onPick = { onEvent(YourDataEvent.PickLevel(it)) },
                            testTagPrefix = "your_data_activity_sheet",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YourDataContent(
    state: YourDataUiState.Content,
    onEvent: (YourDataEvent) -> Unit,
    onFix: (PlanFixTarget) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        if (state.showHandover) HandoverNote(state.handoverKcal, onEvent)
        state.sections.forEach { section ->
            Column(Modifier.testTag("your_data_section_${section.origin.tagName()}")) {
                ListSectionHeader(stringResource(section.origin.title()))
                section.rows.forEach { row ->
                    YourDataRow(row, row.key in state.intakeSourcedKeys, onEvent, onFix)
                }
            }
        }
        IntakeEntry(state.situation, onFix)
    }
}

/** The one-time note after the measured expenditure replaced the one the user said; it tells the server once shown. */
@Composable
private fun HandoverNote(kcal: Double?, onEvent: (YourDataEvent) -> Unit) {
    val latestOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(Unit) { latestOnEvent(YourDataEvent.HandoverShown) }
    InlineNote(
        text = stringResource(
            Res.string.your_data_handover_note,
            kcal?.let { AppNumberFormat.integer(it.roundToInt()) }.orEmpty(),
        ),
        testTag = "your_data_handover_note",
    )
}

/** The situation the coach works with and the row that opens "Para conocerte" (an editor, hence its own row). */
@Composable
private fun IntakeEntry(situation: Situation?, onFix: (PlanFixTarget) -> Unit) {
    Column {
        situation?.let {
            AppListRow(
                headline = stringResource(Res.string.your_data_situation_row),
                modifier = Modifier.testTag("your_data_situation_row"),
                trailing = {
                    Text(
                        text = stringResource(it.intakeLabel()),
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.colors.textSecondary,
                    )
                },
            )
        }
        NavigationRow(
            label = stringResource(Res.string.your_data_intake_row),
            onClick = { onFix(PlanFixTarget.Intake) },
            modifier = Modifier.testTag("your_data_intake_row"),
        )
    }
}

@Composable
private fun YourDataRow(
    row: PlanInputRow,
    fromIntake: Boolean,
    onEvent: (YourDataEvent) -> Unit,
    onFix: (PlanFixTarget) -> Unit,
) {
    val tag = "your_data_row_${row.key.tagName()}"
    val value =
        stringResource(Res.string.trainer_value_supporting, row.valueText(), stringResource(row.source.originLabel()))
    Column {
        val target = row.fixTarget
        if (target != null) {
            NavigationRow(
                label = row.labelText(),
                value = value,
                onClick = {
                    if (target == PlanFixTarget.ActivitySheet) onEvent(YourDataEvent.OpenSheet) else onFix(target)
                },
                modifier = Modifier.testTag(tag),
            )
        } else {
            AppListRow(
                headline = row.labelText(),
                modifier = Modifier.testTag(tag),
                trailing = {
                    Text(text = value, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                },
            )
        }
        if (row.isProvisional || fromIntake) {
            StatusChip(
                text = stringResource(Res.string.trainer_provisional),
                tone = StatusTone.Caution,
                modifier = Modifier.testTag("your_data_chip_provisional_${row.key.tagName()}"),
            )
        }
        if (fromIntake) {
            Text(
                text = stringResource(Res.string.your_data_intake_provisional),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("your_data_intake_provisional_${row.key.tagName()}"),
            )
        }
    }
}

/** Card-shaped placeholder while the data loads (R-31): a title bar and one bar per row. */
@Composable
private fun YourDataSkeleton() {
    AppCard(Modifier.fillMaxWidth().padding(Spacing.screenMargin).testTag("your_data_loading")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SkeletonBox(Modifier.fillMaxWidth(TITLE_FRACTION).height(Spacing.xl))
            repeat(SKELETON_ROWS) { SkeletonBox(Modifier.fillMaxWidth().height(Spacing.minTouchTarget)) }
        }
    }
}
