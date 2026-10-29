@file:Suppress("MagicNumber", "ReturnCount") // Positions of the saved fields; early returns on malformed input.

package io.github.lopezjavier.appfitness.feature.workout

private const val SEPARATOR = '\u001F'
private const val FIELDS = 9
private const val LEGACY_FIELDS = 8

/** The sheet as one string for the `SavedStateHandle` (ADR-40): the session id, then the summary counts. */
internal fun UpdateRoutineSheet.encoded(): String = with(summary) {
    listOf(
        sessionId,
        setsAdded,
        setsRemoved,
        setsChanged,
        restsChanged,
        exercisesAdded,
        exercisesRemoved,
        if (orderOrSupersetChanged) 1 else 0,
        notesChanged,
    ).joinToString("$SEPARATOR")
}

/** Reads back [encoded]; null for a missing or malformed value. */
internal fun decodeUpdateRoutineSheet(text: String?): UpdateRoutineSheet? {
    val fields = text?.split(SEPARATOR)?.takeIf { it.size == FIELDS || it.size == LEGACY_FIELDS } ?: return null
    val n = fields.drop(1).map { it.toIntOrNull() ?: return null }
    return UpdateRoutineSheet(
        sessionId = fields[0],
        summary = RoutineChangeSummary(
            n[0],
            n[1],
            n[2],
            n[3],
            n[4],
            n[5],
            orderOrSupersetChanged = n[6] == 1,
            notesChanged = n.getOrElse(7) { 0 },
        ),
    )
}
