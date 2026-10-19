package io.github.lopezjavier.appfitness.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.DeleteAccountOutcome
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.AuthRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import io.github.lopezjavier.appfitness.domain.usecase.DeleteAccount
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State of the Perfil root: the user's photo and name, plus the account actions' availability. */
sealed interface ProfileRootUiState {
    data object Loading : ProfileRootUiState

    /** The profile could not be read; sign out stays available. */
    data object LoadFailed : ProfileRootUiState

    /** [displayName] is null when the profile has no (non-blank) name; [avatarUrl] when there is no photo. */
    data class Loaded(
        val displayName: String?,
        val avatarUrl: String?,
        val isOnline: Boolean = true,
        val isDeleting: Boolean = false,
    ) : ProfileRootUiState {
        /** "Borrar cuenta" is enabled while online and no delete is running (ADR-29). */
        val canDelete: Boolean get() = isOnline && !isDeleting
    }
}

/** Events of the Perfil root. */
sealed interface ProfileRootEvent {
    data object Retry : ProfileRootEvent

    /** The user confirmed the sign-out dialog. */
    data object SignOutConfirmed : ProfileRootEvent

    /** The user confirmed the delete-account dialog. */
    data object DeleteAccountConfirmed : ProfileRootEvent
}

/** One-off results shown as a snackbar. */
sealed interface ProfileRootEffect {
    /** Deleting the account failed; the session is kept. [offline] is true when the failure was the network. */
    data class DeleteFailed(val offline: Boolean) : ProfileRootEffect
}

/**
 * The Perfil root (ADR-37): reads the name and photo, signs out and deletes the account. Signing out needs no
 * navigation: the root reacts to the auth state.
 */
class ProfileRootViewModel(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val deleteAccount: DeleteAccount,
    connectivity: ConnectivityObserver,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ProfileRootUiState>(ProfileRootUiState.Loading)
    val uiState: StateFlow<ProfileRootUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<ProfileRootEffect>(Channel.BUFFERED)
    val effects: Flow<ProfileRootEffect> = effectChannel.receiveAsFlow()

    private var online = true
    private var work: Job? = null

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { isOnline ->
                online = isOnline
                updateLoaded { it.copy(isOnline = isOnline) }
            }
        }
        load()
    }

    fun onEvent(event: ProfileRootEvent) {
        when (event) {
            ProfileRootEvent.Retry -> if (_uiState.value == ProfileRootUiState.LoadFailed) load()
            ProfileRootEvent.SignOutConfirmed -> viewModelScope.launch { authRepository.signOut() }
            ProfileRootEvent.DeleteAccountConfirmed -> confirmDelete()
        }
    }

    private fun updateLoaded(transform: (ProfileRootUiState.Loaded) -> ProfileRootUiState.Loaded) {
        _uiState.update { if (it is ProfileRootUiState.Loaded) transform(it) else it }
    }

    private fun load() {
        work?.cancel()
        _uiState.value = ProfileRootUiState.Loading
        work = viewModelScope.launch {
            val profile = profileRepository.getOwnProfile().getOrNull()
            _uiState.value =
                if (profile == null) {
                    ProfileRootUiState.LoadFailed
                } else {
                    ProfileRootUiState.Loaded(
                        displayName = profile.displayName?.trim()?.takeIf { it.isNotEmpty() },
                        avatarUrl = profile.avatarUrl,
                        isOnline = online,
                    )
                }
        }
    }

    private fun confirmDelete() {
        val state = _uiState.value as? ProfileRootUiState.Loaded ?: return
        if (!state.canDelete) return
        updateLoaded { it.copy(isDeleting = true) }
        work = viewModelScope.launch {
            val outcome = deleteAccount()
            if (outcome == DeleteAccountOutcome.Deleted) return@launch
            updateLoaded { it.copy(isDeleting = false) }
            effectChannel.send(ProfileRootEffect.DeleteFailed(offline = outcome == DeleteAccountOutcome.NoNetwork))
        }
    }
}
