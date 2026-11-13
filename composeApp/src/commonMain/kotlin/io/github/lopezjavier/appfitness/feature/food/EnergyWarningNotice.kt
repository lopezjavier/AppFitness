package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_energy_help_off
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_energy_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_energy_help_fibre_alcohol
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_energy_help_polyols
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_energy_help_title
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_energy_warning
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_energy_warning_wrong
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_retry_cd
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_retry_offline_action
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_retry_photo_gone
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_retry_running
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_retry_text_gone
import io.github.lopezjavier.appfitness.composeapp.resources.ic_retry
import io.github.lopezjavier.appfitness.composeapp.resources.ic_warning
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.FeatureMetrics
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.HelpContent
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.nestedContainerColor
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val MaxNoticeHeight = Spacing.xl * 8

/**
 * The energy-check block (07 section 4, ADR-104) shown in the pinned footer of a food form: the facts, an optional
 * "Repetir la estimación" icon ([onRetry], AI estimates) and a "?" whose sheet holds the sign-dependent advice (R-57).
 * Shared by the create / edit form and the add sheet. With [offProduct] (an Open Food Facts food the user did not
 * type) the help body is OFF-specific and [onFix] / [fixLabel] open the corrected copy. Tags derive from
 * [testTagPrefix]. Not a failure: no error colour, no haptic.
 */
@Composable
internal fun EnergyWarningNotice(
    warning: EnergyWarning,
    modifier: Modifier = Modifier,
    onFix: (() -> Unit)? = null,
    fixLabel: String? = null,
    offProduct: Boolean = false,
    testTagPrefix: String = "create_food_energy",
    retryState: RetryState = RetryState.None,
    retryFromText: Boolean = false,
    isOffline: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(max = MaxNoticeHeight)
            .background(nestedContainerColor(), AppShapes.md)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md)
            .testTag("${testTagPrefix}_warning"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(Res.drawable.ic_warning),
                contentDescription = null,
                tint = AppTheme.colors.energyAmber,
                modifier = Modifier.size(FeatureMetrics.NoticeIconSize),
            )
            Text(
                text = warningText(warning),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (onRetry != null && (retryState == RetryState.Available || retryState == RetryState.Running)) {
                RetryButton(retryState == RetryState.Running, isOffline, "${testTagPrefix}_retry", onRetry)
            }
            HelpButton(
                contentDescription = stringResource(Res.string.create_food_energy_help_cd),
                title = stringResource(Res.string.create_food_energy_help_title),
                content = HelpContent(listOf(stringResource(adviceOf(warning, offProduct)))),
                testTag = "${testTagPrefix}_warning_help",
            )
        }
        if (retryState == RetryState.PhotoGone) {
            Text(
                text = stringResource(
                    if (retryFromText) {
                        Res.string.create_food_retry_text_gone
                    } else {
                        Res.string.create_food_retry_photo_gone
                    },
                ),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("create_food_retry_photo_gone"),
            )
        }
        if (onRetry != null && isOffline && retryState == RetryState.Available) {
            OfflineHint(stringResource(Res.string.create_food_retry_offline_action))
        }
        if (onFix != null && fixLabel != null) {
            GhostButton(
                text = fixLabel,
                onClick = onFix,
                modifier = Modifier.fillMaxWidth().testTag("${testTagPrefix}_fix"),
            )
        }
    }
}

/** The facts of [warning], with the "Parece un error" prefix when it is clearly wrong. */
@Composable
private fun warningText(warning: EnergyWarning): String = stringResource(
    if (warning.clearlyWrong) Res.string.create_food_energy_warning_wrong else Res.string.create_food_energy_warning,
    AppNumberFormat.decimal(warning.kcal, maxDecimals = 0),
    AppNumberFormat.decimal(warning.computedKcal, maxDecimals = 0),
)

/** The advice behind the "?": the Open Food Facts one, or the one for the sign of the gap. */
private fun adviceOf(warning: EnergyWarning, offProduct: Boolean): StringResource = when {
    offProduct -> Res.string.add_food_energy_help_off
    warning.gap < 0 -> Res.string.create_food_energy_help_fibre_alcohol
    else -> Res.string.create_food_energy_help_polyols
}

/** "Repetir la estimación": disabled while [running] (progress ring) or [offline]. */
@Composable
private fun RetryButton(running: Boolean, offline: Boolean, testTag: String, onClick: () -> Unit) {
    AppIconButton(
        contentDescription = stringResource(Res.string.create_food_retry_cd),
        icon = Res.drawable.ic_retry,
        testTag = testTag,
        onClick = onClick,
        enabled = !running && !offline,
        busyDescription = if (running) stringResource(Res.string.create_food_retry_running) else null,
    )
}
