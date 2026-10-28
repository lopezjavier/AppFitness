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
 * The notification center (US-57): the pending reminders of the [ReminderCoordinator] (an answered check-in is listed
 * apart and not counted). The switches live in Preferencias, "Avisos"; here they only filter: a silenced kind is not
 * listed. The center reloads on [NotificationCenterEvent.Refresh] since it is re-entered after saving there. Nothing
 * here is editable, so there is nothing to restore (ADR-40).
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
        combine(switches, failed, coordinator.reminders, coordinator.checkIn) {
                stored,
                loadFailed,
                reminders,
                checkIn,
            ->
            when {
                stored != null -> {
                    val weight = stored.isEnabled(NotificationKind.Weight)
                    val circumference = stored.isEnabled(NotificationKind.Circumference)
                    val checkInEnabled = stored.isEnabled(NotificationKind.WeeklyCheckIn)
                    NotificationCenterUiState.Content(
                        reminders = reminders.filter {
                            when (it.kind) {
                                ReminderKind.Weight -> weight
                                ReminderKind.Circumference -> circumference
                                ReminderKind.CheckIn -> checkInEnabled
                            }
                        },
                        today = clock.today(),
                        checkIn = checkIn?.takeIf { checkInEnabled },
                    )
                }

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
}
