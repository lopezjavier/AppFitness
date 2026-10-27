package io.github.lopezjavier.appfitness.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.FoodDiaryRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import io.github.lopezjavier.appfitness.domain.repository.StrengthRepository
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import io.github.lopezjavier.appfitness.domain.usecase.BuildStrengthSummary
import io.github.lopezjavier.appfitness.domain.usecase.DiaryHistorySummary
import io.github.lopezjavier.appfitness.domain.usecase.RankTopExercises
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.days

private const val STRENGTH_WINDOW_DAYS = 42
private const val WEEK_BACK_DAYS = 6

/** State of the Perfil root: the user's photo and name plus the values shown on the statistics rows. */
sealed interface ProfileRootUiState {
    data object Loading : ProfileRootUiState

    /** The profile could not be read. */
    data object LoadFailed : ProfileRootUiState

    /**
     * [displayName] is null when the profile has no (non-blank) name; [avatarUrl] when there is no photo. Each
     * highlight is null while it loads and stays null when it cannot be read or there is nothing to show: [weightKg]
     * is the latest weight, [strengthRecordKg] the 1RM record of the most trained exercise of the last 6 weeks (the
     * Fuerza default) and [avgKcal] the average per logged day of the last 7 days (the Historial "week" figure).
     */
    data class Loaded(
        val displayName: String?,
        val avatarUrl: String?,
        val weightKg: Double? = null,
        val strengthRecordKg: Double? = null,
        val avgKcal: Int? = null,
    ) : ProfileRootUiState
}

/** Events of the Perfil root. */
sealed interface ProfileRootEvent {
    data object Retry : ProfileRootEvent
}

/**
 * The Perfil root (ADR-37): reads the name and photo, and three highlights for its statistics rows. The highlights
 * load in separate coroutines after the profile, so none of them can delay it or fail the screen.
 */
@Suppress("LongParameterList") // One dependency per port.
class ProfileRootViewModel(
    private val profileRepository: ProfileRepository,
    private val bodyMeasurementRepository: BodyMeasurementRepository,
    private val strengthRepository: StrengthRepository,
    private val workoutSessionRepository: WorkoutSessionRepository,
    private val foodDiaryRepository: FoodDiaryRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ProfileRootUiState>(ProfileRootUiState.Loading)
    val uiState: StateFlow<ProfileRootUiState> = _uiState.asStateFlow()

    private var work: Job? = null
    private val highlightWork = mutableListOf<Job>()

    init {
        load()
    }

    fun onEvent(event: ProfileRootEvent) {
        when (event) {
            ProfileRootEvent.Retry -> if (_uiState.value == ProfileRootUiState.LoadFailed) load()
        }
    }

    private fun updateLoaded(transform: (ProfileRootUiState.Loaded) -> ProfileRootUiState.Loaded) {
        _uiState.update { if (it is ProfileRootUiState.Loaded) transform(it) else it }
    }

    private fun load() {
        work?.cancel()
        highlightWork.forEach { it.cancel() }
        highlightWork.clear()
        _uiState.value = ProfileRootUiState.Loading
        work = viewModelScope.launch {
            val profile = profileRepository.getOwnProfile().getOrNull()
            if (profile == null) {
                _uiState.value = ProfileRootUiState.LoadFailed
            } else {
                _uiState.value = ProfileRootUiState.Loaded(
                    displayName = profile.displayName?.trim()?.takeIf { it.isNotEmpty() },
                    avatarUrl = profile.avatarUrl,
                )
                loadHighlights()
            }
        }
    }

    private fun loadHighlights() {
        highlightWork += viewModelScope.launch {
            val weight = bodyMeasurementRepository.getLatestWeight().getOrNull()?.weightKg
            updateLoaded { it.copy(weightKg = weight) }
        }
        highlightWork += viewModelScope.launch {
            val record = strengthRecord()
            updateLoaded { it.copy(strengthRecordKg = record) }
        }
        highlightWork += viewModelScope.launch {
            val kcal = weekAverageKcal()
            updateLoaded { it.copy(avgKcal = kcal) }
        }
    }

    /** The 1RM record of the most trained exercise of the last 6 weeks, as the Fuerza screen shows by default. */
    private suspend fun strengthRecord(): Double? {
        val since = clock.now() - STRENGTH_WINDOW_DAYS.days
        val sessions = strengthRepository.recentSessions(since).getOrNull()
        val top = sessions?.let { RankTopExercises(it, since).firstOrNull() }
        val history = top?.let { workoutSessionRepository.exerciseHistory(it).getOrNull() }
        return history?.let { BuildStrengthSummary(it, clock.today()).recordOneRepMax }
    }

    /** The average kcal per logged day of the last 7 days (the Historial "week" figure), null with no records. */
    private suspend fun weekAverageKcal(): Int? {
        val today = clock.today()
        val days = foodDiaryRepository.summaries(today.minus(WEEK_BACK_DAYS, DateTimeUnit.DAY), today).getOrNull()
        return days?.let { DiaryHistorySummary.week(it, today) }?.kcal?.roundToInt()
    }
}
