package com.lanremote.android.data.network

import com.lanremote.android.data.model.TVConfig
import com.lanremote.android.data.model.TVStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cliente HTTP Ktor para comunicação com TV LG NetCast (47LN5700) via protocolo ROAP.
 *
 * IMPORTANTE:
 * O firmware NetCast rejeita (HTTP 400) requisições com User-Agent contendo "UDAP/2.0".
 * Este cliente sempre envia "lg_roap_control/1.0.0 (android)" com Connection: close.
 */
class KtorTVClient(
    private val client: HttpClient = createDefaultHttpClient()
) {
    companion object {
        const val USER_AGENT = "lg_roap_control/1.0.0 (android)"
        const val CONTENT_TYPE_ATOM_XML = "application/atom+xml; charset=utf-8"

        fun createDefaultHttpClient(): HttpClient {
            return HttpClient(CIO) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 3500
                    connectTimeoutMillis = 2000
                    socketTimeoutMillis = 3500
                }
            }
        }
    }

    /**
     * Solicita que a TV exiba na tela o código de pareamento de 6 dígitos.
     */
    suspend fun requestPairingKey(host: String, port: Int = 8080): Result<Unit> = withContext(Dispatchers.IO) {
        val url = "http://$host:$port/roap/api/auth"
        val payload = """
            <?xml version="1.0" encoding="utf-8"?>
            <auth>
                <type>AuthKeyReq</type>
            </auth>
        """.trimIndent()

        try {
            val response: HttpResponse = client.post(url) {
                header("User-Agent", USER_AGENT)
                header("Content-Type", CONTENT_TYPE_ATOM_XML)
                header("Connection", "close")
                setBody(payload)
            }
            if (response.status.value in 200..299) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Falha ao solicitar código: HTTP ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Envia o código de pareamento exibido na TV e retorna o ID da sessão autenticada.
     */
    suspend fun authenticate(host: String, port: Int = 8080, pairingKey: String): Result<String> = withContext(Dispatchers.IO) {
        val url = "http://$host:$port/roap/api/auth"
        val payload = """
            <?xml version="1.0" encoding="utf-8"?>
            <auth>
                <type>AuthReq</type>
                <value>$pairingKey</value>
            </auth>
        """.trimIndent()

        try {
            val response: HttpResponse = client.post(url) {
                header("User-Agent", USER_AGENT)
                header("Content-Type", CONTENT_TYPE_ATOM_XML)
                header("Connection", "close")
                setBody(payload)
            }
            val body = response.bodyAsText()
            if (response.status.value in 200..299) {
                val session = extractXmlTag(body, "session")
                if (!session.isNullOrBlank()) {
                    Result.success(session)
                } else if (body.contains("ROAPError") || body.contains("401")) {
                    Result.failure(Exception("Chave de pareamento inválida"))
                } else {
                    // Algumas versões retornam 200 OK sem tag session explícita
                    Result.success("authenticated")
                }
            } else {
                Result.failure(Exception("Erro de autenticação: HTTP ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Envia um comando de tecla ROAP (HandleKeyInput).
     */
    suspend fun sendCommand(config: TVConfig, keyCode: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val url = "${config.baseUrl}/roap/api/command"
        val payload = """
            <?xml version="1.0" encoding="utf-8"?>
            <command>
                <name>HandleKeyInput</name>
                <value>$keyCode</value>
            </command>
        """.trimIndent()

        try {
            val response: HttpResponse = client.post(url) {
                header("User-Agent", USER_AGENT)
                header("Content-Type", CONTENT_TYPE_ATOM_XML)
                header("Connection", "close")
                if (!config.sessionId.isNullOrBlank()) {
                    header("Session", config.sessionId)
                }
                setBody(payload)
            }

            if (response.status.value in 200..299) {
                Result.success(Unit)
            } else if (response.status == HttpStatusCode.Unauthorized && config.pairingKey.isNotBlank()) {
                // Tentativa de reautenticação automática
                val authResult = authenticate(config.host, config.port, config.pairingKey)
                if (authResult.isSuccess) {
                    val retryResponse = client.post(url) {
                        header("User-Agent", USER_AGENT)
                        header("Content-Type", CONTENT_TYPE_ATOM_XML)
                        header("Connection", "close")
                        header("Session", authResult.getOrNull())
                        setBody(payload)
                    }
                    if (retryResponse.status.value in 200..299) {
                        Result.success(Unit)
                    } else {
                        Result.failure(Exception("Comando falhou após reautenticação: HTTP ${retryResponse.status.value}"))
                    }
                } else {
                    Result.failure(Exception("Não autorizado (HTTP 401)"))
                }
            } else {
                Result.failure(Exception("Comando falhou: HTTP ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Consulta informações de volume da TV (volume_info).
     */
    suspend fun getVolumeInfo(config: TVConfig): Result<TVStatus> = withContext(Dispatchers.IO) {
        val url = "${config.baseUrl}/roap/api/data?target=volume_info"
        try {
            val response: HttpResponse = client.get(url) {
                header("User-Agent", USER_AGENT)
                header("Connection", "close")
                if (!config.sessionId.isNullOrBlank()) {
                    header("Session", config.sessionId)
                }
            }

            if (response.status.value in 200..299) {
                val body = response.bodyAsText()
                val isMuted = extractXmlTag(body, "mute")?.toBooleanStrictOrNull() ?: false
                val level = extractXmlTag(body, "level")?.toIntOrNull() ?: 0
                val minLevel = extractXmlTag(body, "minLevel")?.toIntOrNull() ?: 0
                val maxLevel = extractXmlTag(body, "maxLevel")?.toIntOrNull() ?: 100

                Result.success(
                    TVStatus(
                        isOnline = true,
                        volumeLevel = level,
                        isMuted = isMuted,
                        minVolume = minLevel,
                        maxVolume = maxLevel
                    )
                )
            } else {
                Result.failure(Exception("Falha ao obter volume: HTTP ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Verifica rapidamente se a TV está respondendo na rede local.
     */
    suspend fun checkOnline(host: String, port: Int = 8080): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = client.get("http://$host:$port/roap/api/data?target=volume_info") {
                header("User-Agent", USER_AGENT)
                header("Connection", "close")
            }
            // Qualquer resposta HTTP (inclusive 401 ou 200) confirma que o servidor HTTP está ativo
            response.status.value in 200..499
        } catch (e: Exception) {
            false
        }
    }

    private fun extractXmlTag(xml: String, tagName: String): String? {
        val regex = Regex("<$tagName>(.*?)</$tagName>", RegexOption.DOT_MATCHES_ALL)
        return regex.find(xml)?.groupValues?.get(1)?.trim()
    }
}
