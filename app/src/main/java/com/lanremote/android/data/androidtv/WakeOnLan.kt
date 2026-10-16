package com.lanremote.android.data.androidtv

object WakeOnLan {

    /** Seis bytes 0xFF seguidos do MAC dezesseis vezes: 102 bytes. Nulo se não for um MAC. */
    fun magicPacket(mac: String): ByteArray? {
        val address = MacAddress.bytes(mac) ?: return null
        return ByteArray(6) { 0xFF.toByte() } + ByteArray(16 * 6) { address[it % 6] }
    }
}
