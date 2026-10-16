package com.lanremote.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TVKeyTest {

    @Test
    fun verifyCrucialNetCastKeyCodes() {
        assertEquals(1, TVKey.POWER.code)
        assertEquals(24, TVKey.VOLUME_UP.code)
        assertEquals(25, TVKey.VOLUME_DOWN.code)
        assertEquals(26, TVKey.MUTE.code)
        assertEquals(27, TVKey.CHANNEL_UP.code)
        assertEquals(28, TVKey.CHANNEL_DOWN.code)
        assertEquals(12, TVKey.UP.code)
        assertEquals(13, TVKey.DOWN.code)
        assertEquals(14, TVKey.LEFT.code)
        assertEquals(15, TVKey.RIGHT.code)
        assertEquals(20, TVKey.OK.code)
        assertEquals(21, TVKey.HOME.code)
        assertEquals(22, TVKey.MENU.code)
        assertEquals(23, TVKey.BACK.code)
        assertEquals(31, TVKey.RED.code)
        assertEquals(30, TVKey.GREEN.code)
        assertEquals(32, TVKey.YELLOW.code)
        assertEquals(29, TVKey.BLUE.code)
        assertEquals(33, TVKey.PLAY.code)
        assertEquals(34, TVKey.PAUSE.code)
        assertEquals(35, TVKey.STOP.code)
        assertEquals(47, TVKey.INPUT.code)
        assertEquals(412, TVKey.EXIT.code)
    }

    @Test
    fun verifyConfigBaseUrl() {
        val config = TVConfig(host = "192.168.1.100", port = 8080)
        assertEquals("http://192.168.1.100:8080", config.baseUrl)
    }
}
