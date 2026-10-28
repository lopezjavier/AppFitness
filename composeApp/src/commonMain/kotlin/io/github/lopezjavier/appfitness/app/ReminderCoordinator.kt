package io.github.lopezjavier.appfitness.app

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
    private var pending: Job? = null

    /** The pending reminders; empty until the first successful refresh. */
    val reminders: StateFlow<List<Reminder>> = state.asStateFlow()

    /** Recomputes and returns the outcome; a failure leaves [reminders] untouched. */
    suspend fun refresh(): Result<Unit> = load().map { state.value = it }

    /** Fire-and-forget [refresh] for callers without a coroutine (write effects, lifecycle); the latest call wins. */
    fun requestRefresh() {
        pending?.cancel()
        pending = scope.launch { refresh() }
    }

    /** Forgets the reminders (sign-out): the next user must not see them. */
    fun clear() {
        pending?.cancel()
        state.value = emptyList()
    }
}
