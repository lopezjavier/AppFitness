package io.github.lopezjavier.appfitness.app

import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.test.FakeImageLoaderEngine
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.AuthState
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.domain.usecase.SavePreferences
import io.github.lopezjavier.appfitness.domain.usecase.SearchExercises
import io.github.lopezjavier.appfitness.feature.exercises.ExercisePickerRoot
import io.github.lopezjavier.appfitness.feature.exercises.ExercisePickerViewModel
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseMediaUris
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.LocalExerciseMediaUris
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.profile.PreferencesRoute
import io.github.lopezjavier.appfitness.feature.profile.PreferencesViewModel
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.routines.RoutineBuilderEvent
import io.github.lopezjavier.appfitness.feature.routines.RoutineBuilderRoot
import io.github.lopezjavier.appfitness.feature.routines.RoutineBuilderViewModel
import io.github.lopezjavier.appfitness.feature.routines.RoutinesEvent
import io.github.lopezjavier.appfitness.feature.routines.RoutinesScreen
import io.github.lopezjavier.appfitness.feature.routines.RoutinesUiState
import io.github.lopezjavier.appfitness.feature.weight.FakeWeightRepository
import io.github.lopezjavier.appfitness.feature.workout.FakeWorkoutSessionRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import io.github.lopezjavier.appfitness.feature.workout.SessionDetailRoot
import io.github.lopezjavier.appfitness.feature.workout.SessionDetailViewModel
import io.github.lopezjavier.appfitness.feature.workout.completedSession
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.time.Instant

private const val ROUTINE_COUNT = 40

/**
 * T-050: unsaved drafts survive background and process death. Each test builds its ViewModel inside the composition
 * with `remember` and shares one [SavedStateHandle] (the stand-in for Android's saved-state registry), so
 * `emulateSavedInstanceStateRestore` yields a brand-new ViewModel that only knows what was saved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per restoration scenario.
class DraftRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val connectivity = FakeConnectivityObserver()
    private val exercises =
        FakeExerciseRepository(
            listOf(
                exerciseRow("a", "bench", MuscleGroup.Chest, Equipment.Barbell),
                exerciseRow("b", "row", MuscleGroup.Back, Equipment.Barbell),
            ),
        )
    private val library =
        FakeExerciseLibrary(
            mapOf(
                "bench" to exerciseInfo("bench", "Press de banca"),
                "row" to exerciseInfo("row", "Remo con barra"),
            ),
        )

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

    // -- routine builder ---------------------------------------------------------------------------------------

    @Test
    fun routineBuilder_keepsTheNameAndTheExercisesAfterRecreation_andStaysDirty() {
        val handle = SavedStateHandle()
        var pickerResult by mutableStateOf<String?>(null)
        var current: RoutineBuilderViewModel? = null
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme {
                CompositionLocalProvider(LocalExerciseMediaUris provides FakeExerciseMediaUris) {
                    val vm =
                        remember {
                            RoutineBuilderViewModel(
                                null,
                                handle,
                                FakeRoutineRepository(),
                                LoadExerciseCatalog(exercises, library),
                                connectivity,
                            ).also { current = it }
                        }
                    RoutineBuilderRoot(
                        routineId = null,
                        selectedExerciseId = pickerResult,
                        onConsumeSelection = { pickerResult = null },
                        onAddExercise = {},
                        onOpenExercise = {},
                        onClose = {},
                        viewModel = vm,
                    )
                }
            }
        }
        rule.onNodeWithTag("routine_builder_name_field").performTextInput("Torso fuerza")
        pickerResult = "a"
        rule.waitForIdle()
        val before = current

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        assert(current !== before) { "a new ViewModel must be created" }
        rule.onNodeWithTag("routine_builder_name_field").assertTextContains("Torso fuerza")
        rule.onNodeWithTag("routine_builder_row_0").assertContentDescriptionEquals("Press de banca")
        rule.onNodeWithTag("routine_builder_save_button").assertIsEnabled()
        current?.onEvent(RoutineBuilderEvent.Back)
        rule.waitForIdle()
        rule.onNodeWithTag("routine_builder_discard").assertExists()
    }

    private fun showBuilder(
        tester: StateRestorationTester,
        handle: SavedStateHandle,
        repo: FakeExerciseRepository,
        pickerResult: () -> String?,
        onOpenExercise: (String) -> Unit,
    ) {
        tester.setContent {
            AppTheme {
                CompositionLocalProvider(LocalExerciseMediaUris provides FakeExerciseMediaUris) {
                    val vm =
                        remember {
                            RoutineBuilderViewModel(
                                null,
                                handle,
                                FakeRoutineRepository(),
                                LoadExerciseCatalog(repo, library),
                                connectivity,
                            )
                        }
                    RoutineBuilderRoot(
                        routineId = null,
                        selectedExerciseId = pickerResult(),
                        onConsumeSelection = {},
                        onAddExercise = {},
                        onOpenExercise = onOpenExercise,
                        onClose = {},
                        viewModel = vm,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun routineBuilder_openingADetailAndComingBack_afterRecreation_keepsTheDraft() {
        var opened: String? = null
        var picked by mutableStateOf<String?>(null)
        val tester = StateRestorationTester(rule)
        showBuilder(tester, SavedStateHandle(), exercises, { picked }, { opened = it })
        rule.onNodeWithTag("routine_builder_name_field").performTextInput("Pierna")
        picked = "a"
        rule.waitForIdle()
        rule.onNodeWithTag("routine_builder_exercise_0_open").performClick()
        assertEquals("a", opened)

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("routine_builder_name_field").assertTextContains("Pierna")
        rule.onNodeWithTag("routine_builder_row_0").assertContentDescriptionEquals("Press de banca")
    }

    @Test
    fun routineBuilder_customExercise_resolvesItsNameAfterRecreation() {
        val custom = exerciseRow("c1", null, MuscleGroup.Core, Equipment.Bodyweight, "Curl nordico", custom = true)
        var picked by mutableStateOf<String?>(null)
        val tester = StateRestorationTester(rule)
        showBuilder(tester, SavedStateHandle(), FakeExerciseRepository(listOf(custom)), { picked }, {})
        picked = "c1"
        rule.waitForIdle()
        rule.onNodeWithTag("routine_builder_row_0").assertContentDescriptionEquals("Curl nordico")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("routine_builder_row_0").assertContentDescriptionEquals("Curl nordico")
    }

    // -- exercise picker and create-exercise form -----------------------------------------------------------------

    private fun showPicker(tester: StateRestorationTester, handle: SavedStateHandle) {
        tester.setContent {
            AppTheme {
                CompositionLocalProvider(LocalExerciseMediaUris provides FakeExerciseMediaUris) {
                    val vm =
                        remember {
                            ExercisePickerViewModel(
                                LoadExerciseCatalog(exercises, library),
                                SearchExercises(),
                                exercises,
                                connectivity,
                                true,
                                handle,
                            )
                        }
                    ExercisePickerRoot(selectMode = true, onOpenDetail = {}, onSelect = {}, viewModel = vm)
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun picker_keepsTheSearchTextAndTheFilterAfterRecreation() {
        val tester = StateRestorationTester(rule)
        showPicker(tester, SavedStateHandle())
        rule.onNodeWithTag("exercise_picker_search").performTextInput("Press")
        rule.onNodeWithTag("exercise_picker_muscle_chip_chest").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("exercise_picker_search").assertTextContains("Press")
        rule.onNodeWithTag("exercise_picker_row_a").assertIsDisplayed()
        rule.onNodeWithTag("exercise_picker_row_b").assertDoesNotExist()
    }

    @Test
    fun createExerciseForm_keepsTheTypedNameAndTheChosenGroupAfterRecreation() {
        val tester = StateRestorationTester(rule)
        showPicker(tester, SavedStateHandle())
        rule.onNodeWithTag("exercise_picker_search").performTextInput("Curl nordico")
        rule.onNodeWithTag("exercise_picker_create_button").performClick()
        rule.onNodeWithTag("exercise_create_muscle_chest").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("exercise_create_sheet").assertIsDisplayed()
        rule.onNodeWithTag("exercise_create_name").assertTextContains("Curl nordico")
        rule.onNodeWithTag("exercise_create_submit").assertIsNotEnabled()
        assertEquals(0, exercises.createCalls)
    }

    @Test
    fun createExerciseForm_keepsTheChosenMetricTypeAfterRecreation() {
        val tester = StateRestorationTester(rule)
        showPicker(tester, SavedStateHandle())
        rule.onNodeWithTag("exercise_picker_search").performTextInput("Plancha rara")
        rule.onNodeWithTag("exercise_picker_create_button").performClick()
        rule.onNodeWithTag("create_exercise_metric_type_distance_duration").performScrollTo().performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("create_exercise_metric_type_distance_duration").performScrollTo().assertIsSelected()
        rule.onNodeWithTag("create_exercise_metric_type_weight_reps").assertIsNotSelected()
    }

    // -- "Corregir serie" sheet -------------------------------------------------------------------------------------

    @Test
    fun correctSetSheet_keepsTheTypedValuesAfterRecreation_withoutSaving() {
        val handle = SavedStateHandle()
        val sessions = FakeWorkoutSessionRepository().apply { completed += completedSession("s1") }
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme {
                val vm =
                    remember {
                        SessionDetailViewModel(
                            "s1",
                            sessions,
                            exercises,
                            library,
                            connectivity,
                            MutableClock(),
                            FakeTrainingPreferencesRepository(),
                            FakeWeightRepository(),
                            handle,
                        )
                    }
                SessionDetailRoot(sessionId = "s1", onClose = {}, viewModel = vm)
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("session_detail_set_0_1").performClick()
        rule.onNodeWithTag("session_edit_weight").performTextClearance()
        rule.onNodeWithTag("session_edit_weight").performTextInput("92,5")
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("session_edit_weight").assertTextContains("92,5")
        assertEquals(0, sessions.upserts.size)
    }

    // -- Perfil edit form and picker sheets -----------------------------------------------------------------------

    private fun showPreferences(tester: StateRestorationTester, handle: SavedStateHandle) {
        val profile =
            FakeProfileRepository(Result.success(ProfileSummary("Ana", null, true))).apply {
                biometrics =
                    Result.success(
                        SavedBiometrics(Sex.Male, kotlinx.datetime.LocalDate(2000, 3, 1), 178, ActivityLevel.Moderate),
                    )
            }
        val weights =
            FakeBodyMeasurementRepository(
                BodyMeasurement("w1", Instant.parse("2026-10-10T08:00:00Z"), 80.0, MeasurementSource.Manual),
            )
        val goals = FakeNutritionGoalsRepository(NutritionGoals(GoalStrategy.Deficit, -400, 2000, MacroSplit.DEFAULT))
        tester.setContent {
            AppTheme {
                val vm =
                    remember {
                        PreferencesViewModel(
                            profile,
                            goals,
                            weights,
                            FakeTrainingPreferencesRepository(),
                            SavePreferences(profile),
                            connectivity,
                            FakeClock(),
                            TimeZone.UTC,
                            handle,
                        )
                    }
                PreferencesRoute(onOpenMeals = {}, viewModel = vm)
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun preferencesForm_keepsTheEditedNameAfterRecreation_andGuardarStaysEnabled() {
        val tester = StateRestorationTester(rule)
        showPreferences(tester, SavedStateHandle())
        rule.onNodeWithTag("preferences_name_field").performScrollTo().performTextClearance()
        rule.onNodeWithTag("preferences_name_field").performTextInput("Beatriz")
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("preferences_name_field").performScrollTo().assertTextContains("Beatriz")
        rule.onNodeWithTag("preferences_save").assertIsEnabled()
    }

    @Test
    fun preferencesPickerSheet_keepsTheOpenSheetAndTheChosenLevelAfterRecreation() {
        val tester = StateRestorationTester(rule)
        showPreferences(tester, SavedStateHandle())
        rule.onNodeWithTag("preferences_activity_row").performScrollTo().performClick()
        rule.onNodeWithTag("preferences_activity_light").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("preferences_sheet_activity").assertIsDisplayed()
        rule.onNodeWithTag("preferences_sheet_done").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("preferences_activity_row")
            .performScrollTo()
            .assertContentDescriptionEquals("Actividad, Ligero, editar")
    }

    // -- scroll position (audit of the LazyColumn lists) ---------------------------------------------------------

    @Test
    fun routinesList_keepsItsScrollPositionAfterRecreation() {
        val routines = (0 until ROUTINE_COUNT).map { RoutineSummary("r$it", "Rutina $it", 3) }
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme { RoutinesScreen(RoutinesUiState.Content(routines), { _: RoutinesEvent -> }) }
        }
        rule.onNodeWithTag("routines_list").performScrollToNode(hasTestTag("routines_row_r30"))
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("routines_row_r30").assertIsDisplayed()
        rule.onNodeWithTag("routines_title").assertDoesNotExist()
    }

    // -- process death at the root -----------------------------------------------------------------------------

    @Test
    fun processDeath_withAnExpiredSession_resolvesToWelcome_notToTheSavedTab() {
        val auth = FakeAuthRepository(AuthState.SignedIn("u1"))
        val profile = FakeProfileRepository(Result.success(ProfileSummary("Ana", null, true)))
        val tester = StateRestorationTester(rule)
        tester.setContent {
            val vm = remember { AppViewModel(connectivity, auth, profile) }
            val state by vm.uiState.collectAsState()
            App(rootRoute = state.rootRoute, welcome = { Box(Modifier.fillMaxSize().testTag("welcome_screen")) })
        }
        rule.waitForIdle()
        rule.onNodeWithTag("nav_tab_profile").performClick()
        rule.waitForIdle()

        auth.state.value = AuthState.SignedOut
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("welcome_screen").assertIsDisplayed()
        rule.onNodeWithTag("nav_bar").assertDoesNotExist()
    }
}
