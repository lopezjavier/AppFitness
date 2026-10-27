package io.github.lopezjavier.appfitness.feature.about

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreditsResourceTest {
    private val entries: List<String> by lazy {
        val xml = File("src/commonMain/composeResources/values/strings.xml").readText(Charsets.UTF_8)
        val array = Regex("""<string-array name="credits">(.*?)</string-array>""", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1) ?: error("credits string-array missing")
        Regex("""<item>(.*?)</item>""").findAll(array).map { it.groupValues[1] }.toList()
    }

    @Test
    fun credits_holdExactlyThreeParsableEntries() {
        assertEquals(3, entries.size)
        entries.forEach { Credit.parse(it) }
    }

    @Test
    fun credits_namesTheThreeSourcesInOrder() {
        val credits = entries.map(Credit::parse)

        assertTrue(credits[0].title.contains("RepDB"))
        assertTrue(credits[1].title.contains("Open Food Facts"))
        assertTrue(credits[2].title.contains("Gemini"))
        assertEquals("Datos de ejercicios por RepDB (repdb.co)", credits[0].text)
    }
}
