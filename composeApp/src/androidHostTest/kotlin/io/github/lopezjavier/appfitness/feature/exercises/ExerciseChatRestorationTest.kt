package io.github.lopezjavier.appfitness.feature.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.ExerciseDetail
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ADR-40 for US-130: the conversation survives leaving the app (recreation with the saved state) and is gone when the
 * user leaves the exercise (its destination is popped).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class ExerciseChatRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeChatRepository()
    private val connectivity = FakeConnectivityObserver()
    private val bench =
        ExerciseDetail(exerciseRow("1", "bench"), "Press de banca", exerciseInfo("bench", "Press de banca"))

    @Test
    fun typedDraft_question_andAnswer_surviveRecreation() {
        val handle = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme {
                val vm = remember { ExerciseChatViewModel(repository, connectivity, handle) }
                ExerciseChatRoot(bench, rememberLazyListState(), viewModel = vm)
            }
        }
        rule.onNodeWithTag("exercise_chat_input").performTextInput("Primera")
        rule.onNodeWithTag("exercise_chat_send").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("exercise_chat_input").performTextInput("Borrador")
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("exercise_chat_message_0").assertTextEquals("Primera")
        rule.onNodeWithTag("exercise_chat_message_1").assertTextEquals("Respuesta a Primera")
        rule.onNodeWithTag("exercise_chat_input").assertTextContains("Borrador")
        assertEquals(1, repository.calls.size)
    }

    @Test
    fun poppingTheDetailDestination_clearsTheConversation_andReopeningStartsEmpty() {
        lateinit var nav: NavHostController
        rule.setContent {
            KoinApplication(
                application = {
                    modules(module { viewModel { ExerciseChatViewModel(repository, connectivity, get()) } })
                },
            ) {
                AppTheme {
                    nav = rememberNavController()
                    NavHost(nav, startDestination = "home") {
                        composable("home") {
                            Box(Modifier.size(48.dp).clickable { nav.navigate("detail") }.testTag("open_detail"))
                        }
                        composable("detail") {
                            // Same wiring as the detail screen: the default view model is keyed per exercise.
                            ExerciseChatRoot(bench, rememberLazyListState())
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("open_detail").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("exercise_chat_input").performTextInput("Primera")
        rule.onNodeWithTag("exercise_chat_send").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("exercise_chat_message_1").assertTextEquals("Respuesta a Primera")

        rule.runOnUiThread { nav.popBackStack() }
        rule.waitForIdle()
        rule.onNodeWithTag("open_detail").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("exercise_chat_empty").assertExists()
        rule.onNodeWithTag("exercise_chat_message_0").assertDoesNotExist()
    }
}
