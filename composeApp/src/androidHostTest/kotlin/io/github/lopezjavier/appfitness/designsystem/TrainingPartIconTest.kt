package io.github.lopezjavier.appfitness.designsystem

import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_fitness_center
import io.github.lopezjavier.appfitness.composeapp.resources.ic_part_arms
import io.github.lopezjavier.appfitness.composeapp.resources.ic_part_cardio
import io.github.lopezjavier.appfitness.composeapp.resources.ic_part_core
import io.github.lopezjavier.appfitness.composeapp.resources.ic_part_full_body
import io.github.lopezjavier.appfitness.composeapp.resources.ic_part_glutes
import io.github.lopezjavier.appfitness.composeapp.resources.ic_part_lower
import io.github.lopezjavier.appfitness.composeapp.resources.ic_part_pull
import io.github.lopezjavier.appfitness.composeapp.resources.ic_part_push
import io.github.lopezjavier.appfitness.composeapp.resources.ic_part_upper
import io.github.lopezjavier.appfitness.domain.model.RoutinePart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** R-125: the single part-to-drawable mapping. */
class TrainingPartIconTest {
    @Test
    fun eachPart_mapsToItsOwnGlyph_andGenericKeepsTheDumbbell() {
        val expected = mapOf(
            RoutinePart.FullBody to Res.drawable.ic_part_full_body,
            RoutinePart.Upper to Res.drawable.ic_part_upper,
            RoutinePart.Lower to Res.drawable.ic_part_lower,
            RoutinePart.Push to Res.drawable.ic_part_push,
            RoutinePart.Pull to Res.drawable.ic_part_pull,
            RoutinePart.Arms to Res.drawable.ic_part_arms,
            RoutinePart.Glutes to Res.drawable.ic_part_glutes,
            RoutinePart.Core to Res.drawable.ic_part_core,
            RoutinePart.Cardio to Res.drawable.ic_part_cardio,
            RoutinePart.Generic to Res.drawable.ic_fitness_center,
        )

        RoutinePart.entries.forEach { part ->
            assertEquals(expected.getValue(part), TrainingPartIcon.drawableFor(part), part.name)
        }
    }

    @Test
    fun noTwoPartsShareAGlyph() {
        val glyphs = RoutinePart.entries.map(TrainingPartIcon::drawableFor)

        assertEquals(RoutinePart.entries.size, glyphs.toSet().size)
        assertNotEquals(TrainingPartIcon.drawableFor(RoutinePart.Push), TrainingPartIcon.drawableFor(RoutinePart.Pull))
    }
}
