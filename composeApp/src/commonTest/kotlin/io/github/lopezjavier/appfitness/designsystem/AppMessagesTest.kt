package io.github.lopezjavier.appfitness.designsystem

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AppMessagesTest {
    private val duration = AppMessagesDefaults.DurationMillis

    private fun TestScope.messages() = AppMessages(backgroundScope)

    @Test
    fun noMessage_currentIsNull() = runTest {
        assertNull(messages().current.value)
    }

    @Test
    fun show_makesTheMessageCurrent() = runTest {
        val messages = messages()

        messages.show("Guardado")

        assertEquals("Guardado", messages.current.value?.text)
    }

    @Test
    fun show_twice_queuesTheSecondUntilTheFirstIsDismissed() = runTest {
        val messages = messages()

        messages.show("Primero")
        messages.show("Segundo")

        assertEquals("Primero", messages.current.value?.text)
        messages.consume(messages.current.value!!.id)
        assertEquals("Segundo", messages.current.value?.text)
        messages.consume(messages.current.value!!.id)
        assertNull(messages.current.value)
    }

    @Test
    fun autoDismiss_afterTheDuration_showsTheNextOne() = runTest {
        val messages = messages()
        messages.show("Primero")
        messages.show("Segundo")

        advanceTimeBy(duration - 1)
        runCurrent()
        assertEquals("Primero", messages.current.value?.text)

        advanceTimeBy(1)
        runCurrent()
        assertEquals("Segundo", messages.current.value?.text)

        advanceTimeBy(duration)
        runCurrent()
        assertNull(messages.current.value)
    }

    @Test
    fun theSecondMessage_getsItsOwnFullDuration() = runTest {
        val messages = messages()
        messages.show("Primero")
        messages.show("Segundo")
        advanceTimeBy(duration)
        runCurrent()

        advanceTimeBy(duration - 1)
        runCurrent()

        assertEquals("Segundo", messages.current.value?.text)
    }

    @Test
    fun consume_withAStaleId_isIgnored() = runTest {
        val messages = messages()
        messages.show("Primero")
        val staleId = messages.current.value!!.id
        messages.consume(staleId)
        messages.show("Segundo")

        messages.consume(staleId)

        assertEquals("Segundo", messages.current.value?.text)
    }

    @Test
    fun consume_early_cancelsTheTimerOfThatMessage() = runTest {
        val messages = messages()
        messages.show("Primero")
        messages.consume(messages.current.value!!.id)
        advanceTimeBy(duration - 10)
        messages.show("Segundo")

        advanceTimeBy(20)
        runCurrent()

        assertEquals("Segundo", messages.current.value?.text)
    }

    @Test
    fun aPendingMessage_isStillCurrentWhenALaterDestinationReadsTheSameHolder() = runTest {
        val messages = messages()
        messages.show("Guardado")

        // Navigation pops the screen that sent it; the shell-level holder is unchanged for the next reader.
        advanceTimeBy(duration / 2)

        assertEquals("Guardado", messages.current.value?.text)
    }

    @Test
    fun noAppMessages_ignoresShow() = runTest {
        val messages = NoAppMessages

        messages.show("x")

        assertNull(messages.current.value)
    }
}
