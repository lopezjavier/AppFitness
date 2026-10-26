package io.github.lopezjavier.appfitness.designsystem

import kotlinx.coroutines.flow.Flow

/**
 * Drives the automatic retry of `OfflineState` (R-30): calls `reload` once on every offline to online transition
 * seen while it is collected. The first emission never reloads when it is online (nothing failed because of
 * connectivity yet); a first emission of offline followed by online does.
 */
object OfflineRetry {
    suspend fun collect(isOnline: Flow<Boolean>, reload: () -> Unit) {
        var previous: Boolean? = null
        isOnline.collect { online ->
            if (online && previous == false) reload()
            previous = online
        }
    }
}
