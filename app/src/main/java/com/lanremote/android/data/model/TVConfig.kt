package com.lanremote.android.data.model

data class TVConfig(
    val host: String = "192.168.1.100",
    val port: Int = 8080,
    val pairingKey: String = "",
    val sessionId: String? = null,
    val modelName: String = "LG Smart TV (NetCast)"
) {
    val baseUrl: String
        get() = "http://$host:$port"
}
