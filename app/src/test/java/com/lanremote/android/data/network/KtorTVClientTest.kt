package com.lanremote.android.data.network

import com.lanremote.android.data.model.ChannelInfo
import com.lanremote.android.data.model.TVConfig
import com.lanremote.android.data.model.VolumeInfo
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException

class KtorTVClientTest {

    private val config = TVConfig(host = "192.0.2.100", port = 8080, pairingKey = "112233", sessionId = "sess_123")

    private fun MockRequestHandleScope.reply(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
        respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, "application/atom+xml"))

    private val okEnvelope = "<?xml version=\"1.0\" encoding=\"utf-8\"?><envelope><ROAPError>200</ROAPError><ROAPErrorDetail>OK</ROAPErrorDetail></envelope>"
    private val unauthorized = "<envelope><ROAPError>401</ROAPError></envelope>"

    @Test
    fun requestPairingKey_sendsAuthKeyReqWithRoapHeaders() = runBlocking {
        var userAgent: String? = null
        var contentType: String? = null
        var body: String? = null

        val client = KtorTVClient(HttpClient(MockEngine { request ->
            userAgent = request.headers[HttpHeaders.UserAgent]
            contentType = request.body.contentType?.toString()
            body = request.body.toByteArray().decodeToString()
            reply(okEnvelope)
        }))
        val result = client.requestPairingKey("192.0.2.100", 8080)

        assertTrue(result.isSuccess)
        assertEquals("lg_roap_control/1.0.0 (android)", userAgent)
        assertFalse("a TV responde 400 a User-Agent com UDAP/2.0", userAgent!!.contains("UDAP/2.0"))
        assertEquals("application/atom+xml; charset=utf-8", contentType)
        assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?><auth><type>AuthKeyReq</type></auth>", body)
    }

    @Test
    fun authenticate_returnsTheSession() = runBlocking {
        val client = KtorTVClient(HttpClient(MockEngine { request ->
            assertTrue(request.body.toByteArray().decodeToString().contains("<auth><type>AuthReq</type><value>654321</value></auth>"))
            reply("<envelope><ROAPError>200</ROAPError><ROAPErrorDetail>OK</ROAPErrorDetail><session>123456789</session></envelope>")
        }))

        val result = client.authenticate("192.0.2.100", 8080, "654321")

        assertEquals("123456789", result.getOrNull())
    }

    @Test
    fun authenticate_withoutSessionFails() = runBlocking {
        // Sem <session> nenhuma tecla é aceita: o app não pode gravar uma sessão inventada.
        val client = KtorTVClient(HttpClient(MockEngine { reply(okEnvelope) }))

        val result = client.authenticate("192.0.2.100", 8080, "654321")

        assertTrue(result.isFailure)
    }

    @Test
    fun authenticate_wrongCodeFails() = runBlocking {
        val client = KtorTVClient(HttpClient(MockEngine { reply(unauthorized, HttpStatusCode.Unauthorized) }))

        assertTrue(client.authenticate("192.0.2.100", 8080, "000000").isFailure)
    }

    @Test
    fun sendCommand_putsSessionAndTypeInsideTheXml() = runBlocking {
        var body: String? = null
        var sessionHeader: String? = null
        var contentType: String? = null
        var path: String? = null

        val client = KtorTVClient(HttpClient(MockEngine { request ->
            body = request.body.toByteArray().decodeToString()
            sessionHeader = request.headers["Session"]
            contentType = request.body.contentType?.toString()
            path = request.url.encodedPath
            reply(okEnvelope)
        }))

        val result = client.sendCommand(config, 24) // volume +

        assertEquals(RoapResult.Ok(Unit), result)
        assertEquals("/roap/api/command", path)
        assertEquals(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?><command><session>sess_123</session>" +
                "<type>HandleKeyInput</type><value>24</value></command>",
            body
        )
        assertFalse(body!!.contains("<name>"))
        assertNull("a sessão vai no XML, não num header", sessionHeader)
        assertEquals("application/atom+xml; charset=utf-8", contentType)
    }

    @Test
    fun getVolumeInfo_sendsSessionAndParsesFields() = runBlocking {
        var target: String? = null
        var session: String? = null

        val client = KtorTVClient(HttpClient(MockEngine { request ->
            target = request.url.parameters["target"]
            session = request.url.parameters["session"]
            reply(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?><envelope><ROAPError>200</ROAPError><ROAPErrorDetail>OK</ROAPErrorDetail>" +
                    "<data><mute>true</mute><minLevel>0</minLevel><maxLevel>100</maxLevel><level>22</level></data></envelope>"
            )
        }))

        val result = client.getVolumeInfo(config)

        assertEquals("volume_info", target)
        assertEquals("sess_123", session)
        assertEquals(RoapResult.Ok(VolumeInfo(level = 22, isMuted = true, minLevel = 0, maxLevel = 100)), result)
    }

    @Test
    fun getCurrentChannel_readsInputAndIgnoresTheNoChannelMarker() = runBlocking {
        var target: String? = null
        var session: String? = null

        // Resposta real da 42LN5700 em HDMI2 (PROTOCOLO.md, seção 5): major 65520 = sem canal.
        val client = KtorTVClient(HttpClient(MockEngine { request ->
            target = request.url.parameters["target"]
            session = request.url.parameters["session"]
            reply(
                "<envelope><ROAPError>200</ROAPError><data><chtype>terrestrial</chtype><sourceIndex>8</sourceIndex>" +
                    "<physicalNum>65535</physicalNum><major>65520</major><displayMajor>65520</displayMajor>" +
                    "<minor>65520</minor><chname></chname><inputSourceName>HDMI2</inputSourceName>" +
                    "<inputSourceType>6</inputSourceType></data></envelope>"
            )
        }))

        val result = client.getCurrentChannel(config)

        assertEquals("cur_channel", target)
        assertEquals("sess_123", session)
        assertEquals(RoapResult.Ok(ChannelInfo(inputSource = "HDMI2", channel = null)), result)
    }

    @Test
    fun sendCommand_reauthenticatesOn401AndRetriesOnce() = runBlocking {
        val bodies = mutableListOf<String>()

        val client = KtorTVClient(HttpClient(MockEngine { request ->
            val body = request.body.toByteArray().decodeToString()
            bodies += body
            when {
                request.url.encodedPath.endsWith("/roap/api/auth") -> {
                    assertTrue(body.contains("<value>112233</value>"))
                    reply("<envelope><ROAPError>200</ROAPError><session>new_sess_456</session></envelope>")
                }
                body.contains("<session>sess_123</session>") -> reply(unauthorized, HttpStatusCode.Unauthorized)
                else -> reply(okEnvelope)
            }
        }))

        val result = client.sendCommand(config, 26) // mudo

        assertEquals(RoapResult.Ok(Unit, newSession = "new_sess_456"), result)
        assertEquals("comando (401), AuthReq, comando de novo", 3, bodies.size)
        assertTrue(bodies[2].contains("<session>new_sess_456</session><type>HandleKeyInput</type><value>26</value>"))
    }

    @Test
    fun sendCommand_givesUpAfterOneRetry() = runBlocking {
        var calls = 0
        val client = KtorTVClient(HttpClient(MockEngine { request ->
            calls++
            if (request.url.encodedPath.endsWith("/roap/api/auth")) {
                reply("<envelope><session>new_sess_456</session></envelope>")
            } else {
                reply(unauthorized, HttpStatusCode.Unauthorized)
            }
        }))

        val result = client.sendCommand(config, 26)

        assertEquals(RoapResult.Refused(401, newSession = "new_sess_456"), result)
        assertEquals(3, calls)
    }

    @Test
    fun getVolumeInfo_reauthenticatesOn401AndAsksWithTheNewSession() = runBlocking {
        val sessions = mutableListOf<String?>()
        val client = KtorTVClient(HttpClient(MockEngine { request ->
            if (request.url.encodedPath.endsWith("/roap/api/auth")) {
                reply("<envelope><session>new_sess_456</session></envelope>")
            } else {
                sessions += request.url.parameters["session"]
                if (request.url.parameters["session"] == "new_sess_456") {
                    reply("<envelope><data><mute>false</mute><level>10</level></data></envelope>")
                } else {
                    reply(unauthorized, HttpStatusCode.Unauthorized)
                }
            }
        }))

        val result = client.getVolumeInfo(config)

        assertEquals(listOf("sess_123", "new_sess_456"), sessions)
        assertEquals(RoapResult.Ok(VolumeInfo(10, false, 0, 100), newSession = "new_sess_456"), result)
    }

    @Test
    fun unauthorizedWithoutSavedKey_isRefusedWithoutAuthReq() = runBlocking {
        var calls = 0
        val client = KtorTVClient(HttpClient(MockEngine {
            calls++
            reply(unauthorized, HttpStatusCode.Unauthorized)
        }))

        val result = client.getVolumeInfo(TVConfig(host = "192.0.2.100"))

        assertEquals("a TV respondeu: a rede dela está de pé", RoapResult.Refused(401), result)
        assertEquals(1, calls)
    }

    @Test
    fun fetchModelName_readsTheUdapDescription() = runBlocking {
        var userAgent: String? = null
        var target: String? = null
        val client = KtorTVClient(HttpClient(MockEngine { request ->
            userAgent = request.headers[HttpHeaders.UserAgent]
            target = request.url.parameters["target"]
            reply(
                "<envelope><device><deviceType>TV</deviceType><modelName>42LN5700-SA_11</modelName>" +
                    "<friendlyName>[TV][LG]42LN5700-SA</friendlyName></device></envelope>"
            )
        }))

        assertEquals("42LN5700-SA_11", client.fetchModelName("192.0.2.100"))
        assertEquals("rootservice.xml", target)
        assertTrue("a descrição UDAP pede UDAP/2.0 no User-Agent", userAgent!!.contains("UDAP/2.0"))
    }

    @Test
    fun noAnswer_isUnreachable() = runBlocking {
        val client = KtorTVClient(HttpClient(MockEngine { throw ConnectException("Connection refused") }))

        val command = client.sendCommand(config, 24)
        val volume = client.getVolumeInfo(config)

        assertTrue(command is RoapResult.Unreachable)
        assertTrue(volume is RoapResult.Unreachable)
        assertTrue(client.requestPairingKey("192.0.2.100").isFailure)
    }
}
