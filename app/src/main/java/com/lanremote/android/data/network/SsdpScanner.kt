package com.lanremote.android.data.network

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

data class DiscoveredTV(
    val ip: String,
    val port: Int = 8080,
    val name: String = "LG Smart TV",
    /** Modelo que a TV informou na descrição UDAP ("42LN5700-SA_11"). */
    val model: String? = null
)

/**
 * Procura TVs LG NetCast na rede local: SSDP e, se ninguém responder, a varredura da porta 8080
 * da sub-rede. Só vira TV quem se apresenta como uma pela descrição UDAP ([identify]): outros
 * aparelhos também usam a porta 8080 (um computador com um servidor, por exemplo).
 */
class SsdpScanner(
    private val context: Context,
    /** Modelo da TV em host:porta pela descrição UDAP, ou nulo se ali não houver uma TV LG. */
    private val identify: suspend (host: String, port: Int) -> String?
) {

    companion object {
        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val ROAP_PORT = 8080
    }

    /**
     * Executa busca por TVs LG NetCast na rede local via SSDP M-SEARCH com fallback para scan de sub-rede.
     */
    suspend fun discoverTVs(): List<DiscoveredTV> = withContext(Dispatchers.IO) {
        // 1. SSDP multicast; quem responde ainda precisa se apresentar como TV LG
        val bySsdp = TvCandidates.confirm(runSsdpDiscovery(), ROAP_PORT, identify)
        if (bySsdp.isNotEmpty()) return@withContext bySsdp

        // 2. Fallback: varredura da sub-rede na porta 8080 (caso o roteador bloqueie multicast/SSDP)
        TvCandidates.confirm(scanSubnetForPort8080(), ROAP_PORT, identify)
    }

    /** Endereços que responderam ao M-SEARCH com cara de TV LG. */
    private suspend fun runSsdpDiscovery(): List<String> = withContext(Dispatchers.IO) {
        val found = mutableListOf<String>()
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = wifiManager?.createMulticastLock("LanRemoteSsdpLock")?.apply {
            setReferenceCounted(true)
        }

        try {
            multicastLock?.acquire()

            val query = """
                M-SEARCH * HTTP/1.1
                HOST: $SSDP_ADDRESS:$SSDP_PORT
                MAN: "ssdp:discover"
                MX: 2
                ST: urn:schemas-upnp-org:device:MediaRenderer:1
            """.trimIndent().replace("\n", "\r\n") + "\r\n\r\n"

            DatagramSocket().use { socket ->
                socket.soTimeout = 2500
                val data = query.toByteArray()
                val group = InetAddress.getByName(SSDP_ADDRESS)
                val packet = DatagramPacket(data, data.size, group, SSDP_PORT)
                socket.send(packet)

                val buffer = ByteArray(2048)
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < 2500) {
                    try {
                        val receivePacket = DatagramPacket(buffer, buffer.size)
                        socket.receive(receivePacket)
                        val response = String(receivePacket.data, 0, receivePacket.length)
                        val senderIp = receivePacket.address.hostAddress ?: continue

                        if (response.contains("LG", ignoreCase = true) ||
                            response.contains("NetCast", ignoreCase = true) ||
                            response.contains("roap", ignoreCase = true)
                        ) {
                            found.add(senderIp)
                        }
                    } catch (e: Exception) {
                        // Timeout normal do socket
                        break
                    }
                }
            }
        } catch (e: Exception) {
            // Ignora erro de socket/multicast e permite prosseguir para fallback
        } finally {
            try {
                if (multicastLock?.isHeld == true) {
                    multicastLock.release()
                }
            } catch (ignored: Exception) {}
        }

        found.distinct()
    }

    /** Endereços da sub-rede do Wi-Fi com a porta 8080 aberta. */
    private suspend fun scanSubnetForPort8080(): List<String> = withContext(Dispatchers.IO) {
        val localIp = getLocalIpAddress() ?: return@withContext emptyList()
        val parts = localIp.split(".")
        if (parts.size != 4) return@withContext emptyList()

        val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"

        coroutineScope {
            (1..254).map { hostNum ->
                async {
                    val targetIp = "$prefix.$hostNum"
                    if (targetIp != localIp && isPortOpen(targetIp, ROAP_PORT, 200)) targetIp else null
                }
            }.awaitAll().filterNotNull()
        }
    }

    private fun isPortOpen(ip: String, port: Int, timeoutMillis: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMillis)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172."))) {
                            return host
                        }
                    }
                }
            }
        } catch (ignored: Exception) {}
        return null
    }
}

/** A parte da busca que não depende da rede: confirmar os candidatos e escolher a TV. */
object TvCandidates {
    /**
     * Pergunta a cada endereço, ao mesmo tempo, se ali há uma TV LG; só os que se apresentam ficam,
     * com o nome do modelo.
     */
    suspend fun confirm(
        hosts: List<String>,
        port: Int,
        identify: suspend (host: String, port: Int) -> String?
    ): List<DiscoveredTV> = coroutineScope {
        hosts.distinct().map { host ->
            async {
                identify(host, port)?.let { model ->
                    DiscoveredTV(ip = host, port = port, name = "TV LG ${shortModel(model)}", model = model)
                }
            }
        }.awaitAll().filterNotNull()
    }

    /** "42LN5700-SA_11" → "42LN5700". */
    fun shortModel(model: String): String = model.substringBefore('-').ifBlank { model }

    /**
     * Qual das TVs achadas é a deste controle: a que ainda está no endereço salvo; senão a única
     * da rede; com várias, só a do mesmo modelo, se for uma só. Nulo quando não dá para saber.
     */
    fun choose(found: List<DiscoveredTV>, currentHost: String, currentModel: String?): DiscoveredTV? {
        if (found.isEmpty()) return null
        found.firstOrNull { it.ip == currentHost }?.let { return it }
        found.singleOrNull()?.let { return it }
        return found.filter { it.model != null && it.model == currentModel }.singleOrNull()
    }
}
