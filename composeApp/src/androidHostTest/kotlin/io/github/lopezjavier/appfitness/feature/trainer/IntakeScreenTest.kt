package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.IntakeHost
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.IntakeUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val STORY = "Entrené 4 años, lo dejé hace 8 meses por una lesión de hombro"

/** US-81: "Para conocerte" (the free-text editor), one test per Gherkin scenario over the real view model. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class IntakeScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeCoachIntakeRepository()
    private val connectivity = FakeConnectivityObserver()
    private var done = 0
    private var closed = 0

    private fun show() {
        val model = IntakeViewModel(repository, SavedStateHandle())
        rule.setContent {
            AppTheme {
                IntakeRoot(
                    host = IntakeHost(onDone = { done++ }, onClose = { closed++ }),
                    viewModel = model,
                    connectivity = connectivity,
                )
            }
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun type(text: String) {
        rule.onNodeWithTag("intake_free_text").performTextInput(text)
        rule.waitForIdle()
    }

    @Test
    fun writingInMyOwnWords_showsTheFieldWithItsCounterAndTheHelp() {
        show()

        rule.onNodeWithContentDescription("Cuéntanos lo que quieras sobre tu entrenamiento").assertExists()
        rule.onNodeWithTag("intake_free_text_counter").assertTextEquals("0/500")
        rule.onNodeWithTag("intake_free_text_help").assertIsDisplayed()
        rule.onNodeWithTag("intake_save").assertIsNotEnabled()
    }

    @Test
    fun theHelp_saysTheCoachReadsItWhenPreparingThePlanAndTheBlock() {
        show()

        tap("intake_free_text_help")

        rule.onNodeWithText("Lo lee tal como lo escribes", substring = true).assertExists()
        rule.onNodeWithText("al preparar tu plan y tus bloques", substring = true).assertExists()
    }

    @Test
    fun savingMyText_storesItAsIWroteIt_andFillsNothingElse() {
        repository.stored = CoachIntake(trainingYears = 3.0, knownMaintenanceKcal = 2400)
        show()

        type(STORY)
        rule.onNodeWithTag("intake_free_text_counter").assertTextEquals("${STORY.length}/500")
        tap("intake_save")

        assertEquals(listOf<IntakeUpdate>(IntakeUpdate.FreeText(STORY)), repository.saveCalls)
        assertEquals(3.0, repository.stored?.trainingYears)
        assertEquals(2400, repository.stored?.knownMaintenanceKcal)
        assertEquals(1, done)
    }

    @Test
    fun aSaveThatFails_keepsTheText_andShowsTheErrorAboveGuardar() {
        repository.saveFailure = AppException(AppError.Unknown)
        show()
        type(STORY)

        tap("intake_save")

        rule.onNodeWithTag("intake_save_error").assertIsDisplayed()
        rule.onNodeWithTag("intake_free_text_counter").assertTextEquals("${STORY.length}/500")
        rule.onNodeWithTag("intake_save").assertIsEnabled()
        assertEquals(0, done)
    }

    @Test
    fun theSavedText_isShownWhenIOpenItAgain() {
        repository.stored = CoachIntake(freeText = STORY)

        show()

        rule.onNodeWithTag("intake_free_text_counter").assertTextEquals("${STORY.length}/500")
    }

    @Test
    fun leavingWithUnsavedText_asksBeforeDiscarding_andDiscardingStoresNothing() {
        show()
        type(STORY)

        tap("intake_back")
        rule.onNodeWithTag("intake_discard_dialog").assertExists()
        tap("intake_discard_confirm")

        assertEquals(1, closed)
        assertTrue(repository.saveCalls.isEmpty())
    }

    @Test
    fun leavingWithoutChanges_closesWithoutAsking() {
        show()

        tap("intake_back")

        rule.onNodeWithTag("intake_discard_dialog").assertDoesNotExist()
        assertEquals(1, closed)
    }

    @Test
    fun theRemovedControls_areGone() {
        show()

        listOf(
            "intake_continue",
            "intake_extract",
            "intake_skip",
            "intake_situation",
            "intake_injured_area",
            "intake_add_best",
            "intake_cut_kcal",
            "intake_years_value",
        ).forEach { rule.onNodeWithTag(it).assertDoesNotExist() }
        listOf("Ahora no", "Rellenar con mi texto", "Zona con molestia", "Marcas de antes de parar", "Situación")
            .forEach { rule.onNodeWithText(it).assertDoesNotExist() }
    }
}
