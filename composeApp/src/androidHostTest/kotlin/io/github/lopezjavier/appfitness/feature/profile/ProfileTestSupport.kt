package io.github.lopezjavier.appfitness.feature.profile

import io.github.lopezjavier.appfitness.designsystem.AppHaptics
import io.github.lopezjavier.appfitness.designsystem.AppMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/** Records the messages a screen shows instead of queueing them. */
internal class RecordingMessages : AppMessages(CoroutineScope(Dispatchers.Unconfined)) {
    val shown = mutableListOf<String>()

    override fun show(text: String) {
        shown += text
    }
}

/** Counts the haptics a screen fires. */
internal class CountingHaptics : AppHaptics {
    var confirms = 0
    var rejects = 0

    override fun armed() = Unit

    override fun disarmed() = Unit

    override fun tick() = Unit

    override fun confirm() {
        confirms++
    }

    override fun reject() {
        rejects++
    }
}
