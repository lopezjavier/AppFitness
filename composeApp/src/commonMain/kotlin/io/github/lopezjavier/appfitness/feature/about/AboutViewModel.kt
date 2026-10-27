package io.github.lopezjavier.appfitness.feature.about

import androidx.lifecycle.ViewModel
import io.github.lopezjavier.appfitness.domain.model.BuildInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val CREDIT_SEPARATOR = '|'

/** One third-party credit: the [title] of the source and the [text] of the attribution line. */
data class Credit(val title: String, val text: String) {
    companion object {
        /**
         * Parses an entry of the `credits` string-array, written as `Title|Text` (the text may contain further `|`).
         * The UI loads the array and maps it with this function.
         */
        fun parse(entry: String): Credit {
            val index = entry.indexOf(CREDIT_SEPARATOR)
            require(index > 0) { "Credit entry must be 'Title|Text': $entry" }
            return Credit(title = entry.substring(0, index), text = entry.substring(index + 1))
        }
    }
}

/** State of the About screen: static, nothing to load or edit. The credits come from the `credits` resource. */
data class AboutUiState(val buildInfo: BuildInfo)

/** About screen (US-45): exposes the build info; it holds no state of its own, so nothing needs restoring. */
class AboutViewModel(buildInfo: BuildInfo) : ViewModel() {
    private val _uiState = MutableStateFlow(AboutUiState(buildInfo))
    val uiState: StateFlow<AboutUiState> = _uiState.asStateFlow()
}
