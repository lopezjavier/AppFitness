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
 * App-wide shell state. [isOffline] drives the "Sin conexión" banner, [rootRoute] what the root shows and
 * [avatarUrl] the photo of the top bar avatar. The default is the loading state, so Welcome never flashes
 * at cold start.
 */
data class AppUiState(
    val isOffline: Boolean = false,
    val rootRoute: RootRoute = RootRoute.Loading,
    val avatarUrl: String? = null,
)

/** Events of the root. */
sealed interface AppEvent {
    /** Reads the profile again after a failure. */
    data object RetryProfile : AppEvent

    /** Onboarding saved the goals: the current session's profile counts as onboarded, without a refetch. */
    data object OnboardingCompleted : AppEvent
}

private data class Session(val auth: AuthState, val profile: ProfileLoad, val avatarUrl: String? = null)

/**
 * Root ViewModel: turns connectivity, the session and the profile into shell UI state. The initial value is
 * online and loading, so neither the banner nor Welcome flash at cold start; the first real emissions correct it.
 * On every [AuthState.SignedIn] the profile is read (onboarding flag and avatar).
 */
class AppViewModel(
    connectivity: ConnectivityObserver,
    authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {
    private val retryTick = MutableStateFlow(0)
    private val onboardingDone = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val session: Flow<Session> =
        combine(authRepository.authState.distinctUntilChanged(), retryTick) { auth, _ -> auth }
            .flatMapLatest { auth ->
                onboardingDone.value = false
                when (auth) {
                    is AuthState.SignedIn -> profileSession(auth)
                    else -> flowOf(Session(auth, ProfileLoad.Loading))
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
                avatarUrl = session.avatarUrl,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppUiState())

    fun onEvent(event: AppEvent) {
        when (event) {
            AppEvent.RetryProfile -> retryTick.update { it + 1 }
            AppEvent.OnboardingCompleted -> onboardingDone.value = true
        }
    }

    private fun profileSession(auth: AuthState.SignedIn): Flow<Session> = flow {
        emit(Session(auth, ProfileLoad.Loading))
        val result = profileRepository.getOwnProfile()
        val profile = result.getOrNull()
        emit(
            if (profile != null) {
                Session(auth, ProfileLoad.Loaded(profile.onboardingCompleted), profile.avatarUrl)
            } else {
                Session(auth, ProfileLoad.Failed)
            },
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
