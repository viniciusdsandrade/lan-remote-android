package com.lanremote.android.data.network

import com.lanremote.android.data.model.ChannelInfo
import com.lanremote.android.data.model.TVConfig
import com.lanremote.android.data.model.VolumeInfo
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Cliente HTTP Ktor para a TV LG NetCast (42LN5700) pelo protocolo ROAP, porta 8080.
 *
 * Formato validado na TV real (lan-remote-mac/PROTOCOLO.md, seções 2 a 5):
 * - tecla: `<command><session>ID</session><type>HandleKeyInput</type><value>N</value></command>`;
 * - consultas com `&session=ID` (sem sessão válida a TV responde 401);
 * - a sessão morre quando a TV desliga e liga: num 401 o cliente refaz o `AuthReq` com a chave
 *   salva e repete a requisição uma vez. A sessão nova volta no resultado para o app gravar.
 *
 * IMPORTANTE: o firmware responde 400 se o User-Agent contiver "UDAP/2.0". Este cliente sempre
 * envia "lg_roap_control/1.0.0 (android)" com Connection: close.
 */
class KtorTVClient(
    private val client: HttpClient = createDefaultHttpClient()
) {
    companion object {
        const val USER_AGENT = "lg_roap_control/1.0.0 (android)"
        const val CONTENT_TYPE_ATOM_XML = "application/atom+xml; charset=utf-8"

        /** Só a descrição UDAP (`rootservice.xml`) quer "UDAP/2.0" no User-Agent; o ROAP recusa. */
        const val UDAP_USER_AGENT = "Android/1.0 UDAP/2.0 lg_roap_control/1.0.0"
        private const val XML_HEADER = """<?xml version="1.0" encoding="utf-8"?>"""

        /** `<major>` de `cur_channel` quando não há canal sintonizado (HDMI, AV…). */
        private const val NO_CHANNEL = "65520"

        fun createDefaultHttpClient(): HttpClient {
            return HttpClient(CIO) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 3500
                    connectTimeoutMillis = 2000
                    socketTimeoutMillis = 3500
                }
            }
        }

        fun commandXml(session: String, keyCode: Int): String =
            "$XML_HEADER<command><session>$session</session><type>HandleKeyInput</type>" +
                "<value>$keyCode</value></command>"

        fun extractXmlTag(xml: String, tagName: String): String? {
            val regex = Regex("<$tagName>\\s*(.*?)\\s*</$tagName>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            return regex.find(xml)?.groupValues?.get(1)
        }
    }

    /**
     * Pede para a TV mostrar na tela o código de pareamento de 6 dígitos.
     */
    suspend fun requestPairingKey(host: String, port: Int = 8080): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = postXml("http://$host:$port/roap/api/auth", "$XML_HEADER<auth><type>AuthKeyReq</type></auth>")
            if (response.status == HttpStatusCode.OK) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("A TV respondeu HTTP ${response.status.value} ao pedido do código."))
            }
        } catch (e: Exception) {
            ensureActive()
            Result.failure(unreachable(host, port, e))
        }
    }

    /**
     * Envia o código mostrado na TV e devolve a sessão. Resposta sem `<session>` é falha: sem ela
     * nenhuma tecla é aceita.
     */
    suspend fun authenticate(host: String, port: Int = 8080, pairingKey: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = postXml(
                "http://$host:$port/roap/api/auth",
                "$XML_HEADER<auth><type>AuthReq</type><value>$pairingKey</value></auth>"
            )
            val session = extractXmlTag(response.bodyAsText(), "session")
            when {
                response.status == HttpStatusCode.OK && !session.isNullOrBlank() -> Result.success(session)
                response.status == HttpStatusCode.OK ->
                    Result.failure(Exception("A TV aceitou o código, mas não devolveu a sessão."))
                response.status == HttpStatusCode.Unauthorized ->
                    Result.failure(Exception("Código recusado pela TV. Confira os 6 dígitos na tela."))
                response.status == HttpStatusCode.ServiceUnavailable ->
                    Result.failure(Exception("A TV já tem controles demais pareados. Desligue e ligue a TV."))
                else -> Result.failure(Exception("A TV respondeu HTTP ${response.status.value} ao pareamento."))
            }
        } catch (e: Exception) {
            ensureActive()
            Result.failure(unreachable(host, port, e))
        }
    }

    /**
     * Envia uma tecla (HandleKeyInput). Resposta boa: 200 com `<ROAPError>200</ROAPError>`.
     */
    suspend fun sendCommand(config: TVConfig, keyCode: Int): RoapResult<Unit> =
        withSession(config, request = { session ->
            postXml("${config.baseUrl}/roap/api/command", commandXml(session, keyCode))
        }) { }

    /**
     * Volume, mudo e limites (`volume_info`).
     */
    suspend fun getVolumeInfo(config: TVConfig): RoapResult<VolumeInfo> =
        withSession(config, request = { session -> query(config, "volume_info", session) }) { body ->
            VolumeInfo(
                level = extractXmlTag(body, "level")?.toIntOrNull() ?: 0,
                isMuted = extractXmlTag(body, "mute").equals("true", ignoreCase = true),
                minLevel = extractXmlTag(body, "minLevel")?.toIntOrNull() ?: 0,
                maxLevel = extractXmlTag(body, "maxLevel")?.toIntOrNull() ?: 100
            )
        }

    /**
     * Entrada atual (ex.: `HDMI2`) e canal (`cur_channel`).
     */
    suspend fun getCurrentChannel(config: TVConfig): RoapResult<ChannelInfo> =
        withSession(config, request = { session -> query(config, "cur_channel", session) }) { body ->
            ChannelInfo(
                inputSource = extractXmlTag(body, "inputSourceName")?.takeIf { it.isNotEmpty() },
                channel = extractXmlTag(body, "major")?.takeIf { it.isNotEmpty() && it != NO_CHANNEL }
            )
        }

    /**
     * Modelo da TV pela descrição UDAP ("42LN5700-SA_11"), que responde sem pareamento. Nulo se a
     * TV não disser.
     */
    suspend fun fetchModelName(host: String, port: Int = 8080): String? = withContext(Dispatchers.IO) {
        try {
            val response = client.get("http://$host:$port/udap/api/data") {
                parameter("target", "rootservice.xml")
                header(HttpHeaders.UserAgent, UDAP_USER_AGENT)
                header(HttpHeaders.Connection, "close")
            }
            if (response.status == HttpStatusCode.OK) {
                extractXmlTag(response.bodyAsText(), "modelName")?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        } catch (e: Exception) {
            ensureActive()
            null
        }
    }

    /**
     * Roda a requisição com a sessão salva; num 401 refaz o pareamento com a chave salva e repete
     * uma vez só. Qualquer falha sem resposta HTTP vira [RoapResult.Unreachable].
     */
    private suspend fun <T> withSession(
        config: TVConfig,
        request: suspend (session: String) -> HttpResponse,
        parse: (body: String) -> T
    ): RoapResult<T> = withContext(Dispatchers.IO) {
        try {
            var response = request(config.sessionId.orEmpty())
            var newSession: String? = null
            if (response.status == HttpStatusCode.Unauthorized && config.pairingKey.isNotBlank()) {
                newSession = authenticate(config.host, config.port, config.pairingKey).getOrNull()
                if (newSession != null) {
                    response = request(newSession)
                }
            }
            if (response.status == HttpStatusCode.OK) {
                RoapResult.Ok(parse(response.bodyAsText()), newSession)
            } else {
                RoapResult.Refused(response.status.value, newSession)
            }
        } catch (e: Exception) {
            // Cancelamento de verdade sobe; tempo esgotado e erros de socket viram "sem resposta".
            ensureActive()
            RoapResult.Unreachable(e)
        }
    }

    private fun unreachable(host: String, port: Int, cause: Exception) =
        Exception("Não consegui falar com a TV em $host:$port. Ela está ligada e no mesmo Wi-Fi?", cause)

    private suspend fun postXml(url: String, xml: String): HttpResponse = client.post(url) {
        header(HttpHeaders.UserAgent, USER_AGENT)
        header(HttpHeaders.Connection, "close")
        contentType(ContentType.parse(CONTENT_TYPE_ATOM_XML))
        setBody(xml)
    }

    private suspend fun query(config: TVConfig, target: String, session: String): HttpResponse =
        client.get("${config.baseUrl}/roap/api/data") {
            parameter("target", target)
            parameter("session", session)
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.Connection, "close")
        }
}
