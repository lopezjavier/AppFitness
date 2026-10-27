package io.github.lopezjavier.appfitness.feature.about

import io.github.lopezjavier.appfitness.domain.model.BuildInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AboutViewModelTest {
    private val info = BuildInfo("AppFitness", "1.0.0", 7L, "local")

    @Test
    fun uiState_exposesTheBuildInfo() {
        val viewModel = AboutViewModel(info)

        assertEquals(AboutUiState(info), viewModel.uiState.value)
    }

    @Test
    fun parseCredit_splitsTitleAndText() {
        val credit = Credit.parse("RepDB|Datos de ejercicios por RepDB (repdb.co)")

        assertEquals(Credit("RepDB", "Datos de ejercicios por RepDB (repdb.co)"), credit)
    }

    @Test
    fun parseCredit_keepsLaterSeparatorsInTheText() {
        assertEquals("a|b", Credit.parse("T|a|b").text)
    }

    @Test
    fun parseCredit_withoutSeparator_fails() {
        assertFailsWith<IllegalArgumentException> { Credit.parse("no separator") }
    }
}
