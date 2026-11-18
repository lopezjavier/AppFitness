package io.github.lopezjavier.appfitness.domain.coach.builder

import io.github.lopezjavier.appfitness.domain.model.RoutinePart
import kotlin.test.Test
import kotlin.test.assertEquals

class RoutineTypePartTest {
    @Test
    fun everyBuilderDayType_mapsToItsOwnTrainingPart() {
        val expected = mapOf(
            RoutineType.FullBody to RoutinePart.FullBody,
            RoutineType.Upper to RoutinePart.Upper,
            RoutineType.Lower to RoutinePart.Lower,
            RoutineType.Push to RoutinePart.Push,
            RoutineType.Pull to RoutinePart.Pull,
        )

        RoutineType.entries.forEach { type -> assertEquals(expected.getValue(type), type.part, type.name) }
    }
}
