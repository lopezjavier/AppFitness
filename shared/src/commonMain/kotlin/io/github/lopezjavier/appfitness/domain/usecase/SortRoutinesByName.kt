package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.RoutineSummary

/** "Mis rutinas" order: A to Z ignoring case and accents; equal names keep their incoming order (stable sort). */
fun sortRoutinesByName(routines: List<RoutineSummary>): List<RoutineSummary> =
    routines.sortedBy { normalizeText(it.name) }
