package com.lanremote.android.data.model

/**
 * O que o app sabe sobre a energia da TV. Em standby a LG NetCast desliga a placa de rede,
 * então "sem resposta" quer dizer desligada.
 */
enum class TVPowerState {
    /** Primeira consulta ainda em andamento. */
    UNKNOWN,
    ON,
    /** Não responde: standby, nesta TV. */
    OFF,
    /** A TV Box recebeu o pedido de ligar; esperando a rede da TV voltar. */
    TURNING_ON
}
