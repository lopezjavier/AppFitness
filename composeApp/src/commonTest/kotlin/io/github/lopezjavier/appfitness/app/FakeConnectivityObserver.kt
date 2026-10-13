package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Test double: [state] is the connectivity; its `subscriptionCount` shows who is collecting. */
internal class FakeConnectivityObserver(initial: Boolean = true) : ConnectivityObserver {
    val state = MutableStateFlow(initial)
    override val isOnline: Flow<Boolean> = state
}
