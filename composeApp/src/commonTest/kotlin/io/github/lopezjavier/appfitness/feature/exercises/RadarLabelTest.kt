package io.github.lopezjavier.appfitness.feature.exercises

import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_hamstrings_short
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals

class RadarLabelTest {
    @Test
    fun hamstringsUseTheShortAxisLabel() {
        assertEquals(Res.string.muscle_hamstrings_short, MuscleGroup.Hamstrings.radarLabel)
    }

    @Test
    fun everyOtherGroupKeepsItsNormalLabel() {
        (MuscleGroup.entries - MuscleGroup.Hamstrings).forEach { assertEquals(it.label, it.radarLabel, "$it") }
    }
}
