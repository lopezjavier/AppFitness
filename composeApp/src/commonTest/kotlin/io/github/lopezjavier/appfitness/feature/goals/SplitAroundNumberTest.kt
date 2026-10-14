package io.github.lopezjavier.appfitness.feature.goals

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SplitAroundNumberTest {
    @Test
    fun splitsAroundTheNumber() {
        assertEquals(
            Triple("Meta diaria: ", "2.400", " kcal/día"),
            splitAroundNumber("Meta diaria: 2.400 kcal/día", "2.400"),
        )
    }

    @Test
    fun missingNumber_isNull() {
        assertNull(splitAroundNumber("Meta diaria", "2.400"))
    }
}
