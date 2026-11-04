package io.github.lopezjavier.appfitness.domain.coach.builder

import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.usecase.FolderReviewCalculator
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuilderScoreTest {
    private val cat = catalogue()

    private fun pickAt(state: BuilderState, d: Int, i: Int): BuilderState {
        val slot = state.days[d].slots[i].slot
        val used = state.days[d].slots.mapNotNull { it.pick?.exerciseId }.toSet()
        val e = SlotEligibility.picker(slot, cat, context(), used).first {
            !it.disabled &&
                (
                    slot.kind == SlotKind.Main ||
                        it.exercise.exercise.movementPattern !in FolderReviewCalculator.MAIN_PATTERNS
                    )
        }
        return state.pick(d, i, Pick.of(e.exercise.exercise, slot, null))
    }

    @Test
    fun emptyBuilder_scoresZero_andIsNotComplete() {
        val s = startState(SplitCatalog.splitsFor(4).first().types)
        assertEquals(0, s.score.percent)
        assertFalse(s.score.complete)
        assertFalse(s.canSave)
    }

    @Test
    @Suppress("NestedBlockDepth")
    fun everyPickOfARequiredSlot_strictlyRaisesTheScore_forEverySplitAndBothFamilies() {
        for (days in 1..6) {
            for (split in SplitCatalog.splitsFor(days)) {
                for (intention in listOf(TrainingIntention.Muscle, TrainingIntention.Strength)) {
                    var state = startState(split.types, intention)
                    val order = state.days.flatMapIndexed { d, day -> day.slots.indices.map { d to it } }
                    // Forward and reverse orders: the property holds whatever the order.
                    for (sequence in listOf(order, order.reversed())) {
                        state = startState(split.types, intention)
                        var previous = 0
                        sequence.forEach { (d, i) ->
                            state = pickAt(state, d, i)
                            assertTrue(
                                state.score.percent > previous,
                                "$days $intention at $d/$i: ${state.score.percent} <= $previous",
                            )
                            previous = state.score.percent
                        }
                        assertEquals(100, state.score.percent)
                        assertTrue(state.canSave)
                    }
                }
            }
        }
    }

    @Test
    fun exactlyOneSlotMissing_isBelowOneHundred() {
        val full = fillAll(startState(SplitCatalog.splitsFor(2).first().types))
        val almost = full.clear(1, 0)
        assertTrue(almost.score.percent < 100)
        assertFalse(almost.canSave)
    }

    @Test
    fun weights_muscleFamily_seventyFiveTwentyFive() {
        // One routine of two (Upper/Lower), only the first filled in one slot: pD = 1/2.
        var s = startState(listOf(RoutineType.Upper, RoutineType.Lower))
        s = pickAt(s, 0, 0)
        val sets = s.days[0].slots[0].slot.sets
        val target = 10.0 // every group reaches 10 over the week
        val pV = (sets / target) / 6
        val expected = floor((75 * pV + 25 * 0.5) / 100 * 100 + 0.5).toInt()
        assertEquals(expected, s.score.percent)
    }

    @Test
    fun weights_strengthFamily_fiftyTwenty() {
        var s = startState(listOf(RoutineType.Upper, RoutineType.Lower), TrainingIntention.Strength)
        s = pickAt(s, 0, 0)
        val sets = s.days[0].slots[0].slot.sets
        val pV = (sets / 10.0) / 6
        val expected = floor((50 * pV + 20 * 0.5) / 70 * 100 + 0.5).toInt()
        assertEquals(expected, s.score.percent)
    }

    @Test
    fun extrasAndCardio_neverChangeTheScore() {
        val full = fillAll(startState(SplitCatalog.splitsFor(2).first().types))
        val before = full.score
        val biceps = exercise("biceps-1", MuscleGroup.Biceps)
        val withExtra = full.addExtra(
            0,
            Pick.of(biceps.exercise, SectionPrescription.extraSlot(MuscleGroup.Biceps, null), null),
        )
        assertEquals(before, withExtra.score)
        assertEquals(before, withExtra.withCardio("other").score)
        val partial = full.clear(0, 0)
        assertEquals(
            partial.score,
            partial.addExtra(
                0,
                Pick.of(biceps.exercise, SectionPrescription.extraSlot(MuscleGroup.Biceps, null), null),
            ).score,
        )
    }

    @Test
    fun swappingAPick_keepsTheScore() {
        val s = pickAt(startState(SplitCatalog.splitsFor(2).first().types), 0, 0)
        val slot = s.days[0].slots[0].slot
        val other = cat.first { it.id == "${slot.group.name.lowercase()}-iso2" }
        val swapped = s.pick(0, 0, Pick.of(other.exercise, slot, null))
        assertEquals(s.score, swapped.score)
    }

    @Test
    fun clearingAPick_lowersTheScore() {
        val s = pickAt(startState(SplitCatalog.splitsFor(2).first().types), 0, 0)
        assertTrue(s.clear(0, 0).score.percent < s.score.percent)
    }

    @Test
    fun afterATimeFitCut_theReducedTargetsStillReachOneHundred() {
        val types = SplitCatalog.splitsFor(1).first().types
        val full = BuilderState.start(types, TrainingIntention.Muscle, 80, emptyList(), 150, null) as BuilderStart.Ready
        assertTrue(full.state.days.single().cutSets)
        val filled = fillAll(full.state)
        assertEquals(100, filled.score.percent)
        assertTrue(filled.canSave)
    }
}
