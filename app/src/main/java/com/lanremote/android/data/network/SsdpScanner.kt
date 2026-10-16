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
    val name: String = "LG Smart TV"
)

class SsdpScanner(private val context: Context) {

    companion object {
        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
    }

    /**
     * Executa busca por TVs LG NetCast na rede local via SSDP M-SEARCH com fallback para scan de sub-rede.
     */
    suspend fun discoverTVs(): List<DiscoveredTV> = withContext(Dispatchers.IO) {
        val discovered = mutableMapOf<String, DiscoveredTV>()

        // 1. Tentar descoberta via SSDP Multicast
        val ssdpResults = runSsdpDiscovery()
        for (tv in ssdpResults) {
            discovered[tv.ip] = tv
        }

        // Se encontrou via SSDP, retorna imediatamente
        if (discovered.isNotEmpty()) {
            return@withContext discovered.values.toList()
        }

        // 2. Fallback: Varredura de sub-rede local na porta 8080 (caso o roteador bloqueie multicast/SSDP)
        val subnetResults = scanSubnetForPort8080()
        for (tv in subnetResults) {
            discovered[tv.ip] = tv
        }

        discovered.values.toList()
    }

    private suspend fun runSsdpDiscovery(): List<DiscoveredTV> = withContext(Dispatchers.IO) {
        val tvList = mutableListOf<DiscoveredTV>()
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
                            response.contains("roap", ignoreCase = true) ||
                            response.contains("8080")
                        ) {
                            tvList.add(DiscoveredTV(ip = senderIp, port = 8080, name = "LG NetCast TV ($senderIp)"))
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

        tvList
    }

    private suspend fun scanSubnetForPort8080(): List<DiscoveredTV> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DiscoveredTV>()
        val localIp = getLocalIpAddress() ?: return@withContext emptyList()
        val parts = localIp.split(".")
        if (parts.size != 4) return@withContext emptyList()

        val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"

        coroutineScope {
            val tasks = (1..254).map { hostNum ->
                async {
                    val targetIp = "$prefix.$hostNum"
                    if (isPortOpen(targetIp, 8080, 200)) {
                        DiscoveredTV(ip = targetIp, port = 8080, name = "TV em $targetIp")
                    } else {
                        null
                    }
                }
            }
            tasks.awaitAll().filterNotNull().forEach { results.add(it) }
        }

        results
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
