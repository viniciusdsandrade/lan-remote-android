package com.lanremote.android.data.androidtv

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.File
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NoRouteToHostException
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * O canal TLS de verdade contra um servidor TLS local que pede certificado de cliente (TLS 1.3 na
 * JVM). Os certificados são gerados pelo keytool na hora: nada de chave versionada.
 */
class TlsChannelTest {

    private class Identity(override val privateKey: PrivateKey, override val certificate: X509Certificate) : ClientIdentity

    private companion object {
        val dir: File by lazy { Files.createTempDirectory("lanremote-tls").toFile().apply { deleteOnExit() } }

        fun keyStore(name: String): KeyStore {
            val file = File(dir, "$name.p12")
            if (!file.exists()) {
                val keytool = File(System.getProperty("java.home"), "bin/keytool").path
                val process = ProcessBuilder(
                    keytool, "-genkeypair", "-alias", name, "-keyalg", "RSA", "-keysize", "2048",
                    "-sigalg", "SHA256withRSA", "-dname", "CN=$name", "-validity", "2",
                    "-storetype", "PKCS12", "-keystore", file.path, "-storepass", "changeit", "-keypass", "changeit"
                ).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText()
                check(process.waitFor() == 0) { output }
            }
            return KeyStore.getInstance("PKCS12").apply { file.inputStream().use { load(it, "changeit".toCharArray()) } }
        }

        fun identity(name: String): Identity {
            val store = keyStore(name)
            return Identity(store.getKey(name, "changeit".toCharArray()) as PrivateKey, store.getCertificate(name) as X509Certificate)
        }

        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
    }

    /** Servidor TLS que exige certificado de cliente e roda [script] na conexão aceita. */
    private fun box(script: (SSLSocket) -> Unit): Pair<Int, CompletableFuture<X509Certificate>> {
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            .apply { init(keyStore("box"), "changeit".toCharArray()) }.keyManagers
        val context = SSLContext.getInstance("TLS").apply { init(keyManagers, arrayOf(trustAll), null) }
        val server = context.serverSocketFactory.createServerSocket(0, 1, InetAddress.getLoopbackAddress()) as SSLServerSocket
        server.needClientAuth = true
        val clientCertificate = CompletableFuture<X509Certificate>()
        Thread {
            server.use {
                val socket = it.accept() as SSLSocket
                socket.use { connection ->
                    connection.startHandshake()
                    clientCertificate.complete(connection.session.peerCertificates[0] as X509Certificate)
                    script(connection)
                }
            }
        }.apply { isDaemon = true }.start()
        return server.localPort to clientCertificate
    }

    @Test
    fun exchangesFramedMessagesWithTheClientCertificate() = runBlocking {
        val app = identity("lanremote")
        val big = ByteArray(300) { it.toByte() }
        val received = CompletableFuture<ByteArray>()
        val closeNow = CompletableFuture<Unit>()
        val (port, clientCertificate) = box { socket ->
            received.complete(readFrame(BufferedInputStream(socket.inputStream)))
            socket.outputStream.write(ProtoWriter.framed(bytes(0x0A, 0x00)) + ProtoWriter.framed(big))
            socket.outputStream.flush()
            closeNow.get(5, TimeUnit.SECONDS)
        }

        val channel = TlsChannel.open("127.0.0.1", port, app)
        try {
            assertNotNull(channel.serverCertificate)
            assertEquals("CN=box", channel.serverCertificate!!.subjectX500Principal.name)
            assertEquals(app.certificate, clientCertificate.get(5, TimeUnit.SECONDS))

            channel.send(bytes(0x01, 0x02, 0x03))
            assertArrayEquals(bytes(0x01, 0x02, 0x03), received.get(5, TimeUnit.SECONDS))
            assertArrayEquals(bytes(0x0A, 0x00), channel.receive(2_000))
            assertArrayEquals(big, channel.receive(2_000))

            try {
                channel.receive(200)
                fail("a TV Box está calada")
            } catch (expected: AndroidTvError.Timeout) {
            }

            // Um prazo vencido não estraga o canal: a conexão continua lendo até a TV Box fechar.
            closeNow.complete(Unit)
            try {
                channel.receive(2_000)
                fail("a TV Box fechou a conexão")
            } catch (expected: AndroidTvError.Connection) {
            }
        } finally {
            channel.close()
        }
    }

    @Test
    fun closedPort_isConnectionRefused() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        try {
            TlsChannel.open("127.0.0.1", port, identity("lanremote"), timeoutMs = 2_000)
            fail("porta fechada")
        } catch (expected: AndroidTvError.Connection) {
            assertEquals("Não consegui falar com a TV Box (conexão recusada).", expected.message)
        }
    }

    @Test
    fun networkFailuresBecomeTheAppMessages() {
        assertTrue(TlsChannel.describe(SSLHandshakeException("bad_certificate")) is AndroidTvError.SecureChannel)
        assertTrue(TlsChannel.describe(SocketTimeoutException("connect timed out")) is AndroidTvError.Timeout)
        assertEquals(
            "Não consegui falar com a TV Box (aparelho fora da rede).",
            TlsChannel.describe(ConnectException("failed to connect to /192.168.1.101 (port 6466): EHOSTUNREACH (No route to host)")).message
        )
        assertEquals(
            "Não consegui falar com a TV Box (aparelho fora da rede).",
            TlsChannel.describe(NoRouteToHostException("No route to host")).message
        )
        assertEquals(
            "Não consegui falar com a TV Box (conexão recusada).",
            TlsChannel.describe(ConnectException("ECONNREFUSED (Connection refused)")).message
        )
        assertEquals(
            "Não consegui falar com a TV Box (a TV Box encerrou a conexão).",
            TlsChannel.describe(EOFException()).message
        )
    }

    @Test
    fun subnetBroadcast() {
        fun broadcast(ip: String, prefix: Int) =
            WakeOnLan.broadcastAddress(InetAddress.getByName(ip) as Inet4Address, prefix).hostAddress

        assertEquals("192.168.1.255", broadcast("192.168.1.33", 24))
        assertEquals("10.0.2.255", broadcast("10.0.2.16", 24))
        assertEquals("172.16.255.255", broadcast("172.16.4.9", 16))
        assertEquals("192.168.1.33", broadcast("192.168.1.33", 32))
    }
}
