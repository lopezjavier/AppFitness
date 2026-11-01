package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-72 "Training the cardio": the live row shows the planned minutes as a dim placeholder, no RPE cell. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class LiveSessionCardioTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(planned: Int?) {
        rule.setContent {
            AppTheme {
                Column {
                    SessionSetRow(
                        position = 0,
                        row = LiveSetRow(1, "", "", completed = false, plannedDurationMinutes = planned),
                        onEvent = {},
                        metricType = ExerciseMetricType.Duration,
                        showRpe = planned == null,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun theCardioSet_showsThePlannedMinutes_andNoRpeCell() {
        show(50)

        rule.onNodeWithTag("session_cardio_minutes_field_0_1").assertExists()
        rule.onNodeWithText("50 min").assertExists()
        rule.onNodeWithTag("session_cardio_minutes_field_0_1")
            .assertContentDescriptionEquals("Minutos de cardio, planificados 50")
        rule.onNodeWithTag("live_set_0_1_rpe").assertDoesNotExist()
    }

    @Test
    fun aLiftingSet_hasNoMinutesPlaceholder() {
        show(null)

        rule.onNodeWithTag("session_cardio_minutes_field_0_1").assertDoesNotExist()
    }
}
