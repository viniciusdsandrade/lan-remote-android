package com.lanremote.android.ui.remote

import com.lanremote.android.data.model.TVConfig
import com.lanremote.android.data.model.TVPowerState
import com.lanremote.android.data.model.TVStatus
import com.lanremote.android.data.network.DiscoveredTV

data class RemoteUiState(
    val config: TVConfig = TVConfig(),
    val powerState: TVPowerState = TVPowerState.UNKNOWN,
    val status: TVStatus = TVStatus(),
    /** Sobe a cada tecla apertada com a TV desligada: a tela aponta o caminho para ligá-la. */
    val attentionPulse: Int = 0,
    val isScanning: Boolean = false,
    val discoveredTVs: List<DiscoveredTV> = emptyList(),
    val isKeypadVisible: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val isPowerOffDialogOpen: Boolean = false,
    val pairingMessage: String? = null
) {
    /** Teclas só vão para a TV ligada (ou enquanto a primeira consulta não terminou). */
    val canSendKeys: Boolean
        get() = powerState == TVPowerState.ON || powerState == TVPowerState.UNKNOWN
}
