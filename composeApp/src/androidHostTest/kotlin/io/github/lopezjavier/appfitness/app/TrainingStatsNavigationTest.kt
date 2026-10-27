package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.navigation.compose.rememberNavController
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Perfil "Entrenamiento" row opens "Estadisticas" and its exercise rows open the exercise progress. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class TrainingStatsNavigationTest {
    @get:Rule
    val rule = createComposeRule()

    private val profileSlot: ProfileSlot = { modifier, _, _, _, _, _, onOpenTrainingStats ->
        Box(modifier.fillMaxSize().testTag("profile_screen")) {
            Box(Modifier.size(48.dp).testTag("profile_open_stats").clickable(onClick = onOpenTrainingStats))
        }
    }
    private val statsSlot: TrainingStatsSlot = { modifier, onOpenExercise, onBack ->
        AppScreen("Estadísticas", modifier.testTag("stats_screen"), onBack = onBack, testTagPrefix = "stats") {
            Box(Modifier.size(48.dp).testTag("stats_open_exercise").clickable { onOpenExercise("bench") })
        }
    }
    private val progressSlot: ExerciseProgressSlot = { modifier, exerciseId, _ ->
        Text(exerciseId, modifier.testTag("progress_exercise_id"))
    }

    private fun showShell() {
        rule.setContent {
            AppTheme {
                AppScaffold(
                    navController = rememberNavController(),
                    profile = profileSlot,
                    trainingStats = statsSlot,
                    exerciseProgress = progressSlot,
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun perfilRow_opensEstadisticas_withItsTopBar_andBackReturnsToPerfil() {
        showShell()
        rule.onNodeWithTag("nav_tab_profile").performClick()
        rule.onNodeWithTag("profile_open_stats").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("stats_screen").assertExists()
        rule.onNodeWithTag("stats_title").assertTextEquals("Estadísticas")
        rule.onNodeWithTag("nav_bar").assertDoesNotExist()

        rule.onNodeWithTag("stats_back").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("profile_screen").assertExists()
    }

    @Test
    fun exerciseRow_opensTheProgressOfThatExercise() {
        showShell()
        rule.onNodeWithTag("nav_tab_profile").performClick()
        rule.onNodeWithTag("profile_open_stats").performClick()
        rule.onNodeWithTag("stats_open_exercise").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("progress_exercise_id").assertTextEquals("bench")
    }
}
