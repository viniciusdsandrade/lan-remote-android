package com.lanremote.android.data.network

/**
 * Resultado de uma requisição ROAP. A diferença entre os casos importa para o estado da TV:
 * qualquer resposta HTTP prova que a rede dela está de pé; nenhuma resposta, nesta TV, é standby.
 */
sealed interface RoapResult<out T> {

    /** Sessão nova, quando o cliente teve de refazer o AuthReq (401) no caminho. O app grava. */
    val newSession: String?

    data class Ok<out T>(val value: T, override val newSession: String? = null) : RoapResult<T>

    /** A TV respondeu, mas recusou (401 sem chave salva, 400…). A rede dela está de pé. */
    data class Refused(val httpStatus: Int, override val newSession: String? = null) : RoapResult<Nothing>

    /** Nenhuma resposta HTTP: tempo esgotado, conexão recusada ou aparelho fora da rede. */
    data class Unreachable(val cause: Throwable) : RoapResult<Nothing> {
        override val newSession: String? get() = null
    }
}
