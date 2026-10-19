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
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Entry points to "Fuerza" and to the progress of an exercise (US-15). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class StrengthNavigationTest {
    @get:Rule
    val rule = createComposeRule()

    private val profileSlot: ProfileSlot = { modifier, _, _, onOpenStrength ->
        Box(modifier.fillMaxSize().testTag("profile_screen")) {
            Box(tapTarget().testTag("profile_open_strength").clickable(onClick = onOpenStrength))
        }
    }
    private val strengthSlot: StrengthSlot = { modifier, onOpenExercise, onOpenMuscleVolume, onStartWorkout ->
        Column(modifier.fillMaxSize().testTag("strength_screen")) {
            Box(tapTarget().testTag("strength_open").clickable { onOpenExercise("bench") })
            Box(tapTarget().testTag("strength_open_muscle_volume").clickable(onClick = onOpenMuscleVolume))
            Box(tapTarget().testTag("strength_start").clickable(onClick = onStartWorkout))
        }
    }
    private val muscleVolumeSlot: MuscleVolumeSlot = { modifier, _ ->
        Box(modifier.fillMaxSize().testTag("muscle_volume_screen"))
    }
    private val progressSlot: ExerciseProgressSlot = { modifier, exerciseId ->
        Text(exerciseId, modifier.testTag("progress_exercise_id"))
    }
    private val detailSlot: ExerciseDetailSlot = { modifier, _, _, _, onOpenProgress ->
        Box(modifier.fillMaxSize().testTag("exercise_detail_screen")) {
            Box(tapTarget().testTag("detail_open_progress").clickable(onClick = onOpenProgress))
        }
    }
    private val sessionDetailSlot: SessionDetailSlot = { modifier, _, _, onOpenProgress ->
        Box(modifier.fillMaxSize().testTag("session_detail_screen")) {
            Box(tapTarget().testTag("session_open_progress").clickable { onOpenProgress("squat") })
        }
    }

    private lateinit var nav: NavHostController

    private fun showShell() {
        rule.setContent {
            AppTheme {
                nav = rememberNavController()
                AppScaffold(
                    navController = nav,
                    profile = profileSlot,
                    strength = strengthSlot,
                    muscleVolume = muscleVolumeSlot,
                    exerciseProgress = progressSlot,
                    exerciseDetail = detailSlot,
                    sessionDetail = sessionDetailSlot,
                )
            }
        }
        rule.waitForIdle()
    }

    private fun openStrengthFromProfile() {
        rule.onNodeWithTag("nav_tab_profile").performClick()
        rule.onNodeWithTag("profile_open_strength").performClick()
        rule.waitForIdle()
    }

    @Test
    fun perfilRow_opensFuerza_withItsOwnTopBar_andBackReturnsToPerfil() {
        showShell()
        openStrengthFromProfile()

        rule.onNodeWithTag("strength_screen").assertExists()
        rule.onNodeWithTag("strength_title").assertTextEquals("Fuerza")
        rule.onNodeWithTag("nav_bar").assertDoesNotExist()

        rule.onNodeWithTag("strength_back").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("profile_screen").assertExists()
    }

    @Test
    fun verProgreso_opensTheProgressOfThatExercise() {
        showShell()
        openStrengthFromProfile()

        rule.onNodeWithTag("strength_open").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("progress_exercise_id").assertTextEquals("bench")
        rule.onNodeWithTag("exercise_progress_back").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("strength_screen").assertExists()
    }

    @Test
    fun volumenPorGrupoMuscular_opensItsScreenWithTitle_andBackReturnsToFuerza() {
        showShell()
        openStrengthFromProfile()

        rule.onNodeWithTag("strength_open_muscle_volume").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("muscle_volume_screen").assertExists()
        rule.onNodeWithTag("muscle_volume_title").assertTextEquals("Volumen por grupo muscular")
        rule.onNodeWithTag("muscle_volume_back").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("strength_screen").assertExists()
    }

    @Test
    fun emptyStateAction_leadsToTheEntrenoTab() {
        showShell()
        openStrengthFromProfile()

        rule.onNodeWithTag("strength_start").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("routines_screen").assertExists()
        rule.onNodeWithTag("strength_screen").assertDoesNotExist()
    }

    @Test
    fun exerciseDetail_linksToTheProgressOfItsExercise() {
        showShell()
        rule.runOnUiThread { nav.navigateToFullScreen(ExerciseDetailRoute("squat")) }
        rule.waitForIdle()

        rule.onNodeWithTag("detail_open_progress").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("progress_exercise_id").assertTextEquals("squat")
    }

    @Test
    fun sessionDetail_linksToTheProgressOfAnExercise() {
        showShell()
        rule.runOnUiThread { nav.navigateToFullScreen(SessionDetailRoute("s1")) }
        rule.waitForIdle()

        rule.onNodeWithTag("session_open_progress").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("progress_exercise_id").assertTextEquals("squat")
    }
}

private fun tapTarget() = Modifier.size(48.dp)
