package io.github.lopezjavier.appfitness.feature.trainer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** T-206: the interim "minimal" block strings are gone for good. */
class NoMinimalStringsTest {
    @Test
    fun stringsXml_hasNoMinimalKeys() {
        val file = File("src/commonMain/composeResources/values/strings.xml")
        val keys = Regex("""<string name="([^"]*minimal[^"]*)"""").findAll(file.readText(Charsets.UTF_8))
            .map { it.groupValues[1] }
            .toList()

        assertEquals(emptyList<String>(), keys)
    }
}
