package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.feature.workout.PastWorkoutSource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-37 navigation: History "+" -> form -> back in History; detail -> edit; deleting leaves the details too. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class WorkoutManagementNavigationTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var nav: NavHostController

    private val historySlot: HistorySlot = { modifier, _, _, onStartPastWorkout ->
        Column(modifier.fillMaxSize().testTag("history_screen")) {
            Box(
                Modifier.size(48.dp).testTag("stub_start_empty").clickable {
                    onStartPastWorkout(PastWorkoutSource.Empty)
                },
            )
            Box(
                Modifier.size(48.dp).testTag("stub_start_routine")
                    .clickable { onStartPastWorkout(PastWorkoutSource.FromRoutine("r1")) },
            )
            Box(
                Modifier.size(48.dp).testTag("stub_start_session")
                    .clickable { onStartPastWorkout(PastWorkoutSource.FromSession("s9")) },
            )
        }
    }
    private val pastSlot: PastWorkoutSlot = { modifier, routineId, sessionId, host ->
        Column(modifier.fillMaxSize().testTag("past_workout_screen")) {
            Text("$routineId|$sessionId", Modifier.testTag("past_args"))
            Box(Modifier.size(48.dp).testTag("stub_past_saved").clickable(onClick = host.onClose))
        }
    }
    private val detailSlot: SessionDetailSlot = { modifier, sessionId, _, _, onEdit ->
        Box(modifier.fillMaxSize().testTag("session_detail_screen")) {
            Text(sessionId, Modifier.testTag("detail_id"))
            Box(Modifier.size(48.dp).testTag("stub_edit").clickable(onClick = onEdit))
        }
    }
    private val editSlot: EditWorkoutSlot = { modifier, sessionId, host, onDeleted ->
        Column(modifier.fillMaxSize().testTag("edit_workout_screen")) {
            Text(sessionId, Modifier.testTag("edit_id"))
            Box(Modifier.size(48.dp).testTag("stub_edit_saved").clickable(onClick = host.onClose))
            Box(Modifier.size(48.dp).testTag("stub_edit_deleted").clickable(onClick = onDeleted))
        }
    }

    private fun show() {
        rule.setContent {
            AppTheme {
                nav = rememberNavController()
                AppScaffold(
                    navController = nav,
                    history = historySlot,
                    sessionDetail = detailSlot,
                    editWorkout = editSlot,
                    pastWorkout = pastSlot,
                )
            }
        }
        rule.runOnUiThread { nav.navigateToFullScreen(HistoryRoute) }
        rule.waitForIdle()
    }

    @Test
    fun theEmptySource_opensTheFormWithItsOwnTopBar_andSavingReturnsToHistory() {
        show()

        rule.onNodeWithTag("stub_start_empty").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("past_workout_screen").assertExists()
        rule.onNodeWithTag("past_workout_top_bar_title").assertTextEquals("Registrar entreno pasado")
        rule.onNodeWithTag("nav_bar").assertDoesNotExist()
        rule.onNodeWithTag("past_args").assertTextEquals("null|null")
        rule.onNodeWithTag("stub_past_saved").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("history_screen").assertExists()
    }

    @Test
    fun aRoutineOrAWorkoutSource_reachesTheFormAsItsArgument() {
        show()

        rule.onNodeWithTag("stub_start_routine").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("past_args").assertTextEquals("r1|null")
        rule.onNodeWithTag("past_workout_top_bar_back").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("stub_start_session").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("past_args").assertTextEquals("null|s9")
    }

    @Test
    fun backFromTheForm_returnsToHistoryWithoutSaving() {
        show()

        rule.onNodeWithTag("stub_start_empty").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("past_workout_top_bar_back").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("history_screen").assertExists()
    }

    @Test
    fun theDetailEditAction_opensTheEditScreen_andSavingReturnsToTheDetail() {
        show()
        rule.runOnUiThread { nav.navigateToFullScreen(SessionDetailRoute("s1")) }
        rule.waitForIdle()

        rule.onNodeWithTag("stub_edit").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("edit_workout_top_bar_title").assertTextEquals("Editar entreno")
        rule.onNodeWithTag("edit_id").assertTextEquals("s1")
        rule.onNodeWithTag("stub_edit_saved").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("detail_id").assertTextEquals("s1")
    }

    @Test
    fun deletingFromTheEditScreen_leavesTheDetailToo_backInHistory() {
        show()
        rule.runOnUiThread { nav.navigateToFullScreen(SessionDetailRoute("s1")) }
        rule.waitForIdle()
        rule.onNodeWithTag("stub_edit").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("stub_edit_deleted").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("history_screen").assertExists()
    }
}
