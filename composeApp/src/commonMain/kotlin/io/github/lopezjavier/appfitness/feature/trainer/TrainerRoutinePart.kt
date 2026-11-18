package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.RoutinePart
import io.github.lopezjavier.appfitness.domain.usecase.DeriveRoutinePart

private val deriveRoutinePart = DeriveRoutinePart()

/**
 * The training part of a planned session known only by its name ("Torso A", "Pierna B"): the builder names its days
 * after their type, so the name words of the core use case decide; an unknown name reads as [RoutinePart.Generic].
 */
internal fun routinePartOfName(name: String): RoutinePart = deriveRoutinePart(name, emptyList())
