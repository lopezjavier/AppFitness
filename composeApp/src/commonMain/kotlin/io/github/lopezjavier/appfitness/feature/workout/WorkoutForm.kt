@file:Suppress("TooManyFunctions") // Small pure edits of the form, one per editing operation.

package io.github.lopezjavier.appfitness.feature.workout

import io.github.lopezjavier.appfitness.designsystem.NumberInputDefaults
import io.github.lopezjavier.appfitness.domain.model.DraftExercise
import io.github.lopezjavier.appfitness.domain.model.DraftWorkoutSet
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.NoteText
import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
import io.github.lopezjavier.appfitness.domain.model.SetColumn
import io.github.lopezjavier.appfitness.domain.model.SetColumns
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutDraft
import io.github.lopezjavier.appfitness.domain.model.WorkoutDraftBounds
import io.github.lopezjavier.appfitness.domain.model.columns
import io.github.lopezjavier.appfitness.domain.model.metricType
import io.github.lopezjavier.appfitness.domain.usecase.moveExercise
import io.github.lopezjavier.appfitness.domain.usecase.normalizeGroups
import io.github.lopezjavier.appfitness.feature.routines.weightText
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** The measurement fields of one set that can be typed. Only the ones of the exercise's metric type are used. */
enum class SetField { Weight, Reps, Duration, Distance, Steps, Floors }

/** One set of the form; measurements are as typed ("" = nothing typed yet). [setId] is stable while editing. */
@Serializable
data class FormSet(
    val setId: Long,
    val type: SetType = SetType.Working,
    val rpe: Double? = null,
    val notes: String? = null,
    val weight: String = "",
    val reps: String = "",
    val duration: String = "",
    val distance: String = "",
    val steps: String = "",
    val floors: String = "",
) {
    fun text(field: SetField): String = when (field) {
        SetField.Weight -> weight
        SetField.Reps -> reps
        SetField.Duration -> duration
        SetField.Distance -> distance
        SetField.Steps -> steps
        SetField.Floors -> floors
    }

    fun with(field: SetField, text: String): FormSet = when (field) {
        SetField.Weight -> copy(weight = text)
        SetField.Reps -> copy(reps = text)
        SetField.Duration -> copy(duration = text)
        SetField.Distance -> copy(distance = text)
        SetField.Steps -> copy(steps = text)
        SetField.Floors -> copy(floors = text)
    }
}

/** One exercise of the form; [group] is the superset group (null = standalone), kept as loaded or copied. */
@Serializable
data class FormExercise(
    val rowId: Long,
    val exerciseId: String,
    val metricType: ExerciseMetricType,
    val group: Int?,
    val restSeconds: Int,
    val sets: List<FormSet>,
) {
    /** The fields this exercise's sets have, in display order. */
    val fields: List<SetField> get() = metricType.columns.map { it.toField() }.sortedBy { it.ordinal }
}

/**
 * The editable finished workout of the edit and past-workout screens (US-37). Numbers stay as typed text and the
 * whole form is serializable, so it survives process death inside the `SavedStateHandle` (ADR-40).
 */
@Serializable
data class WorkoutForm(
    val date: LocalDate,
    val name: String = "",
    val description: String = "",
    val gym: String = "",
    /** Minutes as typed; "" means 0 (unknown). */
    val duration: String = "",
    val routineId: String? = null,
    val exercises: List<FormExercise> = emptyList(),
    val nextRowId: Long = 0,
    val nextSetId: Long = 0,
)

private fun SetColumn.toField(): SetField = when (this) {
    SetColumn.WeightKg -> SetField.Weight
    SetColumn.Reps -> SetField.Reps
    SetColumn.DurationS -> SetField.Duration
    SetColumn.DistanceM -> SetField.Distance
    SetColumn.Steps -> SetField.Steps
    SetColumn.Floors -> SetField.Floors
}

/** Maximum characters a numeric field accepts; the UI uses them as the field's input limits. */
object WorkoutFormLimits {
    const val WEIGHT_MAX_CHARS = 7
    const val REPS_MAX_CHARS = 3
    const val DURATION_MAX_CHARS = 5
    const val DISTANCE_MAX_CHARS = 7
    const val COUNT_MAX_CHARS = 7
    const val MINUTES_MAX_CHARS = 4
}

// ---- Draft <-> form -------------------------------------------------------------------------------------------

/** The form that shows [draft]; ids restart from zero. */
fun WorkoutDraft.toForm(): WorkoutForm {
    var rowId = 0L
    var setId = 0L
    val rows = exercises.map { exercise ->
        FormExercise(
            rowId = rowId++,
            exerciseId = exercise.exerciseId,
            metricType = exercise.sets.firstOrNull()?.entry?.metricType ?: ExerciseMetricType.WeightReps,
            group = exercise.supersetGroup,
            restSeconds = exercise.restSeconds,
            sets = exercise.sets.map { set -> set.toFormSet(setId++) },
        )
    }
    return WorkoutForm(
        date = date,
        name = name,
        description = description.orEmpty(),
        gym = gym.orEmpty(),
        duration = if (durationMinutes > 0) durationMinutes.toString() else "",
        routineId = routineId,
        exercises = rows,
        nextRowId = rowId,
        nextSetId = setId,
    )
}

private fun DraftWorkoutSet.toFormSet(setId: Long): FormSet {
    val columns = entry.columns()
    return FormSet(
        setId = setId,
        type = setType,
        rpe = rpe,
        notes = notes,
        weight = columns.weightKg?.let(::weightText).orEmpty(),
        reps = columns.reps?.toString().orEmpty(),
        duration = columns.durationS?.toString().orEmpty(),
        distance = columns.distanceM?.let(::weightText).orEmpty(),
        steps = columns.steps?.toString().orEmpty(),
        floors = columns.floors?.toString().orEmpty(),
    )
}

/** Marks a typed value that is not a number; the validation rejects it (every field's lower bound is above it). */
private const val NOT_A_NUMBER = -1

/**
 * The draft the form describes. A blank number counts as 0 and text that is not a number becomes an out-of-range
 * value, so `ValidateWorkoutDraft` flags exactly that set.
 */
fun WorkoutForm.toDraft(): WorkoutDraft = WorkoutDraft(
    name = name.trim(),
    description = description.trim().ifEmpty { null },
    gym = gym.trim().ifEmpty { null },
    date = date,
    routineId = routineId,
    durationMinutes = duration.trim().let { if (it.isEmpty()) 0 else it.toIntOrNull() ?: NOT_A_NUMBER },
    exercises = exercises.map { exercise ->
        DraftExercise(
            exerciseId = exercise.exerciseId,
            supersetGroup = exercise.group,
            restSeconds = exercise.restSeconds,
            sets = exercise.sets.map { set ->
                DraftWorkoutSet(
                    set.type,
                    set.rpe,
                    set.toColumns(exercise.metricType).toEntry(exercise.metricType),
                    NoteText.normalise(set.notes),
                )
            },
        )
    },
)

private fun FormSet.toColumns(metric: ExerciseMetricType): SetColumns {
    val used = metric.columns
    return SetColumns(
        weightKg = weight.decimalOrNull().takeIf { SetColumn.WeightKg in used },
        reps = reps.countOrNull().takeIf { SetColumn.Reps in used },
        durationS = duration.countOrNull().takeIf { SetColumn.DurationS in used },
        distanceM = distance.decimalOrNull().takeIf { SetColumn.DistanceM in used },
        steps = steps.countOrNull().takeIf { SetColumn.Steps in used },
        floors = floors.countOrNull().takeIf { SetColumn.Floors in used },
    )
}

private fun String.decimalOrNull(): Double = if (isBlank()) {
    0.0
} else {
    NumberInputDefaults.parse(this)
        ?: NOT_A_NUMBER.toDouble()
}

private fun String.countOrNull(): Int = trim().let { if (it.isEmpty()) 0 else it.toIntOrNull() ?: NOT_A_NUMBER }

// ---- Editing --------------------------------------------------------------------------------------------------

/** Appends [exerciseId] with one empty set. */
fun WorkoutForm.withNewExercise(exerciseId: String, metricType: ExerciseMetricType): WorkoutForm {
    if (exercises.size >= WorkoutDraftBounds.EXERCISES_MAX) return this
    val row = FormExercise(
        rowId = nextRowId,
        exerciseId = exerciseId,
        metricType = metricType,
        group = null,
        restSeconds = RoutineBounds.DEFAULT_REST_SECONDS,
        sets = listOf(FormSet(setId = nextSetId)),
    )
    return copy(exercises = exercises + row, nextRowId = nextRowId + 1, nextSetId = nextSetId + 1)
}

/** Removes the exercise; a superset left with one member is ungrouped. */
fun WorkoutForm.withoutExercise(index: Int): WorkoutForm {
    if (index !in exercises.indices) return this
    return copy(exercises = exercises.filterIndexed { i, _ -> i != index }.regrouped())
}

/** Moves the exercise to [to]; a superset that stops being consecutive is ungrouped. */
fun WorkoutForm.movedExercise(from: Int, to: Int): WorkoutForm =
    copy(exercises = moveExercise(exercises, from, to).regrouped())

private fun List<FormExercise>.regrouped(): List<FormExercise> {
    val groups = normalizeGroups(map { it.group })
    return mapIndexed { i, row -> row.copy(group = groups[i]) }
}

fun WorkoutForm.withRest(index: Int, seconds: Int): WorkoutForm = withRow(index) {
    it.copy(restSeconds = seconds.coerceIn(RoutineBounds.REST_MIN_SECONDS, RoutineBounds.REST_MAX_SECONDS))
}

/** Indices of the exercises that move together with [index]: its whole superset, or just itself. */
private fun WorkoutForm.membersOf(index: Int): List<Int> {
    val group = exercises.getOrNull(index)?.group ?: return listOfNotNull(index.takeIf { it in exercises.indices })
    return exercises.indices.filter { exercises[it].group == group }
}

/** Adds a copy of the last set (an empty one when there is none); in a superset every member gets one (ADR-38). */
fun WorkoutForm.withAddedSet(index: Int): WorkoutForm {
    val members = membersOf(index)
    if (members.isEmpty() || members.any { exercises[it].sets.size >= WorkoutDraftBounds.SETS_MAX }) return this
    var next = nextSetId
    val updated = exercises.mapIndexed { i, row ->
        if (i in
            members
        ) {
            row.copy(sets = row.sets + (row.sets.lastOrNull() ?: FormSet(0)).copy(setId = next++, notes = null))
        } else {
            row
        }
    }
    return copy(exercises = updated, nextSetId = next)
}

/** Removes set [setIndex] (0-based) from the exercise and from every member of its superset; the last set stays. */
fun WorkoutForm.withoutSet(index: Int, setIndex: Int): WorkoutForm {
    val members = membersOf(index)
    val blocked = members.isEmpty() || members.any { exercises[it].sets.size <= 1 } ||
        setIndex !in exercises[index].sets.indices
    if (blocked) return this
    return copy(
        exercises = exercises.mapIndexed { i, row ->
            if (i in members) row.copy(sets = row.sets.filterIndexed { s, _ -> s != setIndex }) else row
        },
    )
}

fun WorkoutForm.withField(index: Int, setIndex: Int, field: SetField, text: String): WorkoutForm =
    withSet(index, setIndex) { it.with(field, text) }

fun WorkoutForm.withSetType(index: Int, setIndex: Int, type: SetType): WorkoutForm =
    withSet(index, setIndex) { it.copy(type = type) }

fun WorkoutForm.withRpe(index: Int, setIndex: Int, rpe: Double?): WorkoutForm =
    withSet(index, setIndex) { it.copy(rpe = rpe) }

/** Sets the note of the set (trimmed, blank removes it); more than [NoteText.MAX] characters leaves it as it was. */
fun WorkoutForm.withNote(index: Int, setIndex: Int, text: String): WorkoutForm =
    if (NoteText.isValid(text)) withSet(index, setIndex) { it.copy(notes = NoteText.normalise(text)) } else this

private fun WorkoutForm.withRow(index: Int, change: (FormExercise) -> FormExercise): WorkoutForm = if (index !in
    exercises.indices
) {
    this
} else {
    copy(
        exercises = exercises.mapIndexed { i, row ->
            if (i ==
                index
            ) {
                change(row)
            } else {
                row
            }
        },
    )
}

private fun WorkoutForm.withSet(index: Int, setIndex: Int, change: (FormSet) -> FormSet): WorkoutForm =
    withRow(index) { row ->
        if (setIndex !in
            row.sets.indices
        ) {
            row
        } else {
            row.copy(
                sets = row.sets.mapIndexed { s, set ->
                    if (s ==
                        setIndex
                    ) {
                        change(set)
                    } else {
                        set
                    }
                },
            )
        }
    }
