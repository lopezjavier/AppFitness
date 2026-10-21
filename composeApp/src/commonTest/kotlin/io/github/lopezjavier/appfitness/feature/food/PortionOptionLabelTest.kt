package io.github.lopezjavier.appfitness.feature.food

import kotlin.test.Test
import kotlin.test.assertEquals

class PortionOptionLabelTest {
    @Test
    fun labelThatIsTheBaseAmount_isShownAlone() {
        assertEquals("100 g", portionOptionLabel("100 g", "100", "g", "100 g (100 g)"))
    }

    @Test
    fun milliliterLabel_isShownAlone() {
        assertEquals("100 ml", portionOptionLabel("100 ml", "100", "ml", "100 ml (100 ml)"))
    }

    @Test
    fun labelWithoutSpaceOrWithOtherCase_isShownAlone() {
        assertEquals("100g", portionOptionLabel("100g", "100", "g", "100g (100 g)"))
        assertEquals("100 G", portionOptionLabel("100 G", "100", "g", "100 G (100 g)"))
    }

    @Test
    fun namedPortion_keepsItsAmount() {
        assertEquals("1 huevo (55 g)", portionOptionLabel("1 huevo", "55", "g", "1 huevo (55 g)"))
    }

    @Test
    fun labelWithAnotherAmount_keepsItsAmount() {
        assertEquals("100 g (250 g)", portionOptionLabel("100 g", "250", "g", "100 g (250 g)"))
    }
}
