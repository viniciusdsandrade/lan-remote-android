package com.lanremote.android.ui.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanremote.android.data.model.TVKey
import com.lanremote.android.data.model.TVPowerState
import com.lanremote.android.data.network.DiscoveredTV
import com.lanremote.android.data.network.KtorTVClient
import com.lanremote.android.data.network.RoapResult
import com.lanremote.android.data.network.SsdpScanner
import com.lanremote.android.data.repository.SettingsRepository
import com.lanremote.android.system.HapticFeedbackHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RemoteViewModel(
    private val tvClient: KtorTVClient,
    private val haptic: HapticFeedbackHelper,
    private val settingsRepo: SettingsRepository,
    private val ssdpScanner: SsdpScanner
) : ViewModel() {

    companion object {
        /** A TV descarta teclas iguais em rajada; 300 ms entre elas passa. */
        private const val MIN_GAP_MILLIS = 300L
        private const val POLL_INTERVAL_MILLIS = 5_000L
        /** Espera antes da segunda tentativa quando uma consulta fica sem resposta. */
        private const val RETRY_DELAY_MILLIS = 1_500L
        private const val VOLUME_REFRESH_DELAY_MILLIS = 350L

        private val VOLUME_KEYS = setOf(TVKey.VOLUME_UP, TVKey.VOLUME_DOWN, TVKey.MUTE)
        private val STEP_KEYS = setOf(
            TVKey.VOLUME_UP, TVKey.VOLUME_DOWN, TVKey.CHANNEL_UP, TVKey.CHANNEL_DOWN,
            TVKey.UP, TVKey.DOWN, TVKey.LEFT, TVKey.RIGHT
        )
    }

    private val _uiState = MutableStateFlow(RemoteUiState())
    val uiState: StateFlow<RemoteUiState> = _uiState.asStateFlow()

    private val sendMutex = Mutex()
    private var lastSendTime = 0L
    private var queuedKeys = 0
    private var pollJob: Job? = null
    private var configLoaded = false
    private var refreshing = false
    private var refreshAgain = false

    init {
        // Observa as configurações salvas (DataStore). Só a troca de endereço pede uma consulta
        // nova: uma sessão renovada também chega por aqui.
        viewModelScope.launch {
            var address: Pair<String, Int>? = null
            settingsRepo.tvConfigFlow.collect { config ->
                _uiState.update { it.copy(config = config) }
                configLoaded = true
                if (address != config.host to config.port) {
                    address = config.host to config.port
                    checkTv()
                }
            }
        }
    }

    /**
     * Consulta a TV a cada 5 s enquanto o app está visível (MainActivity.onStart e onStop).
     */
    fun startPollingStatus() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (true) {
                checkTv()
                delay(POLL_INTERVAL_MILLIS)
            }
        }
    }

    fun stopPollingStatus() {
        pollJob?.cancel()
        pollJob = null
    }

    /**
     * Envia uma tecla com intervalo mínimo entre elas e háptico imediato. Com a TV desligada não
     * envia nada: o cartão de ligar pulsa e o celular avisa, em vez de falhar calado.
     */
    fun sendKey(key: TVKey) {
        if (!_uiState.value.canSendKeys) {
            _uiState.update { it.copy(attentionPulse = it.attentionPulse + 1) }
            haptic.performWarning()
            return
        }

        when (key) {
            TVKey.POWER, TVKey.HOME -> haptic.performHeavyClick()
            in STEP_KEYS -> haptic.performTick()
            else -> haptic.performClick()
        }

        queuedKeys++
        viewModelScope.launch {
            try {
                sendMutex.withLock { transmit(key) }
            } finally {
                queuedKeys--
            }
        }
    }

    /**
     * Repetição de um botão físico segurado: só entra na fila vazia, para o volume não continuar
     * mudando depois que o dedo sai do botão.
     */
    fun sendRepeatedKey(key: TVKey) {
        if (queuedKeys == 0) {
            sendKey(key)
        }
    }

    private suspend fun transmit(key: TVKey) {
        if (!_uiState.value.canSendKeys) return   // a TV caiu enquanto a tecla esperava na fila

        val wait = MIN_GAP_MILLIS - (System.currentTimeMillis() - lastSendTime)
        if (wait > 0) {
            delay(wait)
        }
        lastSendTime = System.currentTimeMillis()

        val result = tvClient.sendCommand(_uiState.value.config, key.code)
        keepSession(result.newSession)
        when (result) {
            is RoapResult.Ok -> {
                if (key == TVKey.POWER) {
                    // POWER só desliga: a rede da TV cai logo em seguida.
                    _uiState.update { it.copy(powerState = TVPowerState.OFF) }
                } else {
                    markAnswered()
                    if (key in VOLUME_KEYS) {
                        viewModelScope.launch {
                            delay(VOLUME_REFRESH_DELAY_MILLIS)
                            checkTv()
                        }
                    }
                }
            }
            is RoapResult.Refused -> markAnswered()
            // Sem resposta: a consulta, com a regra das duas falhas, decide se a TV desligou.
            is RoapResult.Unreachable -> viewModelScope.launch { checkTv() }
        }
    }

    // Desligar com confirmação

    fun askToPowerOff() {
        haptic.performWarning()
        _uiState.update { it.copy(isPowerOffDialogOpen = true) }
    }

    fun confirmPowerOff() {
        _uiState.update { it.copy(isPowerOffDialogOpen = false) }
        sendKey(TVKey.POWER)
    }

    fun dismissPowerOff() {
        _uiState.update { it.copy(isPowerOffDialogOpen = false) }
    }

    // Estado da TV

    /**
     * Atualiza estado, volume e entrada da TV.
     */
    fun refreshStatus() {
        viewModelScope.launch { checkTv() }
    }

    private suspend fun checkTv() {
        if (!configLoaded) return
        if (refreshing) {
            refreshAgain = true   // a consulta em andamento repete no fim, para mostrar o valor novo
            return
        }
        refreshing = true
        try {
            do {
                refreshAgain = false
                queryTv()
            } while (refreshAgain)
        } finally {
            refreshing = false
        }
    }

    private suspend fun queryTv() {
        var volume = tvClient.getVolumeInfo(_uiState.value.config)
        val powerState = _uiState.value.powerState
        if (volume is RoapResult.Unreachable && (powerState == TVPowerState.ON || powerState == TVPowerState.UNKNOWN)) {
            // Uma falha só não quer dizer standby: a primeira requisição depois de abrir o app às
            // vezes falha com a TV ligada. Pergunta mais uma vez antes de mostrá-la desligada.
            delay(RETRY_DELAY_MILLIS)
            volume = tvClient.getVolumeInfo(_uiState.value.config)
        }
        keepSession(volume.newSession)
        when (volume) {
            is RoapResult.Unreachable -> {
                markUnreachable()
                return   // não adianta perguntar a entrada também
            }
            is RoapResult.Refused -> markAnswered()
            is RoapResult.Ok -> {
                markAnswered()
                val info = volume.value
                _uiState.update {
                    it.copy(
                        status = it.status.copy(
                            volumeLevel = info.level,
                            isMuted = info.isMuted,
                            minVolume = info.minLevel,
                            maxVolume = info.maxLevel
                        )
                    )
                }
            }
        }

        val channel = tvClient.getCurrentChannel(_uiState.value.config)
        keepSession(channel.newSession)
        if (channel is RoapResult.Ok) {
            _uiState.update {
                it.copy(
                    status = it.status.copy(
                        inputSource = channel.value.inputSource ?: it.status.inputSource,
                        currentChannel = channel.value.channel
                    )
                )
            }
        }
    }

    /** Qualquer resposta HTTP (200 ou 401) prova que a rede da TV está de pé. */
    private fun markAnswered() {
        _uiState.update { it.copy(powerState = TVPowerState.ON) }
    }

    /** Sem resposta: nesta TV é standby, a não ser que ela esteja sendo ligada agora. */
    private fun markUnreachable() {
        _uiState.update {
            if (it.powerState == TVPowerState.TURNING_ON) it else it.copy(powerState = TVPowerState.OFF)
        }
    }

    /** Grava a sessão que a TV deu ao refazer o pareamento (a anterior morre a cada standby). */
    private fun keepSession(session: String?) {
        if (session == null || session == _uiState.value.config.sessionId) return
        _uiState.update { it.copy(config = it.config.copy(sessionId = session)) }
        viewModelScope.launch { settingsRepo.updateSession(session) }
    }

    // Pareamento e ajustes da TV

    /**
     * Pede para a TV mostrar o código de pareamento na tela.
     */
    fun requestPairingKey(host: String, port: Int) {
        haptic.performClick()
        viewModelScope.launch {
            _uiState.update { it.copy(pairingMessage = "Pedindo o código à TV…") }
            val result = tvClient.requestPairingKey(host, port)
            if (result.isSuccess) {
                _uiState.update { it.copy(pairingMessage = "O código de 6 dígitos apareceu na tela da TV.") }
            } else {
                _uiState.update { it.copy(pairingMessage = result.exceptionOrNull()?.message) }
                haptic.performError()
            }
        }
    }

    /**
     * Envia o código digitado e salva a sessão, se a TV aceitar.
     */
    fun pairTV(host: String, port: Int, pairingKey: String) {
        haptic.performClick()
        viewModelScope.launch {
            _uiState.update { it.copy(pairingMessage = "Conferindo o código…") }
            val key = pairingKey.trim()
            val result = tvClient.authenticate(host, port, key)
            val session = result.getOrNull()
            if (session != null) {
                val updated = _uiState.value.config.copy(
                    host = host,
                    port = port,
                    pairingKey = key,
                    sessionId = session
                )
                settingsRepo.saveConfig(updated)
                _uiState.update {
                    it.copy(
                        pairingMessage = "Pareado com sucesso!",
                        config = updated,
                        powerState = TVPowerState.ON
                    )
                }
                haptic.performSuccess()
                checkTv()
            } else {
                _uiState.update { it.copy(pairingMessage = result.exceptionOrNull()?.message) }
                haptic.performError()
            }
        }
    }

    /**
     * Salva as configurações de rede digitadas.
     */
    fun saveSettings(host: String, port: Int, pairingKey: String) {
        viewModelScope.launch {
            val updated = _uiState.value.config.copy(
                host = host.trim(),
                port = port,
                pairingKey = pairingKey.trim()
            )
            settingsRepo.saveConfig(updated)
            _uiState.update { it.copy(config = updated, isSettingsOpen = false) }
            checkTv()
        }
    }

    /**
     * Procura TVs na rede local (SSDP e varredura da sub-rede).
     */
    fun scanForTVs() {
        haptic.performClick()
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, discoveredTVs = emptyList()) }
            val tvs = ssdpScanner.discoverTVs()
            _uiState.update {
                it.copy(
                    isScanning = false,
                    discoveredTVs = tvs,
                    pairingMessage = if (tvs.isEmpty()) "Nenhuma TV encontrada automaticamente" else "${tvs.size} TV(s) encontrada(s)"
                )
            }
        }
    }

    fun selectDiscoveredTV(tv: DiscoveredTV) {
        haptic.performClick()
        viewModelScope.launch {
            val updated = _uiState.value.config.copy(
                host = tv.ip,
                port = tv.port,
                modelName = tv.name
            )
            settingsRepo.saveConfig(updated)
            _uiState.update { it.copy(config = updated) }
            checkTv()
        }
    }

    fun toggleKeypad() {
        haptic.performClick()
        _uiState.update { it.copy(isKeypadVisible = !it.isKeypadVisible) }
    }

    fun openSettings() {
        haptic.performClick()
        _uiState.update { it.copy(isSettingsOpen = true, pairingMessage = null) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(isSettingsOpen = false, pairingMessage = null) }
    }
}
