package com.lanremote.android.data.androidtv

import java.math.BigInteger
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey

/** Módulo e expoente de uma chave RSA pública, em big-endian sem zeros à esquerda. */
class RsaPublicParts(val modulus: ByteArray, val exponent: ByteArray) {
    companion object {
        fun of(key: RSAPublicKey) = RsaPublicParts(
            PairingMath.unsignedBytes(key.modulus),
            PairingMath.unsignedBytes(key.publicExponent)
        )
    }
}

/** O código de 6 caracteres da TV: o primeiro byte é um checksum, os outros dois são o nonce. */
class PairingCode(val checksum: Byte, val nonce: ByteArray)

object PairingMath {

    /** Lê o código digitado (espaços e minúsculas valem). Nulo se não tiver 6 hexadecimais. */
    fun parse(code: String): PairingCode? {
        val clean = code.trim().uppercase()
        if (clean.length != 6 || !clean.all(::isHexDigit)) return null
        val bytes = hexToBytes(clean)
        return PairingCode(checksum = bytes[0], nonce = bytes.copyOfRange(1, 3))
    }

    /**
     * SHA-256 das duas chaves públicas e do nonce, nesta ordem: módulo e expoente do cliente,
     * módulo e expoente do servidor, nonce. O primeiro byte tem que bater com o checksum do código.
     */
    fun secret(client: RsaPublicParts, server: RsaPublicParts, nonce: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").run {
            update(client.modulus)
            update(client.exponent)
            update(server.modulus)
            update(server.exponent)
            update(nonce)
            digest()
        }

    /** Big-endian sem zeros à esquerda: `BigInteger.toByteArray()` traz um 0x00 de sinal. */
    fun unsignedBytes(value: BigInteger): ByteArray =
        value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
}
