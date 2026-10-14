package io.github.lopezjavier.appfitness.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.domain.model.SignInOutcome
import io.github.lopezjavier.appfitness.domain.repository.AuthRepository
import io.github.lopezjavier.appfitness.domain.usecase.SignInWithGoogle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Why the last sign-in attempt failed. A cancelled account picker is not an error. */
enum class WelcomeError { NoNetwork, Generic }

/** Welcome state: [inFlight] while a sign-in runs, [error] after a failed one. */
data class WelcomeUiState(val inFlight: Boolean = false, val error: WelcomeError? = null)

/** Events of the Welcome screen. */
sealed interface WelcomeEvent {
    data object ContinueWithGoogle : WelcomeEvent

    data object TestLogin : WelcomeEvent
}

private enum class SignInMethod { Google, Test }

/**
 * Drives the Welcome screen. A success needs no navigation: the root reacts to the auth state.
 * [AppConfig.testLogin] is the Test login credential and is null outside the `local` flavor.
 */
class WelcomeViewModel(
    private val signInWithGoogle: SignInWithGoogle,
    private val authRepository: AuthRepository,
    private val config: AppConfig,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    fun onEvent(event: WelcomeEvent) {
        when (event) {
            WelcomeEvent.ContinueWithGoogle -> start(SignInMethod.Google)
            WelcomeEvent.TestLogin -> start(SignInMethod.Test)
        }
    }

    private fun start(method: SignInMethod) {
        if (_uiState.value.inFlight) return
        _uiState.value = WelcomeUiState(inFlight = true)
        viewModelScope.launch {
            val outcome =
                when (method) {
                    SignInMethod.Google -> signInWithGoogle()

                    SignInMethod.Test ->
                        config.testLogin?.let { authRepository.signInWithPassword(it) } ?: SignInOutcome.Failed
                }
            _uiState.value =
                when (outcome) {
                    SignInOutcome.Success, SignInOutcome.Cancelled -> WelcomeUiState()
                    SignInOutcome.NoNetwork -> WelcomeUiState(error = WelcomeError.NoNetwork)
                    SignInOutcome.Failed -> WelcomeUiState(error = WelcomeError.Generic)
                }
        }
    }
}
