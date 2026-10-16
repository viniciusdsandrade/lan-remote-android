package com.lanremote.android.data.androidtv

import com.lanremote.android.data.model.AndroidTvBox
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// MACs fictícios (administrados localmente, 02:…): o repositório é público.
class MacAddressTest {

    @Test
    fun normalizesTheUsualSpellings() {
        assertEquals("02:00:5e:10:22:f8", MacAddress.normalize("02:00:5E:10:22:F8"))
        assertEquals("02:00:5e:10:22:f8", MacAddress.normalize("02-00-5E-10-22-F8"))
        assertEquals("02:00:5e:10:22:f8", MacAddress.normalize("02005e1022f8"))
        assertEquals("02:00:5e:10:22:f8", MacAddress.normalize("  02.00.5e.10.22.f8 "))
        assertEquals("o arp do macOS corta zeros", "02:03:55:0c:dc:05", MacAddress.normalize("2:3:55:c:dc:5"))
        assertNull(MacAddress.normalize("02:00:5e:10:22"))
        assertNull(MacAddress.normalize("02:00:5e:10:22:GG"))
        assertNull(MacAddress.normalize("02:00:5e:10:22:f8:01"))
        assertNull(MacAddress.normalize(""))
    }

    @Test
    fun neighbours() {
        assertEquals("02:00:5e:10:22:f8", MacAddress.offset("02:00:5e:10:22:f9", -1))
        assertEquals("vai um para o octeto seguinte", "02:00:5e:10:23:00", MacAddress.offset("02:00:5e:10:22:ff", 1))
        assertNull(MacAddress.offset("00:00:00:00:00:00", -1))
        assertNull(MacAddress.offset("nada", 1))
    }

    @Test
    fun magicPacket() {
        val packet = WakeOnLan.magicPacket("02:00:00:00:00:01")!!
        assertEquals(102, packet.size)
        assertArrayEquals(ByteArray(6) { 0xFF.toByte() }, packet.copyOfRange(0, 6))
        val mac = bytes(0x02, 0x00, 0x00, 0x00, 0x00, 0x01)
        for (i in 0 until 16) {
            assertArrayEquals(mac, packet.copyOfRange(6 + i * 6, 12 + i * 6))
        }
        assertNull(WakeOnLan.magicPacket("nope"))
    }

    @Test
    fun wakeMacs_tryTheBluetoothNeighboursFirst() {
        val box = AndroidTvBox(name = "TV Box", host = "192.168.1.101", paired = true, bluetoothMac = "02:00:00:00:00:F9")
        assertEquals(
            "sem o MAC do Wi-Fi: Bluetooth − 1, + 1 e ele mesmo",
            listOf("02:00:00:00:00:f8", "02:00:00:00:00:fa", "02:00:00:00:00:f9"),
            box.wakeMacs
        )
        assertEquals("o MAC digitado vence", listOf("02:00:00:00:00:01"), box.copy(mac = "02-00-00-00-00-01").wakeMacs)
        assertEquals(emptyList<String>(), AndroidTvBox(name = "x", host = "h", paired = false).wakeMacs)
        assertEquals(
            "MAC digitado inválido não conta",
            3,
            box.copy(mac = "bobagem").wakeMacs.size
        )
    }
}
