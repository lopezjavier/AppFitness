@file:Suppress("TooManyFunctions") // Small pure edits of the draft, one per builder operation.

package io.github.lopezjavier.appfitness.feature.routines

import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WarmUpSet
import io.github.lopezjavier.appfitness.domain.usecase.RpeScale
import io.github.lopezjavier.appfitness.domain.usecase.moveExercise
import io.github.lopezjavier.appfitness.domain.usecase.normalizeGroups
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One planned set of the draft: [weight] and [reps] are the text as typed, [setId] is stable while editing. */
@Serializable
internal data class DraftSet(val setId: Long, val weight: String, val reps: String, val type: SetType)

/** One row of the draft; [group] is the superset group value (null = standalone). */
@Serializable
internal data class DraftRow(
    val rowId: Long,
    val exerciseId: String,
    val sets: List<DraftSet>,
    val restSeconds: Int,
    val group: Int?,
    val targetRpe: Double? = null,
)

/** The editable routine. Serializable so it survives process death inside the [SavedStateHandle]. */
@Serializable
internal data class RoutineDraft(
    val name: String = "",
    val notes: String? = null,
    val rows: List<DraftRow> = emptyList(),
    val nextRowId: Long = 0,
    val nextSetId: Long = 0,
)

internal const val WEIGHT_MAX_CHARS = 8
internal const val REPS_MAX_CHARS = 3

internal fun RoutineDraft.withNewRow(exerciseId: String): RoutineDraft {
    val defaults = RoutineBounds.defaultPlannedSets()
    return copy(
        rows =
        rows +
            DraftRow(
                rowId = nextRowId,
                exerciseId = exerciseId,
                sets = defaults.mapIndexed { i, set -> set.toDraft(nextSetId + i) },
                restSeconds = RoutineBounds.DEFAULT_REST_SECONDS,
                group = null,
            ),
        nextRowId = nextRowId + 1,
        nextSetId = nextSetId + defaults.size,
    )
}

/** Indices of the rows that move together with [index]: the whole superset group, or just the row. */
internal fun RoutineDraft.membersOf(index: Int): List<Int> {
    val group = rows.getOrNull(index)?.group ?: return listOfNotNull(index.takeIf { it in rows.indices })
    return rows.indices.filter { rows[it].group == group }
}

/** Adds a copy of the last set to the row and to every other member of its superset. */
internal fun RoutineDraft.withAddedSet(index: Int): RoutineDraft {
    val members = membersOf(index)
    if (members.isEmpty() || members.any { rows[it].sets.size >= RoutineBounds.SETS_MAX }) return this
    var next = nextSetId
    val updated =
        rows.mapIndexed { i, row ->
            if (i in members) row.copy(sets = row.sets + row.sets.last().copy(setId = next++)) else row
        }
    return copy(rows = updated, nextSetId = next)
}

/** True when [count] more sets fit in the row (and so in every member of its superset). */
internal fun RoutineDraft.canAddWarmUps(index: Int, count: Int): Boolean {
    val row = rows.getOrNull(index) ?: return false
    return row.sets.size + count <= RoutineBounds.SETS_MAX
}

/**
 * Inserts [sets] as warm-up sets after the row's leading warm-ups and before its first other set. In a superset the
 * other members are levelled with copies of their last set (ADR-38). Nothing changes when the row is unknown, [sets]
 * is empty or the result would exceed [RoutineBounds.SETS_MAX].
 */
internal fun RoutineDraft.withWarmUpSets(index: Int, sets: List<WarmUpSet>): RoutineDraft {
    val row = rows.getOrNull(index)
    if (row == null || sets.isEmpty() || !canAddWarmUps(index, sets.size)) return this
    val insertAt = row.sets.indexOfFirst { it.type != SetType.WarmUp }.takeIf { it >= 0 } ?: row.sets.size
    val added = sets.mapIndexed { i, set ->
        DraftSet(nextSetId + i, weightText(set.weightKg), set.reps.toString(), SetType.WarmUp)
    }
    val updated = rows.mapIndexed { i, r ->
        if (i == index) r.copy(sets = r.sets.take(insertAt) + added + r.sets.drop(insertAt)) else r
    }
    return copy(rows = updated, nextSetId = nextSetId + sets.size).leveledTo(index)
}

/** Removes set [setIndex] from the row and from every other member of its superset; the last set stays. */
internal fun RoutineDraft.withoutSet(index: Int, setIndex: Int): RoutineDraft {
    val members = membersOf(index)
    val blocked =
        members.isEmpty() || members.any { rows[it].sets.size <= RoutineBounds.SETS_MIN } ||
            setIndex !in rows[index].sets.indices
    if (blocked) return this
    return copy(
        rows =
        rows.mapIndexed { i, row ->
            if (i in members) row.copy(sets = row.sets.filterIndexed { s, _ -> s != setIndex }) else row
        },
    )
}

internal fun RoutineDraft.withSetEdit(index: Int, setIndex: Int, edit: (DraftSet) -> DraftSet): RoutineDraft = copy(
    rows =
    rows.mapIndexed { i, row ->
        if (i ==
            index
        ) {
            row.copy(sets = row.sets.mapIndexed { s, set -> if (s == setIndex) edit(set) else set })
        } else {
            row
        }
    },
)

/** Makes every member of the superset of row [index] adopt that row's set count (grow by copy, shrink from the end). */
internal fun RoutineDraft.leveledTo(index: Int): RoutineDraft {
    val target = rows.getOrNull(index)?.sets?.size ?: return this
    val members = membersOf(index)
    var next = nextSetId
    val updated =
        rows.mapIndexed { i, row ->
            if (i == index || i !in members) {
                row
            } else if (row.sets.size > target) {
                row.copy(sets = row.sets.take(target))
            } else {
                row.copy(sets = row.sets + List(target - row.sets.size) { row.sets.last().copy(setId = next++) })
            }
        }
    return copy(rows = updated, nextSetId = next)
}

internal fun DraftSet.toUi() = PlannedSetUi(setId, weight, reps, type, isValid = toPlannedSet() != null)

/** The planned set, or null while the reps are not 1-50 or the weight is not a number >= 0. */
internal fun DraftSet.toPlannedSet(): PlannedSet? {
    val repsValue = reps.toIntOrNull()?.takeIf { it in RoutineBounds.REPS_MIN..RoutineBounds.REPS_MAX }
    val weightValue = weight.trimEnd(',').replace(',', '.').toDoubleOrNull()?.takeIf {
        it >= RoutineBounds.WEIGHT_MIN_KG
    }
    val weightOk = weight.isBlank() || weightValue != null
    return if (repsValue != null && weightOk) PlannedSet(weightValue, repsValue, type) else null
}

internal fun PlannedSet.toDraft(setId: Long) =
    DraftSet(setId, weightKg?.let(::weightText).orEmpty(), reps.toString(), type)

internal fun weightText(kg: Double): String =
    if (kg == kg.toLong().toDouble()) kg.toLong().toString() else kg.toString().replace('.', ',')

internal fun RoutineDraft.movedRow(from: Int, to: Int): RoutineDraft =
    copy(rows = moveExercise(rows, from, to)).renormalized()

internal fun RoutineDraft.withGroups(groups: List<Int?>): RoutineDraft =
    copy(rows = rows.mapIndexed { index, row -> row.copy(group = groups[index]) })

/** Clears and renumbers the superset groups after rows moved or went away. */
internal fun RoutineDraft.renormalized(): RoutineDraft = withGroups(normalizeGroups(rows.map(DraftRow::group)))

internal fun RoutineDraft.toRoutine(id: String?) = Routine(
    id = id,
    name = name.trim(),
    notes = notes,
    archived = false,
    exercises =
    rows.mapIndexed { index, row ->
        RoutineExercise(
            exerciseId = row.exerciseId,
            position = index,
            supersetGroup = row.group,
            plannedSets = row.sets.map { it.toPlannedSet() ?: PlannedSet(null, RoutineBounds.REPS_MIN, it.type) },
            restSeconds = row.restSeconds,
            targetRpe = row.targetRpe,
        )
    },
)

internal fun Routine.toDraft(): RoutineDraft {
    val groups = normalizeGroups(exercises.sortedBy { it.position }.map { it.supersetGroup })
    var setId = 0L
    val rows =
        exercises.sortedBy { it.position }.mapIndexed { index, exercise ->
            DraftRow(
                rowId = index.toLong(),
                exerciseId = exercise.exerciseId,
                sets = exercise.plannedSets.map { it.toDraft(setId++) },
                restSeconds = exercise.restSeconds,
                group = groups[index],
                targetRpe = exercise.targetRpe,
            )
        }
    return RoutineDraft(name = name, notes = notes, rows = rows, nextRowId = rows.size.toLong(), nextSetId = setId)
}

private const val DEFAULT_TARGET_RPE = 8.0

/** One half-point step of a target RPE: none + up = 8, 10 + up stays 10, 6 + down = none. */
internal fun steppedRpe(current: Double?, delta: Int): Double? {
    if (current == null) return if (delta > 0) DEFAULT_TARGET_RPE else null
    val next = current + delta * RpeScale.STEP
    return if (next < RpeScale.MIN) null else next.coerceAtMost(RpeScale.MAX)
}

internal fun encode(draft: RoutineDraft): String = Json.encodeToString(RoutineDraft.serializer(), draft)

internal fun decode(json: String): RoutineDraft? =
    runCatching { Json.decodeFromString(RoutineDraft.serializer(), json) }.getOrNull()
