package io.github.lopezjavier.appfitness.feature.routines

import android.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.test.FakeImageLoaderEngine
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseMediaUris
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.LocalExerciseMediaUris
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val BARBELL_ROW = 0
private const val DUMBBELL_ROW = 1

/** US-33 / ADR-55: the calculators inside the routine builder. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class RoutineBuilderCalculatorsTest {
    @get:Rule
    val rule = createComposeRule()

    private val exercises = FakeExerciseRepository(
        listOf(
            exerciseRow("a", "row", equipment = Equipment.Barbell),
            exerciseRow("d", "curl", equipment = Equipment.Dumbbell),
            exerciseRow("m", "press", equipment = Equipment.Machine),
        ),
    )
    private val library = FakeExerciseLibrary(
        mapOf(
            "row" to exerciseInfo("row", "Remo con barra"),
            "curl" to exerciseInfo("curl", "Curl con mancuernas"),
            "press" to exerciseInfo("press", "Prensa"),
        ),
    )
    private val connectivity = FakeConnectivityObserver()
    private val preferences = FakeTrainingPreferencesRepository()

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

    private fun newViewModel(handle: SavedStateHandle = SavedStateHandle()) = RoutineBuilderViewModel(
        null,
        handle,
        FakeRoutineRepository(),
        LoadExerciseCatalog(exercises, library),
        connectivity,
        preferences,
    )

    private fun screen(vm: RoutineBuilderViewModel) {
        rule.setContent { BuilderContent(vm) }
        rule.waitForIdle()
    }

    @androidx.compose.runtime.Composable
    private fun BuilderContent(vm: RoutineBuilderViewModel) {
        AppTheme {
            CompositionLocalProvider(LocalExerciseMediaUris provides FakeExerciseMediaUris) {
                RoutineBuilderRoot(
                    routineId = null,
                    selectedExerciseId = null,
                    onConsumeSelection = {},
                    onAddExercise = {},
                    onOpenExercise = {},
                    onClose = {},
                    viewModel = vm,
                )
            }
        }
    }

    /** A builder with a barbell row (0) and a dumbbell row (1). */
    private fun showWithBarbellAndDumbbell(): RoutineBuilderViewModel {
        val vm = newViewModel()
        screen(vm)
        vm.onEvent(RoutineBuilderEvent.AddExercise("a"))
        vm.onEvent(RoutineBuilderEvent.AddExercise("d"))
        rule.waitForIdle()
        return vm
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun openMenu(row: Int) = tap("routine_builder_menu_$row")

    private fun openFromMenu(row: Int, entry: String) {
        openMenu(row)
        tap("routine_builder_${entry}_$row")
    }

    @Test
    fun menu_barbellRow_offersBothCalculators() {
        showWithBarbellAndDumbbell()

        openMenu(BARBELL_ROW)
        rule.onNodeWithTag("routine_builder_warm_up_calc_0").assertExists().assertIsEnabled()
        rule.onNodeWithTag("routine_builder_plate_calc_0").assertExists().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun menu_dumbbellRow_offersBothCalculators() {
        showWithBarbellAndDumbbell()

        openMenu(DUMBBELL_ROW)

        rule.onNodeWithTag("routine_builder_warm_up_calc_1").assertExists()
        rule.onNodeWithTag("routine_builder_plate_calc_1").assertExists()
    }

    @Test
    fun menu_machineRow_offersOnlyTheWarmUpCalculator() {
        val vm = newViewModel()
        screen(vm)
        vm.onEvent(RoutineBuilderEvent.AddExercise("m"))
        rule.waitForIdle()

        openMenu(0)

        rule.onNodeWithTag("routine_builder_warm_up_calc_0").assertExists()
        rule.onNodeWithTag("routine_builder_plate_calc_0").assertDoesNotExist()
    }

    @Test
    fun warmUp_barbellRow_100kgWith20kgBar_addsAWarmUpSetBeforeTheWorkingSets() {
        val vm = showWithBarbellAndDumbbell()
        vm.onEvent(RoutineBuilderEvent.SetWeight(BARBELL_ROW, 0, "100"))
        rule.waitForIdle()

        openFromMenu(BARBELL_ROW, "warm_up_calc")
        val tag = "routine_builder_0_warm_up_calc"
        rule.onNodeWithTag("${tag}_weight_field").assertTextContains("100")
        rule.onNodeWithTag("${tag}_row_0").assertTextContains("60 kg × 3")
        tap("${tag}_confirm")

        rule.onNodeWithTag("${tag}_sheet").assertDoesNotExist()
        rule.onNodeWithTag("routine_builder_set_weight_0_0").assertTextEquals("60")
        rule.onNodeWithTag("routine_builder_set_weight_0_1").assertTextEquals("100")
        rule.onNodeWithTag("routine_builder_set_weight_0_3").assertExists()
        rule.onNodeWithTag("routine_builder_set_weight_0_4").assertDoesNotExist()
    }

    @Test
    fun warmUp_withTenSets_disablesTheActionAndExplainsTheCap() {
        val vm = showWithBarbellAndDumbbell()
        vm.onEvent(RoutineBuilderEvent.SetWeight(BARBELL_ROW, 0, "100"))
        repeat(7) { vm.onEvent(RoutineBuilderEvent.AddSet(BARBELL_ROW)) }
        rule.waitForIdle()

        openFromMenu(BARBELL_ROW, "warm_up_calc")

        rule.onNodeWithTag("routine_builder_0_warm_up_calc_confirm").assertIsNotEnabled()
        rule.onNodeWithTag("routine_builder_0_warm_up_calc_cap_hint")
            .assertTextEquals("Con estas series superarías el máximo de 10 series")
    }

    @Test
    fun warmUp_inASupersetAtTheCap_namesTheCapForTheWholeGroup() {
        val vm = showWithBarbellAndDumbbell()
        vm.onEvent(RoutineBuilderEvent.GroupWithNext(BARBELL_ROW))
        vm.onEvent(RoutineBuilderEvent.SetWeight(BARBELL_ROW, 0, "100"))
        repeat(7) { vm.onEvent(RoutineBuilderEvent.AddSet(BARBELL_ROW)) }
        rule.waitForIdle()

        openFromMenu(BARBELL_ROW, "warm_up_calc")

        rule.onNodeWithTag("routine_builder_0_warm_up_calc_confirm").assertIsNotEnabled()
        rule.onNodeWithTag("routine_builder_0_warm_up_calc_cap_hint")
            .assertTextContains("superserie", substring = true)
    }

    @Test
    fun discLoader_barbellRow_25And15OnABar20_totals100kg() {
        showWithBarbellAndDumbbell()

        openFromMenu(BARBELL_ROW, "plate_calc")

        val tag = "routine_builder_0_disc_loader"
        rule.onNodeWithTag("${tag}_bar_label").assertTextEquals("Barra 20 kg")
        rule.onNodeWithTag("${tag}_total").assertTextEquals("Total: 20 kg")
        rule.onNodeWithTag("${tag}_weight_field").assertDoesNotExist()
        tap("${tag}_plate_25_increment")
        tap("${tag}_plate_15_increment")
        rule.onNodeWithTag("${tag}_total").assertTextEquals("Total: 100 kg")
        tap("${tag}_close")
        rule.onNodeWithTag("${tag}_sheet").assertDoesNotExist()
    }

    @Test
    fun discLoader_dumbbellRow_oneDisc10kgOnAOneKgHandle_totals21kg() {
        showWithBarbellAndDumbbell()

        openFromMenu(DUMBBELL_ROW, "plate_calc")
        val tag = "routine_builder_1_disc_loader"
        tap("${tag}_plate_10_increment")

        rule.onNodeWithTag("${tag}_total").assertTextEquals("Total: 21 kg")
        rule.onNodeWithTag("${tag}_handle_label").assertTextEquals("Mango: 1 kg")
        rule.onNodeWithTag("${tag}_bar_label").assertDoesNotExist()
    }

    @Test
    fun discLoader_dumbbellRow_usesTheSavedHandle() {
        preferences.result = Result.success(TrainingPreferences(dumbbellHandleKg = 2.5))
        showWithBarbellAndDumbbell()

        openFromMenu(DUMBBELL_ROW, "plate_calc")

        rule.onNodeWithTag("routine_builder_1_disc_loader_handle_label").assertTextEquals("Mango: 2,5 kg")
    }

    @Test
    fun menu_namesTheEntryCargadorDeDiscos() {
        showWithBarbellAndDumbbell()

        openMenu(BARBELL_ROW)

        rule.onNodeWithTag("routine_builder_plate_calc_0").assertTextContains("Cargador de discos")
    }

    @Test
    fun warmUp_dumbbellRow_hasNoBarSelector_andNeverGoesBelowTheHandle() {
        val vm = showWithBarbellAndDumbbell()
        vm.onEvent(RoutineBuilderEvent.SetWeight(DUMBBELL_ROW, 0, "20"))
        rule.waitForIdle()

        openFromMenu(DUMBBELL_ROW, "warm_up_calc")
        val tag = "routine_builder_1_warm_up_calc"
        repeat(2) { tap("warmup_sets_stepper_increment") }

        rule.onNodeWithTag("${tag}_bar").assertDoesNotExist()
        for (i in 0..2) {
            val text = rule.onNodeWithTag("${tag}_row_$i").fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription].single()
            assertTrue(text, !text.contains(": 0 kilos"))
        }
    }

    @Test
    fun discLoaderAndItsCounts_surviveLeavingTheApp() {
        val handle = SavedStateHandle()
        var current: RoutineBuilderViewModel? = null
        val tester = StateRestorationTester(rule)
        tester.setContent { BuilderContent(remember { newViewModel(handle).also { current = it } }) }
        rule.waitForIdle()
        current?.onEvent(RoutineBuilderEvent.AddExercise("a"))
        rule.waitForIdle()
        openFromMenu(BARBELL_ROW, "plate_calc")
        val tag = "routine_builder_0_disc_loader"
        repeat(2) { tap("${tag}_plate_25_increment") }
        tap("${tag}_plate_15_increment")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("${tag}_sheet").assertExists()
        rule.onNodeWithTag("${tag}_plate_25_value").assertTextEquals("2")
        rule.onNodeWithTag("${tag}_plate_15_value").assertTextEquals("1")
        rule.onNodeWithTag("${tag}_total").assertTextEquals("Total: 150 kg")
    }
}
