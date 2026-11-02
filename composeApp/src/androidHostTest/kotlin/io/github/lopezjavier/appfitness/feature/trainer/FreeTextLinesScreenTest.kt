package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.FreeTextKind
import io.github.lopezjavier.appfitness.domain.model.FreeTextLine
import io.github.lopezjavier.appfitness.domain.model.FreeTextStatus
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun line(
    kind: FreeTextKind,
    id: String,
    day: Int,
    text: String,
    status: FreeTextStatus = FreeTextStatus.Sent,
) = FreeTextLine(kind, id, LocalDate(2026, 10, day), text, status)

/** US-89: "Lo que el entrenador sabe de ti", one test per Gherkin scenario over the real view model. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class FreeTextLinesScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeCoachIntakeRepository()
    private val connectivity = FakeConnectivityObserver()

    private var current by mutableStateOf<FreeTextLinesViewModel?>(null)

    private fun show() {
        current = FreeTextLinesViewModel(repository, MutableClock())
        rule.setContent {
            AppTheme {
                FreeTextLinesRoot(onBack = {}, viewModel = checkNotNull(current), connectivity = connectivity)
            }
        }
        rule.waitForIdle()
    }

    private fun top(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot().top

    @Test
    fun theCoachReads_linesVerbatim_inSectionsByOrigin_newestFirst() {
        repository.lines += line(FreeTextKind.Intake, "i1", 3, "Me opero del hombro")
        repository.lines += line(FreeTextKind.CheckIn, "c1", 5, "Semana de mucho estrés")
        repository.lines += line(FreeTextKind.CheckIn, "c2", 12, "Dormí fatal")

        show()

        rule.onNodeWithTag("free_text_section_intake").assertExists()
        rule.onNodeWithTag("free_text_section_check_in").assertExists()
        rule.onNodeWithText("Me opero del hombro").assertExists()
        assertTrue(top("free_text_line_check_in_c2") < top("free_text_line_check_in_c1"))
        rule.onNodeWithTag("free_text_switch_intake_i1").assertIsOn()
    }

    @Test
    fun leavingALineOut_persists_andReopeningShowsItOff() {
        repository.lines += line(FreeTextKind.Discomfort, "d1", 4, "Me duele la rodilla")
        show()

        rule.onNodeWithTag("free_text_switch_discomfort_d1").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("free_text_switch_discomfort_d1").assertIsOff()
        assertEquals(listOf(Triple(FreeTextKind.Discomfort, "d1", true)), repository.toggleCalls)
    }

    @Test
    fun reopening_showsTheLineOffAsTheServerHoldsIt() {
        repository.lines += line(FreeTextKind.Discomfort, "d1", 4, "Me duele la rodilla")
        show()
        rule.onNodeWithTag("free_text_switch_discomfort_d1").performClick()
        rule.waitForIdle()

        // A new view model over the same repository, as when the user leaves and comes back.
        current = FreeTextLinesViewModel(repository, MutableClock())
        rule.waitForIdle()

        rule.onNodeWithTag("free_text_switch_discomfort_d1").assertIsOff()
    }

    @Test
    fun aRefusedSwitch_rollsBack() {
        repository.lines += line(FreeTextKind.Review, "r1", 4, "Fue bien")
        repository.toggleFailure = AppException(AppError.Unknown)
        show()

        rule.onNodeWithTag("free_text_switch_review_r1").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("free_text_switch_review_r1").assertIsOn()
    }

    @Test
    fun sizeLimits_aLineBeyondTheLimit_saysItIsNotSent() {
        repository.lines += line(FreeTextKind.Intake, "i9", 1, "Texto antiguo", FreeTextStatus.OverLimit)

        show()

        rule.onNodeWithTag("free_text_over_limit_i9").assertExists()
        rule.onNodeWithText("No se envía: supera el límite.").assertExists()
    }

    @Test
    fun withNothingToRead_showsTheEmptyState() {
        show()

        rule.onNodeWithTag("free_text_empty").assertExists()
        rule.onNodeWithText("Todavía no hay nada que el entrenador lea de ti.").assertExists()
    }

    @Test
    fun aLoadFailure_offersReintentar() {
        repository.linesFailure = AppException(AppError.Unknown)
        show()
        rule.onNodeWithTag("free_text_error_retry").assertExists()
        repository.linesFailure = null
        repository.lines += line(FreeTextKind.Intake, "i1", 3, "Hola")

        rule.onNodeWithTag("free_text_error_retry").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Hola").assertExists()
    }
}
