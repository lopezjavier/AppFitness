package io.github.lopezjavier.appfitness.domain.model

/**
 * A hard failure ("Falta") the server's block check found in a folder; [data] holds the figures of the rule, as text.
 * Only `CoachFailure.NeedsChanges` carries it now (T-224 retires it with the server model code).
 */
data class FolderFalta(
    val code: String,
    val routineIndex: Int?,
    val exerciseKey: String?,
    val data: Map<String, String>,
)
