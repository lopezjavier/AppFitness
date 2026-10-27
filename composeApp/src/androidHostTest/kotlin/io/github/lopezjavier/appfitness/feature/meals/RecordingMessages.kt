package io.github.lopezjavier.appfitness.feature.meals

import io.github.lopezjavier.appfitness.designsystem.AppMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

internal class RecordingMessages : AppMessages(CoroutineScope(Dispatchers.Unconfined)) {
    val shown = mutableListOf<String>()

    override fun show(text: String) {
        shown += text
    }
}
