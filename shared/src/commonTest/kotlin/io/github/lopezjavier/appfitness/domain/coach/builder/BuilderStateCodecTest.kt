package io.github.lopezjavier.appfitness.domain.coach.builder

import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BuilderStateCodecTest {
    private fun roundTrip(state: BuilderState) = BuilderStateCodec.decode(BuilderStateCodec.encode(state))

    @Test
    fun emptyState_roundTrips() {
        val state = startState(listOf(RoutineType.Upper, RoutineType.Lower))
        assertEquals(state, roundTrip(state))
    }

    @Test
    fun partialPicks_withPlannedWeight_roundTrip() {
        val cat = catalogue()
        val base = startState(listOf(RoutineType.Upper, RoutineType.Lower))
        val slot = base.days[0].slots[0].slot
        val chest = cat.first { it.id == "chest-main" }.exercise
        val state = base.pick(0, 0, Pick.of(chest, slot, ExerciseHistory(3, 80.0, 10)))
        val decoded = assertNotNull(roundTrip(state))
        assertEquals(state, decoded)
        assertEquals(80.0, decoded.days[0].slots[0].pick?.plannedWeightKg)
    }

    @Test
    fun extrasCutSetsAndSupersets_roundTrip() {
        val cat = catalogue()
        val base = startState(listOf(RoutineType.FullBody), intention = TrainingIntention.Strength)
        val extraPick =
            Pick(
                "curl",
                MuscleGroup.Biceps,
                io.github.lopezjavier.appfitness.domain.model.MovementPattern.Isolation,
                12.5,
            )
        val state = fillAll(base, cat).addExtra(0, extraPick)
        assertEquals(state, roundTrip(state))
    }

    @Test
    fun fittedStateWithSupersetsAndCutSets_roundTrips() {
        val state = (15..180 step 5).firstNotNullOf { minutes ->
            val start = BuilderState.start(
                listOf(RoutineType.FullBody),
                TrainingIntention.Muscle,
                minutes,
                emptyList(),
                150,
                null,
            )
            (start as? BuilderStart.Ready)?.state?.takeIf { it.days.single().supersets.isNotEmpty() }
        }
        assertEquals(state, roundTrip(state))
    }

    @Test
    fun noIntentionAndNoCardio_roundTrip() {
        val state = startState(listOf(RoutineType.FullBody), intention = null, cardioId = null)
        assertEquals(state, roundTrip(state))
    }

    @Test
    fun corruptInput_decodesToNull() {
        assertNull(BuilderStateCodec.decode("{not json"))
        assertNull(
            BuilderStateCodec.decode(
                """{"intention":"Nope","sessionMinutes":1,"equipment":[],"days":[],""" +
                    """"cardioTargetMinWeek":0,"cardioExerciseId":null}""",
            ),
        )
    }
}
