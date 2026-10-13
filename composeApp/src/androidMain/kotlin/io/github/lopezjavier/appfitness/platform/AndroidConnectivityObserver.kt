package io.github.lopezjavier.appfitness.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * [ConnectivityObserver] on top of the default network of [ConnectivityManager].
 * Online means the default network has both INTERNET and VALIDATED, so a captive or dead Wi-Fi is offline.
 */
class AndroidConnectivityObserver(context: Context) : ConnectivityObserver {
    private val manager: ConnectivityManager = context.getSystemService(ConnectivityManager::class.java)

    override val isOnline: Flow<Boolean> =
        callbackFlow {
            trySend(currentState())
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                        trySend(capabilities.isOnline())
                    }

                    override fun onLost(network: Network) {
                        trySend(false)
                    }

                    override fun onUnavailable() {
                        trySend(false)
                    }
                }
            val registered =
                try {
                    manager.registerDefaultNetworkCallback(callback)
                    true
                } catch (_: SecurityException) {
                    false
                } catch (_: RuntimeException) {
                    // Documented: too many registered callbacks.
                    false
                }
            if (registered) {
                awaitClose { manager.unregisterNetworkCallback(callback) }
            } else {
                // E.g. too many registered callbacks: keep the state read above and finish quietly.
                close()
            }
        }.distinctUntilChanged().conflate()

    private fun currentState(): Boolean =
        manager.activeNetwork?.let { manager.getNetworkCapabilities(it) }?.isOnline() ?: false

    private fun NetworkCapabilities.isOnline(): Boolean = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
