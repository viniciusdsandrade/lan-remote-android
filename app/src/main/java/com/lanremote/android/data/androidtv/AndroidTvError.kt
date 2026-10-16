package com.lanremote.android.data.androidtv

/**
 * Erros da conversa com a TV Box, com as mensagens que o app mostra (as mesmas do Mac e do iOS).
 */
sealed class AndroidTvError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    class Certificate(why: String, cause: Throwable? = null) :
        AndroidTvError("Não consegui preparar o certificado do app ($why).", cause)

    class Connection(why: String, cause: Throwable? = null) :
        AndroidTvError("Não consegui falar com a TV Box ($why).", cause)

    class SecureChannel(cause: Throwable? = null) : AndroidTvError("A TV Box recusou a conexão segura.", cause)

    class Timeout(cause: Throwable? = null) : AndroidTvError("A TV Box não respondeu a tempo.", cause)

    class Rejected(why: String) : AndroidTvError("A TV Box recusou o pedido ($why).")

    class WrongCode : AndroidTvError("Código incorreto. Confira os 6 caracteres na tela da TV.")

    class NotPaired(cause: Throwable? = null) : AndroidTvError("O app ainda não está pareado com a TV Box.", cause)

    class Asleep : AndroidTvError("A TV Box está em repouso e não acordou pelo Wi-Fi.")
}
