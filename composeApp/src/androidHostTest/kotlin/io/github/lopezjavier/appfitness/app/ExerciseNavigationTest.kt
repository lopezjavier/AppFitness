package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The exercise picker/detail destinations and the selection result hand-off. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class ExerciseNavigationTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var nav: NavHostController

    private val pickerSlot: ExercisePickerSlot = { modifier, selectMode, onOpenDetail, onSelected ->
        Column(modifier.fillMaxSize().testTag("exercise_picker_screen")) {
            Text(if (selectMode) "select" else "browse", Modifier.testTag("picker_mode"))
            Box(Modifier.size(48.dp).testTag("picker_open_detail").clickable { onOpenDetail("ex-1") })
            Box(Modifier.size(48.dp).testTag("picker_select").clickable { onSelected("ex-9") })
        }
    }
    private val detailSlot: ExerciseDetailSlot = { modifier, exerciseId, selectMode, onSelect, _, _ ->
        Column(modifier.fillMaxSize().testTag("exercise_detail_screen")) {
            Text("$exerciseId $selectMode", Modifier.testTag("detail_args"))
            Box(Modifier.size(48.dp).testTag("detail_select").clickable(onClick = onSelect))
        }
    }

    // Stand-ins for the builder and the live session: a draft counter that only survives if the destination does.
    private val builderSlot: RoutineBuilderSlot = { modifier, host ->
        var taps by rememberSaveable { mutableIntStateOf(0) }
        Column(modifier.fillMaxSize().testTag("routine_builder_screen")) {
            Text("taps $taps", Modifier.testTag("builder_draft"))
            Box(Modifier.size(48.dp).testTag("builder_edit").clickable { taps++ })
            Box(Modifier.size(48.dp).testTag("builder_open_exercise").clickable { host.onOpenExercise("ex-1") })
        }
    }
    private val liveSlot: LiveSessionSlot = { modifier, host ->
        var taps by rememberSaveable { mutableIntStateOf(0) }
        Column(modifier.fillMaxSize().testTag("live_screen")) {
            Text("taps $taps", Modifier.testTag("live_draft"))
            Box(Modifier.size(48.dp).testTag("live_edit").clickable { taps++ })
            Box(Modifier.size(48.dp).testTag("live_open_exercise").clickable { host.onOpenExercise("ex-1") })
        }
    }

    private fun showShell() {
        rule.setContent {
            AppTheme {
                nav = rememberNavController()
                Shell()
            }
        }
        rule.waitForIdle()
    }

    @Composable
    private fun Shell() {
        AppScaffold(
            navController = nav,
            exercisePicker = pickerSlot,
            exerciseDetail = detailSlot,
            routineBuilder = builderSlot,
            liveSession = liveSlot,
        )
    }

    private fun openLibrary() {
        rule.onNodeWithTag("nav_tab_workout").performClick()
        rule.runOnUiThread { nav.navigateToFullScreen(ExercisePickerRoute()) }
        rule.waitForIdle()
    }

    @Test
    fun entrenoTab_showsMisRutinas_andNoLibraryButton() {
        showShell()
        rule.onNodeWithTag("nav_tab_workout").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("routines_screen").assertExists()
        rule.onNodeWithTag("workout_exercise_library_button").assertDoesNotExist()
    }

    @Test
    fun picker_opensInBrowseMode_andBackReturnsToEntreno() {
        showShell()
        openLibrary()

        rule.onNodeWithTag("exercise_picker_screen").assertExists()
        rule.onNodeWithTag("picker_mode").assertTextEquals("browse")
        rule.onNodeWithTag("exercise_picker_top_bar_title").assertTextEquals("Ejercicios")
        rule.onNodeWithTag("nav_bar").assertDoesNotExist()

        rule.onNodeWithTag("exercise_picker_top_bar_back").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("exercise_picker_screen").assertDoesNotExist()
        rule.onNodeWithTag("routines_screen").assertExists()
    }

    @Test
    fun pickerRow_opensTheDetail_withTheExerciseId() {
        showShell()
        openLibrary()

        rule.onNodeWithTag("picker_open_detail").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("detail_args").assertTextEquals("ex-1 false")
        rule.onNodeWithTag("exercise_detail_top_bar_title").assertTextEquals("Ejercicio")
    }

    @Test
    fun selection_isHandedToThePreviousDestination_andThePickerCloses() {
        showShell()
        rule.onNodeWithTag("nav_tab_workout").performClick()
        rule.runOnUiThread { nav.navigateToFullScreen(ExercisePickerRoute(selectMode = true)) }
        rule.waitForIdle()
        rule.onNodeWithTag("picker_mode").assertTextEquals("select")

        rule.onNodeWithTag("picker_select").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("exercise_picker_screen").assertDoesNotExist()
        val entry = nav.currentBackStackEntry
        assertTrue(entry?.destination?.hasRoute(WorkoutHome::class) == true)
        assertEquals("ex-9", entry?.savedStateHandle?.get<String>(SELECTED_EXERCISE_KEY))
    }

    @Test
    fun selectingFromTheDetail_closesBothScreensAndReturnsTheId() {
        showShell()
        rule.onNodeWithTag("nav_tab_workout").performClick()
        rule.runOnUiThread { nav.navigateToFullScreen(ExercisePickerRoute(selectMode = true)) }
        rule.waitForIdle()
        rule.onNodeWithTag("picker_open_detail").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("detail_args").assertTextEquals("ex-1 true")

        rule.onNodeWithTag("detail_select").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("exercise_detail_screen").assertDoesNotExist()
        rule.onNodeWithTag("exercise_picker_screen").assertDoesNotExist()
        assertEquals("ex-1", nav.currentBackStackEntry?.savedStateHandle?.get<String>(SELECTED_EXERCISE_KEY))
    }

    @Test
    fun builderToDetailAndBack_keepsTheDraft() {
        showShell()
        rule.onNodeWithTag("nav_tab_workout").performClick()
        rule.runOnUiThread { nav.navigateToFullScreen(RoutineBuilderRoute(null)) }
        rule.waitForIdle()
        rule.onNodeWithTag("builder_edit").performClick()
        rule.onNodeWithTag("builder_edit").performClick()

        rule.onNodeWithTag("builder_open_exercise").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("detail_args").assertTextEquals("ex-1 false")
        rule.runOnUiThread { nav.popBackStack() }
        rule.waitForIdle()

        rule.onNodeWithTag("routine_builder_screen").assertExists()
        rule.onNodeWithTag("builder_draft").assertTextEquals("taps 2")
    }

    @Test
    fun sessionToDetailAndBack_keepsTheSession() {
        showShell()
        rule.onNodeWithTag("nav_tab_workout").performClick()
        rule.runOnUiThread { nav.navigateToFullScreen(LiveSessionRoute) }
        rule.waitForIdle()
        rule.onNodeWithTag("live_edit").performClick()

        rule.onNodeWithTag("live_open_exercise").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("detail_args").assertTextEquals("ex-1 false")
        rule.runOnUiThread { nav.popBackStack() }
        rule.waitForIdle()

        rule.onNodeWithTag("live_screen").assertExists()
        rule.onNodeWithTag("live_draft").assertTextEquals("taps 1")
    }
}
