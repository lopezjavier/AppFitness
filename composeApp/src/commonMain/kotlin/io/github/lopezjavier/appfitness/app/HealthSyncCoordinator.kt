package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.domain.model.SyncOutcome
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.usecase.AutoSyncHealth
import io.github.lopezjavier.appfitness.domain.usecase.AutoSyncResult
import io.github.lopezjavier.appfitness.domain.usecase.SyncHealthData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the last automatic sync left to show; any later successful sync resets it to [Idle]. */
enum class AutoState {
    Idle,

    /** Due but offline: nothing to show beyond the last data, the next open retries. */
    NotPossible,

    /** Due but it failed while online. */
    Failed,

    /** Due but no read permission is granted (never asked for automatically). */
    PermissionNeeded,
}

/**
 * App-wide owner of the Health Connect sync (US-60, ADR-74). [onAppStart] runs the daily automatic sync; [run] is the
 * button. There is one single flight: a call arriving while a sync runs joins it instead of reading again. Nothing is
 * stored here: a new process is a cold start and recomputes whether a sync is due.
 */
class HealthSyncCoordinator(
    private val autoSync: AutoSyncHealth,
    private val syncHealthData: SyncHealthData,
    private val connectivity: ConnectivityObserver,
    private val reminders: ReminderCoordinator,
    private val scope: CoroutineScope,
) {
    private val lock = Mutex()
    private var inFlight: Deferred<Result<SyncOutcome>?>? = null

    private val runningState = MutableStateFlow(false)
    private val lastAutoState = MutableStateFlow(AutoState.Idle)
    private val resultFlow = MutableSharedFlow<SyncOutcome.Synced>()

    val running: StateFlow<Boolean> = runningState.asStateFlow()
    val lastAuto: StateFlow<AutoState> = lastAutoState.asStateFlow()

    /** Every finished sync that stored data (not replayed: a new collector loads the stored state itself). */
    val results: SharedFlow<SyncOutcome.Synced> = resultFlow.asSharedFlow()

    /** The app started or came to the foreground on Main: sync automatically if today's sync is still due. */
    fun onAppStart() {
        scope.launch { join { autoRun() } }
    }

    /**
     * The button (or a permission result). Joins a run in flight and returns its result; otherwise reads Health
     * Connect again, even if today's automatic sync already ran.
     */
    suspend fun run(): Result<SyncOutcome> {
        while (true) {
            val result = join { manualRun() }
            if (result != null) return result
        }
    }

    // Null = the joined run had nothing to do (not due): the caller starts its own.
    private suspend fun join(block: suspend () -> Result<SyncOutcome>?): Result<SyncOutcome>? {
        val job = lock.withLock {
            inFlight?.takeIf { it.isActive } ?: scope.async {
                runningState.value = true
                try {
                    block()
                } finally {
                    runningState.value = false
                }
            }.also { inFlight = it }
        }
        return job.await()
    }

    private suspend fun manualRun(): Result<SyncOutcome> = syncHealthData().also { publish(it.getOrNull()) }

    private suspend fun autoRun(): Result<SyncOutcome>? {
        val result = autoSync()
        lastAutoState.value = when (result) {
            AutoSyncResult.Skipped, AutoSyncResult.Unavailable, is AutoSyncResult.Done -> AutoState.Idle
            AutoSyncResult.PermissionNeeded -> AutoState.PermissionNeeded
            AutoSyncResult.NotPossible -> if (connectivity.isOnline.first()) AutoState.Failed else AutoState.NotPossible
        }
        return when (result) {
            is AutoSyncResult.Done -> Result.success(result.outcome).also { publish(result.outcome) }
            else -> null
        }
    }

    private suspend fun publish(outcome: SyncOutcome?) {
        if (outcome !is SyncOutcome.Synced) return
        lastAutoState.value = AutoState.Idle
        resultFlow.emit(outcome)
        if (outcome.imported > 0) reminders.requestRefresh()
    }
}
