package com.lanremote.android.data.androidtv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import com.lanremote.android.data.model.AndroidTvBox
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import kotlin.coroutines.resume

/**
 * Aparelhos Android TV que anunciam o serviço do controle remoto na rede local (mDNS/DNS-SD).
 * O NsdManager cuida do multicast. No emulador o mDNS não atravessa o NAT: lá, digite o IP.
 */
class TvBoxDiscovery(context: Context) {

    companion object {
        const val SERVICE_TYPE = "_androidtvremote2._tcp"
        private const val RESOLVE_TIMEOUT_MS = 3_000L
        private val ESCAPED_DECIMAL = Regex("""\\(\d{3})""")
    }

    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    /** Procura por [timeoutMs] e resolve o IPv4 e o MAC Bluetooth (`bt=` no TXT) de cada uma. */
    suspend fun find(timeoutMs: Long = 3_000L): List<AndroidTvBox> =
        browse(timeoutMs).mapNotNull { service ->
            // Antes do API 34 só cabe um resolveService por vez: um de cada vez, então.
            withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { resolve(service) }?.let(::toBox)
        }

    private suspend fun browse(timeoutMs: Long): List<NsdServiceInfo> {
        val found = LinkedHashMap<String, NsdServiceInfo>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                synchronized(found) { found[serviceInfo.serviceName] = serviceInfo }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            delay(timeoutMs)
        } finally {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
        return synchronized(found) { found.values.toList() }
    }

    private suspend fun resolve(service: NsdServiceInfo): NsdServiceInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            resolveWithCallback(service)
        } else {
            resolveLegacy(service)
        }

    @Suppress("DEPRECATION")
    private suspend fun resolveLegacy(service: NsdServiceInfo): NsdServiceInfo? =
        suspendCancellableCoroutine { continuation ->
            nsd.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    if (continuation.isActive) continuation.resume(serviceInfo)
                }
            })
        }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun resolveWithCallback(service: NsdServiceInfo): NsdServiceInfo? {
        var registered: NsdManager.ServiceInfoCallback? = null
        try {
            return suspendCancellableCoroutine { continuation ->
                val callback = object : NsdManager.ServiceInfoCallback {
                    override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                        val hasIpv4 = serviceInfo.hostAddresses.any { it is Inet4Address }
                        if (hasIpv4 && continuation.isActive) continuation.resume(serviceInfo)
                    }

                    override fun onServiceLost() {}
                    override fun onServiceInfoCallbackUnregistered() {}
                }
                registered = callback
                nsd.registerServiceInfoCallback(service, Runnable::run, callback)
            }
        } finally {
            registered?.let { runCatching { nsd.unregisterServiceInfoCallback(it) } }
        }
    }

    private fun toBox(info: NsdServiceInfo): AndroidTvBox? {
        val address = ipv4(info) ?: return null
        val bluetooth = info.attributes["bt"]?.decodeToString()?.let(MacAddress::normalize)
        return AndroidTvBox(name = unescape(info.serviceName), host = address, paired = false, bluetoothMac = bluetooth)
    }

    private fun ipv4(info: NsdServiceInfo): String? {
        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            info.hostAddresses.firstOrNull { it is Inet4Address }
        } else {
            @Suppress("DEPRECATION")
            info.host as? Inet4Address
        }
        return address?.hostAddress
    }

    /** Algumas versões entregam o nome com os escapes do DNS ("TV\032Box"). */
    private fun unescape(name: String): String =
        ESCAPED_DECIMAL.replace(name) { it.groupValues[1].toInt().toChar().toString() }
}
