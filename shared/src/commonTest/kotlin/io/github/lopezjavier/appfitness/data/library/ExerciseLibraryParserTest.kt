package io.github.lopezjavier.appfitness.data.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExerciseLibraryParserTest {
    @Test
    fun validJson_givesEntriesByKey() {
        val json =
            """{"schema_version":1,"exercises":{"bench":{"name":"Press de banca","description":"D",""" +
                """"instructions":["a","b"],"tips":["t"]}}}"""

        val entry = ExerciseLibraryParser.parse(json).getValue("bench")

        assertEquals("Press de banca", entry.name)
        assertEquals(listOf("a", "b"), entry.instructions)
        assertEquals(listOf("t"), entry.tips)
        assertEquals("bench", entry.key)
    }

    @Test
    fun unknownKey_isAbsent() {
        val json = """{"schema_version":1,"exercises":{"bench":{"name":"Press"}}}"""

        assertTrue("squat" !in ExerciseLibraryParser.parse(json))
    }

    @Test
    fun emptyOrMalformedJson_givesEmptyMap() {
        assertTrue(ExerciseLibraryParser.parse("").isEmpty())
        assertTrue(ExerciseLibraryParser.parse("{not json").isEmpty())
        assertTrue(ExerciseLibraryParser.parse("[]").isEmpty())
    }

    @Test
    fun missingOptionalFields_defaultToEmpty() {
        val entry = ExerciseLibraryParser.parse("""{"exercises":{"k":{"name":"Solo nombre"}}}""").getValue("k")

        assertEquals("", entry.description)
        assertTrue(entry.instructions.isEmpty())
        assertTrue(entry.tips.isEmpty())
    }

    @Test
    fun poses_absentMeansBoth_startOnlyMeansNoPeak() {
        val json =
            """{"exercises":{"a":{"name":"A"},"b":{"name":"B","poses":["start"]},""" +
                """"c":{"name":"C","poses":["start","peak"]}}}"""

        val entries = ExerciseLibraryParser.parse(json)

        assertTrue(entries.getValue("a").hasPeak)
        assertTrue(!entries.getValue("b").hasPeak)
        assertTrue(entries.getValue("c").hasPeak)
    }

    @Test
    fun entryWithoutName_isSkipped() {
        assertTrue(ExerciseLibraryParser.parse("""{"exercises":{"k":{"description":"x"}}}""").isEmpty())
    }
}
