package io.github.lopezjavier.appfitness.designsystem

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineRetryTest {
    private val connectivity = MutableSharedFlow<Boolean>(extraBufferCapacity = 16)
    private var reloads = 0

    private fun kotlinx.coroutines.test.TestScope.start() {
        backgroundScope.launch { OfflineRetry.collect(connectivity) { reloads++ } }
        runCurrent()
    }

    private fun kotlinx.coroutines.test.TestScope.emit(vararg values: Boolean) {
        values.forEach {
            connectivity.tryEmit(it)
            runCurrent()
        }
    }

    @Test
    fun initialOnlineEmission_doesNotReload() = runTest {
        start()
        emit(true)
        assertEquals(0, reloads)
    }

    @Test
    fun offlineThenOnline_reloadsOnce() = runTest {
        start()
        emit(false, true)
        assertEquals(1, reloads)
    }

    @Test
    fun startingOffline_thenOnline_reloadsOnce() = runTest {
        start()
        emit(false)
        assertEquals(0, reloads)
        emit(true)
        assertEquals(1, reloads)
    }

    @Test
    fun stayingOnline_neverReloads() = runTest {
        start()
        emit(true, true, true)
        assertEquals(0, reloads)
    }

    @Test
    fun stayingOffline_neverReloads() = runTest {
        start()
        emit(false, false)
        assertEquals(0, reloads)
    }

    @Test
    fun repeatedFlaps_reloadOncePerReconnection() = runTest {
        start()
        emit(true, false, true, false, true, false, true)
        assertEquals(3, reloads)
    }
}
