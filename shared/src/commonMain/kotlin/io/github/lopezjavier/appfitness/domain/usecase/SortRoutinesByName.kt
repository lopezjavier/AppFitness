package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.RoutineSummary

private val accentFolds: Map<Char, Char> =
    buildMap {
        "áàâäãå".forEach { put(it, 'a') }
        "éèêë".forEach { put(it, 'e') }
        "íìîï".forEach { put(it, 'i') }
        "óòôöõ".forEach { put(it, 'o') }
        "úùûü".forEach { put(it, 'u') }
        put('ñ', 'n')
        put('ç', 'c')
    }

/** Lowercase, accents folded (explicit map: no JVM-only APIs), whitespace collapsed and trimmed. */
fun normalizeText(text: String): String = text
    .lowercase()
    .map { accentFolds[it] ?: it }
    .joinToString("")
    .split(Regex("\\s+"))
    .filter { it.isNotEmpty() }
    .joinToString(" ")

/** "Mis rutinas" order: A to Z ignoring case and accents; equal names keep their incoming order (stable sort). */
fun sortRoutinesByName(routines: List<RoutineSummary>): List<RoutineSummary> =
    routines.sortedBy { normalizeText(it.name) }
