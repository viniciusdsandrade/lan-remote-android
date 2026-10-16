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
    fun verifyTvFeatureKeyCodes() {
        // Tabela do Anexo A da LG UDAP 2.0, a mesma do ROAP (PROTOCOLO.md).
        assertEquals(411, TVKey.SIMPLINK.code)
        assertEquals(417, TVKey.APPS.code)
        assertEquals(46, TVKey.ASPECT_RATIO.code)
        assertEquals(400, TVKey.KEY_3D.code)
        assertEquals(403, TVKey.PREVIOUS_CHANNEL.code)
        assertEquals(405, TVKey.QUICK_MENU.code)
        assertEquals(43, TVKey.LIVE_TV.code)
        assertEquals(44, TVKey.EPG.code)
        assertEquals(45, TVKey.INFO.code)
    }

    @Test
    fun verifyConfigBaseUrl() {
        val config = TVConfig(host = "192.168.1.100", port = 8080)
        assertEquals("http://192.168.1.100:8080", config.baseUrl)
    }
}
