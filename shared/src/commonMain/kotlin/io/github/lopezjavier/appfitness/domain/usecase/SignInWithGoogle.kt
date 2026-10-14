package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.SignInOutcome
import io.github.lopezjavier.appfitness.domain.port.AuthCredentialProvider
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.port.GoogleCredentialResult
import io.github.lopezjavier.appfitness.domain.repository.AuthRepository
import kotlinx.coroutines.flow.first

/** Google sign-in: connectivity check first (the picker is not opened offline), then picker, then Supabase. */
class SignInWithGoogle(
    private val connectivity: ConnectivityObserver,
    private val credentialProvider: AuthCredentialProvider,
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(): SignInOutcome {
        if (!connectivity.isOnline.first()) return SignInOutcome.NoNetwork
        return when (val result = credentialProvider.requestGoogleIdToken()) {
            is GoogleCredentialResult.Token -> authRepository.signInWithGoogle(result.idToken, result.rawNonce)
            GoogleCredentialResult.Cancelled -> SignInOutcome.Cancelled
            GoogleCredentialResult.NoNetwork -> SignInOutcome.NoNetwork
            is GoogleCredentialResult.Failed -> SignInOutcome.Failed
        }
    }
}
