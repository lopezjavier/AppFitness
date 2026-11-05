package io.github.lopezjavier.appfitness.feature.trainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** T-198: the strings of the removed intake controls are gone, and the kept copy is the final wording. */
class IntakeStringsTest {
    private val strings: String =
        File("src/commonMain/composeResources/values/strings.xml").readText(Charsets.UTF_8)

    @Test
    fun theRemovedIntakeKeys_areGone() {
        val removed = Regex(
            "name=\"(intake_situation_|intake_area_|intake_best|intake_extract|intake_marker|intake_skip|intake_cut_|" +
                "intake_known_title|your_data_situation_row|your_data_intake_row)",
        )

        assertEquals(emptyList<String>(), removed.findAll(strings).map { it.value }.toList())
    }

    @Test
    fun theRemovedLabels_areGone() {
        listOf(
            "Rellenar con mi texto",
            "Leyendo lo que nos cuentas",
            "Rellenado desde tu texto",
            "Marcas de antes de parar",
            "Añadir marca",
            "Zona con molestia",
            "Vuelvo tras una pausa",
        ).forEach { label -> assertTrue("still present: $label", !strings.contains(label)) }
    }

    @Test
    fun theKeptCopy_isTheFinalWording() {
        assertTrue(strings.contains(">Calorías de mantenimiento</string>"))
        assertTrue(strings.contains(">Entre 1200 y 6000 kcal</string>"))
        assertTrue(strings.contains("al preparar tu plan y tus bloques"))
    }
}
