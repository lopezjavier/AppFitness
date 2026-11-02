package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.PlanInputKind
import io.github.lopezjavier.appfitness.domain.model.PlanInputs
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-82 "The minimal phase for busy times": the secondary action of "Antes de empezar" in block mode. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class BeforeStartMinimalTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeCoachRepository()
    private val created = mutableListOf<BeforeStartEffect.Created>()

    private fun show(mode: String, online: Boolean = true) {
        repository.inputs[PlanInputKind.Plan] = Result.success(planKeys())
        repository.inputs[PlanInputKind.Block] = Result.success(
            PlanInputs(PlanInputKind.Block, null, planKeys().inputs),
        )
        val args = mapOf<String, Any?>("mode" to mode, "planId" to "plan-2", "replacingPlanId" to null)
        val model = BeforeStartViewModel(
            repository,
            FakeCoachIntakeRepository(),
            FakeIntakePromptPreference(asked = true),
            MutableClock(),
            SavedStateHandle(args),
        )
        rule.setContent {
            AppTheme {
                BeforeStartRoot(
                    onBack = {},
                    onCreate = { created += it },
                    onOpenYourData = {},
                    onFix = {},
                    onOpenIntake = {},
                    viewModel = model,
                    connectivity = FakeConnectivityObserver(online),
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun blockMode_showsBothButtonsAndTheHelper() {
        show("block")

        rule.onNodeWithTag("before_start_create").assertExists()
        rule.onNodeWithTag("before_start_create_minimal").assertExists()
        rule.onNodeWithText("Tengo poco tiempo").assertExists()
        rule.onNodeWithText("Un bloque mínimo, con lo esencial para no perder el hábito.").assertExists()
    }

    @Test
    fun tappingTengoPocoTiempo_leavesWithTheMinimalFlag() {
        show("block")

        rule.onNodeWithTag("before_start_create_minimal").performClick()
        rule.waitForIdle()

        assertEquals(listOf(true), created.map { it.minimal })
    }

    @Test
    fun tappingCrearBloque_leavesWithoutTheMinimalFlag() {
        show("block")

        rule.onNodeWithTag("before_start_create").performClick()
        rule.waitForIdle()

        assertEquals(listOf(false), created.map { it.minimal })
    }

    @Test
    fun planMode_hasNoMinimalButton() {
        show("plan")

        rule.onNodeWithTag("before_start_create_minimal").assertDoesNotExist()
    }

    @Test
    fun offline_theMinimalButtonIsDisabled() {
        show("block", online = false)

        rule.onNodeWithTag("before_start_create_minimal").assertIsNotEnabled()
    }
}
