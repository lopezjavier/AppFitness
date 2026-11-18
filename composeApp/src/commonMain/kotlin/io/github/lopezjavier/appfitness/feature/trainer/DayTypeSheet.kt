package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.block_type_recommended
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppPickerSheet
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.TrainingPartGlyph
import io.github.lopezjavier.appfitness.domain.coach.builder.DayTypeOption
import io.github.lopezjavier.appfitness.domain.coach.builder.RoutineType
import io.github.lopezjavier.appfitness.domain.coach.builder.part
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The routine types of one day's split family (R-12: a tap applies and closes). The type the split advises says
 * "Recomendado"; one that would leave a muscle without a day is disabled and says why.
 */
@Composable
internal fun DayTypeSheet(
    title: String,
    options: List<DayTypeOption>,
    chosen: RoutineType,
    onPick: (RoutineType) -> Unit,
    onDismiss: () -> Unit,
) {
    AppPickerSheet(title = title, onDismiss = onDismiss, testTagPrefix = "block_types_sheet") {
        Column { options.forEach { DayTypeRow(it, it.type == chosen, onPick) } }
    }
}

@Composable
private fun DayTypeRow(option: DayTypeOption, current: Boolean, onPick: (RoutineType) -> Unit) {
    val reason = option.disabledReason?.let { disabledReasonText(it) }
    val recommended = stringResource(Res.string.block_type_recommended)
    AppListRow(
        headline = option.type.displayName,
        supporting = reason ?: routineGroupsText(option.type),
        trailing = if (option.recommended || current) {
            {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    if (option.recommended) {
                        Text(recommended, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                    }
                    // The lime check marks the day type in use (V-57); the selected semantics carry it for TalkBack.
                    if (current) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_check),
                            contentDescription = null,
                            tint = AppTheme.colors.neonLime,
                            modifier = Modifier.size(Spacing.xl).testTag("block_type_option_${option.type.name}_check"),
                        )
                    }
                }
            }
        } else {
            null
        },
        leading = {
            TrainingPartGlyph(option.type.part, Modifier.testTag("block_type_option_${option.type.name}_part_icon"))
        },
        enabled = option.enabled,
        onClick = { onPick(option.type) },
        modifier = Modifier.testTag("block_type_option_${option.type.name}").semantics { selected = current },
    )
}
