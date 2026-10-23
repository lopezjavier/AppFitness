package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import kotlin.time.Instant

/** One set write waiting to reach the server; the key is ([exercisePosition], [setIndex]) of the session. */
data class SetWrite(
    val sessionId: String,
    val exerciseId: String,
    val exercisePosition: Int,
    val supersetGroup: Int?,
    val setIndex: Int,
    val entry: SetEntry,
    val completedAt: Instant?,
    val setType: SetType,
    val rpe: Double? = null,
)

/**
 * In-memory retry queue of set writes (ADR-01 exception: it lives as long as its owner, nothing is persisted). A set
 * has at most one entry and the last write wins; entries keep the order in which their set was first queued.
 */
class PendingSetQueue {
    private val entries = LinkedHashMap<Pair<Int, Int>, SetWrite>()

    val size: Int get() = entries.size
    val isEmpty: Boolean get() = entries.isEmpty()

    fun enqueue(write: SetWrite) {
        entries[write.exercisePosition to write.setIndex] = write
    }

    fun snapshot(): List<SetWrite> = entries.values.toList()

    fun contains(exercisePosition: Int, setIndex: Int): Boolean = (exercisePosition to setIndex) in entries

    /** Drops [write] once sent, unless a newer write of the same set replaced it meanwhile. */
    fun remove(write: SetWrite) {
        val key = write.exercisePosition to write.setIndex
        if (entries[key] == write) entries.remove(key)
    }
}
