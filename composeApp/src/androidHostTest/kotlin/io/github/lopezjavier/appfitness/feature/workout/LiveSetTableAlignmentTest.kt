package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.feature.routines.BuilderRowUi
import io.github.lopezjavier.appfitness.feature.routines.PlannedSetUi
import io.github.lopezjavier.appfitness.feature.routines.PlannedSets
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

private val Tolerance = 1.dp

/** R-50: every header label of a set table is centred over its field and shares the row's column widths. */
abstract class LiveSetTableAlignmentBase {
    @get:Rule
    val rule = createComposeRule()

    private fun centreX(tag: String): Dp {
        val bounds = rule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        return bounds.left + (bounds.right - bounds.left) / 2
    }

    private fun widthOf(tag: String): Dp {
        val bounds = rule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        return bounds.right - bounds.left
    }

    private fun assertCentred(headerTag: String, cellTag: String) {
        val header = centreX(headerTag)
        val cell = centreX(cellTag)
        assertTrue(
            kotlin.math.abs((header - cell).value) <= Tolerance.value,
            "$headerTag centre $header differs from $cellTag centre $cell",
        )
    }

    private fun showLive() {
        val sets = listOf(LiveSetRow(1, "60", "12", completed = false), LiveSetRow(2, "", "", completed = false))
        val state = LiveSessionUiState.Active(
            name = "Torso",
            startedAt = SESSION_START,
            exercises = listOf(LiveExercise(0, "e1", "Press banca", null, sets)),
            isOnline = true,
        )
        rule.setContent {
            AppTheme { LiveSessionScreen(state, 0, {}, {}, onOpenExercise = {}) }
        }
        rule.waitForIdle()
    }

    @Test
    fun liveTable_headerLabelsAreCentredOverTheirFields() {
        showLive()

        listOf("type", "weight", "reps", "rpe", "check").forEach {
            assertCentred("live_set_header_$it", "live_set_0_1_$it")
            assertCentred("live_set_header_$it", "live_set_0_2_$it")
        }
    }

    @Test
    fun liveTable_headerAndRowShareTheColumnWidths() {
        showLive()

        listOf("type", "weight", "reps", "rpe", "check").forEach {
            val header = widthOf("live_set_header_$it")
            val row = widthOf("live_set_0_1_$it")
            assertTrue(
                kotlin.math.abs((header - row).value) <= Tolerance.value,
                "$it header width $header differs from row width $row",
            )
        }
    }

    @Test
    fun liveTable_hasNoOrdinalColumnNorNumberHeader() {
        showLive()

        rule.onAllNodesWithTag("set_ordinal").assertCountEquals(0)
        rule.onNodeWithText("Nº").assertDoesNotExist()
        rule.onNodeWithTag("live_set_header_type", useUnmergedTree = true).assertExists()
    }

    @Test
    fun editorTable_headerLabelsAreCentredOverTheirFields() {
        val exercise = FormExercise(
            1,
            "e1",
            ExerciseMetricType.WeightReps,
            null,
            90,
            listOf(FormSet(setId = 1, weight = "60", reps = "8")),
        )
        rule.setContent {
            AppTheme {
                Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
                    FormSetHeader(exercise)
                    FormSetRow(exercise, 0, 0, true, "form", {}, {}, {}, {})
                }
            }
        }

        listOf("weight", "reps", "rpe").forEach { assertCentred("form_set_header_$it", "form_set_0_0_$it") }
    }

    @Test
    fun builderTable_headerLabelsAreCentredOverTheirFields() {
        val row = BuilderRowUi(
            rowId = 1,
            exerciseId = "e1",
            name = "Press banca",
            imageKey = null,
            plannedSets = listOf(PlannedSetUi(1, "60", "8", SetType.Working)),
            restSeconds = 90,
            supersetLabel = null,
            canMoveUp = false,
            canMoveDown = false,
            canGroupWithNext = false,
        )
        rule.setContent {
            AppTheme {
                Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) { PlannedSets(row, 0) {} }
            }
        }

        listOf("weight", "reps").forEach {
            assertCentred("routine_set_header_$it", "routine_builder_set_${it}_0_0")
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class LiveSetTableAlignmentTest : LiveSetTableAlignmentBase()

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w600dp-h900dp-xxhdpi")
class LiveSetTableAlignmentTabletTest : LiveSetTableAlignmentBase()
