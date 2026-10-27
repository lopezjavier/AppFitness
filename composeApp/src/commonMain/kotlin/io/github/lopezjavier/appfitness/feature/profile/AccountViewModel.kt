package io.github.lopezjavier.appfitness.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.AccountProtection
import io.github.lopezjavier.appfitness.domain.model.DeleteAccountOutcome
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.AuthRepository
import io.github.lopezjavier.appfitness.domain.usecase.DeleteAccount
import io.github.lopezjavier.appfitness.domain.usecase.GetAccountProtection
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State of the Cuenta screen: whether "Borrar cuenta" can run (ADR-29, ADR-57). */
data class AccountUiState(
    val isOnline: Boolean = true,
    val isDeleting: Boolean = false,
    val protection: AccountProtection = AccountProtection.Unknown,
) {
    /**
     * "Borrar cuenta" is enabled while online, no delete is running and the account is known to be unprotected
     * (ADR-29, ADR-57): an unknown check never enables it.
     */
    val canDelete: Boolean get() = isOnline && !isDeleting && protection == AccountProtection.Unprotected
}

/** Events of the Cuenta screen. */
sealed interface AccountEvent {
    /** The user confirmed the sign-out dialog. */
    data object SignOutConfirmed : AccountEvent

    /** The user confirmed the delete-account dialog. */
    data object DeleteAccountConfirmed : AccountEvent
}

/** One-off results of the Cuenta screen. */
sealed interface AccountEffect {
    /** Deleting the account failed; the session is kept. [offline] is true when the failure was the network. */
    data class DeleteFailed(val offline: Boolean) : AccountEffect
}

/**
 * Cuenta (Preferencias > "Cuenta"): signs out and deletes the account. Signing out needs no navigation: the app root
 * reacts to the auth state. The protection check runs on creation and again when connectivity returns while unknown.
 */
class AccountViewModel(
    private val authRepository: AuthRepository,
    private val deleteAccount: DeleteAccount,
    private val getAccountProtection: GetAccountProtection,
    connectivity: ConnectivityObserver,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<AccountEffect>(Channel.BUFFERED)
    val effects: Flow<AccountEffect> = effectChannel.receiveAsFlow()

    private var online = true
    private var protectionWork: Job? = null

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { isOnline ->
                val regained = isOnline && !online
                online = isOnline
                _uiState.update { it.copy(isOnline = isOnline) }
                if (regained && _uiState.value.protection == AccountProtection.Unknown) checkProtection()
            }
        }
        checkProtection()
    }

    fun onEvent(event: AccountEvent) {
        when (event) {
            AccountEvent.SignOutConfirmed -> viewModelScope.launch { authRepository.signOut() }
            AccountEvent.DeleteAccountConfirmed -> confirmDelete()
        }
    }

    /** A failure leaves the protection Unknown, which never enables the delete. */
    private fun checkProtection() {
        protectionWork?.cancel()
        protectionWork = viewModelScope.launch {
            val protection = getAccountProtection()
            _uiState.update {
                if (it.protection ==
                    AccountProtection.Protected
                ) {
                    it
                } else {
                    it.copy(protection = protection)
                }
            }
        }
    }

    private fun confirmDelete() {
        if (!_uiState.value.canDelete) return
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            val outcome = deleteAccount()
            if (outcome == DeleteAccountOutcome.Deleted) return@launch
            if (outcome == DeleteAccountOutcome.Protected) {
                _uiState.update { it.copy(isDeleting = false, protection = AccountProtection.Protected) }
                return@launch
            }
            _uiState.update { it.copy(isDeleting = false) }
            effectChannel.send(AccountEffect.DeleteFailed(offline = outcome == DeleteAccountOutcome.NoNetwork))
        }
    }
}
