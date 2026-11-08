package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredCoachPlanRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredDiscomfortRepository
import io.github.lopezjavier.appfitness.data.repository.UnconfiguredProfileRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.coach.builder.BuilderInputs
import io.github.lopezjavier.appfitness.domain.model.Discomfort
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import io.github.lopezjavier.appfitness.domain.repository.CoachPlanRepository
import io.github.lopezjavier.appfitness.domain.repository.DiscomfortRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import io.github.lopezjavier.appfitness.domain.usecase.BuildBlockUseCase
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.domain.usecase.LoadFolderBlockUseCase
import io.github.lopezjavier.appfitness.domain.usecase.LoadNextBlockContextUseCase
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.FakeFavoriteExercisesRepository
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T-240 (ADR-40): the builder opened from "Bloque nuevo" (early review, no stored one) keeps the partial target
 * choices when its view model is recreated from the same saved state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class BlockBuilderNewBlockRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val blocks = FakeCoachBlockRepository()
    private val reviews = FakeCoachReviewRepository()
    private val prefs = FakeTrainingPreferencesRepository()
    private val saved = SavedStateHandle()

    init {
        prefs.result = Result.success(
            TrainingPreferences(intention = TrainingIntention.Muscle, daysPerWeek = 4, sessionMinutes = 60),
        )
        blocks.builderInputs = Result.success(BuilderInputs(emptyMap(), emptyList(), null))
        blocks.current = Result.success(coachBlock(saved = true))
        reviews.newBlock = Result.success(newBlockEntry(changed = true))
    }

    private fun newModel(): BlockBuilderViewModel {
        val repository = FakeExerciseRepository(emptyList())
        val plans = object : CoachPlanRepository by UnconfiguredCoachPlanRepository() {
            override suspend fun currentPlan() = Result.success(null)
        }
        val profile = object : ProfileRepository by UnconfiguredProfileRepository() {
            override suspend fun getBiometrics() = Result.success(SavedBiometrics())
        }
        val discomforts = object : DiscomfortRepository by UnconfiguredDiscomfortRepository() {
            override suspend fun unresolved(exerciseIds: Set<String>) = Result.success(emptyList<Discomfort>())
        }
        return BlockBuilderViewModel(
            planId = "plan-2",
            builder = BuildBlockUseCase(blocks, LoadFolderBlockUseCase(FakeRoutineRepository(), repository, prefs)),
            catalog = LoadExerciseCatalog(repository, FakeExerciseLibrary(emptyMap())),
            favourites = FakeFavoriteExercisesRepository(),
            exercises = repository,
            preferences = prefs,
            plans = FakeCoachPlanRepository(),
            clock = MutableClock(),
            previousPlanId = "plan-1",
            nextBlock = LoadNextBlockContextUseCase(reviews, blocks, plans, profile, discomforts, MutableClock()),
            savedState = saved,
        )
    }

    private var current by mutableStateOf(newModel())

    @Test
    fun theTargetsStepWaitingForMiEntrenamiento_survivesRecreationAndRereadsOnReturn() {
        prefs.result = Result.success(
            TrainingPreferences(intention = TrainingIntention.Muscle, daysPerWeek = 4, sessionMinutes = 10),
        )
        current = newModel()
        rule.setContent {
            AppTheme {
                BlockBuilderRoot("plan-2", {}, {}, {}, viewModel = current, connectivity = FakeConnectivityObserver())
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("block_builder_targets_group_chest_prioritise").performClick()
        rule.waitForIdle()
        // The sample catalogue is empty, so nothing "does not fit": the flag stands for a tap on the note.
        saved["block_builder_awaiting_inputs"] = true

        current = newModel()
        rule.waitForIdle()

        rule.onNodeWithTag("block_builder_targets_group_chest_clear").assertIsEnabled()
        assertEquals(true, saved.get<Boolean>("block_builder_awaiting_inputs"))
        prefs.result = Result.success(
            TrainingPreferences(intention = TrainingIntention.Muscle, daysPerWeek = 4, sessionMinutes = 180),
        )
        current.onEvent(BlockBuilderEvent.InputsMayHaveChanged)
        rule.waitForIdle()

        assertNull(saved.get<Boolean>("block_builder_awaiting_inputs"))
        rule.onNodeWithTag("block_builder_targets_group_chest_clear").assertIsEnabled()
    }

    @Test
    fun theTargetChoices_openedFromBloqueNuevo_surviveRecreation() {
        rule.setContent {
            AppTheme {
                BlockBuilderRoot(
                    "plan-2",
                    {},
                    {},
                    {},
                    viewModel = current,
                    connectivity = FakeConnectivityObserver(),
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("block_builder_targets_group_chest_prioritise").performClick()
        rule.waitForIdle()

        current = newModel()
        rule.waitForIdle()

        // The restored choice leaves a "Quitar" control on the chest row; the others have none.
        rule.onNodeWithTag("block_builder_targets_group_chest_clear").assertIsEnabled()
        rule.onNodeWithTag("block_builder_targets_group_back_clear").assertDoesNotExist()
        assertTrue(reviews.newBlockCalls >= 2)
    }
}
