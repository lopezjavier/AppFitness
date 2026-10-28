package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.domain.model.CheckInDay
import io.github.lopezjavier.appfitness.domain.model.Reminder
import io.github.lopezjavier.appfitness.domain.usecase.LoadReminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * App-wide holder of the derived reminders (US-57/US-58). Nothing is stored: every [refresh] recomputes them from the
 * weight, circumference and settings data. A failed refresh keeps the last list, so the count never flickers to zero.
 */
class ReminderCoordinator(private val load: LoadReminders, private val scope: CoroutineScope) {
    private val state = MutableStateFlow<List<Reminder>>(emptyList())
    private val checkInState = MutableStateFlow<CheckInDay?>(null)
    private var pending: Job? = null
    private var version = 0

    /** The pending reminders (the count at the entry point); empty until the first successful refresh. */
    val reminders: StateFlow<List<Reminder>> = state.asStateFlow()

    /**
     * The open check-in window, answered or not (null when none is due, the kind is silenced or nothing loaded yet).
     * An answered one is reachable from the center but is not in [reminders].
     */
    val checkIn: StateFlow<CheckInDay?> = checkInState.asStateFlow()

    /** Recomputes and returns the outcome; a failure leaves [reminders] and [checkIn] untouched. */
    suspend fun refresh(): Result<Unit> {
        val ticket = ++version
        // A result is applied only if no newer refresh or clear started meanwhile, so a slow stale read cannot win.
        val result = load()
        result.onSuccess {
            if (ticket == version) {
                state.value = it.reminders
                checkInState.value = it.checkIn
            }
        }
        return result.map { }
    }

    /** Fire-and-forget [refresh] for callers without a coroutine (write effects, lifecycle); the latest call wins. */
    fun requestRefresh() {
        pending?.cancel()
        pending = scope.launch { refresh() }
    }

    /** Forgets the reminders (sign-out): the next user must not see them. */
    fun clear() {
        pending?.cancel()
        version++
        state.value = emptyList()
        checkInState.value = null
    }
}
