package io.github.lopezjavier.appfitness.feature.exercises

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ExerciseChatContext
import io.github.lopezjavier.appfitness.domain.model.ExerciseDetail
import io.github.lopezjavier.appfitness.domain.repository.ExerciseChatRepository
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Fake chat backend: records every call and answers from a queue (or a plain echo). */
internal class FakeChatRepository : ExerciseChatRepository {
    val calls = mutableListOf<Pair<ExerciseChatContext, String>>()
    val results = ArrayDeque<Result<String>>()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun ask(exercise: ExerciseChatContext, question: String): Result<String> {
        calls += exercise to question
        gate?.await()
        return results.removeFirstOrNull() ?: Result.success("Respuesta a $question")
    }
}

/** US-130: the "Preguntar" tab with the real ViewModel and a fake backend. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class ExerciseChatTabTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeChatRepository()
    private val connectivity = FakeConnectivityObserver()
    private val bench =
        ExerciseDetail(exerciseRow("1", "bench"), "Press de banca", exerciseInfo("bench", "Press de banca"))

    private fun show() {
        rule.setContent {
            AppTheme {
                val vm = remember { ExerciseChatViewModel(repository, connectivity, SavedStateHandle()) }
                ExerciseChatRoot(bench, rememberLazyListState(), viewModel = vm)
            }
        }
        rule.waitForIdle()
    }

    private fun ask(question: String) {
        rule.onNodeWithTag("exercise_chat_input").performTextInput(question)
        rule.onNodeWithTag("exercise_chat_send").performClick()
        rule.waitForIdle()
    }

    @Test
    fun emptyConversation_showsTheHintFirst_andNoDisclaimer() {
        show()

        rule.onNodeWithTag("exercise_chat_empty").assertExists()
        rule.onAllNodesWithText("sustituye", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun sending_showsTheQuestion_theProgress_thenTheAnswer() {
        repository.gate = CompletableDeferred()
        show()

        ask("¿Cuántas series?")

        rule.onNodeWithTag("exercise_chat_message_0").assertTextEquals("¿Cuántas series?")
        rule.onNodeWithTag("exercise_chat_progress").assertExists()
        rule.onNodeWithTag("exercise_chat_empty").assertDoesNotExist()
        repository.gate!!.complete(Unit)
        rule.waitForIdle()

        rule.onNodeWithTag("exercise_chat_progress").assertDoesNotExist()
        rule.onNodeWithTag("exercise_chat_message_1").assertTextEquals("Respuesta a ¿Cuántas series?")
    }

    @Test
    fun theKeyboardSendAction_sendsTheQuestion() {
        show()
        rule.onNodeWithTag("exercise_chat_input").performTextInput("Hola")

        rule.onNodeWithTag("exercise_chat_input").performImeAction()
        rule.waitForIdle()

        assertEquals(listOf("Hola"), repository.calls.map { it.second })
    }

    @Test
    fun theRequestCarriesTheResolvedExercise_andNothingElse() {
        show()

        ask("¿Y el agarre?")

        val context = repository.calls.single().first
        assertEquals("Press de banca", context.name)
        assertEquals(listOf("Pecho"), context.muscles)
        assertEquals("Primer paso\nSegundo paso", context.instructions)
    }

    @Test
    fun busyService_showsTheGenericMessage_withRetry() {
        repository.results += Result.failure(AppException(AppError.ServiceUnavailable))
        show()

        ask("Hola")

        rule.onNodeWithTag("exercise_chat_failure").assertTextEquals("No se pudo responder. Inténtalo de nuevo.")
        rule.onNodeWithTag("exercise_chat_retry").assertExists().assertTextContains("Reintentar")
    }

    @Test
    fun dailyLimit_showsItsMessage_withoutRetry() {
        repository.results += Result.failure(AppException(AppError.QuotaExceeded))
        show()

        ask("Hola")

        rule.onNodeWithTag("exercise_chat_daily_limit")
            .assertTextEquals("Has llegado al límite de preguntas de hoy. Vuelve mañana.")
        rule.onNodeWithTag("exercise_chat_retry").assertDoesNotExist()
    }

    @Test
    fun networkFailure_showsTheOfflineMessage_withRetry() {
        repository.results += Result.failure(AppException(AppError.Network))
        show()

        ask("Hola")

        rule.onNodeWithTag("exercise_chat_failure").assertTextEquals("Sin conexión: no se puede responder.")
        rule.onNodeWithTag("exercise_chat_retry").assertExists()
    }

    @Test
    fun retry_resendsOnlyTheLastQuestion_andShowsTheAnswer() {
        repository.results += Result.failure(AppException(AppError.ServiceUnavailable))
        show()
        ask("Primera")

        rule.onNodeWithTag("exercise_chat_retry").performClick()
        rule.waitForIdle()

        assertEquals(listOf("Primera", "Primera"), repository.calls.map { it.second })
        rule.onNodeWithTag("exercise_chat_failure").assertDoesNotExist()
        rule.onNodeWithTag("exercise_chat_message_1").assertTextEquals("Respuesta a Primera")
    }

    @Test
    fun onlyTheLastFailureOffersRetry() {
        repository.results += Result.failure(AppException(AppError.ServiceUnavailable))
        show()
        ask("Primera")
        // The user moves on instead of retrying; the next question gets answered.
        ask("Segunda")

        rule.onNodeWithTag("exercise_chat_retry").assertDoesNotExist()
        assertEquals(listOf("Primera", "Segunda"), repository.calls.map { it.second })
    }

    @Test
    fun sendIsDisabled_whileTheDraftIsBlank() {
        show()
        rule.onNodeWithTag("exercise_chat_send").assertIsNotEnabled()

        rule.onNodeWithTag("exercise_chat_input").performTextInput("Hola")

        rule.onNodeWithTag("exercise_chat_send").assertIsEnabled()
    }

    @Test
    fun offline_showsTheHint_andDisablesSend() {
        connectivity.state.value = false
        show()
        rule.onNodeWithTag("exercise_chat_input").performTextInput("Hola")

        rule.onNodeWithTag("exercise_chat_offline_hint").assertTextContains("Sin conexión: no se puede preguntar")
        rule.onNodeWithTag("exercise_chat_send").assertIsNotEnabled()
        assertEquals(0, repository.calls.size)
    }

    @Test
    fun everyRequestStandsAlone_theBackendNeverSeesEarlierTurns() {
        show()
        ask("Primera")
        ask("Segunda")

        assertEquals(listOf("Primera", "Segunda"), repository.calls.map { it.second })
        assertEquals(repository.calls[0].first, repository.calls[1].first)
    }
}
