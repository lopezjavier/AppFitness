package io.github.lopezjavier.appfitness.domain.repository

/** Whether the "Para conocerte" prompt was already answered (saved or skipped); kept on the device. */
interface IntakePromptPreference {
    /** True once the user saved or skipped the intake; false when nothing is stored. */
    suspend fun isAsked(): Boolean

    suspend fun markAsked()
}
