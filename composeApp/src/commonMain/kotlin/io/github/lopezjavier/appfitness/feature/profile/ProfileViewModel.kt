package io.github.lopezjavier.appfitness.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.repository.AuthRepository
import kotlinx.coroutines.launch

/** Events of the Profile screen. */
sealed interface ProfileEvent {
    /** The user confirmed the sign-out dialog. */
    data object SignOutConfirmed : ProfileEvent
}

/** Profile actions. Signing out needs no navigation: the root reacts to the auth state. */
class ProfileViewModel(private val authRepository: AuthRepository) : ViewModel() {
    fun onEvent(event: ProfileEvent) {
        when (event) {
            ProfileEvent.SignOutConfirmed -> viewModelScope.launch { authRepository.signOut() }
        }
    }
}
