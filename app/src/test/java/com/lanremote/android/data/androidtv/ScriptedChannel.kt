package com.lanremote.android.data.androidtv

import kotlinx.coroutines.delay

/**
 * Canal falso com o roteiro do que a TV Box manda: cada item é uma mensagem (ByteArray) ou um erro
 * para lançar. Sem mais itens, [receive] espera o prazo inteiro (tempo virtual) e dá Timeout.
 */
class ScriptedChannel(
    vararg script: Any,
    private val log: (String) -> Unit = {}
) : FramedChannel {
    private val script = ArrayDeque(script.toList())
    val sent = mutableListOf<ByteArray>()
    var closed = false
        private set

    /** Teclas enviadas (`remote_key_inject`), na ordem. */
    val keys: List<Int>
        get() = sent.mapNotNull { ProtoMessage(it).message(AndroidTvMessages.REMOTE_KEY_INJECT)?.int(1) }

    override suspend fun send(payload: ByteArray) {
        sent += payload
        ProtoMessage(payload).message(AndroidTvMessages.REMOTE_KEY_INJECT)?.let { log("key${it.int(1)}") }
    }

    override suspend fun receive(timeoutMs: Long): ByteArray {
        return when (val next = script.removeFirstOrNull()) {
            null -> {
                delay(timeoutMs)
                throw AndroidTvError.Timeout()
            }
            is ByteArray -> next
            is Throwable -> throw next
            else -> error("item de roteiro inválido: $next")
        }
    }

    override fun close() {
        closed = true
        log("close")
    }
}

/** O que a TV Box manda na porta 6466. */
object Box {
    val configure: ByteArray = ProtoWriter.build {
        message(AndroidTvMessages.REMOTE_CONFIGURE) {
            int(1, 622)
            message(2) { string(3, "Acme") }
        }
    }
    val setActive: ByteArray = ProtoWriter.build { message(AndroidTvMessages.REMOTE_SET_ACTIVE) { } }
    val error: ByteArray = ProtoWriter.build { message(AndroidTvMessages.REMOTE_ERROR) { int(1, 1) } }

    fun ping(value: Int): ByteArray = ProtoWriter.build { message(AndroidTvMessages.REMOTE_PING_REQUEST) { int(1, value) } }

    /** `remote_start`: 1 = tela da TV Box ligada. */
    fun start(screenOn: Boolean): ByteArray =
        ProtoWriter.build { message(AndroidTvMessages.REMOTE_START) { int(1, if (screenOn) 1 else 0) } }
}
