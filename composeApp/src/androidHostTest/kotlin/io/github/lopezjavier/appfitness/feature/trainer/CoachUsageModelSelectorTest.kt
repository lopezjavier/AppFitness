package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.lopezjavier.appfitness.data.InMemoryCoachModelPreference
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.CoachModelChoice
import io.github.lopezjavier.appfitness.domain.model.CoachUsage
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T-178: the AI model selector in "Uso de IA". */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class CoachUsageModelSelectorTest {
    @get:Rule
    val rule = createComposeRule()

    private val preference = InMemoryCoachModelPreference()
    private val content = CoachUsageUiState.Content(CoachUsage(LocalDate(2026, 11, 1), 0, 0, 0.1))

    private fun show(state: CoachUsageUiState) {
        rule.setContent {
            val model by preference.selected.collectAsState()
            AppTheme {
                CoachUsageScreen(
                    state,
                    flowOf(true),
                    { if (it is CoachUsageEvent.SelectModel) preference.select(it.choice) },
                    {},
                    model = model,
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun showsThreeOptions_withOpusSelectedByDefault() {
        show(content)

        rule.onNodeWithTag("coach_usage_model_card").assertExists()
        rule.onNodeWithTag("coach_usage_model_opus").assertIsSelected()
        rule.onNodeWithTag("coach_usage_model_sonnet").assertIsNotSelected()
        rule.onNodeWithTag("coach_usage_model_haiku").assertIsNotSelected()
        rule.onNodeWithText("Haiku 5.5").assertExists()
    }

    @Test
    fun showsTheTestOnlyNote() {
        show(content)

        rule.onNodeWithTag("coach_usage_model_note").assertTextEquals(
            "Solo para pruebas: un modelo más barato puede dar planes de peor calidad. " +
                "Se restablece al cerrar la app.",
        )
    }

    @Test
    fun tappingAnOption_selectsItOnTap_withoutASaveButton() {
        show(content)

        rule.onNodeWithTag("coach_usage_model_haiku").performScrollTo().performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("coach_usage_model_haiku").assertIsSelected()
        rule.onNodeWithTag("coach_usage_model_opus").assertIsNotSelected()
        assertEquals(CoachModelChoice.Haiku, preference.selected.value)
        rule.onNodeWithText("Guardar").assertDoesNotExist()
        rule.onNodeWithText("Listo").assertDoesNotExist()
    }

    @Test
    fun isHiddenWhileLoadingOrOnErrorOrOffline() {
        var state by mutableStateOf<CoachUsageUiState>(CoachUsageUiState.Loading)
        rule.setContent { AppTheme { CoachUsageScreen(state, flowOf(true), {}, {}) } }
        listOf(CoachUsageUiState.Loading, CoachUsageUiState.Error, CoachUsageUiState.Offline).forEach { next ->
            state = next
            rule.waitForIdle()

            rule.onNodeWithTag("coach_usage_model_card").assertDoesNotExist()
        }
    }
}
