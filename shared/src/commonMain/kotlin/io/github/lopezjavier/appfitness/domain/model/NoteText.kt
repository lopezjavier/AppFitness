package io.github.lopezjavier.appfitness.domain.model

/**
 * Free-text notes (set, routine exercise, discomfort): 1-200 characters once trimmed, blank means none. The text is
 * never interpreted (ADR-81). One source for every caller.
 */
object NoteText {
    const val MAX = 200

    /** The trimmed text, or null when it is blank. */
    fun normalise(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }

    /** Whether the trimmed text fits in [MAX] characters (a blank note is valid: it means no note). */
    fun isValid(raw: String?): Boolean = (normalise(raw)?.let(::length) ?: 0) <= MAX

    /** Characters as the database counts them (code points): an emoji is one, not two UTF-16 units. */
    fun length(text: String): Int = text.count { !it.isLowSurrogate() }
}
