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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_continue
import io.github.lopezjavier.appfitness.composeapp.resources.block_targets_choice_none
import io.github.lopezjavier.appfitness.composeapp.resources.block_targets_clear_cd
import io.github.lopezjavier.appfitness.composeapp.resources.block_targets_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.block_targets_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.block_targets_line
import io.github.lopezjavier.appfitness.composeapp.resources.block_targets_maintain
import io.github.lopezjavier.appfitness.composeapp.resources.block_targets_prioritise
import io.github.lopezjavier.appfitness.composeapp.resources.block_targets_reason_cut
import io.github.lopezjavier.appfitness.composeapp.resources.block_targets_reason_low_adherence
import io.github.lopezjavier.appfitness.composeapp.resources.block_targets_reason_three
import io.github.lopezjavier.appfitness.composeapp.resources.block_targets_state
import io.github.lopezjavier.appfitness.composeapp.resources.block_targets_suggestion
import io.github.lopezjavier.appfitness.composeapp.resources.block_targets_suggestion_applied
import io.github.lopezjavier.appfitness.composeapp.resources.block_targets_suggestion_applied_cd
import io.github.lopezjavier.appfitness.composeapp.resources.block_targets_suggestion_cd
import io.github.lopezjavier.appfitness.composeapp.resources.block_targets_title
import io.github.lopezjavier.appfitness.composeapp.resources.block_types_no_fit
import io.github.lopezjavier.appfitness.composeapp.resources.ic_close
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.AppToggleChip
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.InlineNote
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SegmentedSelector
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.coach.builder.GroupPriority
import io.github.lopezjavier.appfitness.domain.coach.builder.NextBlockTargets
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.feature.exercises.label
import io.github.lopezjavier.appfitness.feature.exercises.tagName
import org.jetbrains.compose.resources.stringResource

private const val PRIORITISE_INDEX = 0
private const val MAINTAIN_INDEX = 1

/**
 * "Objetivo por grupo" (US-102): the first step of the next block. One row per group with "Priorizar" / "Mantener"
 * (neither by default), the sets before and after, why "Priorizar" gives nothing and the tap-to-apply suggestion.
 * "Continuar" is disabled, with the line that says why, while the raised targets do not fit.
 */
@Composable
internal fun BlockTargetsScreen(
    state: BlockBuilderUiState.Targets,
    onEvent: (BlockBuilderEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScreen(
        title = stringResource(Res.string.block_targets_title),
        modifier = modifier,
        onBack = { onEvent(BlockBuilderEvent.Back) },
        footer = { TargetsFooter(state, onEvent) },
        testTagPrefix = "block_builder_targets",
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.screenMargin)
                .testTag("block_builder_targets_screen"),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                HelpButton(
                    contentDescription = stringResource(Res.string.block_targets_help_cd),
                    title = stringResource(Res.string.block_targets_title),
                    body = stringResource(Res.string.block_targets_help_body),
                    testTag = "block_targets_help",
                )
            }
            state.rows.forEach { TargetRow(it, onEvent) }
        }
    }
}

@Composable
private fun TargetRow(row: GroupTargetRow, onEvent: (BlockBuilderEvent) -> Unit) {
    val tag = "block_builder_targets_group_${row.group.tagName()}"
    val name = stringResource(row.group.label)
    val line = stringResource(
        Res.string.block_targets_line,
        name,
        AppNumberFormat.integer(row.before),
        AppNumberFormat.integer(row.after),
    )
    val choiceText = when (row.choice) {
        GroupPriority.Prioritise -> stringResource(Res.string.block_targets_prioritise)
        GroupPriority.Maintain -> stringResource(Res.string.block_targets_maintain)
        GroupPriority.None -> stringResource(Res.string.block_targets_choice_none)
    }
    val stateText = stringResource(Res.string.block_targets_state, choiceText, line)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = line,
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.weight(1f).testTag("${tag}_line").semantics { stateDescription = stateText },
            )
            if (row.choice != GroupPriority.None) {
                AppIconButton(
                    contentDescription = stringResource(Res.string.block_targets_clear_cd, name),
                    icon = Res.drawable.ic_close,
                    testTag = "${tag}_clear",
                    onClick = { onEvent(BlockBuilderEvent.SetPriority(row.group, GroupPriority.None)) },
                    tint = AppTheme.colors.textSecondary,
                )
            }
        }
        PriorityControl(row, tag, onEvent)
        reasonText(row)?.let {
            Text(
                text = it,
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("${tag}_reason"),
            )
        }
        row.suggestion?.let { SuggestionChip(row.group, name, it, tag, onEvent) }
    }
}

@Composable
private fun PriorityControl(row: GroupTargetRow, tag: String, onEvent: (BlockBuilderEvent) -> Unit) {
    val threeChosen = row.blocked == NextBlockTargets.Blocked.ThreeChosen && row.choice != GroupPriority.Prioritise
    SegmentedSelector(
        options = listOf(
            AppOption(stringResource(Res.string.block_targets_prioritise), "${tag}_prioritise"),
            AppOption(stringResource(Res.string.block_targets_maintain), "${tag}_maintain"),
        ),
        selectedIndex = when (row.choice) {
            GroupPriority.Prioritise -> PRIORITISE_INDEX
            GroupPriority.Maintain -> MAINTAIN_INDEX
            GroupPriority.None -> null
        },
        onSelect = {
            val priority = if (it == PRIORITISE_INDEX) GroupPriority.Prioritise else GroupPriority.Maintain
            onEvent(BlockBuilderEvent.SetPriority(row.group, priority))
        },
        disabledIndices = if (threeChosen) setOf(PRIORITISE_INDEX) else emptySet(),
    )
}

@Composable
private fun SuggestionChip(
    group: MuscleGroup,
    name: String,
    state: SuggestionState,
    tag: String,
    onEvent: (BlockBuilderEvent) -> Unit,
) {
    val applied = state == SuggestionState.Applied
    AppToggleChip(
        label = stringResource(
            if (applied) Res.string.block_targets_suggestion_applied else Res.string.block_targets_suggestion,
        ),
        checked = applied,
        onCheckedChange = { onEvent(BlockBuilderEvent.ApplySuggestion(group)) },
        modifier = Modifier.testTag("${tag}_suggestion"),
        contentDescription = stringResource(
            if (applied) Res.string.block_targets_suggestion_applied_cd else Res.string.block_targets_suggestion_cd,
            name,
        ),
    )
}

/** The reason a "Priorizar" gives no sets: a fourth choice is always explained; the others once chosen. */
@Composable
private fun reasonText(row: GroupTargetRow): String? {
    val chosen = row.choice == GroupPriority.Prioritise
    return when (row.blocked) {
        NextBlockTargets.Blocked.ThreeChosen ->
            if (chosen) null else stringResource(Res.string.block_targets_reason_three)

        NextBlockTargets.Blocked.LowAdherence ->
            if (chosen) stringResource(Res.string.block_targets_reason_low_adherence) else null

        NextBlockTargets.Blocked.Cut -> if (chosen) stringResource(Res.string.block_targets_reason_cut) else null

        null -> null
    }
}

@Composable
private fun TargetsFooter(state: BlockBuilderUiState.Targets, onEvent: (BlockBuilderEvent) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        state.noFit?.let {
            InlineNote(
                text = stringResource(Res.string.block_types_no_fit, it.limitMinutes),
                modifier = Modifier.testTag("block_builder_targets_no_fit"),
            )
        }
        PrimaryButton(
            text = stringResource(Res.string.block_builder_continue),
            onClick = { onEvent(BlockBuilderEvent.ContinueFromTargets) },
            modifier = Modifier.fillMaxWidth().testTag("block_builder_targets_continue"),
            enabled = state.canContinue,
        )
    }
}
