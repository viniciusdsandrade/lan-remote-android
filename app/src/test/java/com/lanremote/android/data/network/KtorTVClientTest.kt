package com.lanremote.android.data.network

import com.lanremote.android.data.model.TVConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KtorTVClientTest {

    @Test
    fun requestPairingKey_sendsAuthKeyReqAndValidHeaders() = runBlocking {
        var capturedUserAgent: String? = null
        var capturedBody: String? = null

        val mockEngine = MockEngine { request ->
            capturedUserAgent = request.headers[HttpHeaders.UserAgent]
            capturedBody = request.body.toByteArray().decodeToString()

            respond(
                content = "<?xml version=\"1.0\" encoding=\"utf-8\"?><envelope><status>OK</status></envelope>",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/atom+xml")
            )
        }

        val client = KtorTVClient(HttpClient(mockEngine))
        val result = client.requestPairingKey("192.168.1.100", 8080)

        assertTrue(result.isSuccess)
        assertEquals("lg_roap_control/1.0.0 (android)", capturedUserAgent)
        assertFalse(capturedUserAgent!!.contains("UDAP/2.0"))
        assertTrue(capturedBody!!.contains("<type>AuthKeyReq</type>"))
    }

    @Test
    fun authenticate_parsesSessionIdCorrectly() = runBlocking {
        val mockEngine = MockEngine { request ->
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("<value>654321</value>"))

            respond(
                content = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <envelope>
                        <session>sess_xyz_789</session>
                    </envelope>
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/atom+xml")
            )
        }

        val client = KtorTVClient(HttpClient(mockEngine))
        val result = client.authenticate("192.168.1.100", 8080, "654321")

        assertTrue(result.isSuccess)
        assertEquals("sess_xyz_789", result.getOrNull())
    }

    @Test
    fun sendCommand_sendsHandleKeyInputWithCorrectCode() = runBlocking {
        var sentKey: String? = null
        var sentSession: String? = null

        val mockEngine = MockEngine { request ->
            val body = request.body.toByteArray().decodeToString()
            sentKey = body
            sentSession = request.headers["Session"]

            respond(
                content = "<?xml version=\"1.0\" encoding=\"utf-8\"?><envelope><status>OK</status></envelope>",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/atom+xml")
            )
        }

        val client = KtorTVClient(HttpClient(mockEngine))
        val config = TVConfig(
            host = "192.168.1.100",
            port = 8080,
            sessionId = "sess_123"
        )

        val result = client.sendCommand(config, 24) // Volume Up

        assertTrue(result.isSuccess)
        assertEquals("sess_123", sentSession)
        assertTrue(sentKey!!.contains("<name>HandleKeyInput</name>"))
        assertTrue(sentKey!!.contains("<value>24</value>"))
    }

    @Test
    fun getVolumeInfo_parsesXmlVolumeFields() = runBlocking {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <envelope>
                        <data>
                            <mute>false</mute>
                            <minLevel>0</minLevel>
                            <maxLevel>100</maxLevel>
                            <level>22</level>
                        </data>
                    </envelope>
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/atom+xml")
            )
        }

        val client = KtorTVClient(HttpClient(mockEngine))
        val config = TVConfig(host = "192.168.1.100", port = 8080)
        val result = client.getVolumeInfo(config)

        assertTrue(result.isSuccess)
        val status = result.getOrNull()!!
        assertTrue(status.isOnline)
        assertFalse(status.isMuted)
        assertEquals(22, status.volumeLevel)
        assertEquals(0, status.minVolume)
        assertEquals(100, status.maxVolume)
    }

    @Test
    fun sendCommand_retriesWithAuthOn401() = runBlocking {
        var callCount = 0

        val mockEngine = MockEngine { request ->
            callCount++
            when {
                request.url.encodedPath.endsWith("/roap/api/auth") -> {
                    respond(
                        content = "<envelope><session>new_sess_456</session></envelope>",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/atom+xml")
                    )
                }
                callCount == 1 -> {
                    // First command attempt returns 401 Unauthorized
                    respond(
                        content = "<ROAPError>401 Unauthorized</ROAPError>",
                        status = HttpStatusCode.Unauthorized,
                        headers = headersOf(HttpHeaders.ContentType, "application/atom+xml")
                    )
                }
                else -> {
                    // Second command attempt after auth succeeds
                    assertEquals("new_sess_456", request.headers["Session"])
                    respond(
                        content = "<envelope><status>OK</status></envelope>",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/atom+xml")
                    )
                }
            }
        }

        val client = KtorTVClient(HttpClient(mockEngine))
        val config = TVConfig(
            host = "192.168.1.100",
            port = 8080,
            pairingKey = "112233",
            sessionId = "expired_sess"
        )

        val result = client.sendCommand(config, 26) // Mute
        assertTrue(result.isSuccess)
        assertEquals(3, callCount) // 1: command (401), 2: auth (200), 3: retry command (200)
    }
}
