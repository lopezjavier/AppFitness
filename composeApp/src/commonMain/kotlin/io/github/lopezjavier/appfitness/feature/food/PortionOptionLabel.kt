package io.github.lopezjavier.appfitness.feature.food

/**
 * The chip text of a portion: [formatted] ("1 huevo (55 g)") unless the [label] already is the amount in the base
 * unit ("100 g", "100g", "100 ML"), which would read "100 g (100 g)"; then only the label.
 */
internal fun portionOptionLabel(label: String, amount: String, unit: String, formatted: String): String {
    fun String.squeezed() = filterNot { it.isWhitespace() }.lowercase()
    return if (label.squeezed() == "$amount$unit".squeezed()) label else formatted
}
