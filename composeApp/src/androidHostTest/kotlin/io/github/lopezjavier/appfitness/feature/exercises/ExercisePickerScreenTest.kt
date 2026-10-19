package io.github.lopezjavier.appfitness.feature.exercises

import android.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
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

/** US-09 on the picker: search, filters, rows with thumbnail, attribution and creating a custom exercise. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class ExercisePickerScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val connectivity = FakeConnectivityObserver()
    private val effects = mutableListOf<ExercisePickerEffect>()
    private val repository =
        FakeExerciseRepository(
            listOf(
                exerciseRow("bench", "bench", MuscleGroup.Chest, Equipment.Barbell),
                exerciseRow("incline", "incline", MuscleGroup.Chest, Equipment.Dumbbell),
                exerciseRow("pullup", "pullup", MuscleGroup.Back, Equipment.Bodyweight),
                exerciseRow("row", "row", MuscleGroup.Back, Equipment.Dumbbell),
                exerciseRow("mine", null, MuscleGroup.Core, Equipment.Other, name = "Plancha propia", custom = true),
            ),
        )
    private val library =
        FakeExerciseLibrary(
            mapOf(
                "bench" to exerciseInfo("bench", "Press de banca con barra"),
                "incline" to exerciseInfo("incline", "Press inclinado con mancuernas"),
                "pullup" to exerciseInfo("pullup", "Dominádas"),
                "row" to exerciseInfo("row", "Remo con mancuerna"),
            ),
        )

    @Before
    fun installFakeImageLoader() {
        installLoader(ColorImage(Color.BLUE))
    }

    @After
    fun resetImageLoader() {
        SingletonImageLoader.reset()
    }

    private fun installLoader(image: ColorImage?) {
        val builder = FakeImageLoaderEngine.Builder()
        if (image != null) builder.default(image)
        val engine = builder.build()
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(RuntimeEnvironment.getApplication()).components { add(engine) }.build(),
        )
    }

    private fun show(selectMode: Boolean = false): ExercisePickerViewModel {
        val vm =
            ExercisePickerViewModel(
                LoadExerciseCatalog(repository, library),
                SearchExercises(),
                repository,
                connectivity,
                selectMode,
            )
        rule.setContent {
            AppTheme {
                val state by vm.uiState.collectAsState()
                LaunchedEffect(vm) { vm.effects.collect { effects += it } }
                CompositionLocalProvider(LocalExerciseMediaUris provides FakeExerciseMediaUris) {
                    ExercisePickerScreen(state, vm::onEvent)
                }
            }
        }
        rule.waitForIdle()
        return vm
    }

    private fun rowExists(id: String) = runCatching {
        rule.onNodeWithTag("exercise_picker_row_$id").assertExists()
    }.isSuccess

    private fun visibleRows() = listOf("bench", "incline", "pullup", "row", "mine").filter(::rowExists)

    @Test
    fun searchAndMuscleFilter_showBothChestPresses_andHideTheBackExercise() {
        show()
        rule.onNodeWithTag("exercise_picker_search").performTextInput("press")
        rule.onNodeWithTag("exercise_picker_muscle_chip_chest").performClick()
        rule.waitForIdle()

        assertEquals(listOf("bench", "incline"), visibleRows())
        rule.onNodeWithTag("exercise_picker_muscle_chip_chest").assertIsSelected()
        rule.onNodeWithTag("exercise_picker_muscle_chip_all").assertIsNotSelected()
    }

    @Test
    fun equipmentFilter_showsOnlyDumbbellExercises() {
        show()
        rule.onNodeWithTag("exercise_picker_equipment_chip_dumbbell").performClick()
        rule.waitForIdle()

        assertEquals(listOf("incline", "row"), visibleRows())
    }

    @Test
    fun todosChip_clearsTheFilter() {
        show()
        rule.onNodeWithTag("exercise_picker_equipment_chip_dumbbell").performClick()
        rule.onNodeWithTag("exercise_picker_equipment_chip_all").performClick()
        rule.waitForIdle()

        assertEquals(5, visibleRows().size)
        rule.onNodeWithTag("exercise_picker_equipment_chip_all").assertIsSelected()
    }

    @Test
    fun search_ignoresAccentsAndCase() {
        show()
        val results = mutableListOf<List<String>>()
        listOf("dominadas", "Dominádas", "DOMINADAS").forEach { query ->
            rule.onNodeWithTag("exercise_picker_search").performTextClearance()
            rule.onNodeWithTag("exercise_picker_search").performTextInput(query)
            rule.waitForIdle()
            results += visibleRows()
        }

        assertEquals(listOf(listOf("pullup"), listOf("pullup"), listOf("pullup")), results)
    }

    @Test
    fun catalogRow_showsItsThumbnail_andACustomRowShowsThePlaceholder() {
        show()

        rule.onNodeWithTag("exercise_picker_row_bench_thumbnail_image", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("exercise_picker_row_mine_thumbnail_placeholder", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("exercise_picker_row_mine_thumbnail_image", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun brokenImage_degradesToThePlaceholder_withoutCrashing() {
        installLoader(null)
        show()

        rule.onNodeWithTag("exercise_picker_row_bench_thumbnail_placeholder", useUnmergedTree = true).assertExists()
    }

    @Test
    fun rowsShowNameAndMuscleEquipmentSubtitle() {
        show()

        rule.onNodeWithTag("exercise_picker_row_bench").assertTextContains("Press de banca con barra")
        rule.onNodeWithTag("exercise_picker_row_bench").assertTextContains("Pecho · Barra")
    }

    @Test
    fun attribution_isVisibleUnderTheList() {
        show()

        rule.onNodeWithTag("exercise_picker_attribution")
            .assertTextEquals("Datos de ejercicios por RepDB (repdb.co)")
    }

    @Test
    fun noResults_offersToCreate_andCreatingInSelectModeReturnsTheExercise() {
        show(selectMode = true)
        rule.onNodeWithTag("exercise_picker_search").performTextInput("Press Hammer unilateral")
        rule.waitForIdle()
        rule.onNodeWithTag("exercise_picker_empty").assertExists()

        rule.onNodeWithTag("exercise_picker_create_button").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("exercise_create_sheet").assertExists()
        rule.onNodeWithTag("exercise_create_submit").assertIsNotEnabled()
        rule.onNodeWithTag("exercise_create_muscle_chest").performClick()
        rule.onNodeWithTag("exercise_create_equipment_machine").performClick()
        rule.onNodeWithTag("exercise_create_submit").assertIsEnabled().performClick()
        rule.waitForIdle()

        assertEquals(listOf<ExercisePickerEffect>(ExercisePickerEffect.Selected("custom-1")), effects)
        rule.onNodeWithTag("exercise_create_sheet").assertDoesNotExist()
        assertEquals(Triple("Press Hammer unilateral", MuscleGroup.Chest, Equipment.Machine), repository.lastCreated)
    }

    @Test
    fun createSheet_nameIsPrefilledWithTheQuery_andNothingIsPreselected() {
        show()
        rule.onNodeWithTag("exercise_picker_search").performTextInput("Curl raro")
        rule.onNodeWithTag("exercise_picker_create_button").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("exercise_create_name").assertTextContains("Curl raro")
        rule.onNodeWithTag("exercise_create_muscle_chest").assertIsNotSelected()
        rule.onNodeWithTag("exercise_create_equipment_machine").assertIsNotSelected()
        rule.onNodeWithTag("exercise_create_submit").assertIsNotEnabled()
    }

    @Test
    fun createFailure_keepsTheSheetOpenWithAnError() {
        repository.createFailure = AppError.Network
        show()
        rule.onNodeWithTag("exercise_picker_search").performTextInput("Curl raro")
        rule.onNodeWithTag("exercise_picker_create_button").performClick()
        rule.onNodeWithTag("exercise_create_muscle_chest").performClick()
        rule.onNodeWithTag("exercise_create_equipment_machine").performClick()
        rule.onNodeWithTag("exercise_create_submit").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("exercise_create_sheet").assertExists()
        rule.onNodeWithTag("exercise_create_error").assertExists()
    }

    @Test
    fun offline_disablesCreate_andExplainsWhy() {
        show()
        connectivity.state.value = false
        rule.waitForIdle()

        rule.onNodeWithTag("exercise_picker_create_button").assertIsNotEnabled()
        rule.onNodeWithTag("exercise_picker_offline_hint").assertTextEquals("Sin conexión: no puedes crear ejercicios")
        rule.onNodeWithTag("exercise_picker_row_bench").assertExists()
    }

    @Test
    fun clearButton_emptiesTheSearch() {
        show()
        rule.onNodeWithTag("exercise_picker_search_clear").assertDoesNotExist()
        rule.onNodeWithTag("exercise_picker_search").performTextInput("press")
        rule.waitForIdle()
        rule.onNodeWithTag("exercise_picker_search_clear").performClick()
        rule.waitForIdle()

        assertEquals(5, visibleRows().size)
    }

    @Test
    fun loadFailure_showsRetry_andRetryReloads() {
        repository.failure = AppError.Network
        show()
        rule.onNodeWithTag("exercise_picker_error_action").assertExists()

        repository.failure = null
        rule.onNodeWithTag("exercise_picker_error_action").performClick()
        rule.waitForIdle()

        assertEquals(5, visibleRows().size)
    }

    @Test
    fun emptyCatalog_showsTheEmptyState() {
        repository.rows = emptyList()
        show()

        rule.onNodeWithTag("exercise_picker_empty").assertExists()
        rule.onNodeWithTag("exercise_picker_create_button").assertExists()
    }

    @Test
    fun tappingARow_inBrowseMode_opensItsDetail() {
        show()
        rule.onNodeWithTag("exercise_picker_row_bench").performClick()
        rule.waitForIdle()

        assertEquals(listOf<ExercisePickerEffect>(ExercisePickerEffect.OpenDetail("bench")), effects)
    }

    @Test
    fun touchTargets_areAtLeast48dp() {
        show()

        rule.onNodeWithTag("exercise_picker_row_bench").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("exercise_picker_muscle_chip_chest").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("exercise_picker_equipment_chip_dumbbell").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("exercise_picker_create_button").assertHeightIsAtLeast(48.dp)
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun tablet_listsTwoColumns() {
        show()

        val first = rule.onNodeWithTag("exercise_picker_row_bench").getUnclippedBoundsInRoot()
        val second = rule.onNodeWithTag("exercise_picker_row_incline").getUnclippedBoundsInRoot()
        assertEquals(first.top, second.top)
        assertTrue(second.left > first.left)
    }
}
