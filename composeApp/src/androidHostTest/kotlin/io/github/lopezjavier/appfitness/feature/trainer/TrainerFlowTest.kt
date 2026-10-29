package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import io.github.lopezjavier.appfitness.domain.model.DerivedActivityLevel
import io.github.lopezjavier.appfitness.feature.notifications.LocalNotificationsEntry
import io.github.lopezjavier.appfitness.feature.notifications.NotificationsEntry
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
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
    fun weightTrend_readsAsAPlainSentence_andTheLowConfidenceNote() {
        repository.loaded = Result.success(
            coachValues().let { it.copy(weightTrend = it.weightTrend.copy(lowConfidence = true)) },
        )

        show()

        rule.onNodeWithText("Bajando 0,4 kg por semana · Según tus pesajes").assertExists()
        rule.onAllNodes(hasText("±", substring = true)).assertCountEquals(0)
        rule.onNodeWithTag("trainer_trend_note")
            .assertTextEquals("Pocas semanas de datos: la tendencia es orientativa.")
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
