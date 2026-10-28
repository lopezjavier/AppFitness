package io.github.lopezjavier.appfitness.domain.rules

/**
 * The fixed, versioned list of strong-pain terms (planning/04-business-rules.md §4.12). A free-text note that
 * contains one of them, as a whole word or phrase, triggers [MESSAGE]. Plain "dolor" and "molestia" are not terms.
 * Plurals do not match ("pinchazos" is not "pinchazo"): the approved list is literal.
 */
object StrongPainTerms {
    const val MESSAGE = "Si el dolor es fuerte o no mejora, consulta a un profesional sanitario."

    val TERMS: List<String> = listOf(
        "dolor fuerte",
        "dolor agudo",
        "dolor intenso",
        "pinchazo",
        "chasquido",
        "hormigueo",
        "entumecimiento",
        "hinchazón",
        "lesión",
        "me he lesionado",
        "desgarro",
        "no puedo mover",
    )

    private val padded: List<String> = TERMS.map { " ${normalize(it)} " }

    /** True when [note] contains a term as whole words, ignoring case, accents and punctuation. */
    fun matches(note: String?): Boolean {
        if (note.isNullOrBlank()) return false
        val text = " ${normalize(note)} "
        return padded.any { it in text }
    }

    private fun normalize(text: String): String {
        val folded = text.lowercase().map { fold(it) }
        return buildString {
            folded.forEach { char ->
                if (char in 'a'..'z') {
                    append(char)
                } else if (isNotEmpty() && last() != ' ') {
                    append(' ')
                }
            }
        }.trim()
    }

    private fun fold(char: Char): Char = when (char) {
        'á', 'à', 'ä', 'â' -> 'a'
        'é', 'è', 'ë', 'ê' -> 'e'
        'í', 'ì', 'ï', 'î' -> 'i'
        'ó', 'ò', 'ö', 'ô' -> 'o'
        'ú', 'ù', 'ü', 'û' -> 'u'
        'ñ' -> 'n'
        else -> char
    }
}
