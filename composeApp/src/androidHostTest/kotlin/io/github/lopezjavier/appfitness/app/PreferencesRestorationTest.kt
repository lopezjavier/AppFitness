package io.github.lopezjavier.appfitness.app

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.AccountProtection
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.NotificationKind
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.model.TrainingPlace
import io.github.lopezjavier.appfitness.domain.usecase.SavePreferences
import io.github.lopezjavier.appfitness.feature.food.FakeFoodPreferencesRepository
import io.github.lopezjavier.appfitness.feature.profile.AccountUiState
import io.github.lopezjavier.appfitness.feature.profile.PreferencesEvent
import io.github.lopezjavier.appfitness.feature.profile.PreferencesHubActions
import io.github.lopezjavier.appfitness.feature.profile.PreferencesHubScreen
import io.github.lopezjavier.appfitness.feature.profile.PreferencesSectionRoot
import io.github.lopezjavier.appfitness.feature.profile.PreferencesUiState
import io.github.lopezjavier.appfitness.feature.profile.PreferencesViewModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

/**
 * T-127 (ADR-40): every Preferencias editor keeps its unsaved draft across recreation. Each test builds the section's
 * ViewModel inside the composition and shares one [SavedStateHandle] (the saved-state registry stand-in), so
 * `emulateSavedInstanceStateRestore` yields a brand-new ViewModel that only knows what was saved. The Perfil root and
 * the hub hold no state of their own (the root renders what its ViewModel loads), so they need no restoration test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per restoration scenario.
class PreferencesRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private var viewModel: PreferencesViewModel? = null

    private fun show(tester: StateRestorationTester, section: String, savedDraft: String? = null) {
        val handle = SavedStateHandle(mapOf("section" to section, "preferences_draft" to savedDraft))
        val profile =
            FakeProfileRepository(Result.success(ProfileSummary("Ana", null, true))).apply {
                biometrics =
                    Result.success(SavedBiometrics(Sex.Male, LocalDate(2000, 3, 1), 178, ActivityLevel.Moderate))
            }
        val weights =
            FakeBodyMeasurementRepository(
                BodyMeasurement("w1", Instant.parse("2026-10-10T08:00:00Z"), 80.0, MeasurementSource.Manual),
            )
        tester.setContent {
            AppTheme {
                val vm =
                    remember {
                        PreferencesViewModel(
                            profile,
                            weights,
                            FakeTrainingPreferencesRepository(),
                            FakeFoodPreferencesRepository(),
                            FakeNotificationSettingsRepository(),
                            SavePreferences(profile),
                            FakeConnectivityObserver(),
                            FakeClock(),
                            TimeZone.UTC,
                            handle,
                        )
                    }
                viewModel = vm
                PreferencesSectionRoot(onClose = {}, viewModel = vm)
            }
        }
        rule.waitForIdle()
    }

    private fun send(event: PreferencesEvent) {
        rule.runOnIdle { viewModel!!.onEvent(event) }
        rule.waitForIdle()
    }

    private fun warmUpNow(): Boolean = (viewModel!!.uiState.value as PreferencesUiState.Loaded).form.warmUpIncluded

    private fun recreate(tester: StateRestorationTester) {
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
    }

    @Test
    fun personalData_keepsTheDraftAfterRecreation() {
        val tester = StateRestorationTester(rule)
        show(tester, "personal")
        send(PreferencesEvent.SetName("Beatriz"))

        recreate(tester)

        rule.onNodeWithTag("preferences_name_field").performScrollTo().assertTextContains("Beatriz")
        rule.onNodeWithTag("preferences_personal_save").assertIsEnabled()
    }

    @Test
    fun aSavedRemovedSection_opensDatosPersonales_withoutCrashing() {
        val tester = StateRestorationTester(rule)
        show(tester, "energy")
        send(PreferencesEvent.SetName("Beatriz"))

        recreate(tester)

        rule.onNodeWithTag("preferences_personal_save").assertIsEnabled()
    }

    @Test
    fun cooking_keepsTheDraftAfterRecreation() {
        val tester = StateRestorationTester(rule)
        show(tester, "cooking")
        send(PreferencesEvent.SetFatPctFried("12"))

        recreate(tester)

        rule.onNodeWithTag("preferences_cooking_fried").performScrollTo().assertTextContains("12")
        rule.onNodeWithTag("preferences_cooking_save").assertIsEnabled()
    }

    @Test
    fun training_keepsTheDraftAfterRecreation() {
        val tester = StateRestorationTester(rule)
        show(tester, "training")
        send(PreferencesEvent.SetWarmUpIncluded(!warmUpNow()))

        recreate(tester)

        rule.onNodeWithTag("preferences_training_save").assertIsEnabled()
    }

    @Test
    fun anEditRevertedToTheSavedValue_leavesNoDraftAfterRecreation() {
        val tester = StateRestorationTester(rule)
        show(tester, "training")
        val original = warmUpNow()
        send(PreferencesEvent.SetWarmUpIncluded(!original))
        send(PreferencesEvent.SetWarmUpIncluded(original))

        recreate(tester)

        rule.onNodeWithTag("preferences_training_save").assertIsNotEnabled()
    }

    @Test
    fun anOpenActivitySheet_survivesRecreation_andNothingIsAppliedUntilALevelIsTapped() {
        val tester = StateRestorationTester(rule)
        show(tester, "personal")
        rule.onNodeWithTag("preferences_activity_row").performScrollTo().performClick()
        rule.waitForIdle()

        recreate(tester)

        rule.onNodeWithTag("preferences_sheet_activity").assertIsDisplayed()
        rule.onNodeWithTag("preferences_personal_save").assertIsNotEnabled()
        rule.onNodeWithTag("preferences_activity_light").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("preferences_personal_save").assertIsEnabled()
    }

    @Test
    fun reminders_keepToggledSwitches_afterRecreation() {
        val tester = StateRestorationTester(rule)
        show(tester, "avisos")
        send(PreferencesEvent.SetReminderEnabled(NotificationKind.Circumference, false))

        recreate(tester)

        rule.onNodeWithTag("preferences_reminders_switch_circumference").assertIsOff()
        rule.onNodeWithTag("preferences_avisos_save").assertIsEnabled()
    }

    /** US-142: a draft saved by a build before T-311 still carries the retired check-in day and switch. */
    @Test
    fun reminders_aDraftSavedBeforeT311_opensAvisosWithoutError() {
        val legacyDraft =
            """{"name":"Ana","sex":"Male","birthDate":"2000-03-01","heightCm":178,"weightKg":80.0,""" +
                """"activity":"Moderate","checkInWeekday":3,"weightReminder":false,"circumferenceReminder":true,""" +
                """"checkInReminder":false,"weekSummaryReminder":true,"blockEndReminder":true}"""
        val tester = StateRestorationTester(rule)

        show(tester, "avisos", savedDraft = legacyDraft)

        rule.onNodeWithTag("preferences_reminders_switch_weight").assertIsOff()
        rule.onNodeWithTag("preferences_reminders_switch_circumference").assertIsOn()
        rule.onNodeWithTag("preferences_switch_week_summary").assertIsOn()
        rule.onNodeWithTag("preferences_switch_block_end").performScrollTo().assertIsOn()
        rule.onNodeWithTag("preferences_reminders_switch_checkin").assertDoesNotExist()
        rule.onNodeWithTag("preferences_checkin_weekday_row").assertDoesNotExist()
        rule.onNodeWithTag("preferences_avisos_save").assertIsEnabled()
    }

    @Test
    fun reminders_keepTheBlockEndSwitchDraft_unsaved_afterRecreation() {
        val tester = StateRestorationTester(rule)
        show(tester, "avisos")
        send(PreferencesEvent.SetReminderEnabled(NotificationKind.BlockEnd, false))

        recreate(tester)

        rule.onNodeWithTag("preferences_switch_block_end").performScrollTo().assertIsOff()
        rule.onNodeWithTag("preferences_avisos_save").assertIsEnabled()
    }

    @Test
    fun myTraining_keepsTheDraft_andTheOpenIntentionPicker_afterRecreation() {
        val tester = StateRestorationTester(rule)
        show(tester, "mi_entrenamiento")
        send(PreferencesEvent.SetIntention(TrainingIntention.Muscle))
        send(PreferencesEvent.SetPlace(TrainingPlace.Home))
        send(PreferencesEvent.SetEquipment(Equipment.Barbell, true))
        send(PreferencesEvent.SetEquipment(Equipment.CardioMachine, true))
        send(PreferencesEvent.SetDaysPerWeek(4))
        send(PreferencesEvent.SetSessionMinutes(45))
        rule.onNodeWithTag("preferences_intention_field").performScrollTo().performClick()
        rule.waitForIdle()

        recreate(tester)

        rule.onNodeWithTag("preferences_intention_option_muscle").assertIsDisplayed()
        rule.onNodeWithTag("preferences_intention_option_muscle").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("preferences_intention_field").assertTextContains("Ganar musculatura", substring = true)
        rule.onNodeWithTag("preferences_days_stepper_value").performScrollTo().assertTextEquals("4 días")
        rule.onNodeWithTag("preferences_session_stepper_value").performScrollTo().assertTextEquals("45 min")
        rule.onNodeWithTag("preferences_equipment_barbell").performScrollTo().assertIsOn()
        rule.onNodeWithTag("preferences_equipment_cardio_machine").performScrollTo().assertIsOn()
        rule.onNodeWithTag("preferences_mi_entrenamiento_save").assertIsEnabled()
    }

    @Test
    fun theOpenDiscardDialog_survivesRecreation() {
        val tester = StateRestorationTester(rule)
        show(tester, "training")
        send(PreferencesEvent.SetWarmUpIncluded(!warmUpNow()))
        rule.onNodeWithTag("preferences_training_back").performClick()
        rule.onNodeWithTag("preferences_training_discard_dialog").assertIsDisplayed()

        recreate(tester)

        rule.onNodeWithTag("preferences_training_discard_dialog").assertIsDisplayed()
    }

    @Test
    fun accountSignOutDialog_survivesRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme { PreferencesHubScreen({}, PreferencesHubActions(), accountState = AccountUiState()) }
        }
        rule.onNodeWithTag("preferences_sign_out").performClick()
        rule.onNodeWithTag("account_sign_out_dialog").assertIsDisplayed()

        recreate(tester)

        rule.onNodeWithTag("account_sign_out_dialog").assertIsDisplayed()
        rule.onNodeWithTag("account_delete_dialog").assertDoesNotExist()
    }

    @Test
    fun accountDeleteDialog_survivesRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme {
                PreferencesHubScreen(
                    {},
                    PreferencesHubActions(),
                    accountState = AccountUiState(protection = AccountProtection.Unprotected),
                )
            }
        }
        rule.onNodeWithTag("preferences_delete_account").performClick()
        rule.onNodeWithTag("account_delete_dialog").assertIsDisplayed()

        recreate(tester)

        rule.onNodeWithTag("account_delete_dialog").assertIsDisplayed()
    }
}
