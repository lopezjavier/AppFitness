package io.github.lopezjavier.appfitness.feature.exercises

import android.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.test.FakeImageLoaderEngine
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.domain.usecase.SearchExercises
import io.github.lopezjavier.appfitness.feature.workout.FakeWorkoutSessionRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** US-09 / US-33: hand count when creating, and editing and deleting manual exercises. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class ExerciseManagementScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeExerciseRepository(
        listOf(
            exerciseRow("cat", "bench", MuscleGroup.Chest, Equipment.Barbell).copy(perHand = true),
            exerciseRow("mine", null, MuscleGroup.Chest, Equipment.Machine, "Press Hammer", custom = true),
        ),
    )
    private val library = FakeExerciseLibrary(mapOf("bench" to exerciseInfo("bench", "Press de banca")))
    private val sessions = FakeWorkoutSessionRepository()
    private var closed = 0

    @Before
    fun setUp() {
        ExerciseDeletedNotice.consume()
        val engine = FakeImageLoaderEngine.Builder().default(ColorImage(Color.BLUE)).build()
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(RuntimeEnvironment.getApplication()).components { add(engine) }.build(),
        )
    }

    @After
    fun tearDown() {
        ExerciseDeletedNotice.consume()
        SingletonImageLoader.reset()
    }

    private fun showDetail(id: String): ExerciseDetailViewModel {
        val vm = ExerciseDetailViewModel(id, LoadExerciseCatalog(repository, library), repository, sessions)
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalExerciseMediaUris provides FakeExerciseMediaUris) {
                    ExerciseDetailRoot(
                        exerciseId = id,
                        selectMode = false,
                        onSelect = {},
                        onClose = { closed++ },
                        viewModel = vm,
                        clock = MutableClock(),
                    )
                }
            }
        }
        rule.waitForIdle()
        return vm
    }

    private fun showPicker(): ExercisePickerViewModel {
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
        return vm
    }

    @Test
    fun creatingAnExercise_withThePerHandSwitchOn_createsItWithTheFlag() {
        showPicker()
        rule.onNodeWithTag("exercise_picker_search").performTextInput("Curl unilateral")
        rule.onNodeWithTag("exercise_picker_create_button").performClick()
        rule.onNodeWithTag("exercise_create_per_hand_switch").assertIsOff()
        rule.onNodeWithTag("exercise_create_muscle_chest").performClick()
        rule.onNodeWithTag("exercise_create_equipment_machine").performClick()
        rule.onNodeWithTag("exercise_create_per_hand_switch").performScrollTo().performClick()
        rule.onNodeWithTag("exercise_create_per_hand_switch").assertIsOn()
        rule.onNodeWithTag("exercise_create_submit").performScrollTo().assertIsEnabled().performClick()
        rule.waitForIdle()

        assertEquals(1, repository.createCalls)
        assertEquals(true, repository.lastCreatedPerHand)
    }

    @Test
    fun creatingAnExercise_leavesThePerHandSwitchOffByDefault() {
        showPicker()
        rule.onNodeWithTag("exercise_picker_search").performTextInput("Curl cable")
        rule.onNodeWithTag("exercise_picker_create_button").performClick()
        rule.onNodeWithTag("exercise_create_muscle_chest").performClick()
        rule.onNodeWithTag("exercise_create_equipment_machine").performClick()
        rule.onNodeWithTag("exercise_create_submit").performScrollTo().performClick()
        rule.waitForIdle()

        assertEquals(false, repository.lastCreatedPerHand)
    }

    @Test
    fun theCreateSheet_perHandRow_hasTouchTargetsOf48dp_andAHelpSheetWithoutTheWordMancuerna() {
        showPicker()
        rule.onNodeWithTag("exercise_picker_search").performTextInput("Algo")
        rule.onNodeWithTag("exercise_picker_create_button").performClick()
        val switch = rule.onNodeWithTag("exercise_create_per_hand_switch").performScrollTo().getUnclippedBoundsInRoot()
        val help = rule.onNodeWithTag("exercise_create_per_hand_help").getUnclippedBoundsInRoot()
        assertTrue(switch.height >= 48.dp)
        assertTrue(help.width >= 48.dp && help.height >= 48.dp)

        rule.onNodeWithTag("exercise_create_per_hand_help").performClick()
        rule.onNodeWithTag("exercise_create_per_hand_help_sheet_body").assertIsDisplayed()
        val body = rule.onNodeWithTag("exercise_create_per_hand_help_sheet_body").fetchSemanticsNode()
        val text = body.config[SemanticsProperties.Text].joinToString { it.text }
        assertFalse(text.contains("mancuerna", ignoreCase = true))
    }

    @Test
    fun aManualExercise_showsEditAndDeleteButtons_of48dp() {
        showDetail("mine")

        rule.onNodeWithTag("exercise_detail_edit").assertHasClickAction()
        rule.onNodeWithTag("exercise_detail_delete").assertHasClickAction()
        listOf("exercise_detail_edit", "exercise_detail_delete").forEach {
            val bounds = rule.onNodeWithTag(it).getUnclippedBoundsInRoot()
            assertTrue(bounds.width >= 48.dp && bounds.height >= 48.dp)
        }
    }

    @Test
    fun aCatalogueExercise_hasNoEditOrDelete_andShowsThePerHandValue() {
        showDetail("cat")

        rule.onNodeWithTag("exercise_detail_edit").assertDoesNotExist()
        rule.onNodeWithTag("exercise_detail_delete").assertDoesNotExist()
        rule.onNodeWithTag("exercise_detail_per_hand_value").assertTextEquals("Pesas por mano: Sí")
    }

    @Test
    fun aManualExercise_showsPerHandNo_whenTheFlagIsOff() {
        showDetail("mine")

        rule.onNodeWithTag("exercise_detail_per_hand_value").assertTextEquals("Pesas por mano: No")
    }

    @Test
    fun editingAManualExercise_prefillsTheForm_andSavesTheNewValues() {
        showDetail("mine")
        rule.onNodeWithTag("exercise_detail_edit").performClick()

        rule.onNodeWithTag("exercise_edit_name").assertTextContains("Press Hammer")
        rule.onNodeWithTag("exercise_edit_per_hand_switch").performScrollTo().assertIsOff()
        rule.onNodeWithTag("create_exercise_metric_type_weight_reps").assertDoesNotExist()
        rule.onNodeWithTag("exercise_edit_name").performTextClearance()
        rule.onNodeWithTag("exercise_edit_save").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("exercise_edit_name").performTextInput("Press Hammer unilateral")
        rule.onNodeWithTag("exercise_edit_per_hand_switch").performScrollTo().performClick()
        rule.onNodeWithTag("exercise_edit_save").performScrollTo().assertIsEnabled().performClick()
        rule.waitForIdle()

        val saved = repository.rows.first { it.id == "mine" }
        assertEquals("Press Hammer unilateral", saved.name)
        assertTrue(saved.perHand)
        rule.onNodeWithTag("exercise_edit_save").assertDoesNotExist()
        rule.onNodeWithTag("exercise_detail_per_hand_value").assertTextEquals("Pesas por mano: Sí")
    }

    @Test
    fun editingAManualExercise_keepsTheSheetOpenAndShowsAnError_whenSavingFails() {
        repository.updateFailure = AppError.Network
        showDetail("mine")
        rule.onNodeWithTag("exercise_detail_edit").performClick()
        rule.onNodeWithTag("exercise_edit_save").performScrollTo().performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("exercise_edit_error").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("exercise_edit_save").assertIsEnabled()
    }

    @Test
    fun deletingAnUnusedManualExercise_afterConfirming_removesItAndLeavesTheScreen() {
        showDetail("mine")
        rule.onNodeWithTag("exercise_detail_delete").performClick()
        rule.onNodeWithTag("exercise_detail_delete_confirm").performClick()
        rule.waitForIdle()

        assertEquals(listOf("cat"), repository.rows.map { it.id })
        assertEquals(1, closed)
        assertTrue(ExerciseDeletedNotice.pending.value)
    }

    @Test
    fun cancellingTheDelete_deletesNothing() {
        showDetail("mine")
        rule.onNodeWithTag("exercise_detail_delete").performClick()
        rule.onNodeWithTag("exercise_detail_delete_cancel").performClick()
        rule.waitForIdle()

        assertEquals(0, repository.deleteCalls)
        assertEquals(0, closed)
        rule.onNodeWithTag("exercise_detail_delete_confirm").assertDoesNotExist()
    }

    @Test
    fun deletingAnExerciseInUse_showsTheBlockedMessage_andDeletesNothing() {
        repository.deleteFailure = AppError.InUse(sets = 3, routines = 1)
        showDetail("mine")
        rule.onNodeWithTag("exercise_detail_delete").performClick()
        rule.onNodeWithTag("exercise_detail_delete_confirm").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("exercise_detail_in_use_message")
            .assertTextEquals("Este ejercicio está en uso (3 series, 1 rutina) y no se puede eliminar")
        rule.onNodeWithTag("exercise_detail_delete_confirm").assertDoesNotExist()
        assertEquals(0, closed)
        assertEquals(2, repository.rows.size)
    }

    @Test
    fun aFailedDelete_keepsTheDialogOpenWithARetryableError() {
        repository.deleteFailure = AppError.Network
        showDetail("mine")
        rule.onNodeWithTag("exercise_detail_delete").performClick()
        rule.onNodeWithTag("exercise_detail_delete_confirm").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("exercise_detail_delete_confirm").assertIsDisplayed()
        repository.deleteFailure = null
        rule.onNodeWithTag("exercise_detail_delete_confirm").performClick()
        rule.waitForIdle()

        assertEquals(1, closed)
    }

    @Test
    fun aDetailThatFailsToLoad_showsNoManagementButtons() {
        repository.rows = emptyList()
        showDetail("mine")

        rule.onNodeWithTag("exercise_detail_edit").assertDoesNotExist()
        rule.onNodeWithTag("exercise_detail_error_action").assertExists()
    }
}
