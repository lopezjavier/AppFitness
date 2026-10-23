package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun stateOf(metrics: List<ExerciseMetricType>) = LiveSessionUiState.Active(
    name = "Mixto",
    startedAt = SESSION_START,
    exercises = metrics.mapIndexed { index, metric ->
        LiveExercise(
            index,
            "e$index",
            "Ejercicio $index",
            null,
            listOf(LiveSetRow(1, "", "", false)),
            metricType = metric,
        )
    },
)

/** US-31: one set row per metric type, with exactly the fields of the type. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class LiveSessionSetRowVariantsTest {
    @get:Rule
    val rule = createComposeRule()

    private val metrics = ExerciseMetricType.entries

    private fun show() {
        rule.setContent {
            AppTheme { LiveSessionScreen(stateOf(metrics), 0, {}, {}, onOpenExercise = {}) }
        }
        rule.waitForIdle()
    }

    private fun fieldsOf(metric: ExerciseMetricType): Set<String> {
        val position = metrics.indexOf(metric)
        return listOf(
            "weight",
            "added_weight",
            "assist_weight",
            "reps",
            "distance",
            "steps",
            "floors",
            "stopwatch_time",
        ).filter { part ->
            runCatching { rule.onNodeWithTag("live_set_${position}_1_$part").assertExists() }.isSuccess
        }.toSet()
    }

    @Test
    fun eachMetricType_showsExactlyItsFields() {
        show()

        val expected = mapOf(
            ExerciseMetricType.WeightReps to setOf("weight", "reps"),
            ExerciseMetricType.RepsOnly to setOf("reps"),
            ExerciseMetricType.Duration to setOf("stopwatch_time"),
            ExerciseMetricType.WeightDuration to setOf("weight", "stopwatch_time"),
            ExerciseMetricType.DistanceDuration to setOf("distance", "stopwatch_time"),
            ExerciseMetricType.Bodyweight to setOf("added_weight", "reps"),
            ExerciseMetricType.Assisted to setOf("assist_weight", "reps"),
            ExerciseMetricType.Weighted to setOf("added_weight", "reps"),
            ExerciseMetricType.StepsDuration to setOf("steps", "stopwatch_time"),
            ExerciseMetricType.FloorsDuration to setOf("floors", "stopwatch_time"),
        )
        metrics.forEach { metric ->
            kotlin.test.assertEquals(expected.getValue(metric), fieldsOf(metric), "fields of $metric")
        }
    }
}
