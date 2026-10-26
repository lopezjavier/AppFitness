package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.centerY
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.right
import androidx.compose.ui.test.swipeLeft
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.designsystem.AppMessages
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HapticEvent
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.RecordingAppHaptics
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.usecase.BuildDiaryDay
import io.github.lopezjavier.appfitness.feature.meals.defaults.FakeMealDefaultsRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Day = LocalDate(2026, 10, 19)

/** US-56 "Swipe to delete" and "One vibration per action", on the real Diario screen. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class DiaryDeleteFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val diary = FakeFoodDiaryRepository()
    private val haptics = RecordingAppHaptics()
    private val shownMessages = mutableListOf<String>()
    private val oats = FoodLogEntry("e1", Day, "snack", "oats", "Avena", 80.0, null, "g", 300.0, 10.0, 50.0, 6.0)

    private fun start() {
        diary.server[Day] = listOf(oats)
        val model = DiaryViewModel(
            FakeMealSlotRepository(),
            diary,
            FakeNutritionGoalsRepository(NutritionGoals(GoalStrategy.Maintenance, 0, 2400, MacroSplit(30, 45, 25))),
            BuildDiaryDay(),
            MutableClock(),
            FakeConnectivityObserver(),
            FakeMealDefaultsRepository(),
            SavedStateHandle(),
        )
        rule.setContent {
            val scope = rememberCoroutineScope()
            val messages = remember {
                object : AppMessages(scope) {
                    override fun show(text: String) {
                        shownMessages += text
                    }
                }
            }
            AppTheme {
                CompositionLocalProvider(LocalAppMessages provides messages, LocalAppHaptics provides haptics) {
                    DiaryRoot(
                        onOpenPreferences = {},
                        onAddToMeal = { _, _ -> },
                        onAddFood = {},
                        onSaveTemplate = { _, _ -> },
                        editHost = DiaryEditHost(onEditEntry = { _, _ -> }, null, {}),
                        viewModel = model,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun drag(fraction: Float) {
        rule.onNodeWithTag("diary_entry_e1").performTouchInput {
            down(Offset(right - 1f, centerY))
            moveTo(Offset(right * (1f - fraction), centerY))
            up()
        }
        rule.waitForIdle()
    }

    @Test
    fun swipingPastHalf_deletesAtOnceWithOneConfirm() {
        start()

        rule.onNodeWithTag("diary_entry_e1").performTouchInput { swipeLeft() }
        rule.waitForIdle()

        rule.onNodeWithTag("diary_entry_e1").assertDoesNotExist()
        assertEquals(listOf("e1"), diary.deleted)
        assertEquals(1, haptics.calls.count { it == HapticEvent.Confirm })
    }

    @Test
    fun swipingBelowHalf_springsBackWithNoDeleteAndNoVibration() {
        start()

        drag(0.3f)

        rule.onNodeWithTag("diary_entry_e1").assertExists()
        assertEquals(emptyList<String>(), diary.deleted)
        assertEquals(emptyList<HapticEvent>(), haptics.calls)
    }

    @Test
    fun aRefusedDelete_restoresTheRowShowsTheMessageAndFiresReject() {
        start()
        diary.deleteResult = Result.failure(AppException(AppError.Unknown))

        rule.onNodeWithTag("diary_entry_e1").performTouchInput { swipeLeft() }
        rule.waitForIdle()

        rule.onNodeWithTag("diary_entry_e1").assertExists()
        assertEquals(listOf("No se pudo eliminar. Inténtalo de nuevo."), shownMessages)
        assertEquals(1, haptics.calls.count { it == HapticEvent.Reject })
    }
}
