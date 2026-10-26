package io.github.lopezjavier.appfitness.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HelpContentTest {
    @Test
    fun bullets_defaultToNone() = assertTrue(HelpContent(listOf("Texto")).bullets.isEmpty())

    @Test
    fun bodyOverload_isASingleParagraph() = assertEquals(HelpContent(listOf("Texto")), HelpContent.fromBody("Texto"))

    @Test
    fun paragraphsAndBullets_arePreservedInOrder() {
        val content = HelpContent(listOf("A", "B"), listOf("1", "2"))
        assertEquals(listOf("A", "B"), content.paragraphs)
        assertEquals(listOf("1", "2"), content.bullets)
    }
}
