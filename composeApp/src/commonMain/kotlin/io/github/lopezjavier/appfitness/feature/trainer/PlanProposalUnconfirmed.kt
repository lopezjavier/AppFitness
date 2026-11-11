package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.before_start_confirm_action
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_confirm_values
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_unconfirmed_header
import io.github.lopezjavier.appfitness.composeapp.resources.plan_proposal_unconfirmed_no_editor
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_provisional
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.NavigationRow
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StatusChip
import io.github.lopezjavier.appfitness.designsystem.StatusTone
import io.github.lopezjavier.appfitness.domain.model.PlanFixTarget
import io.github.lopezjavier.appfitness.feature.exercises.tagName
import org.jetbrains.compose.resources.stringResource

/**
 * "Valores por confirmar" (R-59): one row per value that blocks "Aceptar". A value with an editor opens it (R-55); one
 * without says why and is confirmed as shown by the single secondary button under the rows.
 */
@Composable
internal fun UnconfirmedSection(
    state: PlanProposalUiState.Proposal,
    online: Boolean,
    onEvent: (PlanProposalEvent) -> Unit,
    onFix: (PlanFixTarget) -> Unit,
    onEditorEvent: (IntakeRowEvent) -> Unit,
) {
    val hasNoEditorRow = state.unconfirmedValues.any { it.fixTarget == null }
    Column(Modifier.fillMaxWidth().testTag("plan_proposal_unconfirmed_section")) {
        ListSectionHeader(stringResource(Res.string.plan_proposal_unconfirmed_header))
        state.unconfirmedValues.forEach { row -> UnconfirmedRow(row, state, onEvent, onFix, onEditorEvent) }
        if (hasNoEditorRow) {
            SecondaryButton(
                text = stringResource(Res.string.plan_proposal_confirm_values),
                onClick = { onEvent(PlanProposalEvent.ConfirmValues) },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm).testTag("plan_proposal_confirm_values"),
                enabled = online && !state.busy,
            )
            if (!online) OfflineHint(stringResource(Res.string.before_start_confirm_action))
        }
    }
}

@Composable
private fun UnconfirmedRow(
    row: PlanInputRow,
    state: PlanProposalUiState.Proposal,
    onEvent: (PlanProposalEvent) -> Unit,
    onFix: (PlanFixTarget) -> Unit,
    onEditorEvent: (IntakeRowEvent) -> Unit,
) {
    val tag = "plan_proposal_unconfirmed_${row.key.tagName()}"
    val target = row.fixTarget
    Column {
        if (target != null) {
            NavigationRow(
                label = row.labelText(),
                value = row.valueText(),
                onClick = {
                    when (target) {
                        PlanFixTarget.ActivitySheet -> onEvent(PlanProposalEvent.OpenActivitySheet)

                        PlanFixTarget.TrainingYears ->
                            openIntakeRow(IntakeRowKind.TrainingYears, state.intake, onEditorEvent)

                        PlanFixTarget.Maintenance ->
                            openIntakeRow(IntakeRowKind.Maintenance, state.intake, onEditorEvent)

                        else -> onFix(target)
                    }
                },
                modifier = Modifier.testTag(tag),
                enabled = !state.busy,
            )
        } else {
            AppListRow(
                headline = row.labelText(),
                modifier = Modifier.testTag(tag),
                supporting = stringResource(Res.string.plan_proposal_unconfirmed_no_editor),
                trailing = {
                    Text(
                        text = row.valueText(),
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.colors.textSecondary,
                    )
                },
            )
        }
        StatusChip(
            text = stringResource(Res.string.trainer_provisional),
            tone = StatusTone.Caution,
            modifier = Modifier.testTag("${tag}_chip"),
        )
    }
}
