package io.github.lopezjavier.appfitness.feature.trainer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * T-222/T-223/T-224 (ADR-99): the model "Desde cero" flow and the model folder review are gone, so their strings stay
 * gone. The saved-block `block_preview_*` keys are not banned: T-224 owns them.
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
    fun stringsXml_hasNoModelFolderReviewKeys() {
        val keep = setOf("folder_review_error")
        val keys = entry.findAll(text).map { it.groupValues[1] }
            .filter { it.startsWith("folder_review_") && !it.startsWith("folder_review_result_") && it !in keep }
            .toList()

        assertEquals(emptyList<String>(), keys)
    }

    @Test
    fun stringsXml_saysRecomendado_neverRecomendada() {
        val values = entry.findAll(text).filter { it.groupValues[2].contains("Recomendada") }.map { it.groupValues[1] }

        assertEquals(emptyList<String>(), values.toList())
    }

    @Test
    fun theSourceHasNoModelFolderReviewLeftOver() {
        val leftovers = File("src/commonMain/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter {
                Regex("""\bFolderReview(ViewModel|UiState|Screen|Root|Repository)\b""")
                    .containsMatchIn(it.readText(Charsets.UTF_8))
            }
            .map { it.name }
            .toList()

        assertEquals(emptyList<String>(), leftovers)
    }

    @Test
    fun theSourceHasNoModelBlockCallLeftOver() {
        val retired = Regex("""\b(requestSplitOptions|requestBlock|SplitOption\w*|FolderFalta\w*|NeedsChanges)\b""")
        val leftovers = listOf("src/commonMain/kotlin", "../shared/src/commonMain/kotlin")
            .flatMap { File(it).walkTopDown().toList() }
            .filter { it.isFile && it.extension == "kt" }
            .filter { retired.containsMatchIn(it.readText(Charsets.UTF_8)) }
            .map { it.name }

        assertEquals(emptyList<String>(), leftovers)
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
