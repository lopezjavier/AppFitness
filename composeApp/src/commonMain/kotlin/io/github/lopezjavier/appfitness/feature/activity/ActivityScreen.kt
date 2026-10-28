package io.github.lopezjavier.appfitness.feature.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.activity_back_to_today_cd
import io.github.lopezjavier.appfitness.composeapp.resources.activity_calendar_cd
import io.github.lopezjavier.appfitness.composeapp.resources.activity_day_picker_title
import io.github.lopezjavier.appfitness.composeapp.resources.activity_empty_message
import io.github.lopezjavier.appfitness.composeapp.resources.activity_empty_title
import io.github.lopezjavier.appfitness.composeapp.resources.activity_error
import io.github.lopezjavier.appfitness.composeapp.resources.activity_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_calendar
import io.github.lopezjavier.appfitness.designsystem.AppLineChartSkeleton
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTopBarAction
import io.github.lopezjavier.appfitness.designsystem.DayPickerSheet
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.OfflineState
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClass
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.designsystem.windowWidthClassFor
import io.github.lopezjavier.appfitness.feature.weight.HealthStatusLine
import io.github.lopezjavier.appfitness.feature.weight.HealthSyncEffect
import io.github.lopezjavier.appfitness.feature.weight.HealthSyncEvent
import io.github.lopezjavier.appfitness.feature.weight.HealthSyncUiState
import io.github.lopezjavier.appfitness.feature.weight.HealthSyncViewModel
import io.github.lopezjavier.appfitness.platform.HealthConnectLauncher
import io.github.lopezjavier.appfitness.platform.rememberHealthConnectLauncher
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Stateful "Actividad" screen: connects [ActivityViewModel] and [HealthSyncViewModel] (the status line and its
 * "Sincronizar" button, as in Peso). A finished sync and every resume reload the stored data.
 */
@Composable
fun ActivityRoot(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivityViewModel = koinViewModel(),
    healthSyncViewModel: HealthSyncViewModel = koinViewModel(),
    launcherFor: @Composable ((weightReadGranted: Boolean) -> Unit) -> HealthConnectLauncher =
        { rememberHealthConnectLauncher(it) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val healthSync by healthSyncViewModel.uiState.collectAsStateWithLifecycle()
    val launcher = launcherFor { granted -> healthSyncViewModel.onEvent(HealthSyncEvent.PermissionResult(granted)) }
    val latestLauncher by rememberUpdatedState(launcher)
    LaunchedEffect(healthSyncViewModel) {
        healthSyncViewModel.effects.collect { effect ->
            when (effect) {
                HealthSyncEffect.RequestPermissions -> latestLauncher.requestPermissions()
                HealthSyncEffect.OpenInstall -> latestLauncher.openInstall()
                is HealthSyncEffect.Synced -> viewModel.onEvent(ActivityEvent.Refresh)
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        healthSyncViewModel.onEvent(HealthSyncEvent.Refresh)
        viewModel.onEvent(ActivityEvent.Refresh)
    }
    ActivityScreen(
        state = state,
        isOnline = viewModel.isOnline,
        onEvent = viewModel::onEvent,
        healthSync = healthSync,
        onHealthEvent = healthSyncViewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * "Actividad" (US-64) on an `AppScreen`: the Health Connect status line with "Sincronizar", the summary of the
 * selected day (one card per metric), the chart of the selected metric and range, and the sleep stages. The calendar
 * action of the top bar picks another day.
 */
@Composable
@Suppress("LongParameterList") // State, its event sinks for both view models, the back action and the modifier.
fun ActivityScreen(
    state: ActivityUiState,
    isOnline: Flow<Boolean>,
    onEvent: (ActivityEvent) -> Unit,
    healthSync: HealthSyncUiState,
    onHealthEvent: (HealthSyncEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val calendarCd = stringResource(Res.string.activity_calendar_cd)
    val hasContent = state is ActivityUiState.Content
    val actions = remember(calendarCd, hasContent) {
        if (hasContent) {
            listOf(
                AppTopBarAction(Res.drawable.ic_calendar, calendarCd, {
                    pickerOpen = true
                }, "activity_calendar_action"),
            )
        } else {
            emptyList()
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = windowWidthClassFor(maxWidth) == WindowWidthClass.Wide
        AppScreen(
            title = stringResource(Res.string.activity_title),
            modifier = Modifier.testTag("activity_screen"),
            onBack = onBack,
            actions = actions,
            maxContentWidth = if (wide) {
                WindowWidthClassDefaults.TwoColumnContentMaxWidth
            } else {
                WindowWidthClassDefaults.FormContentMaxWidth
            },
            testTagPrefix = "activity",
        ) {
            ActivityBody(state, isOnline, onEvent, healthSync, onHealthEvent, wide)
        }
    }
    if (pickerOpen && state is ActivityUiState.Content) {
        DayPickerSheet(
            title = stringResource(Res.string.activity_day_picker_title),
            todayContentDescription = stringResource(Res.string.activity_back_to_today_cd),
            tagPrefix = "activity_day_picker",
            selected = state.day,
            today = state.today,
            isToday = state.isToday,
            markedDays = state.markedDays,
            onPickDay = { onEvent(ActivityEvent.SelectDay(it)) },
            onGoToToday = { onEvent(ActivityEvent.GoToToday) },
            onMonthChange = {},
            onDismiss = { pickerOpen = false },
        )
    }
}

/** The status line sits directly under the top bar in every state (loading, empty, error, offline, content). */
@Composable
private fun ActivityStatusLine(healthSync: HealthSyncUiState, onHealthEvent: (HealthSyncEvent) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("activity_status_line")) {
        HealthStatusLine(healthSync, onHealthEvent)
    }
}

@Composable
@Suppress("LongParameterList") // State, its event sinks for both view models and the layout choice.
private fun ActivityBody(
    state: ActivityUiState,
    isOnline: Flow<Boolean>,
    onEvent: (ActivityEvent) -> Unit,
    healthSync: HealthSyncUiState,
    onHealthEvent: (HealthSyncEvent) -> Unit,
    wide: Boolean,
) {
    when (state) {
        ActivityUiState.Loading -> ScrollColumn {
            ActivityStatusLine(healthSync, onHealthEvent)
            Column(
                Modifier.testTag("activity_loading"),
                verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
            ) {
                ActivitySummarySkeleton()
                AppLineChartSkeleton()
            }
        }

        is ActivityUiState.Content -> ActivityContent(state, onEvent, healthSync, onHealthEvent, wide)

        else -> StateColumn(healthSync, onHealthEvent) {
            when (state) {
                ActivityUiState.Empty -> EmptyState(
                    message = stringResource(Res.string.activity_empty_message),
                    title = stringResource(Res.string.activity_empty_title),
                    testTagPrefix = "activity_empty",
                )

                ActivityUiState.Offline -> OfflineState(isOnline = isOnline, onReload = {
                    onEvent(ActivityEvent.Retry)
                })

                else -> ErrorState(
                    message = stringResource(Res.string.activity_error),
                    action = AppStateAction(stringResource(Res.string.action_retry)) { onEvent(ActivityEvent.Retry) },
                    testTagPrefix = "activity_error",
                    actionTestTag = "activity_retry",
                )
            }
        }
    }
}

@Composable
private fun ScrollColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) { content() }
}

/** The status line on top and the centred state message (empty, error, offline) in the rest of the screen. */
@Composable
private fun StateColumn(
    healthSync: HealthSyncUiState,
    onHealthEvent: (HealthSyncEvent) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        ActivityStatusLine(healthSync, onHealthEvent)
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

@Composable
private fun ActivityContent(
    state: ActivityUiState.Content,
    onEvent: (ActivityEvent) -> Unit,
    healthSync: HealthSyncUiState,
    onHealthEvent: (HealthSyncEvent) -> Unit,
    wide: Boolean,
) {
    if (wide) {
        Row(Modifier.fillMaxSize().testTag("activity_two_column")) {
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(Spacing.screenMargin),
                verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
            ) {
                ActivityStatusLine(healthSync, onHealthEvent)
                ActivitySummary(state, onEvent)
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(Spacing.screenMargin),
            ) { ActivityChartCard(state, onEvent) }
        }
    } else {
        ScrollColumn {
            ActivityStatusLine(healthSync, onHealthEvent)
            ActivitySummary(state, onEvent)
            ActivityChartCard(state, onEvent)
        }
    }
}
