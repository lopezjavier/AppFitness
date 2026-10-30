package io.github.lopezjavier.appfitness.data.repository

import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Client timeout for `coach` calls; above the 120 s `COACH_TIMEOUT_MS` of the function
 * (`supabase/functions/_shared/anthropic.ts`), so the function answers (or reports its own timeout) first.
 */
internal val COACH_CLIENT_TIMEOUT: Duration = 130.seconds

private const val CONNECT_TIMEOUT_MS = 15_000L
private const val SOCKET_MARGIN_MS = 5_000L

/**
 * Raises the client timeouts for a long model call. The client default (10 s) and OkHttp's own 10 s read timeout are
 * far shorter than a plan or block generation, and a socket timeout surfaces as a network error ("offline"). The socket
 * timeout stays above the request timeout on purpose: supabase-kt wraps socket timeouts into a cause-less
 * HttpRequestException, while the request timeout stays a HttpRequestTimeoutException (mapped to a retryable timeout).
 */
internal fun HttpRequestBuilder.coachTimeout(requestTimeout: Duration) {
    timeout {
        requestTimeoutMillis = requestTimeout.inWholeMilliseconds
        socketTimeoutMillis = requestTimeout.inWholeMilliseconds + SOCKET_MARGIN_MS
        connectTimeoutMillis = CONNECT_TIMEOUT_MS
    }
}
