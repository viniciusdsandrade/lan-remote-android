package com.lanremote.android.data.androidtv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress

object WakeOnLan {

    /** Seis bytes 0xFF seguidos do MAC dezesseis vezes: 102 bytes. Nulo se não for um MAC. */
    fun magicPacket(mac: String): ByteArray? {
        val address = MacAddress.bytes(mac) ?: return null
        return ByteArray(6) { 0xFF.toByte() } + ByteArray(16 * 6) { address[it % 6] }
    }

    /** Broadcast da sub-rede: 192.168.1.33/24 → 192.168.1.255. */
    fun broadcastAddress(address: Inet4Address, prefixLength: Int): InetAddress {
        val ip = address.address.fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xFF) }
        val hostBits = if (prefixLength <= 0) -1 else (-1 shl (32 - prefixLength.coerceAtMost(32))).inv()
        val broadcast = ip or hostBits
        return InetAddress.getByAddress(ByteArray(4) { (broadcast ushr (24 - it * 8)).toByte() })
    }
}

/**
 * Manda os pacotes mágicos por UDP, nas portas 9 e 7: direto para o último IP da TV Box e em
 * broadcast (o da sub-rede do Wi-Fi e 255.255.255.255), que acorda a TV Box mesmo se o IP dela
 * tiver mudado. Pacotes a mais não fazem mal.
 */
class UdpWaker(private val broadcastAddresses: () -> List<InetAddress> = { emptyList() }) : Waker {

    override suspend fun wake(host: String, macs: List<String>) = withContext(Dispatchers.IO) {
        val targets = buildList {
            runCatching { InetAddress.getByName(host) }.getOrNull()?.let(::add)
            addAll(broadcastAddresses())
            add(InetAddress.getByName("255.255.255.255"))
        }.distinct()
        DatagramSocket().use { socket ->
            socket.broadcast = true
            for (mac in macs) {
                val packet = WakeOnLan.magicPacket(mac) ?: continue
                for (target in targets) {
                    for (port in intArrayOf(9, 7)) {
                        // Um destino que falha (rede sem broadcast, por exemplo) não impede os outros.
                        runCatching { socket.send(DatagramPacket(packet, packet.size, target, port)) }
                    }
                }
            }
        }
    }
}
