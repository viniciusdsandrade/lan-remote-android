package com.lanremote.android.data.androidtv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingCodeReaderTest {

    @Test
    fun candidates_coverTheUsualOcrMixUps() {
        // O que o OCR lê na TV: "O" no lugar de 0, "B" no lugar de 8; as outras linhas não são códigos.
        val codes = PairingCodeReader.candidates(listOf("lan remote (celular)", "4f 2a 9c", "8e5434", "o1 b2 c3", "abcdefg"))

        assertTrue(codes.contains("4F2A9C"))
        assertTrue(codes.contains("8E5434"))
        assertTrue("8 pode ser um B lido errado", codes.contains("BE5434"))
        assertTrue("O pode ser um 0 lido errado", codes.contains("01B2C3"))
        assertTrue("B pode ser um 8 lido errado", codes.contains("0182C3"))
        assertFalse(codes.any { it.length != 6 || !it.all { c -> c in '0'..'9' || c in 'A'..'F' } })
        assertEquals("sem repetição", codes.size, codes.toSet().size)
    }

    @Test
    fun candidates_emptyWithoutACodeOnScreen() {
        assertEquals(emptyList<String>(), PairingCodeReader.candidates(listOf("nada aqui", "12345", "1234567")))
    }
}
