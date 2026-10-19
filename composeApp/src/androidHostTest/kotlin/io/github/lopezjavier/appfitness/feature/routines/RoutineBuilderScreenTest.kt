package io.github.lopezjavier.appfitness.feature.routines

import android.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.test.FakeImageLoaderEngine
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseMediaUris
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.LocalExerciseMediaUris
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
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

/** US-10 routine builder: creating, reordering, supersets, validation and the save/discard flows. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class RoutineBuilderScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val exercises =
        FakeExerciseRepository(listOf(exerciseRow("a", "bench"), exerciseRow("b", "row"), exerciseRow("c", "squat")))
    private val library =
        FakeExerciseLibrary(
            mapOf(
                "bench" to exerciseInfo("bench", "Press de banca"),
                "row" to exerciseInfo("row", "Remo con barra"),
                "squat" to exerciseInfo("squat", "Sentadilla"),
            ),
        )
    private val repository = FakeRoutineRepository()
    private val connectivity = FakeConnectivityObserver()
    private var closes = 0
    private var pickerResult by mutableStateOf<String?>(null)
    private var addExerciseTaps = 0

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

    private fun show(routineId: String? = null): RoutineBuilderViewModel {
        val vm =
            RoutineBuilderViewModel(
                routineId,
                SavedStateHandle(),
                repository,
                LoadExerciseCatalog(exercises, library),
                connectivity,
            )
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalExerciseMediaUris provides FakeExerciseMediaUris) {
                    RoutineBuilderRoot(
                        routineId = routineId,
                        selectedExerciseId = pickerResult,
                        onConsumeSelection = { pickerResult = null },
                        onAddExercise = { addExerciseTaps++ },
                        onClose = { closes++ },
                        viewModel = vm,
                    )
                }
            }
        }
        rule.waitForIdle()
        return vm
    }

    private fun RoutineBuilderViewModel.add(vararg ids: String) {
        ids.forEach { onEvent(RoutineBuilderEvent.AddExercise(it)) }
        rule.waitForIdle()
    }

    private fun clickMenuAction(row: Int, action: String) {
        rule.onNodeWithTag("routine_builder_menu_$row").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("routine_builder_${action}_$row").performClick()
        rule.waitForIdle()
    }

    private fun rowName(row: Int) = rule.onNodeWithTag("routine_builder_row_$row")

    @Test
    fun save_isDisabledWithABlankName_andWithNoExercises_andEnabledWithBoth() {
        val vm = show()
        rule.onNodeWithTag("routine_builder_save_button").assertIsNotEnabled()

        vm.add("a")
        rule.onNodeWithTag("routine_builder_save_button").assertIsNotEnabled()

        rule.onNodeWithTag("routine_builder_name_field").performTextInput("   ")
        rule.waitForIdle()
        rule.onNodeWithTag("routine_builder_save_button").assertIsNotEnabled()

        rule.onNodeWithTag("routine_builder_name_field").performTextInput("Torso")
        rule.waitForIdle()
        rule.onNodeWithTag("routine_builder_save_button").assertIsEnabled()
    }

    @Test
    fun withNoExercises_theHintAndTheAddButtonAreShown() {
        show()

        rule.onNodeWithTag("routine_builder_empty_hint").assertExists()
        rule.onNodeWithTag("routine_builder_add_exercise_button").assertTextContains("Añadir ejercicio")
        rule.onNodeWithTag("routine_builder_add_exercise_button").performClick()
        assertEquals(1, addExerciseTaps)
    }

    @Test
    fun creatingARoutine_savesTheNameAndBothExercises_thenCloses() {
        val vm = show()
        rule.onNodeWithTag("routine_builder_name_field").performTextInput("Torso fuerza")
        vm.add("a", "b")

        rule.onNodeWithTag("routine_builder_save_button").performClick()
        rule.waitForIdle()

        val saved = repository.saved.single()
        assertEquals("Torso fuerza", saved.name)
        assertEquals(listOf("a", "b"), saved.exercises.map { it.exerciseId })
        assertEquals(1, closes)
    }

    @Test
    fun anExerciseReturnedByThePicker_isAddedOnce_andConsumed() {
        show()

        pickerResult = "b"
        rule.waitForIdle()

        rowName(0).assertContentDescriptionEquals("Remo con barra")
        rule.onNodeWithTag("routine_builder_row_1").assertDoesNotExist()
        assertEquals(null, pickerResult)
    }

    @Test
    fun rows_showTheNameAndTheDefaultTargets() {
        show().add("a")

        rowName(0).assertContentDescriptionEquals("Press de banca")
        rule.onNodeWithTag("routine_builder_sets_0_value").assertTextEquals("3")
        rule.onNodeWithTag("routine_builder_reps_0_value").assertTextEquals("8")
        rule.onNodeWithTag("routine_builder_rest_0_value").assertTextEquals("120")
    }

    @Test
    fun rowMenu_subir_movesTheRowUp() {
        show().add("a", "b", "c")

        clickMenuAction(row = 2, action = "move_up")

        rowName(0).assertContentDescriptionEquals("Press de banca")
        rowName(1).assertContentDescriptionEquals("Sentadilla")
        rowName(2).assertContentDescriptionEquals("Remo con barra")
    }

    @Test
    fun rowMenu_bajar_movesTheRowDown() {
        show().add("a", "b", "c")

        clickMenuAction(row = 0, action = "move_down")

        rowName(0).assertContentDescriptionEquals("Remo con barra")
        rowName(1).assertContentDescriptionEquals("Press de banca")
    }

    @Test
    fun subir_isDisabledOnTheFirstRow_andBajarOnTheLast() {
        show().add("a", "b")

        rule.onNodeWithTag("routine_builder_menu_0").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("routine_builder_move_up_0").assertIsNotEnabled()
        rule.onNodeWithTag("routine_builder_move_down_0").assertIsEnabled()
        rule.onNodeWithTag("routine_builder_group_0").assertIsEnabled()
    }

    @Test
    fun bajarAndAgrupar_areDisabledOnTheLastRow() {
        show().add("a", "b")

        rule.onNodeWithTag("routine_builder_menu_1").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("routine_builder_move_down_1").assertIsNotEnabled()
        rule.onNodeWithTag("routine_builder_group_1").assertIsNotEnabled()
        rule.onNodeWithTag("routine_builder_move_up_1").assertIsEnabled()
    }

    @Test
    fun rowsExposeSubirAndBajarAsAccessibilityActions() {
        show().add("a", "b", "c")

        fun labels(row: Int) = rule.onNodeWithTag("routine_builder_row_$row").fetchSemanticsNode().config
            .getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }

        assertEquals(listOf("Bajar"), labels(0))
        assertEquals(listOf("Subir", "Bajar"), labels(1))
        assertEquals(listOf("Subir"), labels(2))
    }

    @Test
    fun agruparConElSiguiente_labelsBothRowsSuperserieA1AndA2() {
        show().add("a", "b", "c")

        clickMenuAction(row = 0, action = "group")

        rule.onNodeWithTag("routine_builder_superset_label_0").assertTextEquals("Superserie A1")
        rule.onNodeWithTag("routine_builder_superset_label_1").assertTextEquals("Superserie A2")
        rule.onNodeWithTag("routine_builder_superset_label_2").assertDoesNotExist()
        rowName(0).assertContentDescriptionEquals("Press de banca, Superserie A1")
    }

    @Test
    fun desagrupar_removesTheSuperset() {
        show().add("a", "b")
        clickMenuAction(row = 0, action = "group")

        clickMenuAction(row = 1, action = "ungroup")

        rule.onNodeWithTag("routine_builder_superset_label_0").assertDoesNotExist()
        rule.onNodeWithTag("routine_builder_superset_label_1").assertDoesNotExist()
    }

    @Test
    fun quitar_removesTheRow() {
        show().add("a", "b")

        clickMenuAction(row = 0, action = "remove")

        rule.onNodeWithTag("routine_builder_row_1").assertDoesNotExist()
        rowName(0).assertContentDescriptionEquals("Remo con barra")
    }

    @Test
    fun steppers_changeTheValues_andStopAtTheirBounds() {
        show().add("a")

        repeat(MORE_THAN_MAX_SETS) { rule.onNodeWithTag("routine_builder_sets_0_increment").performClick() }
        rule.waitForIdle()
        rule.onNodeWithTag("routine_builder_sets_0_value").assertTextEquals("10")
        rule.onNodeWithTag("routine_builder_sets_0_increment").assertIsNotEnabled()

        repeat(MORE_THAN_MAX_SETS) { rule.onNodeWithTag("routine_builder_sets_0_decrement").performClick() }
        rule.waitForIdle()
        rule.onNodeWithTag("routine_builder_sets_0_value").assertTextEquals("1")
        rule.onNodeWithTag("routine_builder_sets_0_decrement").assertIsNotEnabled()

        rule.onNodeWithTag("routine_builder_rest_0_increment").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("routine_builder_rest_0_value").assertTextEquals("135")
    }

    @Test
    fun saveFailure_showsTheSnackbar_andKeepsTheEdits() {
        val vm = show()
        rule.onNodeWithTag("routine_builder_name_field").performTextInput("Torso")
        vm.add("a", "b")
        repository.saveFailure = AppError.Unknown

        rule.onNodeWithTag("routine_builder_save_button").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("routine_builder_snackbar").assertExists()
        rule.onNodeWithText("No se pudo guardar la rutina. Inténtalo de nuevo.").assertExists()
        rule.onNodeWithTag("routine_builder_name_field").assertTextContains("Torso")
        rowName(1).assertContentDescriptionEquals("Remo con barra")
        rule.onNodeWithTag("routine_builder_save_button").assertIsEnabled()
        assertEquals(0, closes)
    }

    @Test
    fun offline_showsTheBanner_andDisablesSaving() {
        val vm = show()
        rule.onNodeWithTag("routine_builder_name_field").performTextInput("Torso")
        vm.add("a")

        connectivity.state.value = false
        rule.waitForIdle()

        rule.onNodeWithTag("routine_builder_offline_banner").assertExists()
        rule.onNodeWithTag("routine_builder_save_button").assertIsNotEnabled()
    }

    @Test
    fun editMode_loadsTheRoutine() {
        repository.routines += routineOf("r1", "Pierna", "c", "a")

        show("r1")

        rule.onNodeWithTag("routine_builder_name_field").assertTextContains("Pierna")
        rowName(0).assertContentDescriptionEquals("Sentadilla")
        rowName(1).assertContentDescriptionEquals("Press de banca")
    }

    @Test
    fun editMode_showsTheSkeletonWhileLoading_andAnErrorWithRetry() {
        repository.routines += routineOf("r1", "Pierna", "c")
        repository.failure = AppError.Network
        show("r1")
        rule.onNodeWithTag("routine_builder_error").assertExists()

        repository.failure = null
        rule.onNodeWithTag("routine_builder_error_action").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("routine_builder_name_field").assertTextContains("Pierna")
    }

    @Test
    fun loadingState_showsTheSkeleton() {
        rule.mainClock.autoAdvance = false
        rule.setContent { AppTheme { RoutineBuilderScreen(RoutineBuilderUiState.Loading, {}, {}) } }

        rule.onNodeWithTag("routine_builder_loading").assertExists()
    }

    @Test
    fun back_withUnsavedEdits_asksBeforeDiscarding() {
        val vm = show()
        vm.add("a")

        vm.onEvent(RoutineBuilderEvent.Back)
        rule.waitForIdle()
        rule.onNodeWithTag("routine_builder_discard").assertExists()

        rule.onNodeWithTag("routine_builder_discard_cancel").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("routine_builder_discard").assertDoesNotExist()
        assertEquals(0, closes)

        vm.onEvent(RoutineBuilderEvent.Back)
        rule.waitForIdle()
        rule.onNodeWithTag("routine_builder_discard_confirm").performClick()
        rule.waitForIdle()
        assertEquals(1, closes)
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun back_withoutEdits_closesWithoutAsking() {
        val vm = show()

        vm.onEvent(RoutineBuilderEvent.Back)
        rule.waitForIdle()

        rule.onNodeWithTag("routine_builder_discard").assertDoesNotExist()
        assertEquals(1, closes)
    }
}

private const val MORE_THAN_MAX_SETS = 12
