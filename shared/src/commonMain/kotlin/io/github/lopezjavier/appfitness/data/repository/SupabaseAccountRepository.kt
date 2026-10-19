package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.Functions
import io.github.lopezjavier.appfitness.domain.model.DeleteAccountOutcome
import io.github.lopezjavier.appfitness.domain.repository.AccountRepository
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException

/**
 * Calls the `delete-account` Edge Function (POST, no body, the user's JWT). 200 and 401 (the user is already gone or
 * the token is dead) both count as deleted; anything else leaves the account untouched.
 */
class SupabaseAccountRepository(private val functions: Functions) : AccountRepository {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun deleteAccount(): DeleteAccountOutcome = try {
        val response = functions.invoke(FUNCTION_NAME)
        response.status.toOutcome()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        failure.toOutcome()
    }

    private fun HttpStatusCode.toOutcome(): DeleteAccountOutcome = when (value) {
        HttpStatusCode.OK.value, HttpStatusCode.Unauthorized.value -> DeleteAccountOutcome.Deleted
        else -> DeleteAccountOutcome.Failed
    }

    private fun Throwable.toOutcome(): DeleteAccountOutcome = when (this) {
        is RestException -> HttpStatusCode.fromValue(statusCode).toOutcome()
        is HttpRequestException, is HttpRequestTimeoutException, is IOException -> DeleteAccountOutcome.NoNetwork
        else -> DeleteAccountOutcome.Failed
    }

    private companion object {
        const val FUNCTION_NAME = "delete-account"
    }
}
