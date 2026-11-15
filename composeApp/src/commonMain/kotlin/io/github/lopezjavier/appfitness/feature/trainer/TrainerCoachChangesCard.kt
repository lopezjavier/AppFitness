package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_undo
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_undo_cd
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_undo_offline
import io.github.lopezjavier.appfitness.composeapp.resources.coach_changes_header
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_cancel_cd
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_cancel_cd_plain
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_cancel_offline
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_headline
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_status_cancelled
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import io.github.lopezjavier.appfitness.composeapp.resources.ic_sleep
import io.github.lopezjavier.appfitness.composeapp.resources.ic_trending_down
import io.github.lopezjavier.appfitness.composeapp.resources.ic_trending_flat
import io.github.lopezjavier.appfitness.composeapp.resources.ic_trending_up
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_change_figure
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_title
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppTextButton
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.RowGroupCard
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StatusChip
import io.github.lopezjavier.appfitness.designsystem.StatusTone
import io.github.lopezjavier.appfitness.domain.model.CoachChangeKind
import io.github.lopezjavier.appfitness.domain.model.CoachChangeStatus
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** From this font scale the row's action moves under its text, so the name keeps its width at 360 dp. */
private const val STACK_ACTION_FONT_SCALE = 1.3f

/** The leading glyph of a change: its direction as a shape, tinted only as a second cue (R-41). */
private val GlyphSize = Spacing.xl + Spacing.lg

/**
 * "Cambios del entrenador" on the Trainer root (R-107), full width under "Tu bloque", only while applied changes exist: one
 * card with the title and one row per change, apart by hairlines. A row reads what changed (the exercise and
 * "80 → 82,5 kg" with a rising or falling glyph; a deload "Semana de descarga · Semana 3"), its state when closed
 * ("Revertido" with the old figure struck through and "Vuelve a 80 kg.", "Cancelada", "Subida aceptada") and, while it
 * can be undone, "Deshacer" ("Cancelar descarga" for a deload) at the end. Actions are disabled offline (one hint under
 * the card) and while another change is in flight; the busy row shows the progress on its button. The undo outcome
 * (conflict, skipped routines) is a message of the effects host. A row waits for its exercise name (R-54).
 */
@Composable
internal fun CoachChangesCard(
    state: TrainerUiState.Content,
    names: Map<String, String>,
    online: Boolean,
    onEvent: (TrainerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val deloadName = stringResource(Res.string.coach_deload_headline)
    // A row waits for its exercise name (R-54): only rows with a resolved name (or a deload) are listed.
    val named = state.appliedCoachChanges.mapNotNull { row ->
        val name = if (row.kind.isDeload) deloadName else row.exerciseId?.let { names[it] }
        name?.let { row to it }
    }
    if (named.isEmpty()) return
    val rows = named.map { it.first }
    val stacked = LocalDensity.current.fontScale >= STACK_ACTION_FONT_SCALE
    Column(
        modifier.fillMaxWidth().testTag("trainer_changes_card"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        RowGroupCard(
            title = stringResource(Res.string.coach_changes_header),
            rows = named.map { (row, name) ->
                {
                    ChangeRow(
                        row = row,
                        name = name,
                        busy = state.busyChangeId == row.id,
                        enabled = online && state.busyChangeId == null && !state.requestingDeload,
                        stacked = stacked,
                        onEvent = onEvent,
                    )
                }
            },
        )
        if (!online) {
            if (rows.any { it.undoable }) OfflineHint(stringResource(Res.string.coach_change_undo_offline))
            if (rows.any { it.cancellable }) OfflineHint(stringResource(Res.string.coach_deload_cancel_offline))
        }
    }
}

@Composable
@Suppress("LongParameterList") // One row: the change, its name, its action state and the layout.
private fun ChangeRow(
    row: CoachChangeRowUi,
    name: String,
    busy: Boolean,
    enabled: Boolean,
    stacked: Boolean,
    onEvent: (TrainerEvent) -> Unit,
) {
    val action = rowAction(row, name, onEvent)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget + Spacing.sm)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .testTag("trainer_change_${row.id}"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            ChangeGlyph(row)
            ChangeText(row, name, Modifier.weight(1f))
            // "Deshacer" is short: it sits at the end; a deload's longer action and large fonts go below.
            if (action != null && !stacked && !row.kind.isDeload) ChangeAction(action, busy, enabled)
        }
        if (action != null && (stacked || row.kind.isDeload)) {
            Box(Modifier.padding(start = GlyphSize + Spacing.md)) { ChangeAction(action, busy, enabled) }
        }
    }
}

/** The row's one action: "Deshacer" (weight change) or "Cancelar descarga" (deload); null when it is closed. */
private class ChangeActionUi(val text: String, val contentDescription: String, val tag: String, val onClick: () -> Unit)

@Composable
private fun rowAction(row: CoachChangeRowUi, name: String, onEvent: (TrainerEvent) -> Unit): ChangeActionUi? = when {
    row.undoable -> ChangeActionUi(
        text = stringResource(Res.string.coach_change_undo),
        contentDescription = stringResource(Res.string.coach_change_undo_cd, name),
        tag = "trainer_coach_change_undo_${row.id}",
        onClick = { onEvent(TrainerEvent.UndoCoachChange(row.id)) },
    )

    row.cancellable -> ChangeActionUi(
        text = stringResource(Res.string.coach_deload_cancel),
        contentDescription = row.figures.week?.let { stringResource(Res.string.coach_deload_cancel_cd, it) }
            ?: stringResource(Res.string.coach_deload_cancel_cd_plain),
        tag = "trainer_deload_cancel_${row.id}",
        onClick = { onEvent(TrainerEvent.CancelDeload(row.id)) },
    )

    else -> null
}

@Composable
private fun ChangeAction(action: ChangeActionUi, busy: Boolean, enabled: Boolean) {
    val modifier = Modifier.testTag(action.tag).semantics { contentDescription = action.contentDescription }
    if (busy) {
        // The busy row shows its progress on the button.
        GhostButton(text = action.text, onClick = action.onClick, modifier = modifier, enabled = false, loading = true)
    } else {
        // A compact text action (48 dp target): the name keeps the width, "Press de banca" stays on one line (R-107).
        AppTextButton(text = action.text, onClick = action.onClick, modifier = modifier, enabled = enabled)
    }
}

/** Up for a raise (cyan), down for a drop, flat for a kept weight, a moon for a deload; dimmed once closed. */
@Composable
private fun ChangeGlyph(row: CoachChangeRowUi) {
    val colors = AppTheme.colors
    val closed = row.status == CoachChangeStatus.Undone
    val (icon: DrawableResource, tint: Color) = when {
        row.kind.isDeload -> Res.drawable.ic_sleep to colors.athleticCyan
        row.status == CoachChangeStatus.Decided -> Res.drawable.ic_check to colors.electricGreen
        row.kind == CoachChangeKind.LoadRaise -> Res.drawable.ic_trending_up to colors.athleticCyan
        row.kind == CoachChangeKind.LoadDrop -> Res.drawable.ic_trending_down to colors.textSecondary
        else -> Res.drawable.ic_trending_flat to colors.textSecondary
    }
    Box(
        Modifier.size(GlyphSize).background(colors.surfaceControl, AppShapes.full),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (closed) colors.textTertiary else tint,
            modifier = Modifier.size(Spacing.xl),
        )
    }
}

/** The name, what changed and, for a closed change, its state chip and the weight it went back to. */
@Composable
private fun ChangeText(row: CoachChangeRowUi, name: String, modifier: Modifier) {
    val undone = row.status == CoachChangeStatus.Undone
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = name,
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        changeFigure(row)?.let { figure ->
            Text(
                text = figure,
                style = AppTheme.typography.bodyMedium.copy(
                    textDecoration = if (undone && !row.kind.isDeload) TextDecoration.LineThrough else null,
                ),
                color = if (undone) AppTheme.colors.textTertiary else AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("trainer_change_${row.id}_figure"),
            )
        }
        undoneLine(row)?.let {
            Text(text = it, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        }
        closedStatus(row)?.let { (text, tone) ->
            StatusChip(text, tone, Modifier.testTag("trainer_change_${row.id}_status"))
        }
    }
}

/** "80 → 82,5 kg" for a weight change, "Semana 3" for a deload, the plain reason for a kept weight. */
@Composable
private fun changeFigure(row: CoachChangeRowUi): String? {
    val from = row.figures.weightFromKg
    val to = row.figures.weightToKg
    return when {
        row.kind.isDeload -> row.figures.week?.let { stringResource(Res.string.trainer_week_title, it) }

        from != null && to != null ->
            stringResource(Res.string.trainer_change_figure, coachWeightText(from), coachWeightText(to))

        row.kind == CoachChangeKind.LoadKept -> coachChangeReasonText(row.kind, row.figures).ifEmpty { null }

        else -> null
    }
}

/** The closed state in words: "Revertido" / "Cancelada" (Neutral) or the flag's decision (Good). */
@Composable
private fun closedStatus(row: CoachChangeRowUi): Pair<String, StatusTone>? = when {
    row.kind.isDeload && row.status == CoachChangeStatus.Undone ->
        stringResource(Res.string.coach_deload_status_cancelled) to StatusTone.Neutral

    row.status == CoachChangeStatus.Undone -> statusText(row)?.let { it to StatusTone.Neutral }

    row.status == CoachChangeStatus.Decided -> statusText(row)?.let { it to StatusTone.Good }

    else -> null
}
