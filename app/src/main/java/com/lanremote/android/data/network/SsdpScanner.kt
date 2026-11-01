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

data class DiscoveredTV(
    val ip: String,
    val port: Int = 8080,
    val name: String = "LG Smart TV",
    /** Modelo que a TV informou na descrição UDAP ("42LN5700-SA_11"). */
    val model: String? = null
)

/**
 * Procura TVs LG NetCast na rede local: SSDP e, se ninguém responder, a varredura da porta 8080
 * da sub-rede. Outros aparelhos também usam a porta 8080 (um computador com um servidor, por
 * exemplo), então só vira TV quem responde como uma: pela descrição UDAP ([identify]) na busca que
 * o usuário pediu, pela API ROAP ([answers]) na automática.
 *
 * A busca automática não usa UDAP: cada resposta da TV a uma busca UDAP (`udap:rootservice`) põe na
 * tela o pop-up de descoberta da LG ("modelo_XX", celular e TV, "Pairing Key"; spec UDAP 2.0, "Host
 * screen after discovery response"), e ela roda sempre que a TV para de responder.
 */
class SsdpScanner(
    private val context: Context,
    /** Modelo da TV em host:porta pela descrição UDAP, ou nulo se ali não houver uma TV LG. */
    private val identify: suspend (host: String, port: Int) -> String?,
    /** Se em host:porta há uma TV LG, pela API ROAP (sem nada de UDAP). */
    private val answers: suspend (host: String, port: Int) -> Boolean
) {

    companion object {
        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val BSEARCH_ADDRESS = "255.255.255.255"
        private const val BSEARCH_PORT = 1990
        private const val ROAP_PORT = 8080

        /** User-Agent da busca DLNA: sem "UDAP/2.0". */
        const val DLNA_USER_AGENT = "Android/1.0 UPnP/1.1 lg_roap_control/1.0.0"

        /**
         * As buscas SSDP como (mensagem, endereço, porta): a DLNA (MediaRenderer), que o
         * renderizador DLNA da TV, um servidor à parte, responde sem mostrar nada; com [udap], a
         * busca UDAP em multicast e a B-SEARCH em broadcast na porta 1990 (spec LG NetCast UDAP 2.0).
         */
        fun searches(udap: Boolean): List<Triple<String, String, Int>> =
            if (udap) {
                listOf(
                    Triple(message("M-SEARCH", SSDP_ADDRESS, SSDP_PORT, "udap:rootservice"), SSDP_ADDRESS, SSDP_PORT),
                    Triple(message("B-SEARCH", BSEARCH_ADDRESS, BSEARCH_PORT, "udap:rootservice"), BSEARCH_ADDRESS, BSEARCH_PORT)
                )
            } else {
                val dlna = message("M-SEARCH", SSDP_ADDRESS, SSDP_PORT, "urn:schemas-upnp-org:device:MediaRenderer:1", DLNA_USER_AGENT)
                listOf(Triple(dlna, SSDP_ADDRESS, SSDP_PORT))
            }

        private fun message(method: String, address: String, port: Int, target: String, userAgent: String? = null): String =
            listOfNotNull(
                "$method * HTTP/1.1",
                "HOST: $address:$port",
                "MAN: \"ssdp:discover\"",
                "MX: 2",
                "ST: $target",
                userAgent?.let { "USER-AGENT: $it" }
            ).joinToString("\r\n") + "\r\n\r\n"
    }

    /**
     * Executa busca por TVs LG NetCast na rede local: a busca DLNA; a UDAP só quando o usuário pediu
     * ([askedByUser]) e a DLNA não achou nada; por fim a varredura da sub-rede.
     */
    suspend fun discoverTVs(askedByUser: Boolean = false): List<DiscoveredTV> = withContext(Dispatchers.IO) {
        val confirm: suspend (List<String>) -> List<DiscoveredTV> = { hosts ->
            if (askedByUser) TvCandidates.confirm(hosts, ROAP_PORT, identify)
            else TvCandidates.confirmAnswering(hosts, ROAP_PORT, answers)
        }

        // 1. SSDP DLNA; quem responde ainda precisa se apresentar como TV LG
        val byDlna = confirm(runSsdpDiscovery(udap = false))
        if (byDlna.isNotEmpty()) return@withContext byDlna

        // 2. Busca UDAP, que mostra o pop-up de descoberta na TV: só quando o usuário pediu
        if (askedByUser) {
            val byUdap = confirm(runSsdpDiscovery(udap = true))
            if (byUdap.isNotEmpty()) return@withContext byUdap
        }

        // 3. Fallback: varredura da rede do Wi-Fi na porta 8080 (caso o roteador bloqueie multicast/SSDP)
        confirm(scanSubnetForPort8080())
    }

    /** Endereços que responderam às buscas de [searches] com cara de TV LG. */
    private suspend fun runSsdpDiscovery(udap: Boolean): List<String> = withContext(Dispatchers.IO) {
        val found = mutableListOf<String>()
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = wifiManager?.createMulticastLock("LanRemoteSsdpLock")?.apply {
            setReferenceCounted(true)
        }

        try {
            multicastLock?.acquire()

            DatagramSocket().use { socket ->
                socket.soTimeout = 2500
                socket.broadcast = true
                for ((query, address, port) in searches(udap)) {
                    try {
                        val data = query.toByteArray()
                        socket.send(DatagramPacket(data, data.size, InetAddress.getByName(address), port))
                    } catch (ignored: Exception) {}
                }

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
                            response.contains("UDAP", ignoreCase = true) ||
                            response.contains("rootservice", ignoreCase = true) ||
                            response.contains("LN5700", ignoreCase = true) ||
                            response.contains("HTTP/1.1 200", ignoreCase = true)
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

    /**
     * Endereços da rede do Wi-Fi com a porta 8080 aberta: as redes vêm de [LocalNetworks] (o Wi-Fi,
     * não os dados móveis nem uma VPN).
     */
    private suspend fun scanSubnetForPort8080(): List<String> =
        LocalNetworks.hostsAccepting(LocalNetworks.sweepHosts(LocalNetworks.deviceNetworks()), ROAP_PORT)
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

    /**
     * Como [confirm], para a busca automática: fica quem responde à API ROAP, sem a descrição UDAP
     * (e sem o modelo, que o app lê depois que a TV responde).
     */
    suspend fun confirmAnswering(
        hosts: List<String>,
        port: Int,
        answers: suspend (host: String, port: Int) -> Boolean
    ): List<DiscoveredTV> = coroutineScope {
        hosts.distinct().map { host ->
            async { if (answers(host, port)) DiscoveredTV(ip = host, port = port, name = "TV LG") else null }
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
