package io.github.lopezjavier.appfitness.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** DB values of `public.session_status`. */
@Serializable
enum class SessionStatus {
    @SerialName("in_progress")
    InProgress,

    @SerialName("completed")
    Completed,

    @SerialName("discarded")
    Discarded,
}
