package com.lanremote.android.data.androidtv

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AndroidTvPairingTest {

    private val client = RsaPublicParts(bytes(0x01, 0x02, 0x03), bytes(0x01, 0x00, 0x01))
    private val server = RsaPublicParts(bytes(0x09, 0x08, 0x07), bytes(0x01, 0x00, 0x01))
    private val nonce = bytes(0x54, 0x34)

    /** O código que a TV mostraria para este par de chaves e este nonce. */
    private val code: String
        get() = PairingMath.secret(client, server, nonce)[0].let { hexByte(it.toInt() and 0xFF) } + nonce.toHex()

    private fun reply(field: Int, status: Int = 200): ByteArray = ProtoWriter.build {
        int(1, 2)
        int(2, status)
        message(field) { }
    }

    private fun startedChannel(vararg afterStart: Any) = ScriptedChannel(
        reply(AndroidTvMessages.PAIRING_REQUEST_ACK),
        reply(AndroidTvMessages.PAIRING_OPTION),
        reply(AndroidTvMessages.PAIRING_CONFIGURATION_ACK),
        *afterStart
    )

    @Test
    fun start_asksForTheCodeInThreeSteps() = runTest {
        val channel = startedChannel()

        AndroidTvPairing(channel, client, server).start()

        assertEquals(3, channel.sent.size)
        assertArrayEquals(AndroidTvMessages.pairingRequest(), channel.sent[0])
        assertArrayEquals(AndroidTvMessages.pairingOption(), channel.sent[1])
        assertArrayEquals(AndroidTvMessages.pairingConfiguration(), channel.sent[2])
        assertFalse(channel.closed)
    }

    @Test
    fun finish_sendsTheSecretAndCloses() = runTest {
        val channel = startedChannel(reply(AndroidTvMessages.PAIRING_SECRET_ACK))
        val pairing = AndroidTvPairing(channel, client, server).apply { start() }

        pairing.finish(" ${code.lowercase()} ")

        val secret = ProtoMessage(channel.sent.last()).message(AndroidTvMessages.PAIRING_SECRET)!!.bytes(1)!!
        assertArrayEquals(PairingMath.secret(client, server, nonce), secret)
        assertTrue(channel.closed)
    }

    @Test
    fun typo_isCaughtByTheChecksumWithoutSendingAnything() = runTest {
        val channel = startedChannel()
        val pairing = AndroidTvPairing(channel, client, server).apply { start() }
        val typo = code.substring(0, 5) + if (code.last() == '0') "1" else "0"

        try {
            pairing.finish(typo)
            fail("o checksum deveria pegar o erro")
        } catch (expected: AndroidTvError.WrongCode) {
        }

        assertEquals("a TV Box continua esperando", 3, channel.sent.size)
        assertFalse(channel.closed)
    }

    @Test
    fun notACode_isWrongCode() = runTest {
        val pairing = AndroidTvPairing(startedChannel(), client, server).apply { start() }

        try {
            pairing.finish("XYZ")
            fail("não é um código")
        } catch (expected: AndroidTvError.WrongCode) {
        }
    }

    @Test
    fun status402_isWrongCode() = runTest {
        val channel = startedChannel(reply(AndroidTvMessages.PAIRING_SECRET_ACK, status = 402))
        val pairing = AndroidTvPairing(channel, client, server).apply { start() }

        try {
            pairing.finish(code)
            fail("a TV Box recusou o segredo")
        } catch (expected: AndroidTvError.WrongCode) {
        }
    }

    @Test
    fun otherStatusOrMissingField_isRejected() = runTest {
        try {
            AndroidTvPairing(ScriptedChannel(reply(AndroidTvMessages.PAIRING_REQUEST_ACK, status = 400)), client, server).start()
            fail("status 400")
        } catch (expected: AndroidTvError.Rejected) {
        }
        try {
            AndroidTvPairing(ScriptedChannel(reply(AndroidTvMessages.PAIRING_OPTION)), client, server).start()
            fail("ack de outro passo")
        } catch (expected: AndroidTvError.Rejected) {
        }
    }
}
