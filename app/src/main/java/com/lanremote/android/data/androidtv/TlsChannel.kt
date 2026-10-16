package com.lanremote.android.data.androidtv

import android.annotation.SuppressLint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

/**
 * Conexão TLS com a TV Box, com o certificado do app, trocando mensagens prefixadas pelo tamanho.
 *
 * Uma corrotina lê o socket o tempo todo e entrega as mensagens numa fila: esperar uma mensagem
 * com prazo não mexe no socket (um timeout de leitura no meio de um registro TLS pode deixar o
 * SSLSocket inutilizável), e o prazo segue o relógio das corrotinas.
 */
class TlsChannel private constructor(private val socket: SSLSocket) : FramedChannel {

    /** Certificado da TV Box, lido no handshake (o pareamento usa a chave pública dele). */
    val serverCertificate: X509Certificate? =
        runCatching { socket.session.peerCertificates.firstOrNull() as? X509Certificate }.getOrNull()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    private val output = socket.outputStream

    init {
        scope.launch {
            val input = BufferedInputStream(socket.inputStream)
            try {
                while (true) {
                    incoming.send(readFrame(input))
                }
            } catch (e: Exception) {
                incoming.close(describe(e))
            }
        }
    }

    override suspend fun send(payload: ByteArray) = withContext(Dispatchers.IO) {
        try {
            output.write(ProtoWriter.framed(payload))
            output.flush()
        } catch (e: IOException) {
            throw describe(e)
        }
    }

    override suspend fun receive(timeoutMs: Long): ByteArray {
        val result = withTimeoutOrNull(timeoutMs) { incoming.receiveCatching() } ?: throw AndroidTvError.Timeout()
        return result.getOrNull()
            ?: throw (result.exceptionOrNull() as? AndroidTvError ?: AndroidTvError.Connection("a TV Box encerrou a conexão"))
    }

    override fun close() {
        runCatching { socket.close() }
        scope.cancel()
    }

    companion object {
        private const val ALIAS = "lanremote"

        /** Conecta e completa o handshake TLS, em no máximo [timeoutMs] para cada um. */
        suspend fun open(host: String, port: Int, identity: ClientIdentity, timeoutMs: Int = 6_000): TlsChannel {
            val context = SSLContext.getInstance("TLS").apply {
                init(arrayOf(ClientKeyManager(identity)), arrayOf(TrustTheBox()), null)
            }
            val socket = context.socketFactory.createSocket() as SSLSocket
            try {
                return withContext(Dispatchers.IO) {
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                    socket.soTimeout = timeoutMs   // só para o handshake; depois a leitura espera à vontade
                    socket.startHandshake()
                    socket.soTimeout = 0
                    TlsChannel(socket)
                }
            } catch (e: CancellationException) {
                // Cancelado durante o connect ou o handshake (que não param no meio): o canal pronto
                // seria descartado com o socket aberto e a leitura parada nele.
                runCatching { socket.close() }
                throw e
            } catch (e: Exception) {
                runCatching { socket.close() }
                throw describe(e)
            }
        }

        /** Traduz as falhas de rede para os erros (e as mensagens) do app. */
        fun describe(error: Throwable): AndroidTvError = when (error) {
            is AndroidTvError -> error
            is SSLException -> AndroidTvError.SecureChannel(error)
            is SocketTimeoutException -> AndroidTvError.Timeout(error)
            is NoRouteToHostException -> AndroidTvError.Connection("aparelho fora da rede", error)
            is ConnectException ->
                if (error.message.orEmpty().contains("UNREACH")) {
                    AndroidTvError.Connection("aparelho fora da rede", error)
                } else {
                    AndroidTvError.Connection("conexão recusada", error)
                }
            is UnknownHostException -> AndroidTvError.Connection("nome não encontrado na rede", error)
            is EOFException -> AndroidTvError.Connection("a TV Box encerrou a conexão", error)
            is SocketException -> AndroidTvError.Connection("a conexão caiu", error)
            else -> AndroidTvError.Connection(error.message ?: error.javaClass.simpleName, error)
        }
    }

    /**
     * Sempre o certificado do app. O KeyManagerFactory padrão filtra pelos emissores que o servidor
     * pede e poderia acabar não mandando certificado nenhum.
     */
    private class ClientKeyManager(private val identity: ClientIdentity) : X509ExtendedKeyManager() {
        override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
        override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?) = ALIAS
        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
        override fun getCertificateChain(alias: String?): Array<X509Certificate> = arrayOf(identity.certificate)
        override fun getPrivateKey(alias: String?): PrivateKey = identity.privateKey
        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
    }

    /**
     * Aceita o certificado autoassinado da TV Box: não há autoridade certificadora entre os dois, a
     * confiança vem do pareamento (o código mostrado na TV), como no Mac e no iOS. Fixar o
     * certificado da TV Box no pareamento seria uma melhoria possível.
     */
    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private class TrustTheBox : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
