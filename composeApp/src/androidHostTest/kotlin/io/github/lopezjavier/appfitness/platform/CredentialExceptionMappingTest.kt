package io.github.lopezjavier.appfitness.platform

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import io.github.lopezjavier.appfitness.domain.port.GoogleCredentialResult
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** US-05 "Account picker cancelled": only the user closing the picker is silent. */
class CredentialExceptionMappingTest {
    @Test
    fun cancellation_isCancelled() {
        assertEquals(GoogleCredentialResult.Cancelled, mapCredentialException(GetCredentialCancellationException()))
    }

    @Test
    fun noGoogleAccount_isFailed() {
        assertIs<GoogleCredentialResult.Failed>(mapCredentialException(NoCredentialException()))
    }

    @Test
    fun interruptedAndMisconfigured_areFailed() {
        assertIs<GoogleCredentialResult.Failed>(mapCredentialException(GetCredentialInterruptedException()))
        assertIs<GoogleCredentialResult.Failed>(mapCredentialException(GetCredentialProviderConfigurationException()))
    }

    @Test
    fun parsingAndUnknownErrors_areFailed_keepingTheCause() {
        val parsing = GoogleIdTokenParsingException(IllegalStateException("bad token"))
        assertEquals(parsing, assertIs<GoogleCredentialResult.Failed>(mapCredentialException(parsing)).cause)
        assertIs<GoogleCredentialResult.Failed>(mapCredentialException(IllegalStateException("boom")))
    }
}
