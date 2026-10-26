package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppHaptics
import io.github.lopezjavier.appfitness.designsystem.AppMessages
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.usecase.MeasurementsByDay
import io.github.lopezjavier.appfitness.domain.usecase.SaveBodyMeasurement
import io.github.lopezjavier.appfitness.domain.usecase.ValidateBodyMeasurement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

private object FeedbackClock : Clock {
    override fun today(): LocalDate = LocalDate(2026, 10, 21)

    override fun now(): Instant = Instant.parse("2026-10-21T10:30:00Z")
}

private class RecordingHaptics : AppHaptics {
    val events = mutableListOf<String>()

    override fun armed() = Unit

    override fun disarmed() = Unit

    override fun confirm() {
        events += "confirm"
    }

    override fun reject() {
        events += "reject"
    }

    override fun tick() = Unit
}

private class RecordingMessages : AppMessages(CoroutineScope(Job())) {
    val shown = mutableListOf<String>()

    override fun show(text: String) {
        shown += text
    }
}

/** One haptic and, only on failure, one message per save of the weight day sheet (R-13, R-29, R-37). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class WeightFeedbackTest {
    @get:Rule
    val rule = createComposeRule()

    private val haptics = RecordingHaptics()
    private val messages = RecordingMessages()
    private val repository = FakeWeightRepository(
        BodyMeasurement("a", Instant.parse("2026-10-18T07:30:00Z"), 74.2, MeasurementSource.Manual),
    )

    private fun show() {
        val model = WeightViewModel(
            repository = repository,
            measurementsByDay = MeasurementsByDay(),
            saveBodyMeasurement = SaveBodyMeasurement(repository, FeedbackClock, TimeZone.UTC),
            validate = ValidateBodyMeasurement(),
            connectivity = FakeConnectivityObserver(),
            clock = FeedbackClock,
            timeZone = TimeZone.UTC,
            savedState = SavedStateHandle(),
        )
        rule.setContent {
            CompositionLocalProvider(LocalAppHaptics provides haptics, LocalAppMessages provides messages) {
                AppTheme { WeightHistoryRoot(onBack = {}, modifier = Modifier, viewModel = model) }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("month_calendar_day_2026-10-20").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("weight_sheet_weight_input").performTextReplacement("74,0")
        rule.waitForIdle()
    }

    @Test
    fun aSuccessfulSave_firesOneConfirm_andShowsNoMessage() {
        show()

        rule.onNodeWithTag("weight_day_sheet_commit").performClick()
        rule.waitForIdle()

        assertEquals(listOf("confirm"), haptics.events)
        assertTrue(messages.shown.isEmpty())
    }

    @Test
    fun aFailedSave_firesOneReject_andShowsTheFailureMessage() {
        show()
        repository.saveFailure = AppException(AppError.Unknown)

        rule.onNodeWithTag("weight_day_sheet_commit").performClick()
        rule.waitForIdle()

        assertEquals(listOf("reject"), haptics.events)
        assertEquals(listOf("No se pudo guardar. Inténtalo de nuevo."), messages.shown)
    }

    @Test
    fun anOfflineFailure_showsTheOfflineMessage() {
        show()
        repository.saveFailure = AppException(AppError.Network)

        rule.onNodeWithTag("weight_day_sheet_commit").performClick()
        rule.waitForIdle()

        assertEquals(listOf("Sin conexión: no se puede guardar"), messages.shown)
    }
}
