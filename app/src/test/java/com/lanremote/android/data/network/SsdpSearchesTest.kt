package com.lanremote.android.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Toda resposta da LG NetCast a uma busca UDAP (`udap:rootservice`) põe na tela o pop-up de
 * descoberta ("modelo_XX", celular e TV, "Pairing Key"). A busca automática roda sempre que a TV
 * para de responder, então ela só pode ser a DLNA.
 */
class SsdpSearchesTest {

    @Test
    fun theAutomaticSearchIsOnlyTheDlnaOne() {
        val (message, address, port) = SsdpScanner.searches(udap = false).single()

        assertTrue(message.startsWith("M-SEARCH * HTTP/1.1\r\n"))
        assertTrue(message.contains("ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n"))
        assertTrue(message.contains("USER-AGENT: ${SsdpScanner.DLNA_USER_AGENT}\r\n"))
        assertFalse("busca UDAP põe o pop-up na TV", message.contains("udap", ignoreCase = true))
        assertTrue(message.endsWith("\r\n\r\n"))
        assertEquals("239.255.255.250", address)
        assertEquals(1900, port)
    }

    @Test
    fun theSearchTheUserAskedForKeepsUdapAndTheBroadcast() {
        val searches = SsdpScanner.searches(udap = true)

        assertEquals(listOf("239.255.255.250" to 1900, "255.255.255.255" to 1990), searches.map { it.second to it.third })
        assertTrue(searches.all { it.first.contains("ST: udap:rootservice\r\n") })
        assertTrue(searches[1].first.startsWith("B-SEARCH * HTTP/1.1\r\n"))
    }
}
