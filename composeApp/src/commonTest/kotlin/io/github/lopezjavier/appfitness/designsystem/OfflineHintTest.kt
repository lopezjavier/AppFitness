package io.github.lopezjavier.appfitness.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

class OfflineHintTest {
    @Test
    fun offlineHint_formatsTheStandardSentence() =
        assertEquals("Sin conexión: no se puede guardar", offlineHint("guardar"))

    @Test
    fun offlineHint_acceptsAnyInfinitive() = assertEquals("Sin conexión: no se puede eliminar", offlineHint("eliminar"))
}
