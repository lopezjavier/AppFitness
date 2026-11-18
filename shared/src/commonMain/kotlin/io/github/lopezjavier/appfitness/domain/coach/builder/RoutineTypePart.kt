package io.github.lopezjavier.appfitness.domain.coach.builder

import io.github.lopezjavier.appfitness.domain.model.RoutinePart

/** The training part of a builder day type, for its icon (`07` routine part rule 1: the day type decides first). */
val RoutineType.part: RoutinePart
    get() = when (this) {
        RoutineType.FullBody -> RoutinePart.FullBody
        RoutineType.Upper -> RoutinePart.Upper
        RoutineType.Lower -> RoutinePart.Lower
        RoutineType.Push -> RoutinePart.Push
        RoutineType.Pull -> RoutinePart.Pull
    }
