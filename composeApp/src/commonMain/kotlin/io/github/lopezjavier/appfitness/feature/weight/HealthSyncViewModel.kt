package io.github.lopezjavier.appfitness.feature.weight

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.app.AutoState
import io.github.lopezjavier.appfitness.app.HealthSyncCoordinator
import io.github.lopezjavier.appfitness.domain.model.HealthAvailability
import io.github.lopezjavier.appfitness.domain.model.SyncOutcome
import io.github.lopezjavier.appfitness.domain.model.SyncPart
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.port.HealthDataSource
import io.github.lopezjavier.appfitness.domain.repository.HealthSyncRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private const val KEY_DENIED = "health_permission_denied"
private const val KEY_ASKED_ALL = "health_all_reads_asked"

/** Where the Health Connect sync stands, as the banner on the weight screen shows it (US-26). */
sealed interface HealthSyncStatus {
    /** Availability and the stored state are still being read: no status is shown yet. */
    data object Loading : HealthSyncStatus

    data object NeverSynced : HealthSyncStatus

    data object Syncing : HealthSyncStatus

    /** [failedPart] is set when only one of the two parts (weight, watch data) could be read. */
    data class Synced(val lastSyncedAt: Instant, val age: SyncAge, val failedPart: SyncPart? = null) :
        HealthSyncStatus

    data object PermissionNeeded : HealthSyncStatus

    data class Unavailable(val availability: HealthAvailability) : HealthSyncStatus

    data object Failed : HealthSyncStatus

    /** The due automatic sync failed while online; the previous data stays. */
    data object AutoSyncFailed : HealthSyncStatus
}

data class HealthSyncUiState(val status: HealthSyncStatus = HealthSyncStatus.Loading, val isOnline: Boolean = true)

sealed interface HealthSyncEvent {
    /** The banner's single button: sync, ask for the permission, or offer the install, depending on the state. */
    data object Tap : HealthSyncEvent

    /** The screen came back to the foreground (the user may have installed or granted something meanwhile). */
    data object Refresh : HealthSyncEvent

    data class PermissionResult(val granted: Boolean) : HealthSyncEvent
}

sealed interface HealthSyncEffect {
    data object RequestPermissions : HealthSyncEffect

    data object OpenInstall : HealthSyncEffect

    /** A sync finished; the weight screen reloads to show the [imported] rows. */
    data class Synced(val imported: Int) : HealthSyncEffect
}

/**
 * The Health Connect banner (US-26). On open it reads availability, the granted access and the stored sync state; the
 * only persisted bit is "the user denied the permission request" (ADR-40), everything else is recomputed. A one-minute
 * ticker keeps "Sincronizado hace N min" current; [tick] suspends for that minute and is injectable for tests. Syncing
 * needs connectivity like the weight writes.
 */
@Suppress("LongParameterList") // One dependency per port; the ticker is injectable for tests.
class HealthSyncViewModel(
    private val source: HealthDataSource,
    private val coordinator: HealthSyncCoordinator,
    private val repository: HealthSyncRepository,
    connectivity: ConnectivityObserver,
    private val clock: Clock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val tick: suspend () -> Unit = { delay(1.minutes) },
) : ViewModel() {
    private val base = MutableStateFlow<HealthSyncStatus>(HealthSyncStatus.Loading)
    private val now = MutableStateFlow(clock.now())
    private val online = MutableStateFlow(true)

    private val effectChannel = Channel<HealthSyncEffect>(Channel.BUFFERED)
    val effects: Flow<HealthSyncEffect> = effectChannel.receiveAsFlow()

    // What is shown: the sync in flight, then what the last automatic sync left, then the loaded/manual status.
    private val status: Flow<HealthSyncStatus> =
        combine(base, coordinator.running, coordinator.lastAuto) { b, running, auto ->
            val passive = b is HealthSyncStatus.Synced || b == HealthSyncStatus.NeverSynced
            when {
                running -> HealthSyncStatus.Syncing
                passive && auto == AutoState.Failed -> HealthSyncStatus.AutoSyncFailed
                passive && auto == AutoState.PermissionNeeded -> HealthSyncStatus.PermissionNeeded
                else -> b
            }
        }

    val uiState: StateFlow<HealthSyncUiState> = combine(status, now, online) { status, now, online ->
        val shown = if (status is HealthSyncStatus.Synced) {
            status.copy(age = SyncAge.of(status.lastSyncedAt, now))
        } else {
            status
        }
        HealthSyncUiState(shown, online)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HealthSyncUiState())

    init {
        viewModelScope.launch { connectivity.isOnline.collect { online.value = it } }
        viewModelScope.launch {
            while (true) {
                tick()
                now.value = clock.now()
            }
        }
        viewModelScope.launch { coordinator.results.collect { onSynced(it) } }
        viewModelScope.launch { load() }
    }

    fun onEvent(event: HealthSyncEvent) {
        when (event) {
            HealthSyncEvent.Tap -> viewModelScope.launch { tap() }
            HealthSyncEvent.Refresh -> if (!coordinator.running.value) viewModelScope.launch { load() }
            is HealthSyncEvent.PermissionResult -> viewModelScope.launch { onPermissionResult(event.granted) }
        }
    }

    private suspend fun load() {
        val availability = source.availability()
        if (availability != HealthAvailability.Available) {
            base.value = HealthSyncStatus.Unavailable(availability)
            return
        }
        val canRead = source.access().hasAnyRead()
        if (canRead) savedState[KEY_DENIED] = false
        val stored = repository.getState().getOrElse {
            base.value = HealthSyncStatus.Failed
            return
        }
        base.value = when {
            savedState.get<Boolean>(KEY_DENIED) == true -> HealthSyncStatus.PermissionNeeded
            stored == null -> HealthSyncStatus.NeverSynced
            !canRead -> HealthSyncStatus.PermissionNeeded
            else -> HealthSyncStatus.Synced(stored.lastSyncedAt, SyncAge.Now)
        }
    }

    private suspend fun tap() {
        if (!online.value) return
        if (source.availability() != HealthAvailability.Available) {
            effectChannel.send(HealthSyncEffect.OpenInstall)
            return
        }
        val access = source.access()
        // A user who only granted part of the set (for instance the body types from before the watch data) is asked
        // for the full set once; a partial grant after that is respected and syncs what it covers.
        val askAgain = !access.hasAllReads() && savedState.get<Boolean>(KEY_ASKED_ALL) != true
        if (!access.hasAnyRead() || askAgain) {
            savedState[KEY_ASKED_ALL] = true
            effectChannel.send(HealthSyncEffect.RequestPermissions)
        } else {
            sync()
        }
    }

    // The dialog may grant only some types: what matters is whether anything can be read now, not the launcher flag.
    private suspend fun onPermissionResult(granted: Boolean) {
        if (granted || source.access().hasAnyRead()) {
            sync()
        } else {
            savedState[KEY_DENIED] = true
            base.value = HealthSyncStatus.PermissionNeeded
        }
    }

    private suspend fun sync() {
        base.value = when (val outcome = coordinator.run().getOrNull()) {
            // The shared results flow delivers it (also for a run this call only joined), so it is handled once.
            is SyncOutcome.Synced -> return

            SyncOutcome.PermissionNeeded -> {
                savedState[KEY_DENIED] = true
                HealthSyncStatus.PermissionNeeded
            }

            is SyncOutcome.Unavailable -> HealthSyncStatus.Unavailable(outcome.availability)

            null -> HealthSyncStatus.Failed
        }
    }

    // Also reached by an automatic sync that finishes while the weight screen is open.
    private suspend fun onSynced(outcome: SyncOutcome.Synced) {
        savedState[KEY_DENIED] = false
        now.value = clock.now()
        base.value = HealthSyncStatus.Synced(outcome.syncedAt, SyncAge.Now, outcome.failedPart)
        effectChannel.send(HealthSyncEffect.Synced(outcome.imported))
    }
}
