package io.github.lopezjavier.appfitness.domain.coach.builder

import io.github.lopezjavier.appfitness.domain.model.BlockExercise
import io.github.lopezjavier.appfitness.domain.model.BlockSession
import io.github.lopezjavier.appfitness.domain.model.BlockTraining
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NextBlockSeedTest {
    private val catalogue = catalogue()

    private fun lift(id: String, group: MuscleGroup, role: String = "isolation") = BlockExercise(
        exerciseId = id,
        exerciseKey = id,
        role = role,
        muscleGroup = group.name.lowercase(),
        sets = 3,
        repMin = 8,
        repMax = 12,
        restSeconds = 120,
        reason = "",
    )

    private fun session(index: Int, name: String, focus: List<MuscleGroup>, vararg lifts: BlockExercise) = BlockSession(
        week = 1,
        sessionIndex = index,
        name = name,
        focus = focus.map { it.name.lowercase() },
        exercises = lifts.toList(),
        routineId = "r$index",
    )

    private fun block(vararg sessions: BlockSession) = CoachBlock(
        planId = "plan-1",
        training = BlockTraining(
            daysPerWeek = sessions.size,
            daysChosen = false,
            sessionMinutes = 60,
            repScheme = "",
            blockWeeks = 4,
            equipment = emptyList(),
            weeklySets = emptyMap(),
            sessionsPerMuscle = emptyMap(),
        ),
        sessions = sessions.toList(),
        saved = true,
    )

    private val upperFocus = RoutineType.Upper.groups
    private val lowerFocus = RoutineType.Lower.groups
    private val previous = block(
        session(
            1,
            "Torso A",
            upperFocus,
            lift("chest-main", MuscleGroup.Chest, "main"),
            lift("chest-iso", MuscleGroup.Chest),
            lift("back-main", MuscleGroup.Back, "main"),
        ),
        session(2, "Pierna A", lowerFocus, lift("quads-main", MuscleGroup.Quads, "main")),
    )

    private fun fresh() = startState(listOf(RoutineType.Upper, RoutineType.Lower))

    @Test
    fun `types come from each session focus in order`() {
        assertEquals(listOf(RoutineType.Upper, RoutineType.Lower), NextBlockSeed.types(previous))
    }

    @Test
    fun `a session whose focus matches no type gives no types`() {
        val odd = block(session(1, "Brazos", listOf(MuscleGroup.Biceps)))
        assertNull(NextBlockSeed.types(odd))
    }

    @Test
    fun `main lift ids are the previous main slots`() {
        assertEquals(setOf("chest-main", "back-main", "quads-main"), NextBlockSeed.mainLiftIds(previous))
    }

    @Test
    fun `apply refills the slots with the previous exercises of the same group`() {
        val seeded = NextBlockSeed.apply(
            fresh(),
            previous,
            catalogue,
            context(setOf(Equipment.Barbell, Equipment.Machine)),
        )
        val upper = seeded.days[0].slots.mapNotNull { it.pick?.exerciseId }
        assertEquals(listOf("chest-main", "back-main"), upper.filter { it.endsWith("-main") })
        assertEquals("quads-main", seeded.days[1].slots.first { it.slot.group == MuscleGroup.Quads }.pick?.exerciseId)
        assertNull(seeded.days[0].slots.first { it.slot.group == MuscleGroup.Shoulders }.pick)
    }

    @Test
    fun `an exercise no longer eligible leaves the slot empty`() {
        // Only dumbbells now: the barbell and machine picks are not eligible.
        val seeded = NextBlockSeed.apply(fresh(), previous, catalogue, context(setOf(Equipment.Dumbbell)))
        assertEquals(0, seeded.days.sumOf { d -> d.slots.count { it.pick != null } })
    }

    @Test
    fun `planned weight comes from history inside the slot range only`() {
        val history = mapOf(
            "chest-main" to ExerciseHistory(sessions8w = 4, lastWeightKg = 80.0, lastReps = 10),
            "back-main" to ExerciseHistory(sessions8w = 4, lastWeightKg = 70.0, lastReps = 20),
        )
        val seeded = NextBlockSeed.apply(fresh(), previous, catalogue, context(history = history))
        val picks = seeded.days[0].slots.mapNotNull { it.pick }.associateBy { it.exerciseId }
        assertEquals(80.0, picks.getValue("chest-main").plannedWeightKg)
        assertNull(picks.getValue("back-main").plannedWeightKg)
    }

    @Test
    fun `no pick is used twice in a routine`() {
        val seeded = NextBlockSeed.apply(fresh(), previous, catalogue, context())
        for (day in seeded.days) {
            val ids = day.slots.mapNotNull { it.pick?.exerciseId }
            assertEquals(ids.distinct(), ids)
        }
    }
}
