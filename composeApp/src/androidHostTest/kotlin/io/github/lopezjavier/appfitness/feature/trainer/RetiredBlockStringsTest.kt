package io.github.lopezjavier.appfitness.feature.trainer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * T-222 (ADR-99): the model "Desde cero" flow is gone, so its strings stay gone. Keys the folder review still uses
 * ("Falta", "Cambios propuestos", the saved-block `block_preview_*` ones) are not banned: T-223 and T-224 own them.
 */
class RetiredBlockStringsTest {
    private val text = File("src/commonMain/composeResources/values/strings.xml").readText(Charsets.UTF_8)

    private val entry = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""")

    @Test
    fun stringsXml_hasNoSplitsScreenKeys() {
        val keys = entry.findAll(text).map { it.groupValues[1] }.filter { it.startsWith("block_splits_") }.toList()

        assertEquals(emptyList<String>(), keys)
    }

    @Test
    fun stringsXml_hasNoModelReasonKeys() {
        val keys = entry.findAll(text).map {
            it.groupValues[1]
        }.filter { it.startsWith("block_preview_reason_") }.toList()

        assertEquals(emptyList<String>(), keys)
    }

    @Test
    fun stringsXml_saysRecomendado_neverRecomendada() {
        val values = entry.findAll(text).filter { it.groupValues[2].contains("Recomendada") }.map { it.groupValues[1] }

        assertEquals(emptyList<String>(), values.toList())
    }

    @Test
    fun theSourceHasNoSplitsScreenLeftOver() {
        val leftovers = File("src/commonMain/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter {
                Regex(
                    """\bBlockSplits(Screen|ViewModel|UiState|Route|Root|Slot)\b""",
                ).containsMatchIn(it.readText(Charsets.UTF_8))
            }
            .map { it.name }
            .toList()

        assertEquals(emptyList<String>(), leftovers)
    }
}
