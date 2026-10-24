package io.github.lopezjavier.appfitness.feature.routines

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.test.FakeImageLoaderEngine
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseMediaUris
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.LocalExerciseMediaUris
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** US-29 "Planning sets in a routine": per-set rows, add and remove, the type badge and its picker. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class RoutineBuilderSetsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val exercises = FakeExerciseRepository(listOf(exerciseRow("a", "bench"), exerciseRow("b", "row")))
    private val library =
        FakeExerciseLibrary(
            mapOf("bench" to exerciseInfo("bench", "Press de banca"), "row" to exerciseInfo("row", "Remo con barra")),
        )
    private val repository = FakeRoutineRepository()
    private val connectivity = FakeConnectivityObserver()

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

    private fun viewModel(routineId: String? = null, state: SavedStateHandle = SavedStateHandle()) =
        RoutineBuilderViewModel(
            routineId,
            state,
            repository,
            LoadExerciseCatalog(exercises, library),
            connectivity,
            FakeTrainingPreferencesRepository(),
        )

    @Composable
    private fun Host(vm: RoutineBuilderViewModel) {
        val state by vm.uiState.collectAsState()
        AppTheme {
            CompositionLocalProvider(LocalExerciseMediaUris provides FakeExerciseMediaUris) {
                RoutineBuilderScreen(state, vm::onEvent, {}, {})
            }
        }
    }

    private fun show(routineId: String? = null, vararg exerciseIds: String): RoutineBuilderViewModel {
        val vm = viewModel(routineId)
        rule.setContent { Host(vm) }
        rule.waitForIdle()
        exerciseIds.forEach { vm.onEvent(RoutineBuilderEvent.AddExercise(it)) }
        rule.waitForIdle()
        return vm
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun badge(row: Int, set: Int) = rule.onNode(
        hasTestTag("set_type_badge") and hasAnyAncestor(hasTestTag("routine_builder_set_type_${row}_$set")),
        useUnmergedTree = true,
    )

    private fun pickType(row: Int, set: Int, optionTag: String) {
        tap("routine_builder_set_type_${row}_$set")
        tap(optionTag)
    }

    @Test
    fun eachPlannedSet_hasItsOwnRowWithWeightRepsAndBadge() {
        show(null, "a")

        for (set in 0..2) {
            rule.onNodeWithTag("routine_builder_set_row_0_$set").assertExists()
            rule.onNodeWithTag("routine_builder_set_weight_0_$set").assertExists()
            rule.onNodeWithTag("routine_builder_set_reps_0_$set").assertTextEquals("8")
            badge(0, set).assertTextEquals((set + 1).toString())
        }
    }

    @Test
    fun anExistingRoutine_reopensWithItsPerSetValues() {
        repository.routines +=
            Routine(
                "r1",
                "Torso",
                null,
                false,
                listOf(
                    RoutineExercise(
                        "a",
                        0,
                        null,
                        listOf(
                            PlannedSet(40.0, 10, SetType.WarmUp),
                            PlannedSet(80.0, 8),
                            PlannedSet(80.0, 8),
                            PlannedSet(80.0, 8),
                        ),
                        120,
                    ),
                ),
            )

        show("r1")

        badge(0, 0).assertTextEquals("C")
        rule.onNodeWithTag("routine_builder_set_weight_0_0").assertTextEquals("40")
        rule.onNodeWithTag("routine_builder_set_reps_0_0").assertTextEquals("10")
        rule.onNodeWithTag("routine_builder_set_weight_0_3").assertTextEquals("80")
        rule.onNodeWithTag("routine_builder_set_row_0_4").assertDoesNotExist()
    }

    @Test
    fun typingAWeightAndReps_isSavedForThatSetOnly() {
        val vm = show(null, "a")
        rule.onNodeWithTag("routine_builder_name_field").performTextReplacement("Torso")
        rule.onNodeWithTag("routine_builder_set_weight_0_1").performTextReplacement("82,5")
        rule.onNodeWithTag("routine_builder_set_reps_0_1").performTextReplacement("5")
        rule.waitForIdle()

        vm.onEvent(RoutineBuilderEvent.Save)
        rule.waitForIdle()

        assertEquals(
            listOf(PlannedSet(null, 8), PlannedSet(82.5, 5), PlannedSet(null, 8)),
            repository.saved.single().exercises.single().plannedSets,
        )
    }

    @Test
    fun anadirSerie_addsACopyOfTheLastSet_andIsDisabledAtTen() {
        show(null, "a")

        repeat(7) { tap("routine_builder_add_set_0") }

        rule.onNodeWithTag("routine_builder_set_row_0_9").assertExists()
        rule.onNodeWithTag("routine_builder_add_set_0").assertIsNotEnabled()
    }

    @Test
    fun theDeleteButton_removesThatSet_andIsDisabledOnTheLastOne() {
        show(null, "a")

        tap("routine_builder_set_delete_0_0")
        tap("routine_builder_set_delete_0_0")

        rule.onNodeWithTag("routine_builder_set_row_0_1").assertDoesNotExist()
        rule.onNodeWithTag("routine_builder_set_delete_0_0").assertIsNotEnabled()
        rule.onNodeWithTag("routine_builder_add_set_0").assertIsEnabled()
    }

    @Test
    fun choosingCalentamiento_showsTheBadgeC() {
        show(null, "a")

        pickType(0, 0, "set_type_picker_option_warm_up")

        badge(0, 0).assertTextEquals("C")
        badge(0, 1).assertTextEquals("2")
        rule.onNodeWithTag("set_type_picker").assertDoesNotExist()
    }

    @Test
    fun choosingFalloAndDrop_showTheLettersFAndD() {
        show(null, "a")

        pickType(0, 0, "set_type_picker_option_failure")
        pickType(0, 1, "set_type_picker_option_drop")

        badge(0, 0).assertTextEquals("F")
        badge(0, 1).assertTextEquals("D")
    }

    @Test
    fun tappingNormal_goesBackToNormal_andThePickerOffersNoClearOrDone() {
        show(null, "a")
        tap("routine_builder_set_type_0_0")
        rule.onNodeWithTag("set_type_picker_clear").assertDoesNotExist()
        rule.onNodeWithTag("set_type_picker_done").assertDoesNotExist()
        rule.onNodeWithTag("set_type_picker_option_working").assertIsSelected()
        tap("set_type_picker_option_warm_up")
        badge(0, 0).assertTextEquals("C")

        tap("routine_builder_set_type_0_0")
        rule.onNodeWithTag("set_type_picker_option_warm_up").assertIsSelected()
        tap("set_type_picker_option_working")

        badge(0, 0).assertTextEquals("1")
    }

    @Test
    fun closingThePickerWithoutChoosing_keepsTheType() {
        show(null, "a")
        tap("routine_builder_set_type_0_0")

        tap("set_type_picker_close")

        rule.onNodeWithTag("set_type_picker").assertDoesNotExist()
        badge(0, 0).assertTextEquals("1")
    }

    @Test
    fun inASuperset_addingASet_appearsOnEveryMember_withAHint() {
        show(null, "a", "b")
        tap("routine_builder_menu_0")
        tap("routine_builder_group_0")

        tap("routine_builder_add_set_1")

        rule.onNodeWithTag("routine_builder_set_row_0_3").assertExists()
        rule.onNodeWithTag("routine_builder_set_row_1_3").assertExists()
        rule.onNodeWithTag("routine_builder_superset_sets_hint_0").assertExists()
    }

    @Test
    fun aSetWithoutReps_disablesGuardar() {
        show(null, "a")
        rule.onNodeWithTag("routine_builder_name_field").performTextReplacement("Torso")
        rule.onNodeWithTag("routine_builder_save_button").assertIsEnabled()

        rule.onNodeWithTag("routine_builder_set_reps_0_0").performTextReplacement("")
        rule.waitForIdle()

        rule.onNodeWithTag("routine_builder_save_button").assertIsNotEnabled()
    }

    @Test
    fun theDraftWithSetEditsAndTheOpenPicker_survivesRecreation() {
        val saved = SavedStateHandle()
        val shown = mutableListOf<RoutineBuilderViewModel>()
        val tester = StateRestorationTester(rule)
        tester.setContent { Host(viewModel(null, saved).also { shown += it }) }
        rule.waitForIdle()
        val first = shown.last()
        first.onEvent(RoutineBuilderEvent.AddExercise("a"))
        first.onEvent(RoutineBuilderEvent.SetWeight(0, 0, "40"))
        first.onEvent(RoutineBuilderEvent.ChangeSetType(0, 0, SetType.WarmUp))
        first.onEvent(RoutineBuilderEvent.AddSet(0))
        first.onEvent(RoutineBuilderEvent.OpenTypePicker(0, 1))
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        badge(0, 0).assertTextEquals("C")
        rule.onNodeWithTag("routine_builder_set_weight_0_0").assertTextEquals("40")
        rule.onNodeWithTag("routine_builder_set_row_0_3").assertExists()
        rule.onNodeWithTag("set_type_picker").assertExists()
    }
}
