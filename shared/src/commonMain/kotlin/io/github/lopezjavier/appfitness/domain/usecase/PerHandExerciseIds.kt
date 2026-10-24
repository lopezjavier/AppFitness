package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository

/** Ids of the exercises whose logged weight is per hand (volume counts it twice). */
fun Iterable<Exercise>.perHandIds(): Set<String> = filter { it.perHand }.map { it.id }.toSet()

/** The per-hand exercise ids; a failed read falls back to none and never blocks the caller. */
suspend fun ExerciseRepository.perHandIdsOrDefault(): Set<String> = getAll().getOrNull()?.perHandIds() ?: emptySet()
