package io.github.lopezjavier.appfitness.feature.exercises

import android.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.test.FakeImageLoaderEngine
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.domain.usecase.SearchExercises
import io.github.lopezjavier.appfitness.feature.workout.FakeWorkoutSessionRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** T-150: the optional "Patrón de movimiento" field and its picker on the create and edit exercise sheets. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class ExerciseMovementPatternScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeExerciseRepository(
        listOf(
            exerciseRow("cat", "bench", MuscleGroup.Chest, Equipment.Barbell),
            exerciseRow("mine", null, MuscleGroup.Chest, Equipment.Machine, "Press Hammer", custom = true),
            exerciseRow("tagged", null, MuscleGroup.Back, Equipment.Cable, "Remo propio", custom = true)
                .copy(movementPattern = MovementPattern.HorizontalPull),
        ),
    )
    private val library = FakeExerciseLibrary(mapOf("bench" to exerciseInfo("bench", "Press de banca")))

    @Before
    fun setUp() {
        val engine = FakeImageLoaderEngine.Builder().default(ColorImage(Color.BLUE)).build()
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(RuntimeEnvironment.getApplication()).components { add(engine) }.build(),
        )
    }

    @After
    fun tearDown() {
        SingletonImageLoader.reset()
    }

    private fun showDetail(id: String) {
        val vm = ExerciseDetailViewModel(
            id,
            LoadExerciseCatalog(repository, library),
            repository,
            FakeWorkoutSessionRepository(),
            FakeConnectivityObserver(),
        )
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalExerciseMediaUris provides FakeExerciseMediaUris) {
                    ExerciseDetailRoot(
                        exerciseId = id,
                        selectMode = false,
                        onSelect = {},
                        viewModel = vm,
                        clock = MutableClock(),
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun showCreateSheet() {
        val vm = ExercisePickerViewModel(
            LoadExerciseCatalog(repository, library),
            SearchExercises(),
            repository,
            FakeConnectivityObserver(),
            false,
        )
        rule.setContent {
            AppTheme {
                val state by vm.uiState.collectAsState()
                LaunchedEffect(vm) { vm.effects.collect { } }
                CompositionLocalProvider(LocalExerciseMediaUris provides FakeExerciseMediaUris) {
                    ExercisePickerScreen(state, vm::onEvent)
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("exercise_picker_search").performTextInput("Sentadilla nueva")
        rule.onNodeWithTag("exercise_picker_create_button").performClick()
    }

    private fun fillRequired() {
        rule.onNodeWithTag("exercise_create_muscle_chest").performClick()
        rule.onNodeWithTag("exercise_create_equipment_machine").performClick()
    }

    private fun fillRequiredAndSubmit() {
        fillRequired()
        submit()
    }

    private fun submit() {
        rule.onNodeWithTag("exercise_create_sheet_commit").assertIsEnabled().performClick()
        rule.waitForIdle()
    }

    @Test
    fun theCreateSheet_showsTheMovementPatternRow_withSinDefinirByDefault() {
        showCreateSheet()

        rule.onNodeWithTag("exercise_create_movement_pattern").performScrollTo().assertIsDisplayed()
            .assertTextContains("Patrón de movimiento")
            .assertTextContains("Sin definir")
    }

    @Test
    fun choosingAPattern_closesThePicker_showsItInTheRow_andSendsItOnSubmit() {
        showCreateSheet()
        fillRequired()
        rule.onNodeWithTag("exercise_create_movement_pattern").performScrollTo().performClick()
        rule.onNodeWithTag("exercise_create_movement_pattern_picker_sheet").assertIsDisplayed()
        rule.onNodeWithTag("exercise_create_movement_pattern_picker_option_squat").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("exercise_create_movement_pattern_picker_sheet").assertDoesNotExist()
        rule.onNodeWithTag("exercise_create_movement_pattern").performScrollTo().assertTextContains("Sentadilla")
        submit()

        assertEquals(1, repository.createCalls)
        assertEquals(MovementPattern.Squat, repository.lastCreatedMovementPattern)
    }

    @Test
    fun submittingWithoutChoosingAPattern_sendsNone() {
        showCreateSheet()
        fillRequiredAndSubmit()

        assertEquals(1, repository.createCalls)
        assertNull(repository.lastCreatedMovementPattern)
    }

    @Test
    fun theCreatePicker_listsSinDefinirAndAllTwelvePatterns() {
        showCreateSheet()
        rule.onNodeWithTag("exercise_create_movement_pattern").performScrollTo().performClick()

        rule.onNodeWithTag("exercise_create_movement_pattern_picker_option_none").assertTextContains("Sin definir")
        MovementPattern.entries.forEach {
            rule.onNodeWithTag("exercise_create_movement_pattern_picker_option_${it.tagName()}").assertExists()
        }
    }

    @Test
    fun anOutsideTapOnThePicker_closesOnlyThePicker_andLeavesTheFormOpen() {
        showCreateSheet()
        rule.onNodeWithTag("exercise_create_movement_pattern").performScrollTo().performClick()
        rule.onNodeWithTag("exercise_create_movement_pattern_picker_sheet_close").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("exercise_create_movement_pattern_picker_sheet").assertDoesNotExist()
        rule.onNodeWithTag("exercise_create_sheet").assertIsDisplayed()
        rule.onNodeWithTag("exercise_create_movement_pattern").performScrollTo().assertTextContains("Sin definir")
    }

    @Test
    fun theEditSheet_showsTheStoredPattern_andSinDefinirWhenNone() {
        showDetail("tagged")
        rule.onNodeWithTag("exercise_detail_edit").performClick()
        rule.onNodeWithTag("exercise_edit_movement_pattern").performScrollTo().assertTextContains("Tirón horizontal")
    }

    @Test
    fun theEditSheet_ofAnExerciseWithoutPattern_showsSinDefinir() {
        showDetail("mine")
        rule.onNodeWithTag("exercise_detail_edit").performClick()
        rule.onNodeWithTag("exercise_edit_movement_pattern").performScrollTo().assertTextContains("Sin definir")
    }

    @Test
    fun choosingSinDefinirOnEdit_clearsTheStoredPattern() {
        showDetail("tagged")
        rule.onNodeWithTag("exercise_detail_edit").performClick()
        rule.onNodeWithTag("exercise_edit_movement_pattern").performScrollTo().performClick()
        rule.onNodeWithTag("exercise_edit_movement_pattern_picker_option_none").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("exercise_edit_movement_pattern").performScrollTo().assertTextContains("Sin definir")
        rule.onNodeWithTag("exercise_edit_sheet_commit").performClick()
        rule.waitForIdle()

        assertNull(repository.rows.first { it.id == "tagged" }.movementPattern)
    }

    @Test
    fun changingThePatternOnEdit_isSavedOnlyOnGuardar() {
        showDetail("mine")
        rule.onNodeWithTag("exercise_detail_edit").performClick()
        rule.onNodeWithTag("exercise_edit_movement_pattern").performScrollTo().performClick()
        rule.onNodeWithTag("exercise_edit_movement_pattern_picker_option_carry").performClick()
        rule.waitForIdle()
        assertEquals(0, repository.updateCalls)

        rule.onNodeWithTag("exercise_edit_sheet_commit").performClick()
        rule.waitForIdle()

        assertEquals(MovementPattern.Carry, repository.rows.first { it.id == "mine" }.movementPattern)
    }

    @Test
    fun aCatalogueExercise_hasNoEditSheet_soNoMovementPatternField() {
        showDetail("cat")

        rule.onNodeWithTag("exercise_detail_edit").assertDoesNotExist()
        rule.onNodeWithTag("exercise_edit_movement_pattern").assertDoesNotExist()
    }
}
