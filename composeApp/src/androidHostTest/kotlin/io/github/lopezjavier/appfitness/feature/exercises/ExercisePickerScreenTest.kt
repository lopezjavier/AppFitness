package io.github.lopezjavier.appfitness.feature.exercises

import android.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import io.github.lopezjavier.appfitness.designsystem.HapticEvent
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.RecordingAppHaptics
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
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
                    ExercisePickerScreen(state, vm::onEvent, selectMode = selectMode)
                }
            }
        }
        rule.waitForIdle()
        return vm
    }

    private val messages = RecordingMessages()
    private val haptics = RecordingAppHaptics()

    /** The stateful picker, with the shell's messages and haptics replaced by recorders. */
    private fun showRoot(selectMode: Boolean = false): ExercisePickerViewModel {
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
                CompositionLocalProvider(
                    LocalExerciseMediaUris provides FakeExerciseMediaUris,
                    LocalAppMessages provides messages,
                    LocalAppHaptics provides haptics,
                ) {
                    ExercisePickerRoot(
                        selectMode = selectMode,
                        onOpenDetail = { effects += ExercisePickerEffect.OpenDetail(it) },
                        onSelect = { effects += ExercisePickerEffect.Selected(it) },
                        viewModel = vm,
                    )
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
        rule.onNodeWithTag("exercise_create_sheet_commit").assertIsNotEnabled()
        rule.onNodeWithTag("exercise_create_muscle_chest").performClick()
        rule.onNodeWithTag("exercise_create_equipment_machine").performClick()
        rule.onNodeWithTag("exercise_create_sheet_commit").assertIsEnabled().performClick()
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
        rule.onNodeWithTag("exercise_create_sheet_commit").assertIsNotEnabled()
    }

    @Test
    fun createFailure_keepsTheSheetOpen_showsTheMessage_andRejects() {
        repository.createFailure = AppError.Network
        showRoot()
        rule.onNodeWithTag("exercise_picker_search").performTextInput("Curl raro")
        rule.onNodeWithTag("exercise_picker_create_button").performClick()
        rule.onNodeWithTag("exercise_create_muscle_chest").performClick()
        rule.onNodeWithTag("exercise_create_equipment_machine").performClick()
        rule.onNodeWithTag("exercise_create_sheet_commit").performClick()
        rule.waitUntil { messages.shown.isNotEmpty() }

        rule.onNodeWithTag("exercise_create_sheet").assertExists()
        assertEquals(listOf("No se pudo guardar. Inténtalo de nuevo."), messages.shown)
        haptics.assertSingle(HapticEvent.Reject)
    }

    @Test
    fun offline_disablesCreate_andExplainsWhy() {
        show()
        connectivity.state.value = false
        rule.waitForIdle()

        rule.onNodeWithTag("exercise_picker_create_button").assertIsNotEnabled()
        rule.onNodeWithTag(
            "exercise_picker_offline_hint",
        ).assertTextEquals("Sin conexión: no se puede añadir un ejercicio")
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

    private fun openCreateForm() {
        show()
        rule.onNodeWithTag("exercise_picker_search").performTextInput("Curl raro")
        rule.onNodeWithTag("exercise_picker_create_button").performClick()
        rule.waitForIdle()
    }

    @Test
    fun createForm_defaultsToWeightAndReps_andOffersEveryMetricTypeWithItsLabel() {
        openCreateForm()

        val expected = listOf(
            "weight_reps" to "Peso y repeticiones",
            "reps_only" to "Solo repeticiones",
            "duration" to "Duración",
            "weight_duration" to "Peso y duración",
            "distance_duration" to "Distancia y duración",
            "bodyweight" to "Peso corporal",
            "assisted" to "Asistido",
            "weighted" to "Con lastre",
            "steps_duration" to "Pasos y duración",
            "floors_duration" to "Pisos y duración",
        )
        rule.onNodeWithTag("create_exercise_metric_type_picker").performScrollTo().assertExists()
        expected.forEach { (value, label) ->
            rule.onNodeWithTag("create_exercise_metric_type_$value").performScrollTo().assertTextContains(label)
        }
        rule.onNodeWithTag("create_exercise_metric_type_weight_reps").assertIsSelected()
        rule.onNodeWithTag("create_exercise_metric_type_duration").assertIsNotSelected()
    }

    @Test
    fun createForm_showsAOneLineExplanationPerBodyweightAndAssisted() {
        openCreateForm()

        rule.onNodeWithTag("create_exercise_metric_type_bodyweight").performScrollTo()
            .assertTextContains("Repeticiones + carga extra opcional", substring = true)
        rule.onNodeWithTag("create_exercise_metric_type_assisted").performScrollTo()
            .assertTextContains("Repeticiones con peso de ayuda", substring = true)
    }

    @Test
    fun createForm_choosingDuracion_createsADurationExercise() {
        openCreateForm()

        rule.onNodeWithTag("exercise_create_muscle_chest").performClick()
        rule.onNodeWithTag("exercise_create_equipment_machine").performClick()
        rule.onNodeWithTag("create_exercise_metric_type_duration").performScrollTo().performClick()
        rule.onNodeWithTag("create_exercise_metric_type_duration").assertIsSelected()
        rule.onNodeWithTag("create_exercise_metric_type_weight_reps").assertIsNotSelected()
        rule.onNodeWithTag("exercise_create_sheet_commit").performClick()
        rule.waitForIdle()

        assertEquals(ExerciseMetricType.Duration, repository.lastCreatedMetricType)
    }

    @Test
    fun createForm_withoutTouchingTheMetricType_createsAWeightAndRepsExercise() {
        openCreateForm()

        rule.onNodeWithTag("exercise_create_muscle_chest").performClick()
        rule.onNodeWithTag("exercise_create_equipment_machine").performClick()
        rule.onNodeWithTag("exercise_create_sheet_commit").performClick()
        rule.waitForIdle()

        assertEquals(ExerciseMetricType.WeightReps, repository.lastCreatedMetricType)
    }

    @Test
    fun theTitleIsEjercicios_once_withABackArrow() {
        show()

        rule.onNodeWithTag("exercise_picker_title").assertTextEquals("Ejercicios")
        rule.onNodeWithTag("exercise_picker_back").assertExists()
        rule.onAllNodesWithText("Ejercicios").assertCountEquals(1)
    }

    @Test
    fun inSelectMode_tappingARow_selectsIt_andTheThumbnailOpensTheDetail() {
        show(selectMode = true)

        rule.onNodeWithTag("exercise_picker_row_bench_thumbnail")
            .assertContentDescriptionEquals("Ver detalle de Press de banca con barra")
            .performClick()
        rule.waitForIdle()
        assertEquals(listOf<ExercisePickerEffect>(ExercisePickerEffect.OpenDetail("bench")), effects)

        effects.clear()
        rule.onNodeWithText("Press inclinado con mancuernas").performClick()
        rule.waitForIdle()
        assertEquals(listOf<ExercisePickerEffect>(ExercisePickerEffect.Selected("incline")), effects)
    }

    @Test
    fun inBrowseMode_thereIsNoSeparateThumbnailTarget() {
        show()

        rule.onNodeWithTag("exercise_picker_row_bench_thumbnail").assertDoesNotExist()
    }

    @Test
    fun thumbnailTarget_isAtLeast48dp() {
        show(selectMode = true)

        val bounds = rule.onNodeWithTag("exercise_picker_row_bench_thumbnail").getUnclippedBoundsInRoot()
        assertTrue(bounds.width >= 48.dp && bounds.height >= 48.dp)
    }

    @Test
    fun addOwnExercise_isPinnedInTheFooter_belowTheList() {
        show()

        val footer = rule.onNodeWithTag("exercise_picker_footer").getUnclippedBoundsInRoot()
        val button = rule.onNodeWithTag("exercise_picker_create_button").assertTextEquals("Añadir ejercicio propio")
            .getUnclippedBoundsInRoot()
        val list = rule.onNodeWithTag("exercise_picker_list").getUnclippedBoundsInRoot()
        assertTrue(button.top >= footer.top && button.bottom <= footer.bottom)
        assertTrue(button.top >= list.bottom)
    }

    @Test
    fun addOwnExercise_doesNotScrollAway_whenTheListIsScrolledToItsEnd() {
        show()
        val before = rule.onNodeWithTag("exercise_picker_create_button").getUnclippedBoundsInRoot()

        rule.onNodeWithTag("exercise_picker_attribution").performScrollTo()
        rule.waitForIdle()

        val after = rule.onNodeWithTag("exercise_picker_create_button").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertEquals(before.top, after.top)
    }

    @Test
    fun attribution_endsTheList() {
        show()

        val lastRow = rule.onNodeWithTag("exercise_picker_row_mine").getUnclippedBoundsInRoot()
        val attribution = rule.onNodeWithTag("exercise_picker_attribution").getUnclippedBoundsInRoot()
        assertTrue(attribution.top >= lastRow.bottom)
    }

    @Test
    fun addOwnExercise_staysVisible_whenTheSearchFindsNothing() {
        show()
        rule.onNodeWithTag("exercise_picker_search").performTextInput("zzzz")
        rule.waitForIdle()

        rule.onNodeWithTag("exercise_picker_empty").assertExists()
        rule.onNodeWithTag("exercise_picker_create_button").assertIsDisplayed()
    }

    @Test
    fun theCreateSheet_usesTheEditSheetTemplate_withAnAddCommit() {
        openCreateForm()

        rule.onNodeWithTag("exercise_create_sheet_title").assertTextEquals("Nuevo ejercicio")
        rule.onNodeWithTag("exercise_create_sheet_commit").assertTextContains("Añadir")
        rule.onNodeWithTag("exercise_create_sheet_close").assertExists()
    }

    @Test
    fun theCreateSheet_closingItDiscardsTheDraft_withoutADialogOrACreate() {
        openCreateForm()
        rule.onNodeWithTag("exercise_create_muscle_chest").performClick()
        rule.onNodeWithTag("exercise_create_sheet_close").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("exercise_create_sheet").assertDoesNotExist()
        assertEquals(0, repository.createCalls)
    }

    @Test
    fun offline_theCreateSheetCannotBeCommitted_andExplainsWhy() {
        openCreateForm()
        rule.onNodeWithTag("exercise_create_muscle_chest").performClick()
        rule.onNodeWithTag("exercise_create_equipment_machine").performClick()
        connectivity.state.value = false
        rule.waitForIdle()

        rule.onNodeWithTag("exercise_create_sheet_commit").assertIsNotEnabled()
        rule.onNodeWithTag("exercise_create_sheet_commit_hint")
            .assertTextEquals("Sin conexión: no se puede añadir un ejercicio")
    }
}
