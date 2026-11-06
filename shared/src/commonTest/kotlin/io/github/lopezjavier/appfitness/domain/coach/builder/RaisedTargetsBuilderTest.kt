package io.github.lopezjavier.appfitness.domain.coach.builder

import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The builder with the per-group targets of the next block (T-235): time fit and the 100 % score. */
class RaisedTargetsBuilderTest {
    private val types = listOf(RoutineType.Upper, RoutineType.Lower)

    private fun start(minutes: Int, targets: Map<MuscleGroup, Int>) = BuilderState.start(
        types,
        TrainingIntention.Muscle,
        minutes,
        listOf(Equipment.Barbell),
        150,
        null,
        targets,
    )

    private fun setsOf(state: BuilderState, group: MuscleGroup) =
        state.days.sumOf { d -> d.slots.filter { it.slot.group == group }.sumOf { it.slot.sets } }

    @Test
    fun `with time to spare the raised and lowered targets reach the slots`() {
        val ready = start(600, mapOf(MuscleGroup.Chest to 16, MuscleGroup.Back to 4)) as BuilderStart.Ready

        assertEquals(16, setsOf(ready.state, MuscleGroup.Chest))
        assertEquals(4, setsOf(ready.state, MuscleGroup.Back))
        assertEquals(10, setsOf(ready.state, MuscleGroup.Shoulders))
        assertTrue(ready.state.days.none { it.cutSets })
    }

    @Test
    fun `the score is 100 percent exactly when every required slot of the raised targets is filled`() {
        val ready = start(600, SplitCatalog.ALL_GROUPS.associateWith { 20 }) as BuilderStart.Ready
        val empty = ready.state
        assertEquals(0, empty.score.percent)
        assertTrue(!empty.canSave)

        val filled = fillAll(empty)

        assertEquals(100, filled.score.percent)
        assertTrue(filled.canSave)
    }

    @Test
    fun `one empty slot keeps the raised block under 100 percent`() {
        val ready = start(600, SplitCatalog.ALL_GROUPS.associateWith { 20 }) as BuilderStart.Ready
        val filled = fillAll(ready.state).clear(0, 0)

        assertTrue(filled.score.percent < 100)
        assertTrue(!filled.canSave)
    }

    @Test
    fun `a routine that cannot hold the raised targets in the session time is refused`() {
        val result = start(30, SplitCatalog.ALL_GROUPS.associateWith { 20 })

        assertIs<BuilderStart.NoFit>(result)
    }

    @Test
    fun `between a roomy and an impossible session the raised routine is cut instead of refused`() {
        val targets = SplitCatalog.ALL_GROUPS.associateWith { 14 }

        val results = (30..600 step 5).map { start(it, targets) }

        assertTrue(results.any { it is BuilderStart.Ready && it.state.days.any { d -> d.cutSets } })
    }
}
