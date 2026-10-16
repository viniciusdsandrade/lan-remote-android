package com.lanremote.android.data.androidtv

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey

class PairingMathTest {

    private val client = RsaPublicParts(bytes(0x01, 0x02, 0x03), bytes(0x01, 0x00, 0x01))
    private val server = RsaPublicParts(bytes(0x09, 0x08, 0x07), bytes(0x01, 0x00, 0x01))

    @Test
    fun parsesTheCodeShownOnTheTv() {
        val code = PairingMath.parse(" 8e5434 ")!!
        assertEquals(0x8E.toByte(), code.checksum)
        assertArrayEquals(bytes(0x54, 0x34), code.nonce)
        assertNull("curto demais", PairingMath.parse("8E543"))
        assertNull("não é hexadecimal", PairingMath.parse("8E543G"))
        assertNull(PairingMath.parse(""))
    }

    @Test
    fun secretMatchesTheReferenceVector() {
        // Mesmo algoritmo do iOS e do Mac; conferido também com hashlib.sha256.
        val secret = PairingMath.secret(client, server, bytes(0x54, 0x34))
        assertEquals("b0d4f9c2a661eaf53c593a24ea35123c634fb4bcea9bd08f0fe515d814b3c491", secret.toHex())
    }

    @Test
    fun secretDependsOnTheNonceAndOnTheKeyOrder() {
        val a = PairingMath.secret(client, server, bytes(0x54, 0x34))
        val b = PairingMath.secret(client, server, bytes(0x54, 0x35))
        val swapped = PairingMath.secret(server, client, bytes(0x54, 0x34))
        assertEquals(32, a.size)
        assertFalse(a.contentEquals(b))
        assertFalse("a TV Box faz o hash do cliente primeiro", a.contentEquals(swapped))
    }

    @Test
    fun unsignedBytesDropTheSignByte() {
        val withTopBit = BigInteger(1, bytes(0x80, 0x01))
        assertArrayEquals(bytes(0x00, 0x80, 0x01), withTopBit.toByteArray())
        assertArrayEquals(bytes(0x80, 0x01), PairingMath.unsignedBytes(withTopBit))
        assertArrayEquals(bytes(0x01, 0x00, 0x01), PairingMath.unsignedBytes(BigInteger.valueOf(65537)))
    }

    @Test
    fun rsaPartsOfARealKey() {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public as RSAPublicKey
        val parts = RsaPublicParts.of(key)
        assertEquals("2048 bits sem o byte de sinal", 256, parts.modulus.size)
        assertArrayEquals(bytes(0x01, 0x00, 0x01), parts.exponent)
    }
}
