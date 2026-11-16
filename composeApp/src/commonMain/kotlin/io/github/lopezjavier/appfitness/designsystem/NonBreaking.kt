package io.github.lopezjavier.appfitness.designsystem

/** Non-breaking text helpers: a fact never wraps between its words (R-93, C-110). */
object NonBreaking {
    const val SPACE: Char = ' '

    /** Joins [parts] with U+00A0, e.g. `join("3", "de", "4")` is "3 de 4" that never breaks. */
    fun join(vararg parts: String): String = parts.joinToString(SPACE.toString())

    /** Replaces every ordinary space of a short fact with U+00A0. */
    fun fact(text: String): String = text.replace(' ', SPACE)
}
