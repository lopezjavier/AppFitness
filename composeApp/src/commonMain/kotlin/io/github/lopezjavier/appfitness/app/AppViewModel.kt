package io.github.lopezjavier.appfitness.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.AuthState
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.AuthRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * App-wide shell state. [isOffline] drives the "Sin conexión" banner and [rootRoute] what the root shows. The
 * default is the loading state, so Welcome never flashes at cold start.
 */
data class AppUiState(val isOffline: Boolean = false, val rootRoute: RootRoute = RootRoute.Loading)

/** Events of the root. */
sealed interface AppEvent {
    /** Reads the profile again after a failure. */
    data object RetryProfile : AppEvent

    /** Onboarding saved the goals: the current session's profile counts as onboarded, without a refetch. */
    data object OnboardingCompleted : AppEvent
}

private data class Session(val auth: AuthState, val profile: ProfileLoad)

/**
 * Root ViewModel: turns connectivity, the session and the profile into shell UI state. The initial value is
 * online and loading, so neither the banner nor Welcome flash at cold start; the first real emissions correct it.
 * On every [AuthState.SignedIn] the profile is read (onboarding flag).
 */
class AppViewModel(
    connectivity: ConnectivityObserver,
    authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {
    private val retryTick = MutableStateFlow(0)
    private val onboardingDone = MutableStateFlow(false)

    /** Last profile read for a user id (userId to onboardingCompleted); survives upstream restarts. */
    private var loaded: Pair<String, Boolean>? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val session: Flow<Session> =
        combine(authRepository.authState.distinctUntilChanged(), retryTick) { auth, _ -> auth }
            .flatMapLatest { auth ->
                onboardingDone.value = false
                when (auth) {
                    is AuthState.SignedIn -> profileSession(auth)

                    else -> {
                        if (auth == AuthState.SignedOut) loaded = null
                        flowOf(Session(auth, ProfileLoad.Loading))
                    }
                }
            }
            .combine(onboardingDone) { session, done ->
                if (done && session.profile is ProfileLoad.Loaded) {
                    session.copy(profile = ProfileLoad.Loaded(onboardingCompleted = true))
                } else {
                    session
                }
            }

    val uiState: StateFlow<AppUiState> =
        combine(connectivity.isOnline, session) { online, session ->
            AppUiState(
                isOffline = !online,
                rootRoute = resolveRootRoute(session.auth, session.profile),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppUiState())

    fun onEvent(event: AppEvent) {
        when (event) {
            AppEvent.RetryProfile -> retryTick.update { it + 1 }

            AppEvent.OnboardingCompleted -> {
                loaded = loaded?.copy(second = true)
                onboardingDone.value = true
            }
        }
    }

    private fun profileSession(auth: AuthState.SignedIn): Flow<Session> = flow {
        // The same user re-subscribing (back from the background) keeps the loaded profile: no Loading frame and
        // no refetch, so the shell is never torn down. A retry after a failure has nothing cached and reads again.
        val cached = loaded
        if (cached != null && cached.first == auth.userId) {
            emit(Session(auth, ProfileLoad.Loaded(cached.second)))
            return@flow
        }
        emit(Session(auth, ProfileLoad.Loading))
        val result = profileRepository.getOwnProfile()
        val profile = result.getOrNull()
        loaded = profile?.let { auth.userId to it.onboardingCompleted }
        emit(
            if (profile != null) {
                Session(auth, ProfileLoad.Loaded(profile.onboardingCompleted))
            } else {
                Session(auth, ProfileLoad.Failed)
            },
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
