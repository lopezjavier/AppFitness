package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.CoachChange
import io.github.lopezjavier.appfitness.domain.model.CoachChangeDecision
import io.github.lopezjavier.appfitness.domain.model.CoachChangeFigures
import io.github.lopezjavier.appfitness.domain.model.CoachChangeKind
import io.github.lopezjavier.appfitness.domain.model.CoachChangeStatus

/** Statuses the Trainer list and the editor markers read; anything else (unknown) is never shown. */
internal val ShownCoachChangeStatuses: Set<CoachChangeStatus> = setOf(
    CoachChangeStatus.Applied,
    CoachChangeStatus.Undone,
    CoachChangeStatus.Pending,
    CoachChangeStatus.Decided,
)

/** Most rows the "Cambios del entrenador" section shows; older ones are history. */
internal const val MAX_COACH_CHANGE_ROWS = 10

/**
 * One row of the "Cambios del entrenador" section. The exercise name is resolved by the UI from [exerciseId] with the
 * shared exercise-name resolver; [reason] is the server reason code the text is built from.
 */
data class CoachChangeRowUi(
    val id: String,
    val kind: CoachChangeKind,
    val status: CoachChangeStatus,
    /** Null for a deload, which is plan-level. */
    val exerciseId: String?,
    val routineIds: List<String>,
    val figures: CoachChangeFigures,
    val reason: String,
    val decision: CoachChangeDecision?,
) {
    /** "Deshacer" is offered for an applied weight change only. */
    val undoable: Boolean
        get() = status == CoachChangeStatus.Applied &&
            (kind == CoachChangeKind.LoadRaise || kind == CoachChangeKind.LoadDrop)

    /** "Cancelar descarga": an applied deload (T-230); a cancelled one shows as [CoachChangeStatus.Undone]. */
    val cancellable: Boolean
        get() = status == CoachChangeStatus.Applied && kind.isDeload

    /** Waiting for the user: a flagged raise (two options) or a missing rep range. */
    val pending: Boolean
        get() = status == CoachChangeStatus.Pending &&
            (kind == CoachChangeKind.LoadFlag || kind == CoachChangeKind.NoRangeFlag)
}

/** What the routine editor and the live session show behind the `?` of a changed exercise. */
data class CoachChangeMarker(val kind: CoachChangeKind, val figures: CoachChangeFigures, val reason: String)

/**
 * Routines the change touches, from its planned-values snapshots; a no-range flag has none and stores its routine in
 * the figures. There is no client-side cardio filter: the exercise-name resolver exposes no movement pattern, so the
 * server (flag creation and the cleanup migration) is the guard.
 */
internal fun CoachChange.routineIds(): List<String> {
    val fromSnapshots = ((after.orEmpty()) + (before.orEmpty())).map { it.routineId }.distinct()
    return fromSnapshots.ifEmpty { listOfNotNull(figures.routineId) }
}

private fun CoachChange.isKnown(): Boolean = kind != CoachChangeKind.Unknown && status != CoachChangeStatus.Unknown

internal fun CoachChange.toRowUi(): CoachChangeRowUi = CoachChangeRowUi(
    id = id,
    kind = kind,
    status = status,
    exerciseId = exerciseId,
    routineIds = routineIds(),
    figures = figures,
    reason = reason,
    decision = decision,
)

/** Pending flags first, then the rest as the server sent them (newest first); capped; unknown kinds are dropped. */
internal fun List<CoachChange>.toCoachChangeRows(): List<CoachChangeRowUi> = filter { it.isKnown() }
    .sortedBy { if (it.status == CoachChangeStatus.Pending) 0 else 1 }
    .take(MAX_COACH_CHANGE_ROWS)
    .map { it.toRowUi() }

/**
 * Markers by exercise id of the applied or pending changes (undone and decided ones carry no marker); when an exercise
 * has several, the newest (first) wins. [routineId] limits them to the changes touching that routine.
 */
internal fun List<CoachChange>.toMarkers(routineId: String? = null): Map<String, CoachChangeMarker> =
    filter { it.isKnown() && (routineId == null || routineId in it.routineIds()) }
        .filter { it.status == CoachChangeStatus.Applied || it.status == CoachChangeStatus.Pending }
        .filter { it.exerciseId != null }
        .distinctBy { it.exerciseId }
        .associate { it.exerciseId.orEmpty() to CoachChangeMarker(it.kind, it.figures, it.reason) }
