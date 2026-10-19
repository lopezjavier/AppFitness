package io.github.lopezjavier.appfitness.feature.exercises

import android.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.test.FakeImageLoaderEngine
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.LocalAnimationsEnabled
import io.github.lopezjavier.appfitness.domain.model.ExerciseDetail
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** US-09 exercise detail: animation, "Cómo hacerlo", attribution and the select action. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class ExerciseDetailScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val bench = exerciseRow("1", "bench")
    private val withBothPoses = ExerciseDetail(bench, "Press de banca", exerciseInfo("bench", "Press de banca"))

    @Before
    fun installFakeImageLoader() {
        val engine = FakeImageLoaderEngine.Builder().default(ColorImage(Color.BLUE)).build()
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(RuntimeEnvironment.getApplication()).components { add(engine) }.build(),
        )
    }

    @After
    fun resetImageLoader() {
        SingletonImageLoader.reset()
    }

    private fun show(
        state: ExerciseDetailUiState,
        selectMode: Boolean = false,
        animations: Boolean = true,
        onSelect: () -> Unit = {},
        onRetry: () -> Unit = {},
        onOpenProgress: () -> Unit = {},
    ) {
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(
                    LocalAnimationsEnabled provides animations,
                    LocalExerciseMediaUris provides FakeExerciseMediaUris,
                ) {
                    ExerciseDetailScreen(state, selectMode, onSelect, onRetry, onOpenProgress = onOpenProgress)
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun showsTheAnimation_withBothPoses_andTheSpanishHowTo() {
        show(ExerciseDetailUiState.Loaded(withBothPoses))

        rule.onNodeWithTag("exercise_detail_animation").assertContentDescriptionEquals("Demostración de Press de banca")
        rule.onNodeWithTag("exercise_detail_start", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("exercise_detail_peak", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Cómo hacerlo").assertExists()
        rule.onNodeWithText("1. Primer paso").assertExists()
        rule.onNodeWithText("2. Segundo paso").assertExists()
        rule.onNodeWithText("Un consejo").assertExists()
    }

    @Test
    fun progressLink_opensTheProgressOfTheExercise() {
        var opened = 0
        show(ExerciseDetailUiState.Loaded(withBothPoses), onOpenProgress = { opened++ })

        rule.onNodeWithTag("exercise_detail_progress_link").assertTextEquals("Ver progreso").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun reducedMotion_showsOnlyTheStartPose() {
        show(ExerciseDetailUiState.Loaded(withBothPoses), animations = false)

        rule.onNodeWithTag("exercise_detail_start", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("exercise_detail_peak", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun singleImageExercise_hasNoPeakPose() {
        val single = ExerciseDetail(bench, "Press de banca", exerciseInfo("bench", "Press de banca", hasPeak = false))
        show(ExerciseDetailUiState.Loaded(single))

        rule.onNodeWithTag("exercise_detail_start", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("exercise_detail_peak", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun attribution_isShown() {
        show(ExerciseDetailUiState.Loaded(withBothPoses))

        rule.onNodeWithTag("exercise_detail_attribution").assertTextEquals("Datos de ejercicios por RepDB (repdb.co)")
    }

    @Test
    fun selectButton_existsOnlyInSelectMode_andSelects() {
        var selected = 0
        show(ExerciseDetailUiState.Loaded(withBothPoses), selectMode = true, onSelect = { selected++ })

        rule.onNodeWithTag("exercise_detail_select_button").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, selected)
    }

    @Test
    fun browseMode_hasNoSelectButton() {
        show(ExerciseDetailUiState.Loaded(withBothPoses), selectMode = false)

        rule.onNodeWithTag("exercise_detail_select_button").assertDoesNotExist()
    }

    @Test
    fun keyWithoutLibraryData_rendersWithoutCrashing() {
        show(ExerciseDetailUiState.Loaded(ExerciseDetail(bench, "", null)), selectMode = true)

        rule.onNodeWithTag("exercise_detail_animation").assertDoesNotExist()
        rule.onNodeWithText("Sin instrucciones disponibles.").assertExists()
        rule.onNodeWithTag("exercise_detail_select_button").assertExists()
    }

    @Test
    fun loadFailure_offersRetry() {
        var retries = 0
        show(ExerciseDetailUiState.Error, onRetry = { retries++ })

        rule.onNodeWithTag("exercise_detail_error_action").performClick()
        assertEquals(1, retries)
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun tablet_putsTheAnimationLeftOfTheInstructions() {
        show(ExerciseDetailUiState.Loaded(withBothPoses), selectMode = true)

        val animation = rule.onNodeWithTag("exercise_detail_animation").getUnclippedBoundsInRoot()
        val howTo = rule.onNodeWithTag("exercise_detail_how_to").getUnclippedBoundsInRoot()
        assertTrue(animation.right <= howTo.left)
        assertTrue(animation.width <= 360.dp)
        val button = rule.onNodeWithTag("exercise_detail_select_button").getUnclippedBoundsInRoot()
        assertTrue(button.width > animation.width)
    }
}
