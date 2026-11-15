package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val NO_BREAK_SPACE = ' '

private fun String.plainSpaces(): String = replace(NO_BREAK_SPACE, ' ')

private fun SemanticsNodeInteraction.renderedText(): String =
    fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }

/**
 * Compares the text of a node with [expected] treating U+00A0 as a plain space: the copy keeps number and unit
 * together (R-93), and these tests cover the wording, not the joining.
 */
internal fun SemanticsNodeInteraction.assertTextEqualsIgnoringNbsp(expected: String): SemanticsNodeInteraction {
    assertEquals(expected.plainSpaces(), renderedText().plainSpaces())
    return this
}

/** Like `assertTextContains(substring = true)` but U+00A0 and a plain space are the same character. */
internal fun SemanticsNodeInteraction.assertTextContainsIgnoringNbsp(part: String): SemanticsNodeInteraction {
    val actual = renderedText().plainSpaces()
    assertTrue(actual.contains(part.plainSpaces()), "'$actual' does not contain '$part'")
    return this
}
