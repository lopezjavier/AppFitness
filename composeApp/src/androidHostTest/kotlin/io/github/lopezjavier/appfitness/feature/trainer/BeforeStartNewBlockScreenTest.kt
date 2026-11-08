package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.GoalSwitchPreview
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PlanInputKey
import io.github.lopezjavier.appfitness.domain.model.PlanInputKind
import io.github.lopezjavier.appfitness.domain.model.PlanInputs
import io.github.lopezjavier.appfitness.domain.usecase.OpenNewBlockUseCase
import io.github.lopezjavier.appfitness.domain.usecase.PreviewGoalSwitchUseCase
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T-240 (US-110): "Antes de empezar" in new-block mode: the goal-change note, the footer and its restoration. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class BeforeStartNewBlockScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val coach = FakeCoachRepository()
    private val reviews = FakeCoachReviewRepository()
    private val created = mutableListOf<BeforeStartEffect.Created>()

    @Before
    fun setUp() {
        coach.inputs[PlanInputKind.Block] = Result.success(
            PlanInputs(PlanInputKind.Block, null, listOf(planInput(PlanInputKey.Place))),
        )
        reviews.newBlock = Result.success(newBlockEntry(changed = true))
        reviews.preview = Result.success(GoalSwitchPreview("plan-2", 2800, 2300, Phase.Cut))
    }

    private fun newModel(saved: SavedStateHandle) = BeforeStartViewModel(
        coach,
        FakeCoachIntakeRepository(),
        MutableClock(),
        FakeRoutineRepository(),
        saved,
        openNewBlock = OpenNewBlockUseCase(reviews),
        previewGoalSwitch = PreviewGoalSwitchUseCase(reviews),
    )

    private fun args() = SavedStateHandle(mapOf("mode" to "new_block", "planId" to "plan-1"))

    private fun show(model: () -> BeforeStartViewModel) {
        rule.setContent {
            AppTheme {
                BeforeStartRoot(
                    onBack = {},
                    onCreate = { created += it },
                    onOpenYourData = {},
                    onFix = {},
                    viewModel = model(),
                    connectivity = FakeConnectivityObserver(),
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun goalChanged_showsTheNoteWithFigures() {
        val model = newModel(args())
        show { model }

        rule.onNodeWithTag("before_start_note_goal_change").assertTextContains(
            "Tus calorías pasan de 2.800 a 2.300 kcal: Definición. El bloque actual sigue hasta que guardes el nuevo.",
        )
    }

    @Test
    fun goalChanged_withoutFigures_showsTheFallbackNeverNull() {
        reviews.preview = Result.success(GoalSwitchPreview("plan-2", null, null, null))
        reviews.newBlock = Result.success(newBlockEntry(changed = true, currentKcal = null))

        val model = newModel(args())
        show { model }

        rule.onNodeWithTag("before_start_note_goal_change").assertTextContains(
            "Tu objetivo ha cambiado. El bloque actual sigue hasta que guardes el nuevo.",
        )
    }

    @Test
    fun goalUnchanged_showsNoNote() {
        reviews.newBlock = Result.success(newBlockEntry(changed = false))

        val model = newModel(args())
        show { model }

        rule.onNodeWithTag("before_start_note_goal_change").assertDoesNotExist()
        rule.onNodeWithTag("before_start_create").assertExists()
    }

    @Test
    fun theFooter_opensTheBuilderOnThePreviewPlan_withTheAcceptedPlanAsPrevious() {
        val model = newModel(args())
        show { model }

        rule.onNodeWithTag("before_start_create").performClick()
        rule.waitForIdle()

        assertEquals(
            listOf(BeforeStartEffect.Created(BeforeStartMode.NewBlock, "plan-2", "plan-1")),
            created,
        )
    }

    @Test
    fun recreatedModel_keepsTheNoteAndThePreviewPlan() {
        val saved = args()
        var current by mutableStateOf(newModel(saved))
        show { current }

        current = newModel(saved)
        rule.waitForIdle()

        rule.onNodeWithTag("before_start_note_goal_change").assertExists()
        rule.onNodeWithTag("before_start_create").performClick()
        rule.waitForIdle()
        assertEquals("plan-2", created.single().planId)
    }
}
