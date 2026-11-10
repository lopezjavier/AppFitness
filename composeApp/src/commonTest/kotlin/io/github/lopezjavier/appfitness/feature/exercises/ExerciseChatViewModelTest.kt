package io.github.lopezjavier.appfitness.feature.exercises

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ExerciseChatContext
import io.github.lopezjavier.appfitness.domain.repository.ExerciseChatRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class FakeExerciseChatRepository : ExerciseChatRepository {
    val calls = mutableListOf<Pair<ExerciseChatContext, String>>()
    val results = ArrayDeque<Result<String>>()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun ask(exercise: ExerciseChatContext, question: String): Result<String> {
        calls += exercise to question
        gate?.await()
        return results.removeFirstOrNull() ?: Result.success("answer to $question")
    }
}

class ExerciseChatViewModelTest {
    private val repository = FakeExerciseChatRepository()
    private val connectivity = FakeConnectivityObserver()
    private var savedState = SavedStateHandle()
    private val context = ExerciseChatContext("Press banca", listOf("Pecho"), "Baja la barra.")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ExerciseChatViewModel(repository, connectivity, savedState)

    private fun TestScope.state(vm: ExerciseChatViewModel): ExerciseChatUiState {
        advanceUntilIdle()
        return vm.uiState.value
    }

    private fun TestScope.ask(vm: ExerciseChatViewModel, text: String) {
        vm.setDraft(text)
        vm.send(context)
        advanceUntilIdle()
    }

    private fun failure(state: ExerciseChatUiState) = assertIs<ChatMessage.Failure>(state.messages.last())

    @Test
    fun send_appendsQuestionAndAnswerAndClearsTheDraft() = runTest {
        val vm = viewModel()

        ask(vm, "  ¿Codos?  ")

        val state = state(vm)
        assertEquals(listOf("¿Codos?", "answer to ¿Codos?"), state.messages.map { (it as? ChatMessage.Text)?.text })
        assertIs<ChatMessage.Question>(state.messages[0])
        assertIs<ChatMessage.Answer>(state.messages[1])
        assertEquals("", state.draft)
        assertFalse(state.sending)
        assertEquals(listOf(context to "¿Codos?"), repository.calls)
    }

    @Test
    fun send_ignoresABlankDraft() = runTest {
        val vm = viewModel()

        ask(vm, "   ")

        assertTrue(state(vm).messages.isEmpty())
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun send_isIgnoredWhileAnotherSendIsInFlight() = runTest {
        repository.gate = CompletableDeferred()
        val vm = viewModel()
        ask(vm, "one")
        assertTrue(state(vm).sending)

        ask(vm, "two")
        repository.gate?.complete(Unit)

        assertEquals(1, repository.calls.size)
        assertEquals(2, state(vm).messages.size)
    }

    @Test
    fun setDraft_capsTheDraftAt500Characters() = runTest {
        val vm = viewModel()

        vm.setDraft("a".repeat(800))

        assertEquals(500, state(vm).draft.length)
    }

    @Test
    fun send_offlineAppendsTheQuestionAndAnOfflineFailureWithoutCalling() = runTest {
        connectivity.state.value = false
        val vm = viewModel()
        advanceUntilIdle()

        ask(vm, "q")

        val state = state(vm)
        assertFalse(state.isOnline)
        assertIs<ChatMessage.Question>(state.messages[0])
        assertEquals(ChatFailureReason.Offline, failure(state).reason)
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun send_mapsEachErrorToItsFailureReason() = runTest {
        val expected = mapOf(
            AppError.QuotaExceeded to ChatFailureReason.DailyLimit,
            AppError.Network to ChatFailureReason.Offline,
            AppError.ServiceUnavailable to ChatFailureReason.Busy,
            AppError.Validation to ChatFailureReason.Busy,
            AppError.Unknown to ChatFailureReason.Busy,
            AppError.Unauthorized to ChatFailureReason.Busy,
        )

        expected.forEach { (error, reason) ->
            repository.results += Result.failure(AppException(error))
            val vm = ExerciseChatViewModel(repository, connectivity, SavedStateHandle())
            ask(vm, "q")

            assertEquals(reason, failure(state(vm)).reason, error.toString())
        }
    }

    @Test
    fun retry_resendsOnlyTheLastQuestionAndReplacesTheFailure() = runTest {
        val vm = viewModel()
        ask(vm, "first")
        repository.results += Result.failure(AppException(AppError.ServiceUnavailable))
        ask(vm, "second")

        vm.retry(context)
        advanceUntilIdle()

        assertEquals(listOf("first", "second", "second"), repository.calls.map { it.second })
        val messages = state(vm).messages
        assertEquals(
            listOf("first", "answer to first", "second", "answer to second"),
            messages.map {
                (it as ChatMessage.Text).text
            },
        )
        assertEquals(messages.size, messages.map { it.id }.toSet().size)
    }

    @Test
    fun retry_isIgnoredAfterTheDailyLimit() = runTest {
        repository.results += Result.failure(AppException(AppError.QuotaExceeded))
        val vm = viewModel()
        ask(vm, "q")

        vm.retry(context)
        advanceUntilIdle()

        assertEquals(1, repository.calls.size)
    }

    @Test
    fun send_carriesTheCatalogueAndTheCustomExerciseContext() = runTest {
        val custom = ExerciseChatContext("Mi ejercicio", listOf("Espalda"), "")
        val vm = viewModel()
        ask(vm, "a")
        vm.setDraft("b")
        vm.send(custom)
        advanceUntilIdle()

        assertEquals(listOf(context, custom), repository.calls.map { it.first })
    }

    @Test
    fun recreatedFromTheSameSavedState_restoresMessagesAndDraft() = runTest {
        val first = viewModel()
        ask(first, "q")
        first.setDraft("typing")

        val second = viewModel()

        val restored = state(second)
        assertEquals(state(first).messages.map { it.id to it::class }, restored.messages.map { it.id to it::class })
        assertEquals(listOf("q", "answer to q"), restored.messages.map { (it as ChatMessage.Text).text })
        assertEquals("typing", restored.draft)
    }

    @Test
    fun aNewViewModelWithAFreshHandle_startsEmpty() = runTest {
        ask(viewModel(), "q")

        val fresh = ExerciseChatViewModel(repository, connectivity, SavedStateHandle())

        assertTrue(state(fresh).messages.isEmpty())
        assertEquals("", state(fresh).draft)
    }

    @Test
    fun anInFlightSendLostAtRecreation_showsTheQuestionWithARetryFailure() = runTest {
        repository.gate = CompletableDeferred()
        val first = viewModel()
        ask(first, "q")

        val second = viewModel()

        val restored = state(second)
        assertFalse(restored.sending)
        assertIs<ChatMessage.Question>(restored.messages[0])
        assertEquals(ChatFailureReason.Busy, failure(restored).reason)
    }
}
