package com.lanremote.android.data.androidtv

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/** A sequência de ligar a TV, com falsos e tempo virtual. */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidTvRemoteTest {

    private val device = DeviceInfo("Pixel 9", "Google", "1.1.0")
    private val macs = listOf("02:00:00:00:00:f8", "02:00:00:00:00:fa", "02:00:00:00:00:f9")
    private val events = mutableListOf<String>()

    private fun TestScope.remote(
        channels: List<ScriptedChannel>,
        openHosts: Set<String> = setOf("192.0.2.101")
    ): AndroidTvRemote {
        val pending = ArrayDeque(channels)
        return AndroidTvRemote(
            connector = { host, port ->
                events += "open $host:$port @$currentTime"
                pending.removeFirst()
            },
            waker = { host, wakeMacs -> events += "wake $host ${wakeMacs.size} @$currentTime" },
            probe = { host, _, timeoutMs ->
                if (host in openHosts) {
                    true
                } else {
                    delay(timeoutMs.toLong())
                    false
                }
            },
            device = device,
            timeSource = testScheduler.timeSource
        )
    }

    private fun TestScope.channel(vararg script: Any) =
        ScriptedChannel(*script, log = { events += "$it @$currentTime" })

    @Test
    fun boxScreenOn_sleepsItFirstThenWakesItAgainAndPressesPower() = runTest {
        val remote = remote(
            listOf(
                channel(Box.configure, Box.setActive, Box.start(screenOn = true)),   // só pergunta
                channel(Box.configure, Box.setActive, Box.start(screenOn = true)),   // POWER: dorme
                channel(Box.configure, Box.setActive, Box.start(screenOn = false))   // POWER: acorda e liga a TV
            )
        )

        val host = remote.switchTvOn("192.0.2.101", macs)

        assertEquals("192.0.2.101", host)
        assertEquals(
            listOf(
                "wake 192.0.2.101 3 @0",
                "open 192.0.2.101:6466 @0", "close @0",
                "open 192.0.2.101:6466 @0", "key26 @0", "close @300",
                "wake 192.0.2.101 3 @3300",   // 3 s depois de ela dormir
                "open 192.0.2.101:6466 @3300", "key26 @3300", "close @3600"
            ),
            events
        )
    }

    @Test
    fun boxScreenOff_onlyPower() = runTest {
        val remote = remote(
            listOf(
                channel(Box.configure, Box.setActive, Box.start(screenOn = false)),
                channel(Box.configure, Box.setActive)   // sem remote_start: espera 1 s e segue
            )
        )

        remote.switchTvOn("192.0.2.101", macs)

        assertEquals(
            listOf(
                "wake 192.0.2.101 3 @0",
                "open 192.0.2.101:6466 @0", "close @0",
                "open 192.0.2.101:6466 @0", "key26 @1000", "close @1300"
            ),
            events
        )
    }

    @Test
    fun portThatNeverOpens_isAsleep() = runTest {
        val remote = remote(emptyList(), openHosts = emptySet())

        try {
            remote.switchTvOn("192.0.2.101", wakeMacs = emptyList())
            fail("a TV Box não acordou")
        } catch (expected: AndroidTvError.Asleep) {
        }

        assertFalse(events.any { it.startsWith("open") })
        assertEquals("sem MAC conhecido o prazo é 4 s", 4_500, currentTime)
    }

    @Test
    fun boxWithANewAddress_isFoundAgainByName() = runTest {
        val remote = remote(
            listOf(channel(Box.configure, Box.setActive, Box.start(screenOn = false)), channel(Box.configure, Box.setActive)),
            openHosts = setOf("192.0.2.23")
        )

        val host = remote.switchTvOn("192.0.2.101", macs, relocate = { "192.0.2.23" })

        assertEquals("192.0.2.23", host)
        assertEquals(2, events.count { it.startsWith("open 192.0.2.23:6466") })
    }

    @Test
    fun unknownCertificate_isNotPaired() = runTest {
        val remote = AndroidTvRemote(
            connector = { _, _ -> throw AndroidTvError.SecureChannel() },
            waker = { _, _ -> },
            probe = { _, _, _ -> true },
            device = device,
            timeSource = testScheduler.timeSource
        )

        try {
            remote.switchTvOn("192.0.2.101", macs)
            fail("deveria dizer que não está pareado")
        } catch (expected: AndroidTvError.NotPaired) {
        }
    }

    @Test
    fun coldArp_resendsWakeOnLanDuringProbeMisses() = runTest {
        var attempts = 0
        val testRemote = AndroidTvRemote(
            connector = { _, _ -> channel(Box.configure, Box.setActive) },
            waker = { host, wakeMacs -> events += "wake $host ${wakeMacs.size} @$currentTime" },
            probe = { _, _, timeoutMs ->
                attempts++
                if (attempts >= 3) {
                    true
                } else {
                    delay(timeoutMs.toLong())
                    false
                }
            },
            device = device,
            timeSource = testScheduler.timeSource
        )

        testRemote.waitUntilReachable("192.0.2.101", timeout = 5.seconds, wakeMacs = macs)

        val wakeEvents = events.filter { it.startsWith("wake") }
        assertEquals("reenvia o pacote mágico a cada tentativa sem resposta", 2, wakeEvents.size)
        assertEquals("wake 192.0.2.101 3 @600", wakeEvents[0])
        assertEquals("wake 192.0.2.101 3 @1500", wakeEvents[1])
    }
}
