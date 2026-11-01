package com.lanremote.android.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/** Uma rede IPv4 em que o celular está, pelo Wi-Fi ou pela Ethernet. */
data class LocalIPv4Network(
    val interfaceName: String,
    /** Endereço do celular nesta rede, como inteiro sem sinal de 32 bits (192.0.2.1 = 0xC0000201). */
    val address: Long,
    val prefixLength: Int
) {
    val netmask: Long get() = if (prefixLength <= 0) 0L else (0xFFFF_FFFFL shl (32 - prefixLength)) and 0xFFFF_FFFFL
}

/**
 * Em que rede procurar a TV. Antes a varredura usava o primeiro IPv4 privado de qualquer interface:
 * com os dados móveis ligados (endereço 10.x da operadora), uma VPN ou o roteador do celular ativo,
 * ela podia varrer a rede errada e nunca achar a TV.
 */
object LocalNetworks {

    /** Interfaces que nunca são o Wi-Fi da casa: dados móveis, VPN, ponto de acesso, Wi-Fi Direct. */
    private val foreignInterfaces = listOf("rmnet", "ccmni", "v4-", "tun", "ppp", "ipsec", "ap", "swlan", "p2p", "dummy", "lo")

    /**
     * As redes do Wi-Fi (`wlan*`) ou da Ethernet (`eth*`) com endereço privado. Se o aparelho dá outro
     * nome à interface do Wi-Fi, vale qualquer interface com endereço privado que não seja de dados
     * móveis, VPN ou ponto de acesso.
     */
    fun deviceNetworks(): List<LocalIPv4Network> {
        val all = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { runCatching { it.isUp && !it.isLoopback && !it.isVirtual }.getOrDefault(false) }
                .flatMap { intf ->
                    intf.interfaceAddresses.mapNotNull { ia ->
                        val addr = ia.address as? Inet4Address ?: return@mapNotNull null
                        val value = ipv4Value(addr.hostAddress ?: return@mapNotNull null) ?: return@mapNotNull null
                        if (!isPrivate(value)) null else LocalIPv4Network(intf.name, value, ia.networkPrefixLength.toInt())
                    }
                }
        } catch (e: Exception) {
            emptyList()
        }
        val wifi = all.filter { it.interfaceName.startsWith("wlan") || it.interfaceName.startsWith("eth") }
        if (wifi.isNotEmpty()) return wifi.distinct()
        return all.filter { net -> foreignInterfaces.none { net.interfaceName.startsWith(it) } }.distinct()
    }

    /**
     * Os endereços que a varredura tenta, em ordem: todos os hosts de cada rede do celular (só o /24
     * do próprio celular quando a rede é maior que /22), sem o próprio celular nem os endereços de
     * rede e de broadcast.
     */
    fun sweepHosts(networks: List<LocalIPv4Network>): List<String> {
        val own = networks.map { it.address }.toSet()
        val seen = LinkedHashSet<Long>()
        for (network in networks) {
            val mask = if (network.prefixLength in 22..30) network.netmask else 0xFFFF_FF00L
            val base = network.address and mask
            val broadcast = base or (mask.inv() and 0xFFFF_FFFFL)
            var host = base + 1
            while (host < broadcast) {
                if (host !in own) seen.add(host)
                host++
            }
        }
        return seen.map(::ipv4String)
    }

    /** Tempo de cada conexão da varredura: cobre o ARP e o Wi-Fi do celular saindo da economia de energia. */
    const val SWEEP_CONNECT_TIMEOUT_MILLIS = 1_000

    /** Conexões simultâneas da varredura: um /24 inteiro em duas levas, acima das 64 threads do Dispatchers.IO. */
    private const val SWEEP_PARALLELISM = 128

    @OptIn(ExperimentalCoroutinesApi::class)
    private val sweepDispatcher = Dispatchers.IO.limitedParallelism(SWEEP_PARALLELISM)

    /**
     * Os [hosts] que aceitam conexão TCP na [port], todos em paralelo. 200 ms por conexão, como
     * antes, perdia a TV com o Wi-Fi do celular em economia de energia.
     */
    suspend fun hostsAccepting(
        hosts: List<String>,
        port: Int,
        timeoutMillis: Int = SWEEP_CONNECT_TIMEOUT_MILLIS
    ): List<String> = withContext(Dispatchers.IO) {
        coroutineScope {
            hosts.map { host ->
                async(sweepDispatcher) { if (isPortOpen(host, port, timeoutMillis)) host else null }
            }.awaitAll().filterNotNull()
        }
    }

    private fun isPortOpen(ip: String, port: Int, timeoutMillis: Int): Boolean =
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMillis)
                true
            }
        } catch (e: Exception) {
            false
        }

    fun ipv4Value(text: String): Long? {
        val parts = text.split(".")
        if (parts.size != 4) return null
        var value = 0L
        for (part in parts) {
            val octet = part.toIntOrNull()?.takeIf { it in 0..255 && part.isNotEmpty() } ?: return null
            value = (value shl 8) or octet.toLong()
        }
        return value
    }

    fun ipv4String(value: Long): String =
        "${(value shr 24) and 0xFF}.${(value shr 16) and 0xFF}.${(value shr 8) and 0xFF}.${value and 0xFF}"

    /** Faixas da RFC 1918: 10/8, 172.16/12 e 192.168/16. */
    fun isPrivate(value: Long): Boolean =
        (value and 0xFF00_0000L) == 0x0A00_0000L ||
            (value and 0xFFF0_0000L) == 0xAC10_0000L ||
            (value and 0xFFFF_0000L) == 0xC0A8_0000L
}
