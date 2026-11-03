package io.github.lopezjavier.appfitness.feature.exercises

import android.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
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
import io.github.lopezjavier.appfitness.domain.model.ExerciseAlternative
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.domain.usecase.SearchExercises
import io.github.lopezjavier.appfitness.feature.workout.FakeWorkoutSessionRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** US-96 (T-204): the star in the exercise detail and the favourite marker and order in the picker. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class ExerciseFavoritesScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeExerciseRepository(
        listOf(
            exerciseRow("lateral", "lateral", MuscleGroup.Shoulders, Equipment.Dumbbell),
            exerciseRow("press", "press", MuscleGroup.Shoulders, Equipment.Barbell),
            exerciseRow("bench", "bench", MuscleGroup.Chest, Equipment.Barbell),
            exerciseRow("mine", null, MuscleGroup.Chest, Equipment.Machine, "Press Hammer", custom = true),
        ),
    )
    private val library = FakeExerciseLibrary(
        mapOf(
            "lateral" to exerciseInfo("lateral", "Elevaciones laterales"),
            "press" to exerciseInfo("press", "Press militar"),
            "bench" to exerciseInfo("bench", "Press de banca"),
        ),
    )
    private val favorites = FakeFavoriteExercisesRepository()
    private val connectivity = FakeConnectivityObserver()
    private val messages = RecordingMessages()
    private val haptics = RecordingAppHaptics()

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

    private fun detailModel(id: String, handle: SavedStateHandle = SavedStateHandle()) = ExerciseDetailViewModel(
        id,
        LoadExerciseCatalog(repository, library),
        repository,
        FakeWorkoutSessionRepository(),
        favorites,
        connectivity,
        handle,
    )

    private fun showDetail(id: String, viewModel: ExerciseDetailViewModel = detailModel(id)) {
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(
                    LocalExerciseMediaUris provides FakeExerciseMediaUris,
                    LocalAppMessages provides messages,
                    LocalAppHaptics provides haptics,
                ) {
                    ExerciseDetailRoot(
                        exerciseId = id,
                        selectMode = false,
                        onSelect = {},
                        viewModel = viewModel,
                        clock = MutableClock(),
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun pickerModel(handle: SavedStateHandle = SavedStateHandle()) = ExercisePickerViewModel(
        LoadExerciseCatalog(repository, library),
        SearchExercises(),
        repository,
        favorites,
        connectivity,
        false,
        handle,
    )

    private fun showPicker(viewModel: ExercisePickerViewModel = pickerModel()) {
        rule.setContent {
            AppTheme {
                val state by viewModel.uiState.collectAsState()
                LaunchedEffect(viewModel) { viewModel.effects.collect { } }
                CompositionLocalProvider(LocalExerciseMediaUris provides FakeExerciseMediaUris) {
                    ExercisePickerScreen(state, viewModel::onEvent)
                }
            }
        }
        rule.waitForIdle()
    }

    private fun top(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot().top

    @Test
    fun theStar_isShownForACatalogueExercise_andTappingItTwiceStarsThenUnstars() {
        showDetail("lateral")

        rule.onNodeWithTag("exercise_detail_favorite")
            .assertContentDescriptionEquals("Añadir a favoritos")
            .assertIsOff()
        rule.onNodeWithTag("exercise_detail_favorite").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("exercise_detail_favorite")
            .assertContentDescriptionEquals("Quitar de favoritos")
            .assertIsOn()
            .assert(stateDescriptionIs("Favorito"))
        assertEquals(setOf("lateral"), favorites.favoriteIds.value)
        rule.onNodeWithTag("exercise_detail_favorite").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("exercise_detail_favorite")
            .assertContentDescriptionEquals("Añadir a favoritos")
            .assert(stateDescriptionIs("No es favorito"))
        assertEquals(emptySet<String>(), favorites.favoriteIds.value)
        assertEquals(listOf(HapticEvent.Confirm, HapticEvent.Confirm), haptics.calls)
    }

    @Test
    fun theStar_isShownForMyOwnExercise_besideEdit_whileDeleteGoesToTheOverflow() {
        showDetail("mine")

        rule.onNodeWithTag("exercise_detail_favorite").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("exercise_detail_favorite").assertIsOn()
        rule.onNodeWithTag("exercise_detail_edit").assertExists()
        rule.onNodeWithTag("exercise_detail_more").assertExists()
        assertEquals(setOf("mine"), favorites.favoriteIds.value)
    }

    @Test
    fun aFailedToggle_keepsTheStar_showsTheMessage_andRejects() {
        favorites.failure = AppError.Network
        showDetail("lateral")

        rule.onNodeWithTag("exercise_detail_favorite").performClick()
        rule.waitUntil { messages.shown.isNotEmpty() }

        assertEquals(listOf("No se pudo actualizar el favorito. Inténtalo de nuevo."), messages.shown)
        rule.onNodeWithTag("exercise_detail_favorite").assertIsOff()
        assertEquals(listOf(HapticEvent.Reject), haptics.calls)
    }

    @Test
    fun offline_theStarIsDisabled() {
        connectivity.state.value = false
        showDetail("lateral")

        rule.onNodeWithTag("exercise_detail_favorite").assertIsNotEnabled()
            .assertContentDescriptionEquals("Añadir a favoritos")
        rule.onNodeWithTag("exercise_detail_favorite").performClick()
        assertEquals(emptyList<Pair<String, Boolean>>(), favorites.setCalls)
    }

    @Test
    fun theSimilarTab_marksAFavouriteAlternative_withoutReordering() {
        repository.alternativesById = mapOf(
            "lateral" to listOf(
                ExerciseAlternative(repository.rows[1], 0.9),
                ExerciseAlternative(repository.rows[2], 0.8),
            ),
        )
        favorites.set(setOf("bench"))
        showDetail("lateral")

        rule.onNodeWithTag("exercise_detail_tab_similar").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("exercise_similar_favorite_bench", useUnmergedTree = true)
            .assertContentDescriptionEquals("Favorito")
        rule.onNodeWithTag("exercise_similar_favorite_press", useUnmergedTree = true).assertDoesNotExist()
        assert(top("exercise_detail_similar_row_press") < top("exercise_detail_similar_row_bench"))
    }

    @Test
    fun picker_listsFavouritesFirstUnderAFilter_withTheMarkerAndFavoritoInTheRowDescription() {
        favorites.set(setOf("lateral"))
        showPicker()
        rule.onNodeWithTag("exercise_picker_muscle_chip_shoulders").performClick()
        rule.waitForIdle()

        assert(top("exercise_picker_row_lateral") < top("exercise_picker_row_press"))
        rule.onNodeWithTag("exercise_picker_favorite_lateral", useUnmergedTree = true)
            .assertContentDescriptionEquals("Favorito")
        rule.onNodeWithTag("exercise_picker_favorite_press", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("exercise_picker_row_lateral").assert(hasContentDescription("Favorito"))
    }

    @Test
    fun picker_keepsFavouritesFirstWhenSearching_andHidesAFavouriteOutsideTheFilter() {
        favorites.set(setOf("bench", "mine"))
        showPicker()
        rule.onNodeWithTag("exercise_picker_search").performTextInput("press")
        rule.waitForIdle()

        assert(top("exercise_picker_row_mine") < top("exercise_picker_row_press"))
        assert(top("exercise_picker_row_bench") < top("exercise_picker_row_press"))
        rule.onNodeWithTag("exercise_picker_row_lateral").assertDoesNotExist()
    }

    @Test
    fun picker_reSortsWhenTheSetChanges_asAfterStarringInTheDetail() {
        showPicker()
        assert(top("exercise_picker_row_lateral") < top("exercise_picker_row_bench"))

        favorites.set(setOf("bench"))
        rule.waitForIdle()

        assert(top("exercise_picker_row_bench") < top("exercise_picker_row_lateral"))
    }

    @Test
    fun detail_starSurvivesARecreatedModelAndAScreenRestore() {
        favorites.set(setOf("lateral"))
        val handle = SavedStateHandle()
        var model by mutableStateOf(detailModel("lateral", handle))
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme {
                CompositionLocalProvider(
                    LocalExerciseMediaUris provides FakeExerciseMediaUris,
                    LocalAppMessages provides messages,
                    LocalAppHaptics provides haptics,
                ) {
                    ExerciseDetailRoot(
                        exerciseId = "lateral",
                        selectMode = false,
                        onSelect = {},
                        viewModel = model,
                        clock = MutableClock(),
                    )
                }
            }
        }
        rule.onNodeWithTag("exercise_detail_tab_history").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        model = detailModel("lateral", handle)
        rule.waitForIdle()

        rule.onNodeWithTag("exercise_detail_favorite").assertIsOn()
        rule.onNodeWithTag("exercise_detail_tab_history").assertIsSelected()
    }

    @Test
    fun picker_queryFiltersAndFavouriteOrderSurviveARecreatedModel() {
        favorites.set(setOf("lateral"))
        val handle = SavedStateHandle()
        var model by mutableStateOf(pickerModel(handle))
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme {
                val state by model.uiState.collectAsState()
                LaunchedEffect(model) { model.effects.collect { } }
                CompositionLocalProvider(LocalExerciseMediaUris provides FakeExerciseMediaUris) {
                    ExercisePickerScreen(state, model::onEvent)
                }
            }
        }
        rule.onNodeWithTag("exercise_picker_muscle_chip_shoulders").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        model = pickerModel(handle)
        rule.waitForIdle()

        rule.onNodeWithTag("exercise_picker_row_bench").assertDoesNotExist()
        assert(top("exercise_picker_row_lateral") < top("exercise_picker_row_press"))
    }

    private fun stateDescriptionIs(expected: String) = SemanticsMatcher("stateDescription is $expected") {
        (SemanticsProperties.StateDescription in it.config) &&
            it.config[SemanticsProperties.StateDescription] == expected
    }
}
