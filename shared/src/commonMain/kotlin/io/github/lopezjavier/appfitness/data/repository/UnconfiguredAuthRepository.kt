package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.AuthState
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.SignInOutcome
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.github.lopezjavier.appfitness.domain.repository.AuthRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Stand-in used when the build has no Supabase URL / publishable key (a CI-built `local` APK): the app
 * shows Welcome, every sign-in fails, and nothing crashes at launch on a blank key.
 */
class UnconfiguredAuthRepository : AuthRepository {
    override val authState: Flow<AuthState> = flowOf(AuthState.SignedOut)

    override suspend fun signInWithGoogle(idToken: String, rawNonce: String): SignInOutcome = SignInOutcome.Failed

    override suspend fun signInWithPassword(credentials: TestLoginCredentials): SignInOutcome = SignInOutcome.Failed

    override suspend fun signOut() = Unit
}

/** Profile counterpart of [UnconfiguredAuthRepository]: there is never a profile to read. */
class UnconfiguredProfileRepository : ProfileRepository {
    override suspend fun getOwnProfile(): Result<ProfileSummary> = Result.failure(AppException(AppError.Unauthorized))
}
