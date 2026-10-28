package io.github.lopezjavier.appfitness.domain.port

/** The runtime permission to post notifications (Android 13+; always granted below). */
interface NotificationPermission {
    fun isGranted(): Boolean

    /** Asks the user when needed; true when the permission is granted afterwards. */
    suspend fun request(): Boolean
}
