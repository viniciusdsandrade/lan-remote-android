package com.lanremote.android.data.androidtv

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.lanremote.android.data.model.AndroidTvBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.interfaces.RSAPublicKey

/** Um pareamento em andamento: o código já está na tela da TV. */
interface BoxPairing {
    /** Se [code] passa no próprio dígito verificador, sem mandar nada à TV Box. */
    fun accepts(code: String): Boolean

    /** Envia o código; [AndroidTvError.WrongCode] mantém a TV Box esperando outra tentativa. */
    suspend fun finish(code: String)

    fun cancel()
}

/**
 * Tudo o que o app faz com a TV Box: achar, parear, conferir a conexão e ligar a TV por ela.
 */
interface TvBoxLink {
    /** Sem Wi-Fi não há como falar com a TV Box (nem mandar o Wake-on-LAN). */
    fun isOnWifi(): Boolean

    suspend fun discover(timeoutMs: Long = 3_000L): List<AndroidTvBox>

    /** Abre o pareamento na porta 6467: o código de 6 caracteres aparece na TV. */
    suspend fun startPairing(host: String): BoxPairing

    /** Handshake na porta 6466 sem tecla nenhuma. Devolve se a tela da TV Box está ligada. */
    suspend fun checkConnection(host: String): Boolean?

    /** Liga a TV pela TV Box e devolve o IP em que ela respondeu (pode ter mudado). */
    suspend fun switchTvOn(box: AndroidTvBox): String

    /** Apaga a chave do app: a TV Box deixa de reconhecê-lo e o próximo pareamento gera outra. */
    fun forget()
}

class NetworkTvBoxLink(
    context: Context,
    private val identities: IdentityStore = AndroidTvIdentity()
) : TvBoxLink {

    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
    private val discovery = TvBoxDiscovery(appContext)
    private val remote = AndroidTvRemote(
        connector = { host, port -> TlsChannel.open(host, port, identity()) },
        waker = UdpWaker(::wifiBroadcastAddresses),
        probe = TcpPortProbe,
        device = DeviceInfo(model = Build.MODEL, vendor = Build.MANUFACTURER, appVersion = appVersion())
    )

    override fun isOnWifi(): Boolean {
        val network = connectivity.activeNetwork ?: return false
        return connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    override suspend fun discover(timeoutMs: Long): List<AndroidTvBox> = discovery.find(timeoutMs)

    override suspend fun startPairing(host: String): BoxPairing {
        val identity = identity()
        val channel = TlsChannel.open(host, AndroidTvRemote.PAIRING_PORT, identity)
        try {
            val serverKey = channel.serverCertificate?.publicKey as? RSAPublicKey
                ?: throw AndroidTvError.Certificate("a TV Box não mandou um certificado RSA")
            val clientKey = identity.certificate.publicKey as? RSAPublicKey
                ?: throw AndroidTvError.Certificate("a chave do app não é RSA")
            return AndroidTvPairing(channel, RsaPublicParts.of(clientKey), RsaPublicParts.of(serverKey)).also { it.start() }
        } catch (e: Exception) {
            channel.close()
            throw e
        }
    }

    override suspend fun checkConnection(host: String): Boolean? = remote.press(host, emptyList())

    override suspend fun switchTvOn(box: AndroidTvBox): String =
        remote.switchTvOn(box.host, box.wakeMacs) {
            // O broadcast acorda a TV Box mesmo com IP novo; acordada, ela aparece no NSD.
            discovery.find().firstOrNull { it.name == box.name }?.host
        }

    override fun forget() {
        identities.reset()
    }

    /** A chave pode ser gerada agora (RSA de 2048 bits no Keystore leva um tempo): fora da main. */
    private suspend fun identity(): ClientIdentity = withContext(Dispatchers.IO) { identities.load() }

    private fun wifiBroadcastAddresses(): List<InetAddress> {
        val network = connectivity.activeNetwork ?: return emptyList()
        val link = connectivity.getLinkProperties(network) ?: return emptyList()
        return link.linkAddresses.mapNotNull { linkAddress ->
            (linkAddress.address as? Inet4Address)?.let { WakeOnLan.broadcastAddress(it, linkAddress.prefixLength) }
        }
    }

    private fun appVersion(): String {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.packageManager.getPackageInfo(appContext.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        }
        return info.versionName ?: "1"
    }
}

/** Diz se a porta aceita uma conexão TCP dentro do prazo. */
object TcpPortProbe : PortProbe {
    override suspend fun accepts(host: String, port: Int, timeoutMs: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
            true
        } catch (e: IOException) {
            false
        }
    }
}
