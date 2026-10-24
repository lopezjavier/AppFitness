package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.Functions
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.DeleteAccountOutcome
import io.github.lopezjavier.appfitness.domain.repository.AccountRepository
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class AccountStatusDto(val protected: Boolean)

/**
 * Calls the `delete-account` Edge Function (POST, no body, the user's JWT). 200 and 401 (the user is already gone or
 * the token is dead) both count as deleted; 403 means the account is protected (ADR-57); anything else leaves the
 * account untouched. `account-status` (GET) tells up front whether the account is protected.
 */
class SupabaseAccountRepository(private val functions: Functions) : AccountRepository {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun deleteAccount(): DeleteAccountOutcome = try {
        val response = functions.invoke(DELETE_FUNCTION)
        response.status.toOutcome()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        failure.toOutcome()
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun isProtected(): Result<Boolean> = try {
        val response = functions.invoke(STATUS_FUNCTION) { method = HttpMethod.Get }
        if (response.status == HttpStatusCode.OK) {
            Result.success(json.decodeFromString<AccountStatusDto>(response.bodyAsText()).protected)
        } else {
            Result.failure(AppException(AppError.Unknown))
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    private fun HttpStatusCode.toOutcome(): DeleteAccountOutcome = when (value) {
        HttpStatusCode.OK.value, HttpStatusCode.Unauthorized.value -> DeleteAccountOutcome.Deleted
        HttpStatusCode.Forbidden.value -> DeleteAccountOutcome.Protected
        else -> DeleteAccountOutcome.Failed
    }

    private fun Throwable.toOutcome(): DeleteAccountOutcome = when (this) {
        is RestException -> HttpStatusCode.fromValue(statusCode).toOutcome()
        is HttpRequestException, is HttpRequestTimeoutException, is IOException -> DeleteAccountOutcome.NoNetwork
        else -> DeleteAccountOutcome.Failed
    }

    private companion object {
        const val DELETE_FUNCTION = "delete-account"
        const val STATUS_FUNCTION = "account-status"
        val json = Json { ignoreUnknownKeys = true }
    }
}
