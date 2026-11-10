package io.github.lopezjavier.appfitness.feature.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.ExerciseChatContext
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.ExerciseChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val KEY_MESSAGES = "exercise_chat_messages"
private const val KEY_DRAFT = "exercise_chat_draft"
private const val PREFIX_QUESTION = "Q:"
private const val PREFIX_ANSWER = "A:"
private const val PREFIX_FAILURE = "F:"
private const val PREFIX_LENGTH = 2

/** Longest question, matching the server cap (`MAX_QUESTION`). */
const val CHAT_QUESTION_MAX_LENGTH = 500

/**
 * Conversation about one exercise (US-130). Each question stands alone: the request carries only the exercise
 * [ExerciseChatContext] and the current question, never earlier turns. The messages and the draft live in memory and
 * in the [SavedStateHandle] (ADR-40) and nowhere else. Scope this ViewModel to the detail destination so leaving the
 * exercise clears the conversation.
 *
 * The context is passed by the UI because its muscle labels are Spanish resources resolved there.
 */
class ExerciseChatViewModel(
    private val repository: ExerciseChatRepository,
    connectivity: ConnectivityObserver,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private var nextId = 0
    private val _uiState = MutableStateFlow(restore())
    val uiState: StateFlow<ExerciseChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> _uiState.value = _uiState.value.copy(isOnline = online) }
        }
    }

    fun setDraft(text: String) {
        val draft = text.take(CHAT_QUESTION_MAX_LENGTH)
        savedState[KEY_DRAFT] = draft
        _uiState.value = _uiState.value.copy(draft = draft)
    }

    /** Sends the draft as a new question. Ignored when blank or while another send is in flight. */
    fun send(context: ExerciseChatContext) {
        val current = _uiState.value
        val question = current.draft.trim().take(CHAT_QUESTION_MAX_LENGTH)
        if (question.isEmpty() || current.sending) return
        savedState[KEY_DRAFT] = ""
        publish(current.copy(messages = current.messages + ChatMessage.Question(newId(), question), draft = ""))
        ask(context, question)
    }

    /** Re-sends the last question only, replacing the last failure. Ignored after the daily limit. */
    fun retry(context: ExerciseChatContext) {
        val current = _uiState.value
        val last = current.messages.lastOrNull() as? ChatMessage.Failure
        val question = current.messages.filterIsInstance<ChatMessage.Question>().lastOrNull()?.text
        if (current.sending || question == null || last?.canRetry != true) return
        publish(current.copy(messages = current.messages.dropLast(1)))
        ask(context, question)
    }

    private fun ask(context: ExerciseChatContext, question: String) {
        if (!_uiState.value.isOnline) {
            append(ChatMessage.Failure(newId(), ChatFailureReason.Offline))
            return
        }
        publish(_uiState.value.copy(sending = true))
        viewModelScope.launch {
            val result = repository.ask(context, question)
            val message = result.fold(
                onSuccess = { ChatMessage.Answer(newId(), it) },
                onFailure = { ChatMessage.Failure(newId(), reasonOf(result.appErrorOrNull())) },
            )
            append(message)
        }
    }

    private fun append(message: ChatMessage) {
        publish(_uiState.value.let { it.copy(messages = it.messages + message, sending = false) })
    }

    private fun publish(state: ExerciseChatUiState) {
        _uiState.value = state
        savedState[KEY_MESSAGES] = ArrayList(state.messages.map { it.encode() })
    }

    private fun newId() = nextId++

    private fun reasonOf(error: AppError?): ChatFailureReason = when (error) {
        AppError.QuotaExceeded -> ChatFailureReason.DailyLimit
        AppError.Network -> ChatFailureReason.Offline
        else -> ChatFailureReason.Busy
    }

    /**
     * Rebuilds the conversation from the saved state. A trailing question has no answer (the call died with the
     * process), so it gets a retryable failure.
     */
    private fun restore(): ExerciseChatUiState {
        val stored = savedState.get<List<String>>(KEY_MESSAGES).orEmpty()
        val messages = stored.mapIndexedNotNull { index, line -> line.decode(index) }.toMutableList()
        nextId = messages.size
        if (messages.lastOrNull() is ChatMessage.Question) {
            messages += ChatMessage.Failure(newId(), ChatFailureReason.Busy)
            savedState[KEY_MESSAGES] = ArrayList(messages.map { it.encode() })
        }
        return ExerciseChatUiState(messages = messages, draft = savedState.get<String>(KEY_DRAFT).orEmpty())
    }
}

private fun ChatMessage.encode(): String = when (this) {
    is ChatMessage.Question -> PREFIX_QUESTION + text
    is ChatMessage.Answer -> PREFIX_ANSWER + text
    is ChatMessage.Failure -> PREFIX_FAILURE + reason.name
}

private fun String.decode(id: Int): ChatMessage? {
    val body = drop(PREFIX_LENGTH)
    return when {
        startsWith(PREFIX_QUESTION) -> ChatMessage.Question(id, body)

        startsWith(PREFIX_ANSWER) -> ChatMessage.Answer(id, body)

        startsWith(PREFIX_FAILURE) ->
            ChatFailureReason.entries.firstOrNull { it.name == body }?.let { ChatMessage.Failure(id, it) }

        else -> null
    }
}
