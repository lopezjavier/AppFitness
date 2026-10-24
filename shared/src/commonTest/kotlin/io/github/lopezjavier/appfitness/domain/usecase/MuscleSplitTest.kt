package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SessionStatus
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.model.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class MuscleSplitTest {
    private val at = Instant.parse("2026-03-01T10:00:00Z")

    private fun set(index: Int, type: SetType = SetType.Working, done: Boolean = true) = WorkoutSet(
        setIndex = index,
        setType = type,
        entry = SetEntry.WeightReps(50.0, 8),
        completedAt = if (done) at else null,
    )

    private fun exercise(id: String, position: Int, vararg sets: WorkoutSet) =
        SessionExercise(id, position, null, sets.toList())

    private fun session(vararg exercises: SessionExercise) = WorkoutSession(
        id = "s",
        routineId = null,
        name = "Push",
        status = SessionStatus.Completed,
        startedAt = at,
        endedAt = at,
        exercises = exercises.toList(),
    )

    private val groups = mapOf(
        "bench" to MuscleGroup.Chest,
        "squat" to MuscleGroup.Quads,
        "row" to MuscleGroup.Back,
    )

    @Test
    fun singleGroupIsOneHundredPercent() {
        val split = MuscleSplit(session(exercise("bench", 0, set(0), set(1))), groups, false)

        assertEquals(listOf(MuscleShare(MuscleGroup.Chest, 2, 1.0)), split)
    }

    @Test
    fun mixedGroupsAreSplitByCompletedSets() {
        val split = MuscleSplit(
            session(exercise("bench", 0, set(0), set(1)), exercise("squat", 1, set(0))),
            groups,
            false,
        )

        assertEquals(MuscleGroup.Chest, split[0].group)
        assertEquals(2, split[0].sets)
        assertEquals(2.0 / 3.0, split[0].fraction, 1e-9)
        assertEquals(MuscleGroup.Quads, split[1].group)
        assertEquals(1.0, split.sumOf { it.fraction }, 1e-9)
    }

    @Test
    fun warmUpSetsAreExcludedUnlessIncluded() {
        val s = session(exercise("bench", 0, set(0, SetType.WarmUp), set(1)), exercise("squat", 1, set(0)))

        assertEquals(1, MuscleSplit(s, groups, false).first { it.group == MuscleGroup.Chest }.sets)
        assertEquals(2, MuscleSplit(s, groups, true).first { it.group == MuscleGroup.Chest }.sets)
    }

    @Test
    fun pendingSetsAreIgnored() {
        val split = MuscleSplit(session(exercise("bench", 0, set(0), set(1, done = false))), groups, false)

        assertEquals(1, split.single().sets)
    }

    @Test
    fun unknownExercisesAreSkippedAndFractionsUseCountedSets() {
        val split =
            MuscleSplit(session(exercise("bench", 0, set(0)), exercise("gone", 1, set(0), set(1))), groups, false)

        assertEquals(listOf(MuscleShare(MuscleGroup.Chest, 1, 1.0)), split)
    }

    @Test
    fun emptySessionGivesEmptyList() {
        assertTrue(MuscleSplit(session(), groups, false).isEmpty())
        assertTrue(MuscleSplit(session(exercise("bench", 0, set(0, done = false))), groups, false).isEmpty())
    }

    @Test
    fun tiesAreOrderedByEnumOrder() {
        val split = MuscleSplit(
            session(exercise("squat", 0, set(0)), exercise("row", 1, set(0)), exercise("bench", 2, set(0))),
            groups,
            false,
        )

        assertEquals(listOf(MuscleGroup.Chest, MuscleGroup.Back, MuscleGroup.Quads), split.map { it.group })
    }

    @Test
    fun moreSetsComeFirst() {
        val split = MuscleSplit(
            session(exercise("bench", 0, set(0)), exercise("squat", 1, set(0), set(1))),
            groups,
            false,
        )

        assertEquals(MuscleGroup.Quads, split.first().group)
    }
}
