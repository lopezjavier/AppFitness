package io.github.lopezjavier.appfitness.domain.port

/** Local settings of the end-of-rest alert. */
interface RestAlertPreferences {
    /** Whether the one-time "enable notifications" explanation was already shown. */
    suspend fun explanationShown(): Boolean

    suspend fun markExplanationShown()
}
