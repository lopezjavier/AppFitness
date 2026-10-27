package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

/** US-56 "Same structure on every screen": the routine details screen (R-01, R-17, R-20, R-37). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One test per scenario.
class RoutineDetailsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<RoutineDetailsEvent>()
    private var edited = 0
    private var opened = mutableListOf<String>()
    private var backs = 0

    private val content = RoutineDetailsUiState.Content(
        name = "Torso A",
        exercises = listOf(
            RoutineDetailsExerciseUi("e1", "Press banca", null, List(3) { PlannedSet(null, 8) }),
            RoutineDetailsExerciseUi("e2", "Remo", null, listOf(PlannedSet(60.0, 8), PlannedSet(62.5, 6))),
        ),
        lastDone = Instant.parse("2026-10-24T10:00:00Z"),
    )

    private fun show(state: RoutineDetailsUiState) {
        rule.setContent {
            AppTheme {
                RoutineDetailsScreen(
                    state = state,
                    onEvent = { events += it },
                    onEdit = { edited++ },
                    onOpenExercise = { opened += it },
                    onBack = { backs++ },
                    timeZone = TimeZone.UTC,
                    today = LocalDate(2026, 10, 26),
                )
            }
        }
    }

    @Test
    fun title_isTheRoutineName_withEditAndDeleteInTheTopBar() {
        show(content)

        rule.onNodeWithTag("routine_details_title").assertTextEquals("Torso A")
        rule.onNodeWithTag("routine_details_edit").assert(hasContentDescription("Editar"))
        rule.onNodeWithTag("routine_details_delete").assert(hasContentDescription("Eliminar"))
    }

    @Test
    fun editAction_opensTheBuilder() {
        show(content)

        rule.onNodeWithTag("routine_details_edit").performClick()

        assertEquals(1, edited)
    }

    @Test
    fun deleteAction_deletesAtOnce_withoutADialog() {
        show(content)

        rule.onNodeWithTag("routine_details_delete").performClick()

        assertEquals(listOf<RoutineDetailsEvent>(RoutineDetailsEvent.Delete), events)
    }

    @Test
    fun exercises_showNameAndPlannedSets_andOpenTheirDetail() {
        show(content)

        rule.onNodeWithText("Ejercicios").assertIsDisplayed()
        rule.onNodeWithText("3 x 8").assertIsDisplayed()
        rule.onNodeWithText("60 kg x 8 · 62,5 kg x 6").assertIsDisplayed()
        rule.onNodeWithTag("routine_details_exercise_e1").performClick()
        assertEquals(listOf("e1"), opened)
    }

    @Test
    fun lastDone_isShownInTheListForm() {
        show(content)

        rule.onNodeWithTag("routine_details_last_done").assertTextEquals("Última vez: sáb, 24 oct")
    }

    @Test
    fun neverDone_saysAunSinHacer() {
        show(content.copy(lastDone = null))

        rule.onNodeWithTag("routine_details_last_done").assertTextEquals("Aún sin hacer")
    }

    @Test
    fun empezar_startsTheRoutine_whenOnline() {
        show(content)

        rule.onNodeWithTag("routine_details_start").assertIsEnabled().performClick()

        assertEquals(listOf<RoutineDetailsEvent>(RoutineDetailsEvent.Start), events)
    }

    @Test
    fun offline_disablesEmpezar_andShowsTheHint() {
        show(content.copy(isOnline = false))

        rule.onNodeWithTag("routine_details_start").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede empezar").assertIsDisplayed()
    }

    @Test
    fun loading_showsTheSkeleton_andNoActions() {
        show(RoutineDetailsUiState.Loading)

        rule.onNodeWithTag("routine_details_loading").assertIsDisplayed()
        rule.onNodeWithTag("routine_details_edit").assertDoesNotExist()
    }

    @Test
    fun error_offersReintentar() {
        show(RoutineDetailsUiState.Error)

        rule.onNodeWithText("Reintentar").performClick()

        assertEquals(listOf<RoutineDetailsEvent>(RoutineDetailsEvent.Retry), events)
    }

    @Test
    fun backArrow_leaves() {
        show(content)

        rule.onNodeWithTag("routine_details_back").performClick()

        assertEquals(1, backs)
    }
}
