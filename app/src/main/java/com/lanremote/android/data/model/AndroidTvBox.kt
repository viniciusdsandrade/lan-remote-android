package com.lanremote.android.data.model

import com.lanremote.android.data.androidtv.MacAddress

/**
 * A TV Box (Android TV) ligada no HDMI da TV, usada para ligá-la.
 */
data class AndroidTvBox(
    /** Nome anunciado na rede (NSD), ex.: "TV Box Xiaomi". */
    val name: String,
    /** Último IPv4 conhecido. */
    val host: String,
    val paired: Boolean,
    /** MAC do Wi-Fi para o Wake-on-LAN, quando o usuário digitou. */
    val mac: String? = null,
    /** MAC do Bluetooth, do registro TXT `bt=` do NSD; o do Wi-Fi fica ao lado dele. */
    val bluetoothMac: String? = null
) {
    /**
     * Endereços que valem um pacote mágico: o MAC digitado, se houver; senão os vizinhos do MAC
     * Bluetooth (nesta TV Box o Wi-Fi é o Bluetooth − 1; a ordem varia entre aparelhos) e ele
     * mesmo. Pacotes a mais não fazem mal.
     */
    val wakeMacs: List<String>
        get() {
            mac?.let { MacAddress.normalize(it) }?.let { return listOf(it) }
            val bluetooth = bluetoothMac?.let { MacAddress.normalize(it) } ?: return emptyList()
            return listOfNotNull(MacAddress.offset(bluetooth, -1), MacAddress.offset(bluetooth, 1), bluetooth)
        }
}
