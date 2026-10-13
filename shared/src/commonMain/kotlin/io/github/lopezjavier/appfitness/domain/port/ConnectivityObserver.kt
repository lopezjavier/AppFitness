package io.github.lopezjavier.appfitness.domain.port

import kotlinx.coroutines.flow.Flow

/** Observes whether the device has a working internet connection. */
interface ConnectivityObserver {
    /**
     * `true` = online. Emits the current state on collection, then only changes (distinct).
     * The flow is cold: collecting it registers the platform listener and cancelling unregisters it.
     */
    val isOnline: Flow<Boolean>
}
