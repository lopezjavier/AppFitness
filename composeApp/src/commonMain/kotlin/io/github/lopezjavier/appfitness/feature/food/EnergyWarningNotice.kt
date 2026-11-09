package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_energy_help_off
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_energy_fix
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_energy_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_energy_help_fibre_alcohol
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_energy_help_polyols
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_energy_help_title
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_energy_warning
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_energy_warning_wrong
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.GhostTone
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.HelpContent
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource

private val MaxNoticeHeight = Spacing.xl * 8

/**
 * The energy-check warning (07 section 4) shown in the pinned footer of a food form: the facts, a "?" whose sheet
 * holds the sign-dependent advice (R-57) and "Corregir", which only closes the warning. Shared by every form that
 * runs the check (create / edit, audit editor, add sheet). With [offProduct] (an Open Food Facts food the user did not
 * type) the help body is OFF-specific and [fixLabel] is "Ahora no". Not a failure: no error colour, no haptic.
 */
@Composable
internal fun EnergyWarningNotice(
    warning: EnergyWarning,
    onFix: () -> Unit,
    modifier: Modifier = Modifier,
    fixLabel: String = stringResource(Res.string.create_food_energy_fix),
    offProduct: Boolean = false,
) {
    val facts = stringResource(
        Res.string.create_food_energy_warning,
        AppNumberFormat.decimal(warning.kcal, maxDecimals = 0),
        AppNumberFormat.decimal(warning.computedKcal, maxDecimals = 0),
    )
    val text = if (warning.clearlyWrong) stringResource(Res.string.create_food_energy_warning_wrong, facts) else facts
    val advice = stringResource(
        if (offProduct) {
            Res.string.add_food_energy_help_off
        } else if (warning.gap < 0) {
            Res.string.create_food_energy_help_fibre_alcohol
        } else {
            Res.string.create_food_energy_help_polyols
        },
    )
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(max = MaxNoticeHeight)
            .background(AppTheme.colors.surfaceOverlay, AppShapes.md)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md)
            .testTag("create_food_energy_warning"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.energyAmber,
                modifier = Modifier.weight(1f),
            )
            HelpButton(
                contentDescription = stringResource(Res.string.create_food_energy_help_cd),
                title = stringResource(Res.string.create_food_energy_help_title),
                content = HelpContent(listOf(advice)),
                testTag = "create_food_energy_warning_help",
            )
        }
        GhostButton(
            text = fixLabel,
            onClick = onFix,
            modifier = Modifier.fillMaxWidth().testTag("create_food_energy_fix"),
            tone = GhostTone.Action,
        )
    }
}
