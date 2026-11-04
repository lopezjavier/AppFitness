package io.github.lopezjavier.appfitness.domain.coach.builder

import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** JSON round trip of [BuilderState] so a ViewModel can keep it in a `SavedStateHandle` (ADR-40). Enums by name. */
object BuilderStateCodec {
    fun encode(state: BuilderState): String = Json.encodeToString(SavedBuilder.of(state))

    /** The state, or null when [raw] is not a valid encoding (a stale or corrupt saved value). */
    fun decode(raw: String): BuilderState? = runCatching {
        Json.decodeFromString<SavedBuilder>(raw).toState()
    }.getOrNull()
}

@Suppress("LongParameterList") // Mirror of Slot.
@Serializable
private class SavedSlot(
    val group: String,
    val kind: String,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
    val rpe: Int,
    val downgraded: Boolean,
) {
    fun toSlot() = Slot(
        MuscleGroup.valueOf(group),
        SlotKind.valueOf(kind),
        sets,
        repMin,
        repMax,
        restSeconds,
        rpe,
        downgraded,
    )

    companion object {
        fun of(s: Slot) =
            SavedSlot(s.group.name, s.kind.name, s.sets, s.repMin, s.repMax, s.restSeconds, s.rpe, s.downgraded)
    }
}

@Serializable
private class SavedPick(
    val exerciseId: String,
    val group: String,
    val movementPattern: String?,
    val plannedWeightKg: Double?,
) {
    fun toPick() = Pick(
        exerciseId,
        MuscleGroup.valueOf(group),
        movementPattern?.let { MovementPattern.valueOf(it) },
        plannedWeightKg,
    )

    companion object {
        fun of(p: Pick) = SavedPick(p.exerciseId, p.group.name, p.movementPattern?.name, p.plannedWeightKg)
    }
}

@Serializable
private class SavedFilled(val slot: SavedSlot, val pick: SavedPick?) {
    fun toFilled() = FilledSlot(slot.toSlot(), pick?.toPick())

    companion object {
        fun of(f: FilledSlot) = SavedFilled(SavedSlot.of(f.slot), f.pick?.let(SavedPick::of))
    }
}

@Suppress("LongParameterList") // Mirror of BuilderDay.
@Serializable
private class SavedDay(
    val type: String,
    val name: String,
    val slots: List<SavedFilled>,
    val supersets: List<List<Int>>,
    val extras: List<SavedFilled>,
    val cutSets: Boolean,
    val liftingSeconds: Int,
) {
    fun toDay() = BuilderDay(
        RoutineType.valueOf(type),
        name,
        slots.map { it.toFilled() },
        supersets.map { SupersetPair(it[0], it[1]) },
        extras.map { it.toFilled() },
        cutSets,
        liftingSeconds,
    )

    companion object {
        fun of(d: BuilderDay) = SavedDay(
            d.type.name,
            d.name,
            d.slots.map(SavedFilled::of),
            d.supersets.map { listOf(it.first, it.second) },
            d.extras.map(SavedFilled::of),
            d.cutSets,
            d.liftingSeconds,
        )
    }
}

@Serializable
private class SavedBuilder(
    val intention: String?,
    val sessionMinutes: Int,
    val equipment: List<String>,
    val days: List<SavedDay>,
    val cardioTargetMinWeek: Int,
    val cardioExerciseId: String?,
) {
    fun toState() = BuilderState(
        intention?.let { TrainingIntention.valueOf(it) },
        sessionMinutes,
        equipment.map { Equipment.valueOf(it) },
        days.map { it.toDay() },
        cardioTargetMinWeek,
        cardioExerciseId,
    )

    companion object {
        fun of(s: BuilderState) = SavedBuilder(
            s.intention?.name,
            s.sessionMinutes,
            s.equipment.map { it.name },
            s.days.map(SavedDay::of),
            s.cardioTargetMinWeek,
            s.cardioExerciseId,
        )
    }
}
