package com.lanremote.android.data.androidtv

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteSessionTest {

    private val device = DeviceInfo("Pixel 9", "Google", "1.1.0")

    private fun TestScope.session(channel: FramedChannel) = RemoteSession(channel, device, testScheduler.timeSource)

    @Test
    fun handshake_answersEachStepAndReadsTheScreenState() = runTest {
        val channel = ScriptedChannel(Box.configure, Box.setActive, Box.ping(7), Box.start(screenOn = true))

        val screenOn = session(channel).handshake()

        assertEquals(true, screenOn)
        assertEquals(3, channel.sent.size)
        assertArrayEquals(AndroidTvMessages.configureReply(device), channel.sent[0])
        assertArrayEquals(hexBytes("12 03 08 EE 04"), channel.sent[1])
        assertArrayEquals("ping com n = 7 volta como eco", hexBytes("4A 02 08 07"), channel.sent[2])
        assertEquals("nada de esperar à toa", 0, currentTime)
    }

    @Test
    fun handshake_screenOff() = runTest {
        val screenOn = session(ScriptedChannel(Box.configure, Box.setActive, Box.start(screenOn = false))).handshake()

        assertEquals(false, screenOn)
    }

    @Test
    fun handshake_withoutRemoteStartWaitsOneSecondAndGoesOn() = runTest {
        val screenOn = session(ScriptedChannel(Box.configure, Box.setActive)).handshake()

        assertNull(screenOn)
        assertEquals(1_000, currentTime)
    }

    @Test
    fun endOfConnectionBeforeConfigure_meansNotPaired() = runTest {
        // TLS 1.3: a TV Box só derruba um certificado desconhecido depois do handshake TLS.
        val channel = ScriptedChannel(AndroidTvError.Connection("a TV Box encerrou a conexão"))

        try {
            session(channel).handshake()
            fail("deveria dizer que não está pareado")
        } catch (expected: AndroidTvError.NotPaired) {
        }
    }

    @Test
    fun tlsAlert_meansNotPaired() = runTest {
        try {
            session(ScriptedChannel(Box.configure, AndroidTvError.SecureChannel())).handshake()
            fail("deveria dizer que não está pareado")
        } catch (expected: AndroidTvError.NotPaired) {
        }
    }

    @Test
    fun endOfConnectionAfterConfigure_isAConnectionError() = runTest {
        try {
            session(ScriptedChannel(Box.configure, AndroidTvError.Connection("caiu"))).handshake()
            fail("deveria falhar")
        } catch (expected: AndroidTvError.Connection) {
        }
    }

    @Test
    fun remoteError_isRejected() = runTest {
        try {
            session(ScriptedChannel(Box.configure, Box.error)).handshake()
            fail("deveria recusar")
        } catch (expected: AndroidTvError.Rejected) {
        }
    }

    @Test
    fun silentBox_timesOutAfterEightSeconds() = runTest {
        try {
            session(ScriptedChannel()).handshake()
            fail("deveria estourar o prazo")
        } catch (expected: AndroidTvError.Timeout) {
            assertEquals(8_000, currentTime)
        }
    }

    @Test
    fun press_sendsTheKeyAndAnswersPingsInTheGap() = runTest {
        val channel = ScriptedChannel(Box.ping(42))

        session(channel).press(listOf(AndroidTvRemote.KEY_POWER))

        assertEquals(listOf(AndroidTvRemote.KEY_POWER), channel.keys)
        assertArrayEquals(AndroidTvMessages.pingReply(42), channel.sent.last())
        assertEquals("300 ms depois da tecla", 300, currentTime)
        assertFalse(channel.closed)
    }

    @Test
    fun press_boxClosingAfterPowerIsFine() = runTest {
        // Com a tela ligada, POWER faz a TV Box dormir: a conexão pode cair logo depois.
        val channel = ScriptedChannel(AndroidTvError.Connection("a TV Box encerrou a conexão"))

        session(channel).press(listOf(AndroidTvRemote.KEY_POWER))

        assertTrue(channel.keys == listOf(AndroidTvRemote.KEY_POWER))
    }
}
