package io.github.lopezjavier.appfitness.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.model.isCardioWork
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ExerciseLibrary
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import io.github.lopezjavier.appfitness.domain.repository.FoodDiaryRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import io.github.lopezjavier.appfitness.domain.repository.StrengthRepository
import io.github.lopezjavier.appfitness.domain.repository.TrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import io.github.lopezjavier.appfitness.domain.usecase.BuildStrengthSummary
import io.github.lopezjavier.appfitness.domain.usecase.DiaryHistorySummary
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
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
private const val WINDOW_BACK_DAYS = 28

/** State of the Perfil root: the user's photo and name plus the values shown on the statistics rows. */
sealed interface ProfileRootUiState {
    data object Loading : ProfileRootUiState

    /** The profile could not be read. */
    data object LoadFailed : ProfileRootUiState

    /**
     * [displayName] is null when the profile has no (non-blank) name; [avatarUrl] when there is no photo. Each
     * highlight is null while it loads and stays null when it cannot be read or there is nothing to show: [weightKg]
     * is the latest weight, [strengthRecordKg] the 1RM record of the most trained non-cardio exercise of the
     * last 6 weeks (the Fuerza default) and [strengthExerciseName] its name (both null when any read fails), and
     * [avgKcal] the average per complete day of the 7 days before today (the Historial "week" figure).
     */
    data class Loaded(
        val displayName: String?,
        val avatarUrl: String?,
        val weightKg: Double? = null,
        val strengthRecordKg: Double? = null,
        val avgKcal: Int? = null,
        val intention: TrainingIntention? = null,
        val strengthExerciseName: String? = null,
    ) : ProfileRootUiState
}

/** Events of the Perfil root. */
sealed interface ProfileRootEvent {
    data object Retry : ProfileRootEvent

    /** The screen resumed (for example back from the "Mi entrenamiento" editor): re-read the intention. */
    data object ReloadIntention : ProfileRootEvent
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
    private val trainingPreferences: TrainingPreferencesRepository,
    private val exerciseRepository: ExerciseRepository,
    exerciseLibrary: ExerciseLibrary,
    private val exerciseNames: ExerciseNameResolver = ExerciseNameResolver(exerciseRepository, exerciseLibrary),
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
            ProfileRootEvent.ReloadIntention -> if (_uiState.value is ProfileRootUiState.Loaded) loadIntention()
        }
    }

    /** A failed read keeps the last known value: the row never flickers or shows an error. */
    private fun loadIntention() {
        highlightWork += viewModelScope.launch {
            trainingPreferences.get().onSuccess { prefs -> updateLoaded { it.copy(intention = prefs.intention) } }
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
        loadIntention()
        highlightWork += viewModelScope.launch {
            val weight = bodyMeasurementRepository.getLatestWeight().getOrNull()?.weightKg
            updateLoaded { it.copy(weightKg = weight) }
        }
        highlightWork += viewModelScope.launch {
            val strength = strengthHighlight()
            updateLoaded { it.copy(strengthRecordKg = strength?.second, strengthExerciseName = strength?.first) }
        }
        highlightWork += viewModelScope.launch {
            val kcal = weekAverageKcal()
            updateLoaded { it.copy(avgKcal = kcal) }
        }
    }

    /**
     * Name and 1RM record of the most trained non-cardio exercise of the last 6 weeks, as the Fuerza screen shows by
     * default; null when any read fails, so a record is never shown without its exercise name.
     */
    private suspend fun strengthHighlight(): Pair<String, Double>? {
        val top = topStrengthExercise() ?: return null
        val name = exerciseNames.resolve().getOrNull()?.get(top)
        val history = workoutSessionRepository.exerciseHistory(top).getOrNull()
        val record = history?.let { BuildStrengthSummary(it, clock.today()).recordOneRepMax }
        return if (name != null && record != null) name to record else null
    }

    private suspend fun topStrengthExercise(): String? {
        val since = clock.now() - STRENGTH_WINDOW_DAYS.days
        val sessions = strengthRepository.recentSessions(since).getOrNull()
        val catalog = exerciseRepository.getAll().getOrNull()
        if (sessions == null || catalog == null) return null
        val cardioIds = catalog.filter { it.isCardioWork }.map { it.id }.toSet()
        return RankTopExercises(sessions, since, excluded = cardioIds).firstOrNull()
    }

    /** The average kcal per complete day of the 7 days before today (the Historial "week" figure); null if none. */
    private suspend fun weekAverageKcal(): Int? {
        val today = clock.today()
        val days = foodDiaryRepository.summaries(today.minus(WINDOW_BACK_DAYS, DateTimeUnit.DAY), today).getOrNull()
        val week = days?.let { DiaryHistorySummary.week(it, today) }
        return week?.takeIf { it.completeDays > 0 }?.kcal?.roundToInt()
    }
}
