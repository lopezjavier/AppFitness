package io.github.lopezjavier.appfitness.feature.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.health_diagnostics_check
import io.github.lopezjavier.appfitness.composeapp.resources.health_diagnostics_counts_format
import io.github.lopezjavier.appfitness.composeapp.resources.health_diagnostics_error
import io.github.lopezjavier.appfitness.composeapp.resources.health_diagnostics_feature
import io.github.lopezjavier.appfitness.composeapp.resources.health_diagnostics_feature_unavailable
import io.github.lopezjavier.appfitness.composeapp.resources.health_diagnostics_history
import io.github.lopezjavier.appfitness.composeapp.resources.health_diagnostics_no
import io.github.lopezjavier.appfitness.composeapp.resources.health_diagnostics_old
import io.github.lopezjavier.appfitness.composeapp.resources.health_diagnostics_oldest
import io.github.lopezjavier.appfitness.composeapp.resources.health_diagnostics_recent
import io.github.lopezjavier.appfitness.composeapp.resources.health_diagnostics_title
import io.github.lopezjavier.appfitness.composeapp.resources.health_diagnostics_yes
import io.github.lopezjavier.appfitness.composeapp.resources.health_install_needed
import io.github.lopezjavier.appfitness.composeapp.resources.health_update_needed
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.HealthAvailability
import io.github.lopezjavier.appfitness.domain.model.HistoryProbeResult
import io.github.lopezjavier.appfitness.domain.model.WindowStats
import io.github.lopezjavier.appfitness.platform.HealthConnectLauncher
import io.github.lopezjavier.appfitness.platform.rememberHealthHistoryLauncher
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant

private const val NO_VALUE = "-"
private const val DATE_FIELD_WIDTH = 2
private const val YEAR_FIELD_WIDTH = 4

/**
 * Stateful temporary diagnostic (T-139): connects [HealthDiagnosticsViewModel] and the permission screen.
 * Removed with the diagnostic (T-141).
 */
@Composable
fun HealthDiagnosticsRoot(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HealthDiagnosticsViewModel = koinViewModel(),
    launcherFor: @Composable (() -> Unit) -> HealthConnectLauncher = { rememberHealthHistoryLauncher(it) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launcher = launcherFor { viewModel.onEvent(HealthDiagnosticsEvent.PermissionResult) }
    val latestLauncher by rememberUpdatedState(launcher)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                HealthDiagnosticsEffect.RequestPermissions -> latestLauncher.requestPermissions()
            }
        }
    }
    HealthDiagnosticsScreen(
        state = state,
        onCheck = { viewModel.onEvent(HealthDiagnosticsEvent.Check) },
        onBack = onBack,
        modifier = modifier,
    )
}

/** The five diagnostic values plus the "Comprobar" button; all formatting happens here, the state holds raw data. */
@Composable
fun HealthDiagnosticsScreen(
    state: HealthDiagnosticsState,
    onCheck: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    timeZone: TimeZone = remember { TimeZone.currentSystemDefault() },
) {
    val values = (state as? HealthDiagnosticsState.Result)?.values
    AppScreen(
        title = stringResource(Res.string.health_diagnostics_title),
        modifier = modifier,
        onBack = onBack,
        testTagPrefix = "health_diagnostics",
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg)
                .testTag("health_diagnostics_screen"),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            StatusMessage(state)
            DiagnosticRows(values, timeZone)
            PrimaryButton(
                text = stringResource(Res.string.health_diagnostics_check),
                onClick = onCheck,
                modifier = Modifier.fillMaxWidth().testTag("health_diagnostics_check_button"),
                enabled = state != HealthDiagnosticsState.Running,
                loading = state == HealthDiagnosticsState.Running,
            )
        }
    }
}

/** The five rows; with no [values] yet each shows "-". */
@Composable
private fun DiagnosticRows(values: HistoryProbeResult?, timeZone: TimeZone) {
    val yes = stringResource(Res.string.health_diagnostics_yes)
    val no = stringResource(Res.string.health_diagnostics_no)
    val unavailable = stringResource(Res.string.health_diagnostics_feature_unavailable)
    AppCard(Modifier.fillMaxWidth()) {
        ValueRow(
            stringResource(Res.string.health_diagnostics_feature),
            values?.let { if (it.featureAvailable) yes else unavailable } ?: NO_VALUE,
            "health_diagnostics_feature_row",
        )
        ValueRow(
            stringResource(Res.string.health_diagnostics_history),
            values?.let { if (it.historyGranted) yes else no } ?: NO_VALUE,
            "health_diagnostics_history_row",
        )
        ValueRow(
            stringResource(Res.string.health_diagnostics_old),
            values?.let { pair(it.steps.old.recordCount.toString(), it.weight.old.recordCount.toString()) }
                ?: NO_VALUE,
            "health_diagnostics_old_row",
        )
        ValueRow(
            stringResource(Res.string.health_diagnostics_recent),
            values?.let { pair(it.steps.recent.recordCount.toString(), it.weight.recent.recordCount.toString()) }
                ?: NO_VALUE,
            "health_diagnostics_recent_row",
        )
        ValueRow(
            stringResource(Res.string.health_diagnostics_oldest),
            values?.let {
                pair(
                    formatDate(earliest(it.steps.old, it.steps.recent), timeZone),
                    formatDate(earliest(it.weight.old, it.weight.recent), timeZone),
                )
            } ?: NO_VALUE,
            "health_diagnostics_oldest_row",
        )
    }
}

@Composable
private fun StatusMessage(state: HealthDiagnosticsState) {
    val text = when (state) {
        is HealthDiagnosticsState.Unavailable -> stringResource(
            if (state.availability == HealthAvailability.UpdateRequired) {
                Res.string.health_update_needed
            } else {
                Res.string.health_install_needed
            },
        )

        HealthDiagnosticsState.Error -> stringResource(Res.string.health_diagnostics_error)

        else -> return
    }
    val isError = state is HealthDiagnosticsState.Error
    Text(
        text = text,
        style = AppTheme.typography.bodyMedium,
        color = if (isError) AppTheme.colors.error else AppTheme.colors.textSecondary,
        modifier = Modifier.testTag(if (isError) "health_diagnostics_error" else "health_diagnostics_unavailable"),
    )
}

@Composable
private fun ValueRow(label: String, value: String, tag: String) {
    AppListRow(
        headline = label,
        modifier = Modifier.semantics(mergeDescendants = true) {}.testTag(tag),
        trailing = {
            Text(text = value, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        },
    )
}

@Composable
private fun pair(steps: String, weight: String) =
    stringResource(Res.string.health_diagnostics_counts_format, steps, weight)

private fun earliest(first: WindowStats, second: WindowStats): Instant? =
    listOfNotNull(first.oldest, second.oldest).minOrNull()

/** `dd/MM/yyyy` in [timeZone], or "-" when there is no date. */
internal fun formatDate(instant: Instant?, timeZone: TimeZone): String {
    val date = instant?.toLocalDateTime(timeZone)?.date ?: return NO_VALUE
    return "${date.day.toString().padStart(DATE_FIELD_WIDTH, '0')}/" +
        "${date.month.number.toString().padStart(DATE_FIELD_WIDTH, '0')}/" +
        date.year.toString().padStart(YEAR_FIELD_WIDTH, '0')
}
