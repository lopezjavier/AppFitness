package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.ReminderCoordinator
import io.github.lopezjavier.appfitness.app.ReminderEnv
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HapticEvent
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.RecordingAppHaptics
import io.github.lopezjavier.appfitness.domain.usecase.DecideCalorieProposalUseCase
import io.github.lopezjavier.appfitness.domain.usecase.DecidePhaseChangeUseCase
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
import io.github.lopezjavier.appfitness.domain.usecase.LoadBlockReviewUseCase
import io.github.lopezjavier.appfitness.domain.usecase.MarkBlockReviewSeen
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** R-29: a stored decision confirms, a failed one rejects; both through the real ViewModel. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class BlockReviewHapticsTest {
    @get:Rule
    val rule = createComposeRule()

    private val env = ReminderEnv(LocalDate(2026, 10, 19))
    private val reviews = env.reviews.apply { review = Result.success(blockReview()) }
    private val haptics = RecordingAppHaptics()

    private fun show() {
        val viewModel = BlockReviewViewModel(
            loadReview = LoadBlockReviewUseCase(reviews),
            decidePhase = DecidePhaseChangeUseCase(reviews),
            plans = FakeCoachPlanRepository(),
            decideCalorie = DecideCalorieProposalUseCase(FakeCoachPlanRepository()),
            markSeen = MarkBlockReviewSeen(reviews),
            reminders = ReminderCoordinator(env.load, CoroutineScope(Dispatchers.Unconfined)),
            names = ExerciseNameResolver(FakeExerciseRepository(emptyList()), FakeExerciseLibrary(emptyMap())),
            clock = MutableClock(),
        )
        rule.setContent {
            CompositionLocalProvider(LocalAppHaptics provides haptics) {
                AppTheme {
                    BlockReviewRoot(
                        onBack = {},
                        onOpenBeforeStart = { _, _, _ -> },
                        viewModel = viewModel,
                        connectivity = FakeConnectivityObserver(),
                    )
                }
            }
        }
        rule.waitUntil(TIMEOUT_MS) {
            rule.onAllNodesWithTag("block_review_phase_discard").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun seguirComoEstoy_confirms() {
        show()

        rule.onNodeWithTag("block_review_phase_discard").performScrollTo().performClick()
        rule.waitUntil(TIMEOUT_MS) { haptics.calls.isNotEmpty() }

        assertEquals(listOf(HapticEvent.Confirm), haptics.calls)
    }

    @Test
    fun failedDecision_rejects() {
        reviews.keepResult = Result.failure(IllegalStateException("boom"))
        show()

        rule.onNodeWithTag("block_review_phase_discard").performScrollTo().performClick()
        rule.waitUntil(TIMEOUT_MS) { haptics.calls.isNotEmpty() }

        assertEquals(listOf(HapticEvent.Reject), haptics.calls)
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
