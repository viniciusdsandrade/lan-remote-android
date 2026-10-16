package com.lanremote.android.data.androidtv

/**
 * Uma tentativa de pareamento na porta 6467: [start] faz o código de 6 caracteres aparecer na TV
 * e [finish] manda a prova calculada com ele. Para o código aparecer, a TV tem que estar ligada
 * na entrada da TV Box.
 */
class AndroidTvPairing(
    private val channel: FramedChannel,
    private val client: RsaPublicParts,
    private val server: RsaPublicParts
) : BoxPairing {
    companion object {
        private const val REPLY_TIMEOUT_MS = 8_000L
    }

    suspend fun start() {
        channel.send(AndroidTvMessages.pairingRequest())
        expect(AndroidTvMessages.PAIRING_REQUEST_ACK)
        channel.send(AndroidTvMessages.pairingOption())
        expect(AndroidTvMessages.PAIRING_OPTION)
        channel.send(AndroidTvMessages.pairingConfiguration())
        expect(AndroidTvMessages.PAIRING_CONFIGURATION_ACK)   // o código está na tela agora
    }

    /**
     * Confere e envia o código. Com erro de digitação lança [AndroidTvError.WrongCode] sem enviar
     * nada: a TV Box continua esperando e dá para tentar de novo.
     */
    override suspend fun finish(code: String) {
        val parsed = PairingMath.parse(code) ?: throw AndroidTvError.WrongCode()
        val secret = PairingMath.secret(client, server, parsed.nonce)
        // O primeiro byte do código é um checksum do resto: pega o erro antes da TV Box.
        if (secret[0] != parsed.checksum) throw AndroidTvError.WrongCode()
        channel.send(AndroidTvMessages.pairingSecret(secret))
        expect(AndroidTvMessages.PAIRING_SECRET_ACK)
        cancel()
    }

    override fun cancel() {
        channel.close()
    }

    private suspend fun expect(field: Int) {
        val reply = ProtoMessage(channel.receive(REPLY_TIMEOUT_MS))
        when (val status = reply.int(2)) {
            AndroidTvMessages.STATUS_OK -> Unit
            AndroidTvMessages.STATUS_BAD_SECRET -> throw AndroidTvError.WrongCode()
            else -> throw AndroidTvError.Rejected("status $status")
        }
        if (!reply.has(field)) throw AndroidTvError.Rejected("resposta inesperada")
    }
}
