package com.lanremote.android.ui.remote

import com.lanremote.android.data.model.AndroidTvBox
import com.lanremote.android.data.model.TVConfig
import com.lanremote.android.data.model.TVPowerState
import com.lanremote.android.data.model.TVStatus
import com.lanremote.android.data.network.DiscoveredTV
import com.lanremote.android.ui.help.ControlHelp

/** Onde está o pareamento com a TV Box (uma vez só, em Ajustes). */
sealed interface BoxPairingStage {
    data object Idle : BoxPairingStage
    data object Searching : BoxPairingStage
    data object RequestingCode : BoxPairingStage
    data object WaitingForCode : BoxPairingStage
    data object Confirming : BoxPairingStage
    data class Failed(val message: String) : BoxPairingStage
}

data class RemoteUiState(
    val config: TVConfig = TVConfig(),
    val powerState: TVPowerState = TVPowerState.UNKNOWN,
    /** Ligada (responde ao ping), mas a porta 8080 não aceitou conexão agora: teclas aguardam na fila. */
    val notAnswering: Boolean = false,
    /** Outro aparelho na rede respondeu no mesmo IP da TV logo depois de ela estar ativa ali. */
    val ipConflict: Boolean = false,
    val status: TVStatus = TVStatus(),
    /** Sobe a cada tecla apertada com a TV desligada: a tela aponta o caminho para ligá-la. */
    val attentionPulse: Int = 0,
    val isScanning: Boolean = false,
    val discoveredTVs: List<DiscoveredTV> = emptyList(),
    val isKeypadVisible: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val isPowerOffDialogOpen: Boolean = false,
    val pairingMessage: String? = null,

    // Ligar a TV pela TV Box
    val box: AndroidTvBox? = null,
    /** O que está acontecendo enquanto a TV liga ("Acordando a TV Box…"). */
    val powerOnStep: String = "",
    val powerOnError: String? = null,
    val boxPairingStage: BoxPairingStage = BoxPairingStage.Idle,
    val discoveredBoxes: List<AndroidTvBox> = emptyList(),
    val boxCodeError: String? = null,

    // Controle didático
    /** O controle sendo explicado agora (um dedo segurou o botão). */
    val explaining: ControlHelp? = null,
    val isGuideOpen: Boolean = false
) {
    /** Teclas só vão para a TV ligada (ou enquanto a primeira consulta não terminou), e só com endereço. */
    val canSendKeys: Boolean
        get() = config.isConfigured && (powerState == TVPowerState.ON || powerState == TVPowerState.UNKNOWN)

    /** Com a TV Box pareada o app consegue ligar a TV. */
    val canTurnOnTv: Boolean
        get() = box?.paired == true

    /** A TV não recebe teclas agora: o controle fica esmaecido e o cartão mostra como ligá-la. */
    val isTvAsleep: Boolean
        get() = powerState == TVPowerState.OFF || powerState == TVPowerState.TURNING_ON
}
