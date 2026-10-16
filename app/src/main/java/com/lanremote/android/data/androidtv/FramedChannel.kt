package com.lanremote.android.data.androidtv

/**
 * Canal de mensagens protobuf enquadradas (o tamanho em varint vai na frente de cada uma).
 * [TlsChannel] fala com a TV Box de verdade; os testes usam um canal com roteiro.
 */
interface FramedChannel {

    /** Envia uma mensagem; o canal põe o tamanho na frente. */
    suspend fun send(payload: ByteArray)

    /**
     * A próxima mensagem, sem o tamanho, esperando no máximo [timeoutMs]. Falhas:
     * [AndroidTvError.Timeout] no fim do prazo, [AndroidTvError.Connection] se a conexão cair e
     * [AndroidTvError.SecureChannel] se vier um alerta TLS.
     */
    suspend fun receive(timeoutMs: Long): ByteArray

    fun close()
}
