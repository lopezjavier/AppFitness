package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_continue
import io.github.lopezjavier.appfitness.composeapp.resources.block_type_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.block_type_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.block_type_help_title
import io.github.lopezjavier.appfitness.composeapp.resources.block_types_day
import io.github.lopezjavier.appfitness.composeapp.resources.block_types_day_value_recommended
import io.github.lopezjavier.appfitness.composeapp.resources.block_types_no_fit
import io.github.lopezjavier.appfitness.composeapp.resources.block_types_title
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.InlineNote
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.NavigationRow
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.coach.builder.RoutineType
import org.jetbrains.compose.resources.stringResource

/**
 * "Rutina de cada día" (US-72): one row per day with its type, opening [DayTypeSheet]. "Continuar" is disabled, with
 * the line that says why, while the first routine that does not fit exists.
 */
@Composable
internal fun BlockTypesScreen(
    state: BlockBuilderUiState.Types,
    onEvent: (BlockBuilderEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScreen(
        title = stringResource(Res.string.block_types_title),
        modifier = modifier,
        onBack = { onEvent(BlockBuilderEvent.Back) },
        footer = { TypesFooter(state, onEvent) },
        testTagPrefix = "block_types",
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.screenMargin)
                .testTag("block_types_screen"),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            ListSectionHeader(
                title = splitNameText(state.split),
                modifier = Modifier.testTag("block_types_split_name"),
            )
            state.chosen.forEachIndexed { day, type ->
                DayTypeRow(state, day, type, onEvent)
            }
        }
    }
    val openDay = state.openDay
    if (openDay != null) {
        DayTypeSheet(
            title = stringResource(Res.string.block_types_day, openDay + 1),
            options = state.options[openDay],
            chosen = state.chosen[openDay],
            onPick = { onEvent(BlockBuilderEvent.PickType(it)) },
            onDismiss = { onEvent(BlockBuilderEvent.OpenDay(null)) },
        )
    }
}

@Composable
private fun DayTypeRow(
    state: BlockBuilderUiState.Types,
    day: Int,
    type: RoutineType,
    onEvent: (BlockBuilderEvent) -> Unit,
) {
    val option = state.options[day].firstOrNull { it.type == type }
    val value = if (option?.recommended == true) {
        stringResource(Res.string.block_types_day_value_recommended, type.displayName)
    } else {
        type.displayName
    }
    val label = stringResource(Res.string.block_types_day, day + 1)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (state.options[day].size > 1) {
            NavigationRow(
                label = label,
                value = value,
                onClick = { onEvent(BlockBuilderEvent.OpenDay(day)) },
                modifier = Modifier.weight(1f).testTag("block_types_day_$day"),
            )
        } else {
            AppListRow(
                headline = label,
                trailing = {
                    Text(
                        type.displayName,
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.colors.textSecondary,
                    )
                },
                modifier = Modifier.weight(1f).testTag("block_types_day_$day"),
            )
        }
        if (option?.strengthNote == true) {
            HelpButton(
                contentDescription = stringResource(Res.string.block_type_help_cd, label),
                title = stringResource(Res.string.block_type_help_title),
                body = stringResource(Res.string.block_type_help_body),
                testTag = "block_type_help_$day",
            )
        }
    }
}

@Composable
private fun TypesFooter(state: BlockBuilderUiState.Types, onEvent: (BlockBuilderEvent) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        state.noFit?.let {
            InlineNote(
                text = stringResource(Res.string.block_types_no_fit, it.limitMinutes),
                modifier = Modifier.testTag("block_types_no_fit"),
            )
        }
        PrimaryButton(
            text = stringResource(Res.string.block_builder_continue),
            onClick = { onEvent(BlockBuilderEvent.Continue) },
            modifier = Modifier.fillMaxWidth().testTag("block_types_continue"),
            enabled = state.canContinue,
        )
    }
}
