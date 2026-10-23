package io.github.lopezjavier.appfitness.feature.exercises

import android.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.test.FakeImageLoaderEngine
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ExerciseLayoutDefaults
import io.github.lopezjavier.appfitness.designsystem.LocalAnimationsEnabled
import io.github.lopezjavier.appfitness.domain.model.ExerciseDetail
import io.github.lopezjavier.appfitness.domain.model.PersonalRecord
import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
import kotlinx.datetime.LocalDate
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

/** US-09 / US-28 exercise detail: tabs, animation, records, "Cómo hacerlo", attribution and the select action. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class ExerciseDetailScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate(2026, 10, 23)
    private val bench = exerciseRow("1", "bench")
    private val withBothPoses = ExerciseDetail(bench, "Press de banca", exerciseInfo("bench", "Press de banca"))

    private fun loaded(
        detail: ExerciseDetail = withBothPoses,
        tab: ExerciseDetailTab = ExerciseDetailTab.Summary,
        records: RecordsState = RecordsState.Empty,
        history: HistoryState = HistoryState.Empty,
    ) = ExerciseDetailUiState.Loaded(detail, tab, records, history)

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

    private fun show(
        state: ExerciseDetailUiState,
        selectMode: Boolean = false,
        animations: Boolean = true,
        fontScale: Float = 1f,
        onSelect: () -> Unit = {},
        onRetry: () -> Unit = {},
        onOpenProgress: () -> Unit = {},
        actions: ExerciseDetailActions = ExerciseDetailActions(),
    ) {
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(
                    LocalAnimationsEnabled provides animations,
                    LocalExerciseMediaUris provides FakeExerciseMediaUris,
                    LocalDensity provides Density(LocalDensity.current.density, fontScale),
                ) {
                    ExerciseDetailScreen(
                        state,
                        selectMode,
                        onSelect,
                        onRetry,
                        today,
                        onOpenProgress = onOpenProgress,
                        actions = actions,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun threeTabsAreShown_withSummarySelected() {
        show(loaded())

        rule.onNodeWithTag("exercise_detail_tab_summary").assertIsSelected().assertTextContains("Resumen")
        rule.onNodeWithTag("exercise_detail_tab_history").assertTextContains("Historial")
        rule.onNodeWithTag("exercise_detail_tab_how_to").assertTextContains("Cómo hacerlo")
    }

    @Test
    fun tappingATab_asksForIt() {
        val asked = mutableListOf<ExerciseDetailTab>()
        show(loaded(), actions = ExerciseDetailActions(onSelectTab = { asked += it }))

        rule.onNodeWithTag("exercise_detail_tab_history").performClick()
        rule.onNodeWithTag("exercise_detail_tab_how_to").performClick()

        assertEquals(listOf(ExerciseDetailTab.History, ExerciseDetailTab.HowTo), asked)
    }

    @Test
    fun summary_showsTheAnimation_andHidesTheOtherTabsContent() {
        show(loaded())

        rule.onNodeWithTag("exercise_detail_animation").assertContentDescriptionEquals("Demostración de Press de banca")
        rule.onNodeWithTag("exercise_detail_start", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("exercise_detail_peak", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("exercise_detail_how_to").assertDoesNotExist()
        rule.onNodeWithTag("exercise_history_list").assertDoesNotExist()
    }

    @Test
    fun howTo_showsTheSpanishStepsAndTips() {
        show(loaded(tab = ExerciseDetailTab.HowTo))

        rule.onNodeWithTag("exercise_detail_tab_how_to").assertIsSelected()
        rule.onNodeWithText("1. Primer paso").assertExists()
        rule.onNodeWithText("2. Segundo paso").assertExists()
        rule.onNodeWithText("Un consejo").assertExists()
        rule.onNodeWithTag("exercise_detail_animation").assertDoesNotExist()
    }

    @Test
    fun progressLink_opensTheProgressOfTheExercise() {
        var opened = 0
        show(loaded(), onOpenProgress = { opened++ })

        rule.onNodeWithTag("exercise_detail_progress_link").assertTextEquals("Ver progreso").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun reducedMotion_showsOnlyTheStartPose() {
        show(loaded(), animations = false)

        rule.onNodeWithTag("exercise_detail_start", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("exercise_detail_peak", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun singleImageExercise_hasNoPeakPose() {
        val single = ExerciseDetail(bench, "Press de banca", exerciseInfo("bench", "Press de banca", hasPeak = false))
        show(loaded(single))

        rule.onNodeWithTag("exercise_detail_start", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("exercise_detail_peak", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun attribution_isShownInTheHowToTab() {
        show(loaded(tab = ExerciseDetailTab.HowTo))

        rule.onNodeWithTag("exercise_detail_attribution").assertTextEquals("Datos de ejercicios por RepDB (repdb.co)")
    }

    @Test
    fun records_showTheFourWeightFamiliesWithTheirValues() {
        val records = RecordsState.Content(
            listOf(
                PersonalRecord(PersonalRecordType.HeaviestLift, 100.0, "s1", LocalDate(2026, 10, 22)),
                PersonalRecord(PersonalRecordType.BestSet, 80.0, "s1", LocalDate(2026, 10, 22)),
                PersonalRecord(PersonalRecordType.Best1Rm, 106.7, "s1", LocalDate(2026, 10, 20)),
                PersonalRecord(PersonalRecordType.MaxVolume, 2120.0, "s1", LocalDate(2026, 10, 23)),
            ),
        )
        show(loaded(records = records))

        rule.onNodeWithTag("exercise_detail_record_heaviest_lift").assertTextContains("Récord de peso")
            .assertTextContains("100 kg").assertTextContains("Ayer")
        rule.onNodeWithTag("exercise_detail_record_best_set").assertTextContains("Mejor serie")
        rule.onNodeWithTag("exercise_detail_record_best_1rm").assertTextContains("1RM estimado")
            .assertTextContains("106,7 kg")
        rule.onNodeWithTag("exercise_detail_record_max_volume").assertTextContains("Volumen máximo")
            .assertTextContains("2.120 kg").assertTextContains("Hoy")
    }

    @Test
    fun noRecords_showsTheInlineMessage() {
        show(loaded(records = RecordsState.Empty))

        rule.onNodeWithTag(
            "exercise_detail_records_empty",
        ).assertTextEquals("Aún no tienes registros de este ejercicio")
    }

    @Test
    fun recordsFailure_offersRetry_withoutHidingTheRestOfTheSummary() {
        var retries = 0
        show(loaded(records = RecordsState.Error), actions = ExerciseDetailActions(onRetryRecords = { retries++ }))

        rule.onNodeWithTag("exercise_detail_records_retry").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(1, retries)
        rule.onNodeWithTag("exercise_detail_progress_link").assertExists()
    }

    @Test
    fun selectButton_existsOnlyInSelectMode_andSelects() {
        var selected = 0
        show(loaded(), selectMode = true, onSelect = { selected++ })

        rule.onNodeWithTag("exercise_detail_select_button").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, selected)
    }

    @Test
    fun selectButton_staysPinnedOnEveryTab() {
        var tab by mutableStateOf(ExerciseDetailTab.Summary)
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalExerciseMediaUris provides FakeExerciseMediaUris) {
                    ExerciseDetailScreen(loaded(tab = tab), true, {}, {}, today)
                }
            }
        }
        ExerciseDetailTab.entries.forEach {
            tab = it
            rule.waitForIdle()
            rule.onNodeWithTag("exercise_detail_select_button").assertExists()
        }
    }

    @Test
    fun browseMode_hasNoSelectButton() {
        show(loaded(), selectMode = false)

        rule.onNodeWithTag("exercise_detail_select_button").assertDoesNotExist()
    }

    @Test
    fun keyWithoutLibraryData_rendersWithoutCrashing() {
        show(loaded(ExerciseDetail(bench, "", null), tab = ExerciseDetailTab.HowTo), selectMode = true)

        rule.onNodeWithTag("exercise_detail_animation").assertDoesNotExist()
        rule.onNodeWithTag("exercise_detail_how_to_empty").assertExists()
        rule.onNodeWithTag("exercise_detail_attribution").assertDoesNotExist()
        rule.onNodeWithTag("exercise_detail_select_button").assertExists()
    }

    @Test
    fun customExercise_showsItsNameAndNoMedia_andAnEmptyHowTo() {
        val custom = ExerciseDetail(exerciseRow("c1", null, name = "Curl nordico", custom = true), "Curl nordico", null)
        show(loaded(custom))

        rule.onNodeWithText("Curl nordico").assertExists()
        rule.onNodeWithTag("exercise_detail_animation").assertDoesNotExist()
    }

    @Test
    fun customExercise_howToTabShowsTheNoInstructionsMessage() {
        val custom = ExerciseDetail(exerciseRow("c1", null, name = "Curl nordico", custom = true), "Curl nordico", null)
        show(loaded(custom, tab = ExerciseDetailTab.HowTo))

        rule.onNodeWithText("Este ejercicio no tiene instrucciones").assertExists()
    }

    @Test
    fun loadFailure_offersRetry() {
        var retries = 0
        show(ExerciseDetailUiState.Error, onRetry = { retries++ })

        rule.onNodeWithTag("exercise_detail_error_action").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun at130PercentFont_theThreeTabLabelsStayVisible() {
        show(loaded(), fontScale = 1.3f)

        listOf("summary", "history", "how_to").forEach {
            rule.onNodeWithTag("exercise_detail_tab_$it").assertExists().assertHeightIsAtLeast(48.dp)
        }
        rule.onNodeWithText("Cómo hacerlo", useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun tablet_putsTheAnimationLeftOfTheHeader_andConstrainsTabsAndContent() {
        show(loaded(), selectMode = true)

        val animation = rule.onNodeWithTag("exercise_detail_animation").getUnclippedBoundsInRoot()
        val link = rule.onNodeWithTag("exercise_detail_progress_link").getUnclippedBoundsInRoot()
        assertTrue(animation.right <= link.left)
        assertTrue(animation.width <= 360.dp)
        val tabs = rule.onNodeWithTag("exercise_detail_tabs").getUnclippedBoundsInRoot()
        val button = rule.onNodeWithTag("exercise_detail_select_button").getUnclippedBoundsInRoot()
        assertTrue(tabs.width <= ExerciseLayoutDefaults.ContentMaxWidth)
        assertTrue(button.width > animation.width)
    }
}
