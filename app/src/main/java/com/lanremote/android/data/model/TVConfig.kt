package com.lanremote.android.data.model

data class TVConfig(
    /** Vazio enquanto o app não achou a TV na rede (nem alguém digitou o endereço). */
    val host: String = "",
    val port: Int = 8080,
    val pairingKey: String = "",
    val sessionId: String? = null,
    val modelName: String = "LG Smart TV (NetCast)"
) {
    val baseUrl: String
        get() = "http://$host:$port"

    val isConfigured: Boolean
        get() = host.isNotBlank()
}
