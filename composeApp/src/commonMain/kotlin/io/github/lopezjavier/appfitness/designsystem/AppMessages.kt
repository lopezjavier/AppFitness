package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One transient message. [text] is already resolved by the caller (no resource ids in the holder). */
data class AppMessage(val id: Long, val text: String)

object AppMessagesDefaults {
    /** How long a message stays visible before the next one (or nothing) replaces it. */
    @Suppress("ktlint:standard:property-naming") // Public API name fixed by the T-118 contract.
    const val DurationMillis: Long = 4_000L
}

/**
 * App-level message queue (R-28): FIFO, one visible at a time, no action buttons. It lives in the shell, not in
 * a screen, so a message sent right before a destination pops is still [current] on the destination below.
 * It is not restored after process death (a transient message may be lost). Use from the main thread.
 *
 * [scope] runs the auto-dismiss timers (tests pass a virtual-time scope).
 */
open class AppMessages(
    private val scope: CoroutineScope,
    private val durationMillis: Long = AppMessagesDefaults.DurationMillis,
) {
    private val pending = ArrayDeque<AppMessage>()
    private val state = MutableStateFlow<AppMessage?>(null)
    private var timer: Job? = null
    private var nextId = 0L

    /** The visible message, or null. */
    open val current: StateFlow<AppMessage?> get() = state.asStateFlow()

    /** Queues [text]; it shows at once when nothing is visible. */
    open fun show(text: String) {
        pending.addLast(AppMessage(nextId++, text))
        if (state.value == null) advance()
    }

    /** Dismisses the visible message when its id is [id]; a stale id is ignored. */
    open fun consume(id: Long) {
        if (state.value?.id != id) return
        timer?.cancel()
        advance()
    }

    private fun advance() {
        val next = pending.removeFirstOrNull()
        state.value = next
        timer =
            next?.let { message ->
                scope.launch {
                    delay(durationMillis)
                    consume(message.id)
                }
            }
    }
}

/** Default of [LocalAppMessages]: drops every message (previews and tests that do not care). */
object NoAppMessages : AppMessages(CoroutineScope(Job())) {
    private val none = MutableStateFlow<AppMessage?>(null)

    override val current: StateFlow<AppMessage?> get() = none

    override fun show(text: String) = Unit

    override fun consume(id: Long) = Unit
}

/** The shell's message queue. */
val LocalAppMessages = staticCompositionLocalOf<AppMessages> { NoAppMessages }
