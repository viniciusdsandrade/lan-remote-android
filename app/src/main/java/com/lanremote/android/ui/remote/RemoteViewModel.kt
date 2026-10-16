package com.lanremote.android.ui.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanremote.android.data.model.TVConfig
import com.lanremote.android.data.model.TVKey
import com.lanremote.android.data.model.TVStatus
import com.lanremote.android.data.network.DiscoveredTV
import com.lanremote.android.data.network.KtorTVClient
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
    private val ssdpScanner: SsdpScanner,
    autoStartPolling: Boolean = true
) : ViewModel() {

    private val _uiState = MutableStateFlow(RemoteUiState())
    val uiState: StateFlow<RemoteUiState> = _uiState.asStateFlow()

    private val mutex = Mutex()
    private var lastSendTime = 0L
    private val minGapMillis = 300L
    private var pollJob: Job? = null

    init {
        // Observa mudanças nas configurações salvas (DataStore)
        viewModelScope.launch {
            settingsRepo.tvConfigFlow.collect { config ->
                _uiState.update { it.copy(config = config) }
                refreshStatus()
            }
        }

        if (autoStartPolling) {
            startPollingStatus()
        }
    }

    fun startPollingStatus() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                refreshStatus()
                delay(5000L)
            }
        }
    }

    fun stopPollingStatus() {
        pollJob?.cancel()
        pollJob = null
    }

    /**
     * Envia tecla de controle com debounce serial e feedback tátil imediato.
     */
    fun sendKey(key: TVKey) {
        when (key) {
            TVKey.POWER, TVKey.HOME -> haptic.performHeavyClick()
            TVKey.VOLUME_UP, TVKey.VOLUME_DOWN, TVKey.CHANNEL_UP, TVKey.CHANNEL_DOWN,
            TVKey.UP, TVKey.DOWN, TVKey.LEFT, TVKey.RIGHT -> haptic.performTick()
            else -> haptic.performClick()
        }

        viewModelScope.launch {
            mutex.withLock {
                val now = System.currentTimeMillis()
                val elapsed = now - lastSendTime
                if (elapsed < minGapMillis) {
                    delay(minGapMillis - elapsed)
                }
                lastSendTime = System.currentTimeMillis()

                _uiState.update { it.copy(isSendingCommand = true) }
                val config = _uiState.value.config
                val result = tvClient.sendCommand(config, key.code)

                if (result.isSuccess) {
                    _uiState.update {
                        it.copy(
                            isSendingCommand = false,
                            lastMessage = "${key.label} enviado",
                            status = it.status.copy(isOnline = true)
                        )
                    }
                    // Atualiza status se volume/mudo foi acionado
                    if (key == TVKey.VOLUME_UP || key == TVKey.VOLUME_DOWN || key == TVKey.MUTE) {
                        delay(150L)
                        queryVolume(config)
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isSendingCommand = false,
                            lastMessage = "Falha: ${result.exceptionOrNull()?.message ?: "Erro desconhecido"}"
                        )
                    }
                }
            }
        }
    }

    /**
     * Atualiza o status de volume e conectividade da TV.
     */
    fun refreshStatus() {
        viewModelScope.launch {
            val config = _uiState.value.config
            queryVolume(config)
        }
    }

    private suspend fun queryVolume(config: TVConfig) {
        val volumeResult = tvClient.getVolumeInfo(config)
        if (volumeResult.isSuccess) {
            val status = volumeResult.getOrNull() ?: TVStatus(isOnline = true)
            _uiState.update { it.copy(status = status) }
        } else {
            val isOnline = tvClient.checkOnline(config.host, config.port)
            _uiState.update { it.copy(status = it.status.copy(isOnline = isOnline)) }
        }
    }

    /**
     * Dispara solicitação de exibição de código de pareamento na tela da TV.
     */
    fun requestPairingKey(host: String, port: Int) {
        haptic.performClick()
        viewModelScope.launch {
            _uiState.update { it.copy(pairingMessage = "Solicitando código na TV…") }
            val result = tvClient.requestPairingKey(host, port)
            if (result.isSuccess) {
                _uiState.update { it.copy(pairingMessage = "Código de 6 dígitos exibido na TV!") }
            } else {
                _uiState.update { it.copy(pairingMessage = "Erro: ${result.exceptionOrNull()?.message}") }
            }
        }
    }

    /**
     * Envia o código de pareamento digitado pelo usuário e salva a sessão se autenticado.
     */
    fun pairTV(host: String, port: Int, pairingKey: String) {
        haptic.performClick()
        viewModelScope.launch {
            _uiState.update { it.copy(pairingMessage = "Verificando código…") }
            val result = tvClient.authenticate(host, port, pairingKey)
            if (result.isSuccess) {
                val session = result.getOrNull()
                val updated = _uiState.value.config.copy(
                    host = host,
                    port = port,
                    pairingKey = pairingKey,
                    sessionId = session
                )
                settingsRepo.saveConfig(updated)
                _uiState.update {
                    it.copy(
                        pairingMessage = "Pareado com sucesso!",
                        config = updated,
                        status = it.status.copy(isOnline = true)
                    )
                }
            } else {
                _uiState.update { it.copy(pairingMessage = "Falha no pareamento: ${result.exceptionOrNull()?.message}") }
            }
        }
    }

    /**
     * Salva as configurações de rede manualmente.
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
            refreshStatus()
        }
    }

    /**
     * Inicia a busca por TVs na rede local via SSDP Multicast e scan de sub-rede.
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
            refreshStatus()
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
