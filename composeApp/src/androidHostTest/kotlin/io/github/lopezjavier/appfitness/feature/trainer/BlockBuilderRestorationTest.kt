package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.coach.builder.BuilderInputs
import io.github.lopezjavier.appfitness.domain.coach.builder.PickerEntry
import io.github.lopezjavier.appfitness.domain.coach.builder.SplitCatalog
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import io.github.lopezjavier.appfitness.domain.usecase.BuildBlockUseCase
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.domain.usecase.LoadFolderBlockUseCase
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.FakeFavoriteExercisesRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ADR-40: every step of "Desde cero" survives recreation (a new ViewModel on the same `SavedStateHandle`) and the
 * screen-local sheets survive a `StateRestorationTester` round trip. Nothing here bills: there is no AI call.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One step per test.
class BlockBuilderRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val blocks = FakeCoachBlockRepository()
    private val saved = SavedStateHandle()
    private val prefs = FakeTrainingPreferencesRepository()

    private val mainPattern = mapOf(
        MuscleGroup.Chest to MovementPattern.HorizontalPush,
        MuscleGroup.Back to MovementPattern.HorizontalPull,
        MuscleGroup.Shoulders to MovementPattern.VerticalPush,
        MuscleGroup.Quads to MovementPattern.Squat,
        MuscleGroup.Hamstrings to MovementPattern.Hinge,
        MuscleGroup.Glutes to MovementPattern.Hinge,
    )

    private fun rows(): List<Exercise> = SplitCatalog.ALL_GROUPS.flatMap { g ->
        val key = g.name.lowercase()
        listOf(exerciseRow("$key-main", "$key-main", g, Equipment.Barbell, pattern = mainPattern.getValue(g))) +
            (1..4).map {
                exerciseRow("$key-iso$it", "$key-iso$it", g, Equipment.Machine, pattern = MovementPattern.Isolation)
            }
    } + listOf("bike", "rower").map {
        exerciseRow(
            it,
            it,
            MuscleGroup.FullBody,
            metric = ExerciseMetricType.Duration,
            pattern = MovementPattern.Cardio,
        )
    }

    private fun newModel(handle: SavedStateHandle = saved): BlockBuilderViewModel {
        val all = rows()
        val repository = FakeExerciseRepository(all)
        return BlockBuilderViewModel(
            planId = "plan-1",
            builder = BuildBlockUseCase(blocks, LoadFolderBlockUseCase(FakeRoutineRepository(), repository, prefs)),
            catalog = LoadExerciseCatalog(
                repository,
                FakeExerciseLibrary(all.associate { it.key!! to exerciseInfo(it.key!!, "Nombre ${it.key}") }),
            ),
            favourites = FakeFavoriteExercisesRepository(),
            exercises = repository,
            preferences = prefs,
            plans = FakeCoachPlanRepository(),
            clock = MutableClock(),
            savedState = handle,
        )
    }

    init {
        prefs.result = Result.success(
            TrainingPreferences(intention = TrainingIntention.Muscle, daysPerWeek = 4, sessionMinutes = 180),
        )
        blocks.builderInputs = Result.success(BuilderInputs(emptyMap(), listOf("bike", "rower"), "bike"))
    }

    private var current by mutableStateOf(newModel())

    private fun showRoot() {
        rule.setContent {
            AppTheme {
                BlockBuilderRoot("plan-1", {}, {}, {}, viewModel = current, connectivity = FakeConnectivityObserver())
            }
        }
        rule.waitForIdle()
    }

    private fun recreate() {
        current = newModel()
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun toBuilder() {
        tap("block_split_continue")
        tap("block_types_continue")
    }

    @Test
    fun theSplitPick_survivesRecreation() {
        showRoot()
        tap("block_split_card_1")

        recreate()

        rule.onNodeWithTag("block_split_card_1").assertIsSelected()
    }

    @Test
    fun theTypeStep_withAChangedDay_survivesRecreation() {
        showRoot()
        tap("block_split_continue")
        tap("block_types_day_0")
        tap("block_type_option_Lower")

        recreate()

        rule.onNodeWithTag("block_types_day_0").assertTextContains("Pierna", substring = true)
    }

    @Test
    fun theOpenDaySheet_ofATorsoPiernaWeek_survivesRecreation_stillFilteredToTheFamily() {
        showRoot()
        tap("block_split_continue")
        tap("block_types_day_1")

        recreate()

        rule.onNodeWithTag("block_types_split_name").assertTextContains("Torso-Pierna")
        rule.onNodeWithTag("block_type_option_Upper").assertExists()
        rule.onNodeWithTag("block_type_option_Push").assertDoesNotExist()
        rule.onNodeWithTag("block_type_option_FullBody").assertDoesNotExist()
    }

    @Test
    fun theBuilder_withAPartialPick_survivesRecreation() {
        showRoot()
        toBuilder()
        tap("block_builder_slot_0_0")
        tap("block_builder_pick_chest-iso1")

        recreate()

        rule.onNodeWithTag("block_builder_slot_0_0").assertTextContains("Nombre chest-iso1", substring = true)
        rule.onNodeWithTag("block_builder_score").assertTextContains("Rutina completa al ", substring = true)
    }

    @Test
    fun theOpenSlotPicker_survivesRecreation() {
        showRoot()
        toBuilder()
        tap("block_builder_slot_0_0")

        recreate()

        rule.onNodeWithText("Elegir ejercicio").assertExists()
        rule.onNodeWithTag("block_builder_pick_chest-iso1").assertExists()
    }

    @Test
    fun theSelectedDayTab_survivesRecreation() {
        showRoot()
        toBuilder()
        tap("block_builder_day_tab_1")

        recreate()

        rule.onNodeWithTag("block_builder_day_title_1").assertTextContains("Pierna", substring = true)
    }

    @Test
    fun theDiscardDialog_survivesRecreation() {
        showRoot()
        toBuilder()
        tap("block_builder_slot_0_0")
        tap("block_builder_pick_chest-iso1")
        tap("block_builder_back")
        rule.onNodeWithText("¿Descartar los cambios?").assertExists()

        recreate()

        rule.onNodeWithText("¿Descartar los cambios?").assertExists()
    }

    @Test
    fun recreating_neverAsksAnythingThatBills() {
        showRoot()
        toBuilder()

        recreate()
    }

    @Test
    fun theCardioSheetAndThePickerSearch_surviveStateRestoration() {
        val tester = StateRestorationTester(rule)
        val entries = listOf(PickerEntry(BlockBuilderTestData.bench), PickerEntry(BlockBuilderTestData.fly))
        val state = BlockBuilderTestData.builderUi(openSlot = SlotRef(0, 0), pickerEntries = entries)
        tester.setContent {
            AppTheme { BlockBuilderScreen(state, FakeConnectivityObserver().isOnline, {}, {}) }
        }
        rule.onNodeWithTag("block_builder_picker_search").performTextInput("aper")

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag("block_builder_pick_bench").assertDoesNotExist()
        rule.onNodeWithTag("block_builder_pick_fly").assertExists()
    }

    @Test
    fun theExtraPickerMuscleFilter_survivesStateRestoration() {
        val tester = StateRestorationTester(rule)
        val entries = listOf(PickerEntry(BlockBuilderTestData.bench), PickerEntry(BlockBuilderTestData.row))
        val state = BlockBuilderTestData.builderUi(openSlot = SlotRef(0, 0, extra = true), pickerEntries = entries)
        tester.setContent {
            AppTheme { BlockBuilderScreen(state, FakeConnectivityObserver().isOnline, {}, {}) }
        }
        rule.onNodeWithTag("block_builder_picker_muscle_chip_back").performClick()

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag("block_builder_pick_bench").assertDoesNotExist()
        rule.onNodeWithTag("block_builder_pick_row").assertExists()
        rule.onNodeWithTag("block_builder_picker_muscle_chip_back").assertIsSelected()
    }

    @Test
    fun theCardioSwapSheet_survivesStateRestoration() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme {
                BlockBuilderScreen(BlockBuilderTestData.builderUi(), FakeConnectivityObserver().isOnline, {}, {})
            }
        }
        rule.onNodeWithTag("block_builder_cardio_swap_0").performClick()

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag("block_builder_cardio_option_rower").assertExists()
    }
}
