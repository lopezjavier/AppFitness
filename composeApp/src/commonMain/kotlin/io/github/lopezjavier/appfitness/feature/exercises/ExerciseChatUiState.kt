package io.github.lopezjavier.appfitness.feature.exercises

/** Why a question got no answer; the UI maps each to one short Spanish message. */
enum class ChatFailureReason {
    /** Service busy, invalid or unknown error: one generic "no answer" text, with retry. */
    Busy,

    /** The daily limit of questions is reached: no retry. */
    DailyLimit,

    /** No connection (detected before sending or during the call): with retry. */
    Offline,
}

/** One row of the conversation. [id] is unique and stable within the conversation (list key). */
sealed interface ChatMessage {
    val id: Int

    /** A row that carries text. */
    sealed interface Text : ChatMessage {
        val text: String
    }

    data class Question(override val id: Int, override val text: String) : Text

    data class Answer(override val id: Int, override val text: String) : Text

    data class Failure(override val id: Int, val reason: ChatFailureReason) : ChatMessage {
        /** Only the last failure offers "Reintentar", and never after the daily limit. */
        val canRetry: Boolean get() = reason != ChatFailureReason.DailyLimit
    }
}

/** State of the exercise chat tab (US-130). Kept in memory and the saved state only; never persisted. */
data class ExerciseChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val sending: Boolean = false,
    val isOnline: Boolean = true,
) {
    /** The send button is enabled. */
    val canSend: Boolean get() = draft.isNotBlank() && !sending && isOnline
}
