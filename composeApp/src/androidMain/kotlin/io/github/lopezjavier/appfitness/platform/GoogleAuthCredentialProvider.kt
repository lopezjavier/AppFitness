package io.github.lopezjavier.appfitness.platform

import android.app.Activity
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.domain.port.AuthCredentialProvider
import io.github.lopezjavier.appfitness.domain.port.GoogleCredentialResult
import kotlinx.coroutines.CancellationException

private const val TAG = "GoogleAuth"

/**
 * [AuthCredentialProvider] on Credential Manager with the Google ID option. It needs the foreground Activity (from
 * [activityHolder]); a missing activity or a blank web client id is a generic failure, never a crash.
 * Logs the failure class and message, never a token.
 */
class GoogleAuthCredentialProvider(private val config: AppConfig, private val activityHolder: CurrentActivityHolder) :
    AuthCredentialProvider {
    override suspend fun requestGoogleIdToken(): GoogleCredentialResult {
        val activity = activityHolder.get()
        return when {
            config.googleWebClientId.isBlank() -> {
                Log.w(TAG, "google.webClientId is not configured")
                GoogleCredentialResult.Failed()
            }

            activity == null -> GoogleCredentialResult.Failed()

            else -> requestToken(activity)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun requestToken(activity: Activity): GoogleCredentialResult {
        val rawNonce = generateRawNonce()
        val option =
            GetGoogleIdOption.Builder()
                .setServerClientId(config.googleWebClientId)
                .setFilterByAuthorizedAccounts(false)
                .setNonce(sha256Hex(rawNonce))
                .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleCredentialResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken, rawNonce)
            } else {
                GoogleCredentialResult.Failed()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            Log.w(TAG, "Google sign-in failed: ${failure::class.java.simpleName}: ${failure.message}")
            mapCredentialException(failure)
        }
    }
}

/**
 * Maps a Credential Manager failure: the user closing the account picker is [GoogleCredentialResult.Cancelled]
 * (silent); everything else, including no Google account on the device, is [GoogleCredentialResult.Failed].
 */
fun mapCredentialException(failure: Exception): GoogleCredentialResult = when (failure) {
    is GetCredentialCancellationException -> GoogleCredentialResult.Cancelled
    else -> GoogleCredentialResult.Failed(failure)
}
