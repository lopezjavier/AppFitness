package io.github.lopezjavier.appfitness.feature.about

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.HealthAvailability
import io.github.lopezjavier.appfitness.domain.model.HistoryProbeResult
import io.github.lopezjavier.appfitness.domain.model.HistoryWindows
import io.github.lopezjavier.appfitness.domain.model.WindowStats
import io.github.lopezjavier.appfitness.domain.port.HealthDataSource
import io.github.lopezjavier.appfitness.domain.port.HealthHistoryProbe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.time.Instant

private const val KEY_RESULT = "health_diagnostics_result"
private const val FLAG_COUNT = 2
private const val FIELD_COUNT = 10
private const val NO_OLDEST = -1L

/** State of the temporary Health Connect history diagnostic (T-139). */
sealed interface HealthDiagnosticsState {
    data object Idle : HealthDiagnosticsState

    data object Running : HealthDiagnosticsState

    data class Result(val values: HistoryProbeResult) : HealthDiagnosticsState

    /** Health Connect is missing or needs an update; nothing was requested or read. */
    data class Unavailable(val availability: HealthAvailability) : HealthDiagnosticsState

    data object Error : HealthDiagnosticsState
}

sealed interface HealthDiagnosticsEvent {
    /** The "Comprobar" button. */
    data object Check : HealthDiagnosticsEvent

    /** The system permission screen closed; the probe re-reads the grants itself. */
    data object PermissionResult : HealthDiagnosticsEvent
}

sealed interface HealthDiagnosticsEffect {
    data object RequestPermissions : HealthDiagnosticsEffect
}

/**
 * Asks for the steps, weight and history permissions, then probes how far back Health Connect can be read. Only a
 * finished [HealthDiagnosticsState.Result] is persisted (as primitives, ADR-40); a running probe restores as idle.
 */
class HealthDiagnosticsViewModel(
    private val source: HealthDataSource,
    private val probe: HealthHistoryProbe,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(restore())
    val state: StateFlow<HealthDiagnosticsState> = mutableState

    private val effectChannel = Channel<HealthDiagnosticsEffect>(Channel.BUFFERED)
    val effects: Flow<HealthDiagnosticsEffect> = effectChannel.receiveAsFlow()

    private var awaitingPermission = false

    fun onEvent(event: HealthDiagnosticsEvent) {
        when (event) {
            HealthDiagnosticsEvent.Check -> check()

            HealthDiagnosticsEvent.PermissionResult -> if (awaitingPermission) {
                awaitingPermission = false
                viewModelScope.launch { runProbe() }
            }
        }
    }

    private fun check() {
        if (mutableState.value == HealthDiagnosticsState.Running) return
        val availability = source.availability()
        if (availability != HealthAvailability.Available) {
            mutableState.value = HealthDiagnosticsState.Unavailable(availability)
            return
        }
        mutableState.value = HealthDiagnosticsState.Running
        awaitingPermission = true
        effectChannel.trySend(HealthDiagnosticsEffect.RequestPermissions)
    }

    private suspend fun runProbe() {
        val result = probe.probe().getOrNull()
        if (result == null) {
            mutableState.value = HealthDiagnosticsState.Error
        } else {
            savedState[KEY_RESULT] = encode(result)
            mutableState.value = HealthDiagnosticsState.Result(result)
        }
    }

    private fun restore(): HealthDiagnosticsState =
        savedState.get<String>(KEY_RESULT)?.let(::decode)?.let { HealthDiagnosticsState.Result(it) }
            ?: HealthDiagnosticsState.Idle
}

/** Flat list of longs joined by commas: two flags, then (count, oldest millis or -1) per window. */
private fun encode(result: HistoryProbeResult): String {
    val windows = listOf(result.steps.recent, result.steps.old, result.weight.recent, result.weight.old)
    val numbers = listOf(result.featureAvailable, result.historyGranted).map { if (it) 1L else 0L } +
        windows.flatMap { listOf(it.recordCount.toLong(), it.oldest?.toEpochMilliseconds() ?: NO_OLDEST) }
    return numbers.joinToString(",")
}

private fun decode(text: String): HistoryProbeResult? = runCatching {
    val numbers = text.split(",").map(String::toLong)
    require(numbers.size == FIELD_COUNT)
    val windows = numbers.drop(FLAG_COUNT).chunked(2).map { (count, oldest) ->
        WindowStats(count.toInt(), oldest.takeIf { it != NO_OLDEST }?.let(Instant::fromEpochMilliseconds))
    }
    HistoryProbeResult(
        featureAvailable = numbers[0] == 1L,
        historyGranted = numbers[1] == 1L,
        steps = windows.pair(0),
        weight = windows.pair(1),
    )
}.getOrNull()

/** The recent and old windows of the [index]th record type (steps, then weight). */
private fun List<WindowStats>.pair(index: Int) = HistoryWindows(this[index * 2], this[index * 2 + 1])
