package io.github.lopezjavier.appfitness.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.app.ReminderCoordinator
import io.github.lopezjavier.appfitness.domain.model.NotificationKind
import io.github.lopezjavier.appfitness.domain.model.ReminderKind
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.NotificationSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

/**
 * The notification center (US-57): the pending reminders of the [ReminderCoordinator]. The switches live in
 * Preferencias, "Avisos"; here they only filter: a silenced kind is not listed. The center reloads on
 * [NotificationCenterEvent.Refresh] since it is re-entered after saving there. Nothing here is editable, so there is
 * nothing to restore (ADR-40).
 */
class NotificationCenterViewModel(
    private val coordinator: ReminderCoordinator,
    private val settings: NotificationSettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    /** The stored switches; null while loading and [failed] after a failed first read. Missing = enabled. */
    private val switches = MutableStateFlow<Map<NotificationKind, Boolean>?>(null)
    private val failed = MutableStateFlow(false)

    val uiState: StateFlow<NotificationCenterUiState> =
        combine(switches, failed, coordinator.reminders) { stored, loadFailed, reminders ->
            when {
                stored != null -> NotificationCenterUiState.Content(
                    reminders = reminders.filter { stored.isEnabled(it.kind.notificationKind()) },
                    today = clock.today(),
                )

                loadFailed -> NotificationCenterUiState.Error

                else -> NotificationCenterUiState.Loading
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NotificationCenterUiState.Loading)

    init {
        load()
    }

    fun onEvent(event: NotificationCenterEvent) {
        when (event) {
            NotificationCenterEvent.Retry -> {
                failed.value = false
                load()
            }

            NotificationCenterEvent.Refresh -> if (switches.value != null) load()
        }
    }

    private fun load() {
        viewModelScope.launch {
            val stored = settings.getAll()
            val refreshed = coordinator.refresh()
            val map = stored.getOrNull()
            when {
                // A quiet refresh that fails keeps what is on screen.
                switches.value != null -> map?.let { switches.value = it }

                map != null && refreshed.isSuccess -> switches.value = map

                else -> failed.value = true
            }
        }
    }

    private fun Map<NotificationKind, Boolean>.isEnabled(kind: NotificationKind): Boolean = this[kind] ?: true

    private fun ReminderKind.notificationKind(): NotificationKind = when (this) {
        ReminderKind.Weight -> NotificationKind.Weight
        ReminderKind.Circumference -> NotificationKind.Circumference
        ReminderKind.WeekSummary -> NotificationKind.WeekSummary
        ReminderKind.BlockEnd -> NotificationKind.BlockEnd
    }
}
