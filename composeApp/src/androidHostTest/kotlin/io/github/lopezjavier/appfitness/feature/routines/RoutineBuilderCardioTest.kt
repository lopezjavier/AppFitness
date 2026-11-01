package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
import io.github.lopezjavier.appfitness.domain.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-72 "A cardio set is minutes only": the routine editor shows only the planned minutes for a cardio set. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class RoutineBuilderCardioTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<RoutineBuilderEvent>()

    private fun row(minutes: Int?) = BuilderRowUi(
        rowId = 1,
        exerciseId = "bike",
        name = "Bicicleta",
        imageKey = null,
        plannedSets = listOf(PlannedSetUi(1, "", "", SetType.Working, timed = true, minutes = minutes)),
        restSeconds = 0,
        supersetLabel = null,
        canMoveUp = false,
        canMoveDown = false,
        canGroupWithNext = false,
    )

    private fun show(minutes: Int?) {
        rule.setContent { AppTheme { Column { PlannedSets(row(minutes), 0) { events += it } } } }
        rule.waitForIdle()
    }

    @Test
    fun aCardioSet_showsOnlyItsMinutes_noWeightRepsOrRpe() {
        show(50)

        rule.onNodeWithTag("routine_set_minutes_picker_0_0_value").assertExists()
        rule.onNodeWithText("50 min").assertExists()
        rule.onNodeWithTag("routine_builder_set_weight_0_0").assertDoesNotExist()
        rule.onNodeWithTag("routine_builder_set_reps_0_0").assertDoesNotExist()
        rule.onNodeWithTag("routine_set_header_weight").assertDoesNotExist()
        rule.onNodeWithTag("routine_set_header_minutes").assertExists()
    }

    @Test
    fun increasing_sendsFiveMoreMinutes() {
        show(50)

        rule.onNodeWithTag("routine_set_minutes_picker_0_0_increment").performClick()

        assertEquals(listOf<RoutineBuilderEvent>(RoutineBuilderEvent.SetMinutes(0, 0, 55)), events)
    }

    @Test
    fun anUnsetSet_readsSinDefinir_andCannotGoLower() {
        show(null)

        rule.onNodeWithText("Sin definir").assertExists()
        rule.onNodeWithTag("routine_set_minutes_picker_0_0_increment").performClick()
        assertEquals(listOf<RoutineBuilderEvent>(RoutineBuilderEvent.SetMinutes(0, 0, 30)), events)
    }

    @Test
    fun theLowestStep_goesBackToSinDefinir() {
        show(5)

        rule.onNodeWithTag("routine_set_minutes_picker_0_0_decrement").performClick()

        assertEquals(listOf<RoutineBuilderEvent>(RoutineBuilderEvent.SetMinutes(0, 0, null)), events)
    }

    @Test
    fun theDraftSet_roundTripsThePlannedDuration() {
        val set = PlannedSet.ofMinutes(50)
        assertEquals(3000, set.durationS)
        assertEquals(50, set.toDraft(1).minutes)
        assertEquals(set, set.toDraft(1).toPlannedSet())
        assertEquals(RoutineBounds.DURATION_MIN_SECONDS, PlannedSet.ofMinutes(1).durationS)
    }
}
