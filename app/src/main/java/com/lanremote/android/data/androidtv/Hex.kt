package com.lanremote.android.data.androidtv

private const val HEX_DIGITS = "0123456789abcdef"

internal fun isHexDigit(c: Char): Boolean = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

/** "8e54" → [0x8E, 0x54]. Quem chama garante tamanho par e só dígitos hexadecimais. */
internal fun hexToBytes(hex: String): ByteArray =
    ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

internal fun hexByte(value: Int): String = "${HEX_DIGITS[value shr 4 and 0xF]}${HEX_DIGITS[value and 0xF]}"

internal fun ByteArray.toHex(): String = joinToString("") { hexByte(it.toInt() and 0xFF) }
