package com.lanremote.android.data.androidtv

object MacAddress {

    /**
     * "02:00:00:00:00:f8" a partir das grafias comuns: dois-pontos, hífens, pontos, sem separador
     * e octetos de um dígito só. Nulo se não for um MAC.
     */
    fun normalize(text: String): String? {
        val octets = text.trim().split(':', '-', '.').filter { it.isNotEmpty() }
        val parts = when {
            octets.size == 6 -> octets
            octets.size == 1 && octets[0].length == 12 -> octets[0].chunked(2)
            else -> return null
        }
        if (parts.any { it.length !in 1..2 || !it.all(::isHexDigit) }) return null
        return parts.joinToString(":") { it.padStart(2, '0') }.lowercase()
    }

    fun bytes(mac: String): ByteArray? = normalize(mac)?.let { hexToBytes(it.replace(":", "")) }

    /** O endereço a [delta] passos deste (o Wi-Fi e o Bluetooth de um aparelho costumam ser vizinhos). */
    fun offset(mac: String, delta: Int): String? {
        val bytes = bytes(mac) ?: return null
        val value = bytes.fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 0xFF) } + delta
        if (value < 0) return null
        val wrapped = value and 0xFFFF_FFFF_FFFFL
        return (5 downTo 0).joinToString(":") { hexByte(((wrapped ushr (it * 8)) and 0xFF).toInt()) }
    }
}
