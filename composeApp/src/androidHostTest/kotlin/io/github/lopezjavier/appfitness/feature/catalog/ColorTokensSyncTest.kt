package io.github.lopezjavier.appfitness.feature.catalog

import io.github.lopezjavier.appfitness.designsystem.AppColors
import io.github.lopezjavier.appfitness.designsystem.KineticDarkColors
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals

/** The theme catalog lists the colour tokens by hand: it must not drift from [AppColors]. */
class ColorTokensSyncTest {
    @Test
    fun colorTokens_coverEveryAppColorsProperty() {
        val properties =
            AppColors::class.java.declaredFields
                .filter { !Modifier.isStatic(it.modifiers) }
                .map { it.name }
                .toSet()
        assertEquals(properties, ColorTokens.map { it.first }.toSet())
    }

    @Test
    fun colorTokens_haveUniqueNamesAndResolve() {
        assertEquals(ColorTokens.size, ColorTokens.map { it.first }.toSet().size)
        ColorTokens.forEach { (_, read) -> read(KineticDarkColors) }
    }
}
