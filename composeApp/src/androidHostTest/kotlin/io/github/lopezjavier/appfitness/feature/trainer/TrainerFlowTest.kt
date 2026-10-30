package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.App
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.RootRoute
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.CoachDerivedValues
import io.github.lopezjavier.appfitness.domain.model.CoachMissing
import io.github.lopezjavier.appfitness.domain.model.CoachUsage
import io.github.lopezjavier.appfitness.domain.model.DerivedActivityLevel
import io.github.lopezjavier.appfitness.feature.notifications.LocalNotificationsEntry
import io.github.lopezjavier.appfitness.feature.notifications.NotificationsEntry
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-68 and US-69 (display) end to end: the real view model over a fake repository, driven through the screen. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class TrainerFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeCoachRepository()
    private val connectivity = FakeConnectivityObserver()

    private fun show(saved: SavedStateHandle = SavedStateHandle()) {
        val model = TrainerViewModel(repository, MutableClock(), saved)
        rule.setContent { AppTheme { TrainerRoute(viewModel = model, connectivity = connectivity) } }
        rule.waitForIdle()
    }

    private fun showWithBell(entry: NotificationsEntry) {
        val model = TrainerViewModel(repository, MutableClock(), SavedStateHandle())
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalNotificationsEntry provides entry) {
                    TrainerRoute(viewModel = model, connectivity = connectivity)
                }
            }
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun CoachDerivedValues.mapActivity(transform: (DerivedActivityLevel) -> DerivedActivityLevel) =
        copy(activityLevel = transform(activityLevel))

    @Test
    fun fourTabs_inOrder_andTrainerOpensItsRoot() {
        rule.setContent { App(rootRoute = RootRoute.Main) }
        val tags = listOf("nav_tab_diary", "nav_tab_workout", "nav_tab_trainer", "nav_tab_profile")

        val lefts = tags.map { rule.onNodeWithTag(it).getUnclippedBoundsInRoot().left }

        assertEquals(lefts.sorted(), lefts)
        tap("nav_tab_trainer")
        rule.onNodeWithTag("trainer_screen").assertExists()
    }

    @Test
    fun values_areShownWithTheirSourceAndMissingData() {
        repository.loaded = Result.success(
            coachValues().mapActivity { it.copy(missing = listOf(CoachMissing.Steps, CoachMissing.TrainingHistory)) },
        )

        show()

        rule.onNodeWithText("Ligero · Según tu perfil").assertExists()
        rule.onNodeWithTag("trainer_missing_note_activity_level")
            .assertTextEquals("Falta: pasos, historial de entrenos")
        rule.onNodeWithTag("trainer_row_total_expenditure").assertExists()
        rule.onNodeWithTag("trainer_row_weight_trend").assertExists()
        rule.onNodeWithTag("trainer_row_experience").assertExists()
    }

    @Test
    fun unknownMissingKey_isNeverShownRaw() {
        repository.loaded = Result.success(
            coachValues().mapActivity { it.copy(missing = listOf(CoachMissing.Unknown("brand_new_key"))) },
        )

        show()

        rule.onNodeWithTag("trainer_missing_note_activity_level").assertDoesNotExist()
    }

    @Test
    fun startingValueWithoutHistory_isProvisionalWithTheProfileLevel() {
        show()

        rule.onNodeWithTag("trainer_provisional_activity_level").assertTextEquals("Provisional")
        rule.onNodeWithText("Ligero · Según tu perfil").assertExists()
    }

    @Test
    fun confirming_removesTheLabel_andTheButtonReadsConfirmado() {
        show()
        rule.onNodeWithTag("trainer_confirm_button").assertTextEquals("Confirmar")

        tap("trainer_confirm_button")

        rule.onNodeWithTag("trainer_provisional_activity_level").assertDoesNotExist()
        rule.onNodeWithTag("trainer_confirm_button").assertTextEquals("Confirmado").assertIsNotEnabled()
        assertEquals(listOf(null), repository.confirmCalls.map { it.second })
    }

    @Test
    fun editingTheLevel_showsTheFiveCards_withoutAFooter_andTappingACardAppliesAtOnce() {
        show()
        tap("trainer_edit_activity_button")
        listOf("sedentary", "light", "moderate", "high", "very_high").forEach {
            rule.onNodeWithTag("activity_level_sheet_activity_$it").assertExists()
        }
        rule.onNodeWithTag("activity_level_sheet_commit").assertDoesNotExist()
        rule.onNodeWithText("Guardar").assertDoesNotExist()

        tap("activity_level_sheet_activity_moderate")

        rule.onNodeWithTag("activity_level_sheet_close").assertDoesNotExist()
        rule.onNodeWithText("Moderado · Editado por ti").assertExists()
        assertEquals(listOf<ActivityLevel?>(ActivityLevel.Moderate), repository.confirmCalls.map { it.second })
    }

    @Test
    fun editSheet_closesWithX_withoutConfirming() {
        show()
        tap("trainer_edit_activity_button")

        tap("activity_level_sheet_close")

        rule.onNodeWithTag("activity_level_sheet_activity_high").assertDoesNotExist()
        assertTrue(repository.confirmCalls.isEmpty())
    }

    @Test
    fun failedTap_keepsTheSheetOpen_andTheValuesUnchanged() {
        repository.confirmResult = { Result.failure(AppException(AppError.Unknown)) }
        show()
        tap("trainer_edit_activity_button")

        tap("activity_level_sheet_activity_high")

        rule.onNodeWithTag("activity_level_sheet_activity_high").assertExists().assertIsEnabled()
        rule.onNodeWithText("Ligero · Según tu perfil").assertExists()
        assertEquals(listOf<ActivityLevel?>(ActivityLevel.High), repository.confirmCalls.map { it.second })
    }

    @Test
    fun offline_sheetCardsAreDisabled_andTheHintIsInTheBody() {
        show()
        tap("trainer_edit_activity_button")
        rule.onNodeWithTag("trainer_sheet_offline").assertDoesNotExist()

        connectivity.state.value = false
        rule.waitForIdle()

        rule.onNodeWithTag("trainer_sheet_offline")
            .assertTextEquals("Sin conexión: no puedes cambiar el nivel ahora.")
        rule.onNodeWithTag("activity_level_sheet_activity_high").assertIsNotEnabled()
        rule.onNodeWithTag("trainer_sheet_note").assertExists()
    }

    @Test
    fun bell_isShownWithTheCount_andOpensTheNotificationCenter() {
        var opened = 0
        showWithBell(NotificationsEntry(count = 2, onOpen = { opened++ }))

        tap("trainer_action_notifications")

        assertEquals(1, opened)
    }

    @Test
    fun bell_isAlsoShownInTheErrorAndOfflineStates() {
        repository.loaded = Result.failure(AppException(AppError.Unknown))
        showWithBell(NotificationsEntry(count = 0, onOpen = {}))
        rule.onNodeWithTag("trainer_action_notifications").assertExists()
    }

    @Test
    fun bell_isAlsoShownWhenOffline() {
        repository.loaded = Result.failure(AppException(AppError.Network))
        showWithBell(NotificationsEntry(count = 0, onOpen = {}))
        rule.onNodeWithText("Sin conexión").assertExists()
        rule.onNodeWithTag("trainer_action_notifications").assertExists()
    }

    @Test
    fun bell_isHiddenWithoutANotificationsEntry() {
        show()

        rule.onNodeWithTag("trainer_action_notifications").assertDoesNotExist()
    }

    @Test
    fun reaskedValue_isProvisionalAgain_withTheChangedDataLine() {
        repository.loaded = Result.success(coachValues(provisional = true, reask = true))

        show()

        rule.onNodeWithTag("trainer_provisional_activity_level").assertTextEquals("Provisional")
        rule.onNodeWithText("Ligero · Tus datos han cambiado: vuelve a confirmar.").assertExists()
    }

    @Test
    fun weightTrend_showsThePill_theMargin_andTheLowConfidenceNote_withoutAHelpButton() {
        repository.loaded = Result.success(
            coachValues().let { it.copy(weightTrend = it.weightTrend.copy(lowConfidence = true)) },
        )

        show()

        rule.onNodeWithTag(
            "trainer_trend_pill",
        ).assertExists().assertContentDescriptionEquals("Tendencia: −0,40 kg por semana")
        rule.onNodeWithText("−0,40 kg esta semana").assertExists()
        rule.onNodeWithText("Margen ±0,2 kg/sem · Según tus pesajes").assertExists()
        rule.onAllNodes(hasText("Bajando", substring = true)).assertCountEquals(0)
        rule.onAllNodes(hasText("Sin tendencia clara")).assertCountEquals(0)
        rule.onNodeWithTag("trainer_trend_note")
            .assertTextEquals("Pocas semanas de datos: la tendencia es orientativa.")
        rule.onNodeWithTag("trainer_help_button").assertExists()
        rule.onAllNodes(hasTestTag("trainer_help_button").and(hasAnyAncestor(hasTestTag("trainer_row_weight_trend"))))
            .assertCountEquals(0)
    }

    @Test
    fun weightTrend_withoutMargin_showsOnlyTheSource() {
        repository.loaded = Result.success(
            coachValues().let { it.copy(weightTrend = it.weightTrend.copy(intervalLow = null, intervalHigh = null)) },
        )

        show()

        rule.onNodeWithTag("trainer_trend_pill").assertExists()
        rule.onNodeWithText("Según tus pesajes").assertExists()
    }

    @Test
    fun weightTrend_withoutSlope_hasNoPill() {
        repository.loaded = Result.success(
            coachValues().let { it.copy(weightTrend = it.weightTrend.copy(slopeKgWeek = null)) },
        )

        show()

        rule.onNodeWithTag("trainer_trend_pill").assertDoesNotExist()
    }

    @Test
    fun overflow_opensTheSheetWithUsoDeIa_andOpensTheUsageScreen() {
        var opened = 0
        val model = TrainerViewModel(repository, MutableClock(), SavedStateHandle())
        rule.setContent {
            AppTheme { TrainerRoute(onOpenCoachUsage = { opened++ }, viewModel = model, connectivity = connectivity) }
        }
        rule.waitForIdle()

        tap("trainer_overflow")
        rule.onNodeWithText("Uso de IA").assertExists()
        tap("trainer_action_coach_usage")

        assertEquals(1, opened)
    }

    @Test
    fun overflow_isShownInEveryState() {
        repository.loaded = Result.failure(AppException(AppError.Unknown))
        show()
        rule.onNodeWithTag("trainer_overflow").assertExists()
    }

    @Test
    fun overflow_isShownWhenOffline() {
        repository.loaded = Result.failure(AppException(AppError.Network))
        show()
        rule.onNodeWithTag("trainer_overflow").assertExists()
    }

    @Test
    fun usageScreen_opensFromTheTrainerOverflow_andBackReturns() {
        val model = TrainerViewModel(repository, MutableClock(), SavedStateHandle())
        val usage = CoachUsage(LocalDate(2026, 11, 1), 0, 0, 0.42)
        rule.setContent {
            App(
                rootRoute = RootRoute.Main,
                trainer = { modifier, open, _, _ ->
                    TrainerRoute(modifier, open, viewModel = model, connectivity = connectivity)
                },
                coachUsage = { modifier, onBack ->
                    CoachUsageScreen(CoachUsageUiState.Content(usage), flowOf(true), {}, onBack, modifier)
                },
            )
        }
        tap("nav_tab_trainer")

        tap("trainer_overflow")
        tap("trainer_action_coach_usage")
        rule.onNodeWithTag("coach_usage_screen").assertExists()
        tap("coach_usage_back")

        rule.onNodeWithTag("trainer_screen").assertExists()
    }

    @Test
    fun reliableIntake_showsNoUnreliableNote() {
        show()

        rule.onNodeWithTag("trainer_unreliable_note").assertDoesNotExist()
        rule.onNodeWithTag("trainer_trend_note").assertDoesNotExist()
    }

    @Test
    fun unreliableIntake_showsTheNote_withTheLoggedDays() {
        repository.loaded = Result.success(
            coachValues().let { it.copy(intake = it.intake.copy(reliable = false)) },
        )

        show()

        rule.onNodeWithTag("trainer_unreliable_note")
            .assertTextEquals("Diario incompleto: la estimación del gasto no es fiable. 12 de 28 días registrados.")
    }

    @Test
    fun helpSheet_opensAndClosesWithX() {
        show()

        tap("trainer_help_button")
        rule.onNodeWithTag("trainer_help_button_sheet_body").assertExists()
        tap("trainer_help_button_sheet_close")

        rule.onNodeWithTag("trainer_help_button_sheet_body").assertDoesNotExist()
    }

    @Test
    fun withoutConnection_showsTheOfflineState() {
        repository.loaded = Result.failure(AppException(AppError.Network))

        show()

        rule.onNodeWithText("Sin conexión").assertExists()
        rule.onNodeWithTag("trainer_card").assertDoesNotExist()
    }

    @Test
    fun loadError_offersReintentar_thatRecovers() {
        repository.loaded = Result.failure(AppException(AppError.Unknown))
        show()
        rule.onNodeWithText("Reintentar").assertExists()
        repository.loaded = Result.success(coachValues())

        tap("trainer_retry")

        rule.onNodeWithTag("trainer_card").assertExists()
    }

    @Test
    fun offline_disablesConfirm_andExplainsWhy() {
        connectivity.state.value = false

        show()

        rule.onNodeWithTag("trainer_confirm_button").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede confirmar").assertExists()
    }

    @Test
    fun confirmedValues_withConnection_enableNothingExtra_andTargetsAreBigEnough() {
        show()

        rule.onNodeWithTag("trainer_confirm_button").assertIsEnabled().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("trainer_edit_activity_button").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("trainer_help_button").assertHeightIsAtLeast(48.dp)
    }
}
