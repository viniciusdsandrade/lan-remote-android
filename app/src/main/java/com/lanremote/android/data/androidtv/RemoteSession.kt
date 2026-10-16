package com.lanremote.android.data.androidtv

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Conversa na porta 6466 sobre um canal já aberto. O servidor fala primeiro: manda
 * `remote_configure` e `remote_set_active` e espera a resposta de cada um; logo depois costuma
 * dizer se a tela dele está ligada (`remote_start`). Pings são respondidos sempre.
 */
class RemoteSession(
    private val channel: FramedChannel,
    private val device: DeviceInfo,
    private val timeSource: TimeSource = TimeSource.Monotonic
) {
    companion object {
        val HANDSHAKE_TIMEOUT = 8.seconds
        /** Depois do handshake, quanto esperar pelo `remote_start`. */
        val START_TIMEOUT = 1.seconds
        val KEY_GAP = 300.milliseconds
    }

    /**
     * Completa o handshake. Devolve se a tela da TV Box está ligada, ou nulo se ela não disse.
     *
     * Pegadinha do TLS 1.3: a TV Box só rejeita um certificado desconhecido depois que o handshake
     * TLS do cliente já terminou, e o erro aparece na primeira leitura (alerta ou fim da conexão).
     * Por isso qualquer queda antes do `remote_configure` quer dizer "não pareado".
     */
    suspend fun handshake(): Boolean? {
        var configured = false
        var active = false
        var screenOn: Boolean? = null
        val handshakeDeadline = timeSource.markNow() + HANDSHAKE_TIMEOUT
        var startDeadline: TimeMark? = null

        while (true) {
            if (configured && active) {
                val deadline = startDeadline ?: (timeSource.markNow() + START_TIMEOUT).also { startDeadline = it }
                if (screenOn != null || deadline.hasPassedNow()) break
            }
            val remaining = -(startDeadline ?: handshakeDeadline).elapsedNow()
            if (!remaining.isPositive()) throw AndroidTvError.Timeout()

            val raw = try {
                channel.receive(remaining.inWholeMilliseconds.coerceAtLeast(1))
            } catch (e: AndroidTvError.Timeout) {
                if (startDeadline != null) break else throw e
            } catch (e: AndroidTvError.SecureChannel) {
                throw AndroidTvError.NotPaired(e)
            } catch (e: AndroidTvError.Connection) {
                if (!configured) throw AndroidTvError.NotPaired(e) else throw e
            }

            val message = ProtoMessage(raw)
            when {
                message.has(AndroidTvMessages.REMOTE_CONFIGURE) -> {
                    channel.send(AndroidTvMessages.configureReply(device))
                    configured = true
                }
                message.has(AndroidTvMessages.REMOTE_SET_ACTIVE) -> {
                    channel.send(AndroidTvMessages.setActiveReply())
                    active = true
                }
                message.has(AndroidTvMessages.REMOTE_PING_REQUEST) -> {
                    val ping = message.message(AndroidTvMessages.REMOTE_PING_REQUEST)
                    channel.send(AndroidTvMessages.pingReply(ping?.long(1) ?: 0L))
                }
                message.has(AndroidTvMessages.REMOTE_START) ->
                    screenOn = message.message(AndroidTvMessages.REMOTE_START)?.int(1) == 1
                message.has(AndroidTvMessages.REMOTE_ERROR) -> throw AndroidTvError.Rejected("erro remoto")
            }
        }
        return screenOn
    }

    /** Aperta as teclas na ordem, com 300 ms entre elas, respondendo pings no intervalo. */
    suspend fun press(keyCodes: List<Int>) {
        for (code in keyCodes) {
            channel.send(AndroidTvMessages.keyInject(code))
            answerPingsFor(KEY_GAP)
        }
    }

    private suspend fun answerPingsFor(duration: Duration) {
        val until = timeSource.markNow() + duration
        while (true) {
            val remaining = -until.elapsedNow()
            if (!remaining.isPositive()) return
            val raw = try {
                channel.receive(remaining.inWholeMilliseconds.coerceAtLeast(1))
            } catch (e: AndroidTvError) {
                // Fim do intervalo, ou a TV Box fechou a conexão depois da tecla (com POWER ela dorme).
                return
            }
            val ping = runCatching { ProtoMessage(raw) }.getOrNull()
                ?.message(AndroidTvMessages.REMOTE_PING_REQUEST) ?: continue
            channel.send(AndroidTvMessages.pingReply(ping.long(1)))
        }
    }
}
