package io.github.lopezjavier.appfitness.designsystem

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T-128 glossary (planning/05-design-system.md section 10, R-35..R-37): one wording per concept. Reads the shipped
 * `strings.xml` so a duplicate or a stray synonym fails the build.
 */
@Suppress("TooManyFunctions") // One test per glossary row.
class GlossaryStringsTest {
    private val stringsFile: File =
        listOf(
            "src/commonMain/composeResources/values/strings.xml",
            "composeApp/src/commonMain/composeResources/values/strings.xml",
        ).map(::File).first { it.exists() }

    private val entry = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""")
    private val strings: Map<String, String> =
        entry.findAll(stringsFile.readText(Charsets.UTF_8)).associate {
            it.groupValues[1] to unescape(it.groupValues[2])
        }

    private fun unescape(raw: String) = raw.replace("\\'", "'").replace("\\n", "\n").replace("&amp;", "&")

    private fun value(key: String): String = strings[key] ?: error("Missing string $key")

    private fun valuesWith(predicate: (String) -> Boolean): List<String> =
        strings.filterValues(predicate).map { "${it.key}=${it.value}" }

    @Test
    fun genericActions_haveOneSharedKeyEach() {
        assertEquals("Guardar", value("action_save"))
        assertEquals("Cancelar", value("action_cancel"))
        assertEquals("Cerrar", value("action_close"))
        assertEquals("Eliminar", value("action_delete"))
        assertEquals("Quitar", value("action_remove"))
        assertEquals("Editar", value("action_edit"))
        assertEquals("Listo", value("action_done"))
        assertEquals("Descartar", value("action_discard"))
        assertEquals("Seguir editando", value("action_keep_editing"))
        assertEquals("Reintentar", value("action_retry"))
        assertEquals("Añadir", value("action_add"))
    }

    @Test
    fun genericActionTexts_existOnlyUnderTheSharedActionKeys() {
        val generic = setOf("Guardar", "Cancelar", "Cerrar", "Eliminar", "Listo", "Descartar", "Reintentar", "Editar")
        val duplicates = strings.filter { (key, text) -> text in generic && !key.startsWith("action_") }

        assertTrue(duplicates.isEmpty(), "Use the action_* keys instead of: $duplicates")
    }

    @Test
    fun addQuitAndFinalizar_existOnlyUnderTheSharedActionKeys() {
        val duplicates = strings.filter { (key, text) ->
            text in setOf("Añadir", "Quitar", "Finalizar") && !key.startsWith("action_")
        }

        assertTrue(duplicates.isEmpty(), "Use the action_* keys instead of: $duplicates")
    }

    @Test
    fun forbiddenSynonyms_doNotAppearAsValues() {
        // "Confirmar" is the canonical derived-values button (T-158), so it is not forbidden.
        val forbidden =
            listOf("Aceptar", "Cerrar sin guardar", "Archivar", "Crear ejercicio", "Último mes")
        forbidden.forEach { word ->
            // "Aceptar" is the coach-proposal decision (T-162 glossary row); only under its key.
            val stray = strings.filter { (key, text) -> text == word && key != "action_accept" }
            assertTrue(stray.isEmpty(), "\"$word\" must not be a value: $stray")
        }
        assertEquals("Aceptar", value("action_accept"))
        assertTrue(valuesWith { it.contains("Deshacer") }.isEmpty(), "No Deshacer")
        assertTrue(valuesWith { it.contains("Tasa metabólica") }.isEmpty(), "Use Metabolismo basal")
    }

    @Test
    fun borrar_isOnlyUsedForTheAccountAndTheSearchField() {
        val allowed = setOf("Borrar cuenta", "Borrar búsqueda")
        val stray = valuesWith { it.startsWith("Borrar") && it !in allowed }

        assertTrue(stray.isEmpty(), "Delete a saved record is Eliminar: $stray")
        assertEquals("Borrar búsqueda", value("action_clear_search"))
    }

    @Test
    fun savedTemplate_isNeverCalledPlantilla() {
        val stray = valuesWith { it.contains("Plantilla", ignoreCase = true) }

        assertTrue(stray.isEmpty(), "A saved template is a comida guardada: $stray")
        assertEquals("Guardar como comida", value("save_template_title"))
    }

    @Test
    fun food_isAlimento() {
        assertEquals("Editar alimento", value("edit_food_title"))
        assertTrue(valuesWith { it.contains("Crear alimento") }.isNotEmpty())
        assertTrue(valuesWith { it.contains("Añadir alimento") }.isNotEmpty())
    }

    @Test
    fun history_namesWhoseHistoryItIs() {
        assertEquals("Historial de entrenos", value("history_title"))
        assertEquals("Historial de entrenos", value("workout_entry_history_cd"))
        assertEquals("Historial de peso", value("weight_history_title"))
        assertEquals("Historial", value("exercise_tab_history"))
    }

    @Test
    fun statistics_haveOneNameEach() {
        assertEquals("Calorías", value("diary_history_title"))
        assertEquals("Entrenamiento", value("stats_title"))
        assertEquals("Estadísticas", value("profile_statistics_section"))
    }

    @Test
    fun bodyFat_isGrasaCorporal() {
        assertEquals("Grasa corporal", value("metric_body_fat_label"))
        assertEquals("Grasa corporal (%)", value("weight_field_fat"))
        assertTrue(valuesWith { it == "Grasa (%)" }.isEmpty())
    }

    @Test
    fun bone_isMasaOsea() {
        assertEquals("Masa ósea", value("weight_composition_row_bone"))
        assertEquals("Masa ósea", value("weight_chart_metric_bone_cd"))
        assertTrue(valuesWith { it.contains("Hueso masa", ignoreCase = true) }.isEmpty())
    }

    @Test
    fun workout_isEntreno_andSesionIsOnlyForAuth() {
        val authAllowed = listOf("Iniciando sesión", "Cerrar sesión", "iniciar sesión")
        val stray = valuesWith { text ->
            text.contains("sesión", ignoreCase = true) && authAllowed.none { text.contains(it, ignoreCase = true) }
        }

        assertTrue(stray.isEmpty(), "A workout is an entreno: $stray")
    }

    @Test
    fun hamstrings_areAbbreviatedOnlyOnTheRadarKey() {
        assertEquals("Isquiotibiales", value("muscle_hamstrings"))
        val stray = strings.filter { (key, text) -> text.contains("Isquios") && key != "muscle_hamstrings_short" }

        assertTrue(stray.isEmpty(), "Isquios only on the radar: $stray")
    }

    @Test
    fun mealsOfTheDay_haveOneWording() {
        assertTrue(valuesWith { it == "Comidas del día" }.isNotEmpty())
        assertTrue(valuesWith { it == "Comidas" }.isEmpty(), "No bare Comidas label")
        assertEquals("No tienes comidas del día. Añade una en Preferencias.", value("add_food_no_meal_hint"))
    }

    @Test
    fun retrySentences_endWithAPeriod() {
        val missing = valuesWith { it.contains("Inténtalo") && !it.trimEnd().endsWith(".") }

        assertTrue(missing.isEmpty(), "R-37: every error sentence ends with a period: $missing")
    }

    @Test
    fun failedActions_followTheMessageTemplate() {
        val wrong = valuesWith {
            it.startsWith("No se pudo ") && it.contains("Inténtalo") && !it.endsWith(". Inténtalo de nuevo.")
        }

        assertTrue(wrong.isEmpty(), "No se pudo <acción>. Inténtalo de nuevo.: $wrong")
        assertEquals("No se pudo guardar. Inténtalo de nuevo.", value("onboarding_save_error"))
        assertEquals("No se pudo iniciar sesión. Inténtalo de nuevo.", value("auth_error_generic"))
    }

    @Test
    fun offlineMessages_followTheTemplate() {
        assertEquals("Sin conexión: no se puede guardar.", value("onboarding_save_error_offline"))
        assertEquals("Sin conexión: conéctate para guardar tus datos.", value("onboarding_offline_hint"))
        val wrong = valuesWith {
            it.startsWith("Sin conexión") && it != "Sin conexión" &&
                !it.startsWith("Sin conexión: ")
        }

        assertTrue(wrong.isEmpty(), "Sin conexión: <frase>: $wrong")
    }

    @Test
    fun valuesNeverUseThreeDots_theEllipsisCharacterInstead() {
        assertTrue(valuesWith { it.contains("...") }.isEmpty())
    }

    @Test
    fun valuesNeverStartOrEndWithSpaces_exceptTheJoiningWord() {
        val stray = strings.filter { (key, text) -> text != text.trim() && key != "cooking_impact_and" }

        assertTrue(stray.isEmpty(), "Stray spaces: $stray")
    }

    @Test
    fun trainingIntentionAndPlace_useTheGlossaryLabels() {
        assertEquals("Mi entrenamiento", value("preferences_my_training_title"))
        assertEquals("Objetivo de entrenamiento", value("my_training_intention_label"))
        assertEquals("Ganar fuerza", value("my_training_intention_strength"))
        assertEquals("Ganar musculatura", value("my_training_intention_muscle"))
        assertEquals("Perder grasa", value("my_training_intention_fat_loss"))
        assertEquals("Recomposición", value("my_training_intention_recomposition"))
        assertEquals("Mantenimiento", value("my_training_intention_maintenance"))
        assertEquals("Sin definir", value("my_training_unset"))
        assertEquals("Lugar", value("my_training_place_label"))
        assertEquals("Mancuernas", value("equipment_dumbbell"))
    }
}
