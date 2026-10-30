package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import kotlin.test.Test
import kotlin.test.assertEquals

class CoachErrorParserTest {
    private fun error(code: String, scope: String? = null): String {
        val extra = if (scope == null) "" else ",\"scope\":\"$scope\""
        return "{\"error\":{\"code\":\"$code\",\"message\":\"m\"$extra}}"
    }

    @Test
    fun limitReached_userScope() = assertEquals(
        CoachFailure.LimitReached(CoachFailure.Scope.User),
        CoachErrorParser.parse(error("coach_limit_reached", "user")),
    )

    @Test
    fun limitReached_globalScope() = assertEquals(
        CoachFailure.LimitReached(CoachFailure.Scope.Global),
        CoachErrorParser.parse(error("coach_limit_reached", "global")),
    )

    @Test
    fun limitReached_withoutScope_isUser() = assertEquals(
        CoachFailure.LimitReached(CoachFailure.Scope.User),
        CoachErrorParser.parse(error("coach_limit_reached")),
    )

    @Test
    fun noCredit() = assertEquals(CoachFailure.NoCredit, CoachErrorParser.parse(error("coach_no_credit")))

    @Test
    fun timeout() = assertEquals(CoachFailure.Timeout, CoachErrorParser.parse(error("coach_timeout")))

    @Test
    fun sseErrorEventData_isParsedToo() =
        assertEquals(CoachFailure.Timeout, CoachErrorParser.parse("{\"code\":\"coach_timeout\"}"))

    @Test
    fun unknownCode_isOther() = assertEquals(CoachFailure.Other, CoachErrorParser.parse(error("INVALID_INPUT")))

    @Test
    fun garbage_isOther() {
        assertEquals(CoachFailure.Other, CoachErrorParser.parse("not json"))
        assertEquals(CoachFailure.Other, CoachErrorParser.parse(""))
        assertEquals(CoachFailure.Other, CoachErrorParser.parse("[1]"))
    }
}
