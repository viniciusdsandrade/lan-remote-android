package com.lanremote.android.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket

/** Redes fictícias: o repositório é público. */
class LocalNetworksTest {

    private fun net(name: String, address: String, prefix: Int) =
        LocalIPv4Network(name, LocalNetworks.ipv4Value(address)!!, prefix)

    @Test
    fun theSweepCoversEveryHostOfTheWifiSubnetButThePhoneItself() {
        val hosts = LocalNetworks.sweepHosts(listOf(net("wlan0", "10.20.30.40", 24)))

        assertEquals(253, hosts.size)
        assertEquals("10.20.30.1", hosts.first())
        assertEquals("10.20.30.254", hosts.last())
        assertTrue("10.20.30.10" in hosts)
        assertFalse("o próprio celular fica de fora", "10.20.30.40" in hosts)
        assertFalse("sem endereço de rede nem de broadcast", "10.20.30.0" in hosts || "10.20.30.255" in hosts)
    }

    @Test
    fun aSlash22IsSweptWholeAndWiderNetworksOnlyThePhonesSlash24() {
        val hosts22 = LocalNetworks.sweepHosts(listOf(net("wlan0", "10.1.5.7", 22)))
        assertEquals(1021, hosts22.size)
        assertEquals("10.1.4.1", hosts22.first())
        assertEquals("10.1.7.254", hosts22.last())

        val hosts16 = LocalNetworks.sweepHosts(listOf(net("wlan0", "172.16.5.9", 16)))
        assertEquals(253, hosts16.size)
        assertEquals("172.16.5.1", hosts16.first())

        assertTrue(LocalNetworks.sweepHosts(emptyList()).isEmpty())
    }

    @Test
    fun twoInterfacesOnTheSameSubnetAreSweptOnce() {
        val hosts = LocalNetworks.sweepHosts(listOf(net("wlan0", "10.20.30.40", 24), net("eth0", "10.20.30.41", 24)))

        assertEquals(252, hosts.size)
        assertFalse("10.20.30.41" in hosts)
    }

    @Test
    fun onlyRfc1918AddressesArePrivate() {
        // Antes valia qualquer 172.x, e o primeiro IPv4 de qualquer interface (dados móveis, VPN).
        assertTrue(LocalNetworks.isPrivate(LocalNetworks.ipv4Value("10.0.0.1")!!))
        assertTrue(LocalNetworks.isPrivate(LocalNetworks.ipv4Value("172.31.255.1")!!))
        assertTrue(LocalNetworks.isPrivate(LocalNetworks.ipv4Value("192.168.0.1")!!))
        assertFalse(LocalNetworks.isPrivate(LocalNetworks.ipv4Value("172.32.0.1")!!))
        assertFalse(LocalNetworks.isPrivate(LocalNetworks.ipv4Value("192.0.2.100")!!))
        assertFalse(LocalNetworks.isPrivate(LocalNetworks.ipv4Value("169.254.3.4")!!))
    }

    @Test
    fun ipv4TextRoundTripsAndRejectsGarbage() {
        assertEquals("10.20.30.40", LocalNetworks.ipv4String(LocalNetworks.ipv4Value("10.20.30.40")!!))
        assertNull(LocalNetworks.ipv4Value("10.20.30"))
        assertNull(LocalNetworks.ipv4Value("10.20.30.256"))
        assertNull(LocalNetworks.ipv4Value("a.b.c.d"))
        assertNull(LocalNetworks.ipv4Value("10..30.40"))
    }

    @Test
    fun deviceNetworksAreAlwaysPrivate() {
        for (network in LocalNetworks.deviceNetworks()) {
            assertTrue(network.interfaceName, LocalNetworks.isPrivate(network.address))
        }
    }

    @Test
    fun theConnectSweepFindsTheOpenPortOnly() = runBlocking {
        ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1")).use { server ->
            assertEquals(listOf("127.0.0.1"), LocalNetworks.hostsAccepting(listOf("127.0.0.1"), server.localPort, 1_000))
        }
        val closed = ServerSocket(0).use { it.localPort }
        assertTrue(LocalNetworks.hostsAccepting(listOf("127.0.0.1"), closed, 1_000).isEmpty())
    }

    /**
     * Opcional, contra a TV LG de verdade nesta rede, pelo mesmo caminho da busca automática no
     * celular (rede do aparelho, conexão na 8080 e confirmação ROAP):
     * `LANREMOTE_LIVE_TV=1 ./gradlew testDebugUnitTest --tests '*LocalNetworksTest*'`
     */
    @Test
    fun liveSweepFindsTheTvOnThisNetwork() = runBlocking {
        assumeTrue("precisa de uma TV LG NetCast na rede: LANREMOTE_LIVE_TV=1", System.getenv("LANREMOTE_LIVE_TV") == "1")
        val started = System.currentTimeMillis()
        val open = LocalNetworks.hostsAccepting(LocalNetworks.sweepHosts(LocalNetworks.deviceNetworks()), 8080)
        val tvClient = KtorTVClient(KtorTVClient.createDefaultHttpClient())
        val tvs = TvCandidates.confirmAnswering(open, 8080) { host, port -> tvClient.answersRoap(host, port) }
        val elapsed = System.currentTimeMillis() - started
        assertTrue("a varredura da rede do aparelho precisa achar a TV", tvs.isNotEmpty())
        assertTrue("levou $elapsed ms", elapsed < 15_000)
        println("live sweep: ${tvs.size} TV(s) em $elapsed ms")
    }
}
