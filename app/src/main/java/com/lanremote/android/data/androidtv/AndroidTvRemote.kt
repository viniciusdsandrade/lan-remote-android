package com.lanremote.android.data.androidtv

import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

// Ligar a TV. A LG NetCast desliga a rede em standby (Wake-on-LAN nela não faz nada), então o app
// pede à TV Box ligada no HDMI (Android TV): quando ela acorda, liga a TV pelo HDMI-CEC ("One Touch
// Play", que a LG chama de SIMPLINK). A TV Box também dorme fundo, mas acorda com Wake-on-LAN.

/** Abre o canal com a TV Box numa porta (TLS com o certificado do app, na vida real). */
fun interface RemoteConnector {
    suspend fun open(host: String, port: Int): FramedChannel
}

/** Manda os pacotes mágicos do Wake-on-LAN para a TV Box. */
fun interface Waker {
    suspend fun wake(host: String, macs: List<String>)
}

/** Diz se a porta aceita conexão TCP dentro do prazo. */
fun interface PortProbe {
    suspend fun accepts(host: String, port: Int, timeoutMs: Int): Boolean
}

class AndroidTvRemote(
    private val connector: RemoteConnector,
    private val waker: Waker,
    private val probe: PortProbe,
    private val device: DeviceInfo,
    private val timeSource: TimeSource = TimeSource.Monotonic
) {
    companion object {
        const val REMOTE_PORT = 6466
        const val PAIRING_PORT = 6467

        /** KEYCODE_POWER. KEYCODE_TV_POWER (177) e KEYCODE_WAKEUP (224) não funcionam na TV Box Xiaomi. */
        const val KEY_POWER = 26

        private const val PROBE_TIMEOUT_MS = 600
        private val PROBE_INTERVAL = 300.milliseconds
        private val WAKE_TIMEOUT_WITHOUT_MAC = 4.seconds
        private val WAKE_TIMEOUT_WITH_MAC = 10.seconds
        private val SLEEP_BEFORE_REWAKE = 3.seconds
        private val REWAKE_TIMEOUT = 12.seconds
    }

    /**
     * Conecta, completa o handshake e aperta [keyCodes] na ordem. Devolve se a tela da TV Box
     * estava ligada antes das teclas (nulo se ela não disse).
     */
    suspend fun press(host: String, keyCodes: List<Int>): Boolean? {
        val channel = try {
            connector.open(host, REMOTE_PORT)
        } catch (e: AndroidTvError.SecureChannel) {
            throw AndroidTvError.NotPaired(e)
        }
        try {
            val session = RemoteSession(channel, device, timeSource)
            val screenOn = session.handshake()
            session.press(keyCodes)
            return screenOn
        } finally {
            channel.close()
        }
    }

    /**
     * Liga a TV pela TV Box e devolve o endereço em que ela respondeu (pode ter mudado).
     *
     * Poucos segundos depois que a TV entra em standby a TV Box também dorme e fecha o serviço do
     * controle. O Wake-on-LAN devolve a rede dela por alguns segundos (a porta responde em ~1 s), o
     * bastante para apertar POWER; ao acordar, ela manda o One Touch Play e a TV liga na entrada
     * dela. Se a porta não abrir, [relocate] procura a TV Box pelo nome (o IP pode ter mudado).
     */
    suspend fun switchTvOn(
        host: String,
        wakeMacs: List<String>,
        relocate: suspend () -> String? = { null }
    ): String {
        var current = host
        val wakeTimeout = if (wakeMacs.isEmpty()) WAKE_TIMEOUT_WITHOUT_MAC else WAKE_TIMEOUT_WITH_MAC
        waker.wake(current, wakeMacs)
        if (!waitUntilReachable(current, wakeTimeout, wakeMacs)) {
            current = relocate()?.takeIf { it != current && waitUntilReachable(it, wakeTimeout, wakeMacs) }
                ?: throw AndroidTvError.Asleep()
        }
        if (press(current, emptyList()) == true) {
            // TV Box acordada com a TV desligada: só um despertar novo manda o One Touch Play,
            // então ela dorme primeiro e acorda de novo.
            press(current, listOf(KEY_POWER))
            delay(SLEEP_BEFORE_REWAKE)
            waker.wake(current, wakeMacs)
            waitUntilReachable(current, REWAKE_TIMEOUT, wakeMacs)
        }
        press(current, listOf(KEY_POWER))
        return current
    }

    /**
     * Tenta abrir a porta do controle a cada 300 ms até o prazo, reenviando o Wake-on-LAN a cada
     * tentativa sem sucesso para que um cache ARP frio resolva no primeiro pacote e receba o
     * pacote mágico em seguida.
     */
    suspend fun waitUntilReachable(
        host: String,
        timeout: Duration,
        wakeMacs: List<String> = emptyList()
    ): Boolean {
        val deadline = timeSource.markNow() + timeout
        while (!deadline.hasPassedNow()) {
            if (probe.accepts(host, REMOTE_PORT, PROBE_TIMEOUT_MS)) return true
            if (wakeMacs.isNotEmpty()) {
                waker.wake(host, wakeMacs)
            }
            delay(PROBE_INTERVAL)
        }
        return false
    }
}
