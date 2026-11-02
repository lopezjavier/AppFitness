package io.github.lopezjavier.appfitness.sweep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** US-90: no generic health disclaimer anywhere in the Spanish resources (05 section 8). */
class HealthDisclaimerSweepTest {
    /** Strings allowed to carry a health line; empty until T-194 adds the persistent-discomfort note. */
    private val allowList = emptySet<String>()

    private val forbidden = listOf(
        "consulta con",
        "consulta antes con",
        "profesional sanitario",
        "tu médico",
        "no es un dispositivo médico",
        "no sirven para diagnóstico",
    )

    private fun stringsFile(): File {
        val candidates = listOf(
            "src/commonMain/composeResources/values/strings.xml",
            "composeApp/src/commonMain/composeResources/values/strings.xml",
        )
        return candidates.map(::File).first { it.exists() }
    }

    private fun flagged(xml: String): List<String> = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""")
        .findAll(xml)
        .filter { match -> forbidden.any { match.groupValues[2].contains(it, ignoreCase = true) } }
        .map { it.groupValues[1] }
        .filter { it !in allowList }
        .toList()

    @Test
    fun spanishResources_haveNoGenericHealthDisclaimer() {
        assertEquals(emptyList<String>(), flagged(stringsFile().readText(Charsets.UTF_8)))
    }

    @Test
    fun aPlantedDisclaimer_isFlagged() {
        val fixture = """<string name="planted">Si te preocupa, consulta con un profesional.</string>"""

        assertTrue(flagged(fixture).contains("planted"))
    }
}
