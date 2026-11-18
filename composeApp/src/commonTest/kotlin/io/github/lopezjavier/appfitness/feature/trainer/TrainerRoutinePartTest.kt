package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.RoutinePart
import kotlin.test.Test
import kotlin.test.assertEquals

class TrainerRoutinePartTest {
    @Test
    fun theBuildersDayNames_mapToTheirPart() {
        assertEquals(RoutinePart.Upper, routinePartOfName("Torso A"))
        assertEquals(RoutinePart.Lower, routinePartOfName("Pierna B"))
        assertEquals(RoutinePart.Push, routinePartOfName("Empuje A"))
        assertEquals(RoutinePart.Pull, routinePartOfName("Tirón B"))
        assertEquals(RoutinePart.FullBody, routinePartOfName("Cuerpo completo"))
    }

    @Test
    fun anUnknownName_readsAsGeneric() {
        assertEquals(RoutinePart.Generic, routinePartOfName("Rutina 3"))
    }
}
