package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CoachStreamParserTest {
    private fun failureOf(result: Result<String>): CoachFailure {
        val exception = result.exceptionOrNull()
        assertIs<CoachException>(exception)
        return exception.failure
    }

    @Test
    fun deltaThenDone_returnsTheDeltaText() {
        val body = "event: delta\ndata: {\"text\":\"{\\\"a\\\":1}\"}\n\nevent: done\ndata: {\"usage\":{}}\n\n"

        assertEquals("""{"a":1}""", CoachStreamParser.parse(body).getOrThrow())
    }

    @Test
    fun severalDeltas_areConcatenatedInOrder() {
        val body = "event: delta\ndata: {\"text\":\"ab\"}\n\nevent: delta\ndata: {\"text\":\"cd\"}\n\n" +
            "event: done\ndata: {}\n\n"

        assertEquals("abcd", CoachStreamParser.parse(body).getOrThrow())
    }

    @Test
    fun doneWithoutDelta_isEmptyText() {
        assertEquals("", CoachStreamParser.parse("event: done\ndata: {}\n\n").getOrThrow())
    }

    @Test
    fun windowsLineEndings_areAccepted() {
        val body = "event: delta\r\ndata: {\"text\":\"x\"}\r\n\r\nevent: done\r\ndata: {}\r\n\r\n"

        assertEquals("x", CoachStreamParser.parse(body).getOrThrow())
    }

    @Test
    fun errorFrame_isMappedEvenAfterADelta() {
        val body = "event: delta\ndata: {\"text\":\"x\"}\n\nevent: error\ndata: {\"code\":\"coach_timeout\"}\n\n"

        assertEquals(CoachFailure.Timeout, failureOf(CoachStreamParser.parse(body)))
    }

    @Test
    fun limitErrorFrame_keepsItsScope() {
        val body = "event: error\ndata: {\"code\":\"coach_limit_reached\",\"scope\":\"global\"}\n\n"

        assertEquals(CoachFailure.LimitReached(CoachFailure.Scope.Global), failureOf(CoachStreamParser.parse(body)))
    }

    @Test
    fun truncatedStream_withoutDone_isOther() {
        val body = "event: delta\ndata: {\"text\":\"po\"}\n\n"

        assertEquals(CoachFailure.Other, failureOf(CoachStreamParser.parse(body)))
    }

    @Test
    fun emptyBody_isOther() {
        assertEquals(CoachFailure.Other, failureOf(CoachStreamParser.parse("")))
    }
}
