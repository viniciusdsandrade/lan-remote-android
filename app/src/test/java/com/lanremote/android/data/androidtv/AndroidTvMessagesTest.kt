package com.lanremote.android.data.androidtv

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Vetores do PLANO_ATUALIZACAO.md, seção 4.3 (bytes enquadrados, em hexadecimal). */
class AndroidTvMessagesTest {

    private fun framed(payload: ByteArray) = ProtoWriter.framed(payload)

    @Test
    fun pairingRequest() {
        assertArrayEquals(
            hexBytes(
                "27 08 02 10 C8 01 52 20 0A 09 61 74 76 72 65 6D 6F 74 65 12 13 4C 61 6E 52 65 6D 6F 74 65 " +
                    "20 28 41 6E 64 72 6F 69 64 29"
            ),
            framed(AndroidTvMessages.pairingRequest())
        )
    }

    @Test
    fun pairingOption() {
        assertArrayEquals(
            hexBytes("10 08 02 10 C8 01 A2 01 08 0A 04 08 03 10 06 18 01"),
            framed(AndroidTvMessages.pairingOption())
        )
    }

    @Test
    fun pairingConfiguration() {
        assertArrayEquals(
            hexBytes("10 08 02 10 C8 01 F2 01 08 0A 04 08 03 10 06 10 01"),
            framed(AndroidTvMessages.pairingConfiguration())
        )
    }

    @Test
    fun pairingSecretCarriesTheHash() {
        val secret = ByteArray(32) { it.toByte() }
        val message = ProtoMessage(AndroidTvMessages.pairingSecret(secret))
        assertEquals(2, message.int(1))
        assertEquals(200, message.int(2))
        assertArrayEquals(secret, message.message(AndroidTvMessages.PAIRING_SECRET)!!.bytes(1))
    }

    @Test
    fun keyInjectPower() {
        assertArrayEquals(hexBytes("06 52 04 08 1A 10 03"), framed(AndroidTvMessages.keyInject(AndroidTvRemote.KEY_POWER)))
    }

    @Test
    fun setActiveReply() {
        assertArrayEquals(hexBytes("05 12 03 08 EE 04"), framed(AndroidTvMessages.setActiveReply()))
    }

    @Test
    fun pingReplyEchoesTheValue() {
        assertArrayEquals(hexBytes("04 4A 02 08 07"), framed(AndroidTvMessages.pingReply(7)))
    }

    @Test
    fun configureReplyDescribesTheApp() {
        val reply = ProtoMessage(AndroidTvMessages.configureReply(DeviceInfo("Pixel 9", "Google", "1.1.0")))
        val configure = reply.message(AndroidTvMessages.REMOTE_CONFIGURE)!!
        assertEquals(622, configure.int(1))
        val info = configure.message(2)!!
        assertEquals("Pixel 9", info.string(1))
        assertEquals("Google", info.string(2))
        assertEquals(1, info.int(3))
        assertEquals("1", info.string(4))
        assertEquals("lanremote", info.string(5))
        assertEquals("1.1.0", info.string(6))
    }
}
