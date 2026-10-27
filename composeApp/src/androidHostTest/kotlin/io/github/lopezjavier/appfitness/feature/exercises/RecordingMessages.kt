package io.github.lopezjavier.appfitness.feature.exercises

import io.github.lopezjavier.appfitness.designsystem.AppMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/** [AppMessages] that records every text sent to it, in order (the shell queue is not under test here). */
internal class RecordingMessages : AppMessages(CoroutineScope(Job())) {
    val shown = mutableListOf<String>()

    override fun show(text: String) {
        shown += text
    }
}
