package io.github.lopezjavier.appfitness.platform

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NonceTest {
    @Test
    fun sha256Hex_matchesAKnownVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex("abc"))
    }

    @Test
    fun rawNonce_isUrlSafeAndLongEnough() {
        val nonce = generateRawNonce()

        // 32 bytes encode to 43 URL-safe Base64 characters without padding.
        assertEquals(43, nonce.length)
        assertTrue(nonce.all { it.isLetterOrDigit() || it == '-' || it == '_' }, nonce)
    }

    @Test
    fun rawNonce_isUniquePerCall() {
        assertNotEquals(generateRawNonce(), generateRawNonce())
    }
}
