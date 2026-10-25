package io.github.lopezjavier.appfitness.platform

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class RawPhotoCleanupTest {
    private fun rawFile(): File = File.createTempFile("src_", ".jpg").also { it.writeBytes(byteArrayOf(1, 2, 3)) }

    @Test
    fun deletesTheRawFileWhenTheCaptureIsPicked() = runTest {
        val raw = rawFile()

        val result = deletingAfter(raw) { "photo" }

        assertEquals("photo", result)
        assertFalse(raw.exists())
    }

    @Test
    fun deletesTheRawFileWhenTheCaptureFails() = runTest {
        val raw = rawFile()

        assertFailsWith<IllegalStateException> { deletingAfter(raw) { error("camera failed") } }

        assertFalse(raw.exists())
    }

    @Test
    fun deletesTheRawFileWhenTheCoroutineIsCancelledWhileWaiting() = runTest {
        val raw = rawFile()
        val never = CompletableDeferred<Unit>()
        val job = launch(StandardTestDispatcher(testScheduler)) {
            deletingAfter(raw) { never.await() }
        }
        advanceUntilIdle()

        job.cancel(CancellationException("activity gone"))
        advanceUntilIdle()

        assertFalse(raw.exists())
    }
}
