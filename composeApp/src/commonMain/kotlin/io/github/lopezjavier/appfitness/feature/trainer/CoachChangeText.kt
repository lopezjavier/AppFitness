package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.Composable
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_reason_drop
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_reason_flag
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_reason_kept
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_reason_no_range
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_reason_raise
import io.github.lopezjavier.appfitness.composeapp.resources.coach_change_reason_raise_plain
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_reason_scheduled
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_reason_triggered
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_reason_user
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.domain.model.CoachChangeFigures
import io.github.lopezjavier.appfitness.domain.model.CoachChangeKind
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val DEFAULT_DROP_SESSIONS = 2

/** A weight in kilograms as the coach copy shows it: "62,5". */
internal fun coachWeightText(kg: Double): String = AppNumberFormat.decimal(kg, maxDecimals = 2)

/**
 * The plain-words reason of a coach change (07 §1): the figures fill the template of its [kind]. Empty when the
 * figures a template needs are missing, so a row never shows a half-built sentence.
 */
@Composable
internal fun coachChangeReasonText(kind: CoachChangeKind, figures: CoachChangeFigures): String = when (kind) {
    CoachChangeKind.LoadRaise -> raiseReason(figures)
    CoachChangeKind.LoadDrop -> dropReason(figures)
    CoachChangeKind.LoadKept -> stringResource(Res.string.coach_change_reason_kept)
    CoachChangeKind.LoadFlag -> flagReason(figures)
    CoachChangeKind.NoRangeFlag -> stringResource(Res.string.coach_change_reason_no_range)
    CoachChangeKind.DeloadTriggered -> stringResource(Res.string.coach_deload_reason_triggered)
    CoachChangeKind.DeloadScheduled -> stringResource(Res.string.coach_deload_reason_scheduled)
    CoachChangeKind.DeloadUser -> stringResource(Res.string.coach_deload_reason_user)
    CoachChangeKind.Unknown -> ""
}

@Composable
private fun raiseReason(figures: CoachChangeFigures): String {
    val from = figures.weightFromKg?.let(::coachWeightText)
    val to = figures.weightToKg?.let(::coachWeightText)
    val rpe = figures.rpeMax?.let { AppNumberFormat.decimal(it) }
    return when {
        from == null || to == null -> ""
        rpe == null -> stringResource(Res.string.coach_change_reason_raise_plain, from, to)
        else -> stringResource(Res.string.coach_change_reason_raise, from, to, rpe)
    }
}

@Composable
private fun dropReason(figures: CoachChangeFigures): String {
    val from = figures.weightFromKg?.let(::coachWeightText)
    val to = figures.weightToKg?.let(::coachWeightText)
    val sessions = figures.sessions ?: DEFAULT_DROP_SESSIONS
    return if (from == null || to == null) {
        ""
    } else {
        pluralStringResource(Res.plurals.coach_change_reason_drop, sessions, from, to, sessions)
    }
}

@Composable
private fun flagReason(figures: CoachChangeFigures): String {
    val next = figures.nextKg?.let(::coachWeightText)
    val percent = figures.percent?.let { AppNumberFormat.decimal(it) }
    return if (next == null || percent == null) {
        ""
    } else {
        stringResource(Res.string.coach_change_reason_flag, next, percent)
    }
}
