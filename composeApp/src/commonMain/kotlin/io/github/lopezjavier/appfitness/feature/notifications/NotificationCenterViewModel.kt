package io.github.lopezjavier.appfitness.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.app.ReminderCoordinator
import io.github.lopezjavier.appfitness.domain.model.NotificationKind
import io.github.lopezjavier.appfitness.domain.model.ReminderKind
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.NotificationSettingsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

/**
 * The notification center (US-57): the pending reminders of the [ReminderCoordinator] and the three switches (weight,
 * circumference, weekly check-in; an answered check-in is listed apart and not counted). A switch
 * applies on toggle (R-42): the row leaves or joins the list at once, the change is saved, and a failure puts the
 * switch back and emits [NotificationCenterEffect.ToggleFailed]. After a save the coordinator recomputes, so the
 * count in the bars follows. Nothing here is editable text, so there is nothing to restore (ADR-40).
 */
class NotificationCenterViewModel(
    private val coordinator: ReminderCoordinator,
    private val settings: NotificationSettingsRepository,
    connectivity: ConnectivityObserver,
    private val clock: Clock,
) : ViewModel() {
    /** The stored switches; null while loading and [failed] after a failed first read. Missing = enabled. */
    private val switches = MutableStateFlow<Map<NotificationKind, Boolean>?>(null)
    private val failed = MutableStateFlow(false)

    private val effectChannel = Channel<NotificationCenterEffect>(Channel.BUFFERED)
    val effects: Flow<NotificationCenterEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<NotificationCenterUiState> =
        combine(switches, failed, coordinator.reminders, coordinator.checkIn, connectivity.isOnline) {
                stored,
                loadFailed,
                reminders,
                checkIn,
                online,
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
                        weightEnabled = weight,
                        circumferenceEnabled = circumference,
                        today = clock.today(),
                        isOffline = !online,
                        checkInEnabled = checkInEnabled,
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
            is NotificationCenterEvent.SetEnabled -> setEnabled(event.kind, event.enabled)

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

    private fun setEnabled(kind: NotificationKind, enabled: Boolean) {
        val previous = switches.value ?: return
        if (previous.isEnabled(kind) == enabled) return
        switches.value = previous + (kind to enabled)
        viewModelScope.launch {
            if (settings.setEnabled(kind, enabled).isSuccess) {
                coordinator.refresh()
            } else {
                switches.update { current -> current?.plus(kind to previous.isEnabled(kind)) }
                effectChannel.send(NotificationCenterEffect.ToggleFailed)
            }
        }
    }

    private fun Map<NotificationKind, Boolean>.isEnabled(kind: NotificationKind): Boolean = this[kind] ?: true
}
