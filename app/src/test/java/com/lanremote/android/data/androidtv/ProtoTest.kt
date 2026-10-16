package com.lanremote.android.data.androidtv

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.EOFException

class ProtoTest {

    @Test
    fun roundTrip() {
        val data = ProtoWriter.build {
            int(1, 2)
            int(2, 200)
            message(10) {
                string(1, "atvremote")
                string(2, "LanRemote (Android)")
            }
            int(7, 300)   // varint de dois bytes
        }

        val message = ProtoMessage(data)
        assertEquals(2, message.int(1))
        assertEquals(200, message.int(2))
        assertEquals(300, message.int(7))
        assertFalse(message.has(3))
        val request = message.message(10)
        assertNotNull(request)
        assertEquals("atvremote", request!!.string(1))
        assertEquals("LanRemote (Android)", request.string(2))
    }

    @Test
    fun wireFormatMatchesTheSpec() {
        assertArrayEquals(bytes(0x08, 0x96, 0x01), ProtoWriter.build { int(1, 150) })   // exemplo da documentação do protobuf
        assertArrayEquals(bytes(0x96, 0x01), ProtoWriter().varint(150).toByteArray())
        assertArrayEquals(bytes(0xAC, 0x02), ProtoWriter().varint(300).toByteArray())
        assertArrayEquals(bytes(0x02, 0xAA, 0xBB), ProtoWriter.framed(bytes(0xAA, 0xBB)))
    }

    @Test
    fun negativeIntsUseTenBytes() {
        val data = ProtoWriter.build { int(1, -1) }
        assertEquals(11, data.size)
        assertEquals(-1L, ProtoMessage(data).long(1))
    }

    @Test
    fun skipsFixedWidthFields() {
        // campo 2 fixed64, campo 3 fixed32, campo 4 varint
        val data = bytes(0x11, 1, 2, 3, 4, 5, 6, 7, 8, 0x1D, 1, 2, 3, 4, 0x20, 0x05)
        assertEquals(5, ProtoMessage(data).int(4))
    }

    @Test
    fun rejectsTruncatedMessages() {
        for (broken in listOf(bytes(0x0A, 0x05, 0x01), bytes(0x08), bytes(0x11, 1, 2))) {
            try {
                ProtoMessage(broken)
                fail("deveria recusar ${broken.toHex()}")
            } catch (expected: AndroidTvError.Rejected) {
            }
        }
    }

    @Test
    fun readFrame_splitsTheStreamByTheLengthPrefix() {
        val big = ByteArray(300) { it.toByte() }
        val stream = ByteArrayInputStream(ProtoWriter.framed(bytes(0x01, 0x02)) + ProtoWriter.framed(big))

        assertArrayEquals(bytes(0x01, 0x02), readFrame(stream))
        assertArrayEquals(big, readFrame(stream))
        try {
            readFrame(stream)
            fail("fim do fluxo")
        } catch (expected: EOFException) {
        }
    }

    @Test
    fun readFrame_endOfStreamMidMessageIsEof() {
        try {
            readFrame(ByteArrayInputStream(bytes(0x05, 0x01, 0x02)))
            fail("mensagem cortada")
        } catch (expected: EOFException) {
            assertTrue(true)
        }
    }
}

internal fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

internal fun hexBytes(spaced: String): ByteArray = hexToBytes(spaced.replace(" ", ""))
