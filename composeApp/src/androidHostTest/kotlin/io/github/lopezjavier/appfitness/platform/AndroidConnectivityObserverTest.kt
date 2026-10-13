package io.github.lopezjavier.appfitness.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowConnectivityManager
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo
import kotlin.test.assertEquals

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
class AndroidConnectivityObserverTest {
    private lateinit var context: Context
    private lateinit var shadow: ShadowConnectivityManager
    private val network: Network = ShadowNetwork.newInstance(NETWORK_ID)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        shadow = shadowOf(context.getSystemService(ConnectivityManager::class.java))
    }

    private fun capabilities(capability: List<Int>): NetworkCapabilities =
        ShadowNetworkCapabilities.newInstance().also { caps ->
            val shadowCaps = Shadow.extract<ShadowNetworkCapabilities>(caps)
            capability.forEach { shadowCaps.addCapability(it) }
        }

    private fun activate(caps: NetworkCapabilities) {
        shadow.addNetwork(network, connectedInfo())
        shadow.setNetworkCapabilities(network, caps)
    }

    private fun connectedInfo(): NetworkInfo = ShadowNetworkInfo.newInstance(
        NetworkInfo.DetailedState.CONNECTED,
        ConnectivityManager.TYPE_WIFI,
        0,
        true,
        NetworkInfo.State.CONNECTED,
    )

    private val validated = listOf(
        NetworkCapabilities.NET_CAPABILITY_INTERNET,
        NetworkCapabilities.NET_CAPABILITY_VALIDATED,
    )

    @Test
    fun emitsCurrentStateOnCollection_online() = runTest {
        shadow.setActiveNetworkInfo(connectedInfo())
        activate(capabilities(validated))
        AndroidConnectivityObserver(context).isOnline.test {
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun emitsCurrentStateOnCollection_offline() = runTest {
        shadow.setActiveNetworkInfo(null)
        AndroidConnectivityObserver(context).isOnline.test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun callbackChanges_areEmitted() = runTest {
        shadow.setActiveNetworkInfo(null)
        AndroidConnectivityObserver(context).isOnline.test {
            assertEquals(false, awaitItem())
            val callback = shadow.networkCallbacks.single()
            callback.onCapabilitiesChanged(network, capabilities(validated))
            assertEquals(true, awaitItem())
            callback.onLost(network)
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun notValidated_isOffline() = runTest {
        shadow.setActiveNetworkInfo(connectedInfo())
        activate(capabilities(listOf(NetworkCapabilities.NET_CAPABILITY_INTERNET)))
        AndroidConnectivityObserver(context).isOnline.test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun cancellation_unregistersCallback() = runTest {
        shadow.setActiveNetworkInfo(null)
        AndroidConnectivityObserver(context).isOnline.test {
            awaitItem()
            assertEquals(1, shadow.networkCallbacks.size)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, shadow.networkCallbacks.size)
    }

    private companion object {
        const val NETWORK_ID = ConnectivityManager.TYPE_WIFI
    }
}
