package io.github.lopezjavier.appfitness.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.BiometricBounds
import io.github.lopezjavier.appfitness.domain.model.NotificationKind
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.MealSlotRepository
import io.github.lopezjavier.appfitness.domain.repository.NotificationSettingsRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What "Datos personales" shows under its hub row: "31 años · 180 cm · Moderado". */
data class PersonalSummary(val ageYears: Int, val heightCm: Int, val activity: ActivityLevel)

/**
 * The current values the Preferencias hub shows under its rows (R-109). A value is null while it loads or when it
 * cannot be read: its row then shows no supporting line, and the hub never fails because of it.
 */
data class PreferencesHubSummary(
    val personal: PersonalSummary? = null,
    val mealCount: Int? = null,
    val remindersOn: Int? = null,
) {
    companion object {
        /** The number of switches of "Avisos". */
        val REMINDER_TOTAL: Int = NotificationKind.entries.size
    }
}

/** Events of the Preferencias hub. */
sealed interface PreferencesHubEvent {
    /** The hub came back to the screen (an editor may have saved): read every value again. */
    data object Refresh : PreferencesHubEvent
}

/** Reads the values of the hub rows. Read-only: every editor keeps its own view model. */
class PreferencesHubViewModel(
    private val profileRepository: ProfileRepository,
    private val notificationSettings: NotificationSettingsRepository,
    private val mealSlots: MealSlotRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _summary = MutableStateFlow(PreferencesHubSummary())
    val summary: StateFlow<PreferencesHubSummary> = _summary.asStateFlow()

    private var work: Job? = null

    init {
        load()
    }

    fun onEvent(event: PreferencesHubEvent) {
        when (event) {
            PreferencesHubEvent.Refresh -> load()
        }
    }

    /** A failed read keeps the last known value, so a row never flickers. */
    private fun load() {
        work?.cancel()
        work = viewModelScope.launch {
            launch {
                val biometrics = profileRepository.getBiometrics().getOrNull()?.toBiometricsOrNull()
                if (biometrics != null) {
                    val age = BiometricBounds.ageOn(biometrics.birthDate, clock.today())
                    _summary.value = _summary.value.copy(
                        personal = PersonalSummary(age, biometrics.heightCm, biometrics.activityLevel),
                    )
                }
            }
            launch {
                notificationSettings.getAll().onSuccess { switches ->
                    val on = NotificationKind.entries.count { switches[it] ?: true }
                    _summary.value = _summary.value.copy(remindersOn = on)
                }
            }
            launch {
                mealSlots.listAll().onSuccess { slots ->
                    _summary.value = _summary.value.copy(mealCount = slots.size)
                }
            }
        }
    }
}
