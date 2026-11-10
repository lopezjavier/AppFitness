package io.github.lopezjavier.appfitness.domain.model

/**
 * The only exercise data shared with the trainer chat (US-130). Strings are already resolved for display; nothing else
 * (no user data, no history) can be added by construction.
 */
data class ExerciseChatContext(val name: String, val muscles: List<String>, val instructions: String)
