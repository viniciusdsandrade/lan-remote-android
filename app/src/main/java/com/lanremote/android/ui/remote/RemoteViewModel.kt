package com.lanremote.android.ui.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanremote.android.data.androidtv.AndroidTvError
import com.lanremote.android.data.androidtv.BoxPairing
import com.lanremote.android.data.androidtv.MacAddress
import com.lanremote.android.data.androidtv.TvBoxLink
import com.lanremote.android.data.model.AndroidTvBox
import com.lanremote.android.data.model.TVKey
import com.lanremote.android.data.model.TVPowerState
import com.lanremote.android.data.network.DiscoveredTV
import com.lanremote.android.data.network.KtorTVClient
import com.lanremote.android.data.network.RoapResult
import com.lanremote.android.data.network.SsdpScanner
import com.lanremote.android.data.network.TvCandidates
import com.lanremote.android.data.repository.SettingsRepository
import com.lanremote.android.system.HapticFeedbackHelper
import com.lanremote.android.ui.help.ControlHelp
import kotlinx.coroutines.CancellationException
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
    private val tvBoxLink: TvBoxLink,
    /** Relógio monotônico em milissegundos (os testes passam o do agendador virtual). */
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 }
) : ViewModel() {

    companion object {
        /** A TV descarta teclas iguais em rajada; 300 ms entre elas passa. */
        private const val MIN_GAP_MILLIS = 300L
        private const val POLL_INTERVAL_MILLIS = 5_000L
        /** Espera antes da segunda tentativa quando uma consulta fica sem resposta. */
        private const val RETRY_DELAY_MILLIS = 1_500L
        private const val VOLUME_REFRESH_DELAY_MILLIS = 350L

        /** Depois do POWER na TV Box: a imagem volta em segundos; a rede da TV, em ~40–55 s. */
        private const val TURN_ON_CHECK_MILLIS = 1_500L
        private const val TURN_ON_TIMEOUT_MILLIS = 90_000L

        /** Logo depois do POWER a TV ainda pode responder enquanto desliga: não volta a "Ligada". */
        private const val POWER_OFF_GRACE_MILLIS = 8_000L

        /**
         * Procurar a TV na rede: sem endereço salvo, a cada 30 s; enquanto ela liga, a cada 10 s (a
         * rede dela volta em ~40–55 s); sumida do endereço salvo, a cada 2 min (o roteador pode ter
         * dado outro IP a ela; em standby a busca só não acha nada).
         */
        private const val TV_SEARCH_UNCONFIGURED_MILLIS = 30_000L
        private const val TV_SEARCH_TURNING_ON_MILLIS = 10_000L
        private const val TV_SEARCH_MILLIS = 2 * 60_000L

        /** Com a TV ligada a TV Box está acordada e aparece no NSD: renova o endereço dela. */
        private const val BOX_REFRESH_MILLIS = 5 * 60_000L
        private const val BOX_REFRESH_DISCOVERY_MILLIS = 2_000L

        /** A explicação de um botão some sozinha depois disso. */
        private const val EXPLANATION_MILLIS = 7_000L

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
    private var activePairing: BoxPairing? = null
    private var pairingJob: Job? = null
    /** Sobe a cada POWER aceito: respostas de consultas que começaram antes dele não valem mais. */
    private var powerEpoch = 0
    private var poweredOffAt: Long? = null
    private var pairingCandidate: AndroidTvBox? = null
    private var lastBoxRefresh: Long? = null
    private var lastTvSearch: Long? = null
    private var searchingTv = false
    private var hideExplanation: Job? = null
    private var modelChecked = false

    init {
        // A TV Box primeiro: a primeira consulta à TV já sabe se dá para ligá-la.
        viewModelScope.launch {
            settingsRepo.tvBoxFlow.collect { box -> _uiState.update { it.copy(box = box) } }
        }
        // Observa as configurações salvas (DataStore). Só a troca de endereço pede uma consulta
        // nova: uma sessão renovada também chega por aqui.
        viewModelScope.launch {
            var address: Pair<String, Int>? = null
            settingsRepo.tvConfigFlow.collect { config ->
                _uiState.update { it.copy(config = config) }
                configLoaded = true
                if (address != config.host to config.port) {
                    address = config.host to config.port
                    modelChecked = false
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

        val wait = MIN_GAP_MILLIS - (clock() - lastSendTime)
        if (wait > 0) {
            delay(wait)
        }
        lastSendTime = clock()

        val result = tvClient.sendCommand(_uiState.value.config, key.code)
        keepSession(result.newSession)
        when (result) {
            is RoapResult.Ok -> {
                if (key == TVKey.POWER) {
                    // POWER só desliga: a rede da TV cai logo em seguida.
                    powerEpoch++
                    poweredOffAt = clock()
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
        if (!_uiState.value.config.isConfigured) {
            // Primeiro uso: ninguém disse onde a TV está. Procura na rede em vez de perguntar a ninguém.
            locateTv()
            return
        }
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
        val epoch = powerEpoch
        var volume = tvClient.getVolumeInfo(_uiState.value.config)
        val powerState = _uiState.value.powerState
        if (volume is RoapResult.Unreachable && (powerState == TVPowerState.ON || powerState == TVPowerState.UNKNOWN)) {
            // Uma falha só não quer dizer standby: a primeira requisição depois de abrir o app às
            // vezes falha com a TV ligada. Pergunta mais uma vez antes de mostrá-la desligada.
            delay(RETRY_DELAY_MILLIS)
            volume = tvClient.getVolumeInfo(_uiState.value.config)
        }
        keepSession(volume.newSession)
        // A TV foi desligada enquanto a resposta vinha (as respostas levam 2 a 4 s): vale o POWER.
        if (epoch != powerEpoch) return
        when (volume) {
            is RoapResult.Unreachable -> {
                markUnreachable()
                refreshBoxAddressIfNeeded()
                locateTv()
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

        readModelOnce()

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
        refreshBoxAddressIfNeeded()
    }

    /** O modelo da TV, para o cabeçalho ("TV LG 42LN5700"): uma vez por sessão (e por endereço). */
    private fun readModelOnce() {
        if (modelChecked) return
        modelChecked = true
        val config = _uiState.value.config
        viewModelScope.launch {
            val model = tvClient.fetchModelName(config.host, config.port)
            if (model == null) {
                modelChecked = false   // tenta de novo na próxima consulta
                return@launch
            }
            if (model != _uiState.value.config.modelName) {
                _uiState.update { it.copy(config = it.config.copy(modelName = model)) }
                settingsRepo.updateModelName(model)
            }
        }
    }

    /**
     * Qualquer resposta HTTP (200 ou 401) prova que a rede da TV está de pé, menos nos primeiros
     * segundos depois de um POWER aceito, quando ela ainda pode responder enquanto desliga.
     */
    private fun markAnswered() {
        val shuttingDown = poweredOffAt?.let { clock() - it < POWER_OFF_GRACE_MILLIS } == true
        _uiState.update {
            if (it.powerState == TVPowerState.OFF && shuttingDown) it else it.copy(powerState = TVPowerState.ON, powerOnError = null)
        }
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
            _uiState.update { it.copy(config = updated) }
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
            val config = _uiState.value.config
            val updated = config.copy(host = tv.ip, port = tv.port, modelName = tv.model ?: config.modelName)
            settingsRepo.saveConfig(updated)
            _uiState.update { it.copy(config = updated) }
            checkTv()
        }
    }

    /**
     * Acha a TV na rede sozinho: no primeiro uso (nenhum endereço salvo) e quando ela para de
     * responder no endereço salvo, porque o roteador pode ter dado outro IP a ela. Só aceita uma TV
     * LG que se apresentou pela descrição UDAP; com várias na rede, a escolha é de [TvCandidates.choose].
     */
    private fun locateTv() {
        if (searchingTv) return
        val state = _uiState.value
        val interval = when {
            state.powerState == TVPowerState.TURNING_ON -> TV_SEARCH_TURNING_ON_MILLIS
            !state.config.isConfigured -> TV_SEARCH_UNCONFIGURED_MILLIS
            else -> TV_SEARCH_MILLIS
        }
        val now = clock()
        val last = lastTvSearch
        if (last != null && now - last < interval) return
        if (!tvBoxLink.isOnWifi()) {
            if (!state.config.isConfigured) noTvFound(emptyList())
            return
        }
        lastTvSearch = now
        searchingTv = true
        viewModelScope.launch {
            try {
                val found = ssdpScanner.discoverTVs()
                val current = _uiState.value.config
                val tv = TvCandidates.choose(found, current.host, current.modelName)
                when {
                    tv == null -> if (!current.isConfigured) noTvFound(found)
                    tv.ip != current.host || tv.port != current.port -> useTv(tv)
                }
            } finally {
                searchingTv = false
            }
        }
    }

    /** A TV foi achada em outro endereço (ou pela primeira vez): grava e consulta de novo. */
    private suspend fun useTv(tv: DiscoveredTV) {
        val config = _uiState.value.config
        val updated = config.copy(host = tv.ip, port = tv.port, modelName = tv.model ?: config.modelName)
        _uiState.update { it.copy(config = updated) }
        settingsRepo.updateHostAndPort(tv.ip, tv.port)
        tv.model?.let { settingsRepo.updateModelName(it) }
        modelChecked = false
        checkTv()
    }

    /** Sem endereço salvo e nenhuma TV que dê para escolher sozinho: o cartão diz o que fazer. */
    private fun noTvFound(found: List<DiscoveredTV>) {
        _uiState.update {
            it.copy(
                powerState = if (it.powerState == TVPowerState.TURNING_ON) it.powerState else TVPowerState.OFF,
                discoveredTVs = found.ifEmpty { it.discoveredTVs },
                powerOnError = if (found.size > 1) {
                    "Achei mais de uma TV LG no Wi-Fi. Escolha a sua em Ajustes."
                } else {
                    "Não achei a TV no Wi-Fi. Com ela ligada, abra os Ajustes e toque em Buscar TVs."
                }
            )
        }
    }

    // Controle didático: segurar um botão explica o que ele faz, sem mandar nada para a TV.

    fun explain(help: ControlHelp) {
        _uiState.update { it.copy(explaining = help) }
        hideExplanation?.cancel()
        hideExplanation = viewModelScope.launch {
            delay(EXPLANATION_MILLIS)
            _uiState.update { it.copy(explaining = null) }
        }
    }

    fun closeExplanation() {
        hideExplanation?.cancel()
        _uiState.update { it.copy(explaining = null) }
    }

    fun openGuide() {
        closeExplanation()
        _uiState.update { it.copy(isGuideOpen = true) }
    }

    fun closeGuide() {
        _uiState.update { it.copy(isGuideOpen = false) }
    }

    fun toggleKeypad() {
        haptic.performClick()
        _uiState.update { it.copy(isKeypadVisible = !it.isKeypadVisible) }
    }

    fun openSettings() {
        haptic.performClick()
        _uiState.update { it.copy(isSettingsOpen = true, pairingMessage = null) }
        val state = _uiState.value
        if (state.box?.paired != true && state.discoveredBoxes.isEmpty()) {
            searchBoxes()
        }
    }

    fun closeSettings() {
        cancelBoxPairing()
        _uiState.update { it.copy(isSettingsOpen = false, pairingMessage = null) }
    }

    // Ligar a TV pela TV Box (HDMI-CEC)

    /**
     * Acorda a TV Box pelo Wi-Fi e aperta POWER nela: ao acordar, ela liga a TV pelo HDMI-CEC
     * (SIMPLINK). Depois espera a rede da TV voltar, consultando a cada 1,5 s por até 90 s.
     */
    fun turnOn() {
        val state = _uiState.value
        val box = state.box
        if (box == null || !box.paired || state.powerState == TVPowerState.TURNING_ON) return
        if (!tvBoxLink.isOnWifi()) {
            _uiState.update { it.copy(powerOnError = "Conecte o celular ao Wi-Fi de casa para ligar a TV.") }
            haptic.performError()
            return
        }
        viewModelScope.launch { performTurnOn(box) }
    }

    private suspend fun performTurnOn(box: AndroidTvBox) {
        _uiState.update {
            it.copy(powerState = TVPowerState.TURNING_ON, powerOnError = null, powerOnStep = "Acordando a ${box.name} pelo Wi-Fi…")
        }
        haptic.performClick()
        try {
            val host = tvBoxLink.switchTvOn(box)
            updateBox(box.name) { it.copy(host = host) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AndroidTvError.NotPaired) {
            updateBox(box.name) { it.copy(paired = false) }
            failTurnOn("A TV Box não reconhece mais este celular. Pareie de novo em Ajustes.")
            return
        } catch (e: AndroidTvError.Asleep) {
            failTurnOn(
                if (box.wakeMacs.isEmpty()) {
                    "A TV Box está em repouso e o app não sabe o MAC do Wi-Fi dela para acordá-la. Informe em Ajustes."
                } else {
                    "A TV Box não acordou pelo Wi-Fi. Confira se o modo de energia dela está em 'Sempre conectado' e se o IP não mudou."
                }
            )
            return
        } catch (e: Exception) {
            failTurnOn("${describe(e)} Ela está ligada na tomada e no Wi-Fi?")
            return
        }

        // A imagem volta em segundos; a rede da NetCast, em quase um minuto. O prazo é de relógio:
        // com a TV sem rede cada consulta ainda gasta o tempo de conexão.
        _uiState.update { it.copy(powerOnStep = "A TV está acendendo. O controle volta a responder em até 1 minuto…") }
        val deadline = clock() + TURN_ON_TIMEOUT_MILLIS
        while (clock() < deadline) {
            delay(TURN_ON_CHECK_MILLIS)
            checkTv()
            if (_uiState.value.powerState == TVPowerState.ON) {
                _uiState.update { it.copy(powerOnStep = "") }
                haptic.performSuccess()
                return
            }
        }
        failTurnOn(
            "A TV Box recebeu o pedido, mas a TV não respondeu. Confira se o SIMPLINK está ligado na TV " +
                "e o controle HDMI (CEC) na TV Box."
        )
    }

    private fun failTurnOn(message: String) {
        _uiState.update { it.copy(powerState = TVPowerState.OFF, powerOnStep = "", powerOnError = message) }
        haptic.performError()
    }

    /**
     * Mantém o IP da TV Box, o MAC Bluetooth e a entrada no cache ARP local atualizados sempre
     * que ela estiver acessível na rede (com a TV ligada, ou em standby no modo "Sempre conectado").
     */
    private fun refreshBoxAddressIfNeeded() {
        val box = _uiState.value.box ?: return
        val now = clock()
        val last = lastBoxRefresh
        if (!box.paired) return
        if (last != null && now - last < BOX_REFRESH_MILLIS) return
        if (!tvBoxLink.isOnWifi()) return
        lastBoxRefresh = now
        viewModelScope.launch {
            val fresh = tvBoxLink.discover(BOX_REFRESH_DISCOVERY_MILLIS).firstOrNull { it.name == box.name } ?: return@launch
            val current = _uiState.value.box ?: return@launch
            if (current.name != fresh.name) return@launch
            val updated = current.copy(host = fresh.host, bluetoothMac = fresh.bluetoothMac ?: current.bluetoothMac)
            if (updated != current) {
                saveBox(updated)
            }
        }
    }

    // Pareamento com a TV Box (uma vez)

    fun searchBoxes() {
        _uiState.update { it.copy(boxPairingStage = BoxPairingStage.Searching) }
        viewModelScope.launch {
            val boxes = tvBoxLink.discover()
            _uiState.update {
                it.copy(
                    discoveredBoxes = boxes,
                    boxPairingStage = if (it.boxPairingStage == BoxPairingStage.Searching) BoxPairingStage.Idle else it.boxPairingStage
                )
            }
        }
    }

    /** Faz a TV Box mostrar um código de 6 caracteres na tela da TV. */
    fun requestBoxCode(candidate: AndroidTvBox) {
        pairingJob?.cancel()
        activePairing?.cancel()
        activePairing = null
        haptic.performClick()
        _uiState.update { it.copy(boxCodeError = null, boxPairingStage = BoxPairingStage.RequestingCode) }
        pairingJob = viewModelScope.launch {
            try {
                activePairing = tvBoxLink.startPairing(candidate.host)
                pairingCandidate = candidate
                _uiState.update { it.copy(boxPairingStage = BoxPairingStage.WaitingForCode) }
                haptic.performSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(boxPairingStage = BoxPairingStage.Failed(describe(e))) }
                haptic.performError()
            }
        }
    }

    /** Pareamento pelo IP digitado (no emulador o NSD não atravessa o NAT). */
    fun requestBoxCode(host: String) {
        val clean = host.trim()
        if (clean.isEmpty()) return
        requestBoxCode(_uiState.value.discoveredBoxes.firstOrNull { it.host == clean } ?: AndroidTvBox(name = "TV Box", host = clean, paired = false))
    }

    fun confirmBoxCode(code: String) {
        val pairing = activePairing ?: return
        val candidate = pairingCandidate ?: return
        _uiState.update { it.copy(boxCodeError = null, boxPairingStage = BoxPairingStage.Confirming) }
        pairingJob = viewModelScope.launch {
            try {
                pairing.finish(code)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AndroidTvError.WrongCode) {
                // O checksum do código pega o erro antes de enviar: a TV Box continua esperando.
                _uiState.update { it.copy(boxCodeError = e.message, boxPairingStage = BoxPairingStage.WaitingForCode) }
                haptic.performWarning()
                return@launch
            } catch (e: Exception) {
                pairing.cancel()
                activePairing = null
                _uiState.update { it.copy(boxPairingStage = BoxPairingStage.Failed(describe(e))) }
                haptic.performError()
                return@launch
            }
            activePairing = null
            pairingCandidate = null

            // Confere na porta do controle, sem apertar nada, que a TV Box aceita o app.
            val accepted = try {
                tvBoxLink.checkConnection(candidate.host)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: AndroidTvError.NotPaired) {
                false
            } catch (e: Exception) {
                true   // a TV Box aceitou o código; uma falha passageira aqui não desfaz o pareamento
            }
            if (!accepted) {
                _uiState.update {
                    it.copy(boxPairingStage = BoxPairingStage.Failed("A TV Box não aceitou o certificado do app. Tente parear de novo."))
                }
                haptic.performError()
                return@launch
            }
            // Um MAC digitado antes só vale se for a mesma TV Box pareada de novo.
            val typedMac = _uiState.value.box?.takeIf { it.name == candidate.name }?.mac
            saveBox(candidate.copy(paired = true, mac = typedMac))
            _uiState.update { it.copy(powerOnError = null, boxPairingStage = BoxPairingStage.Idle) }
            haptic.performSuccess()
        }
    }

    fun cancelBoxPairing() {
        pairingJob?.cancel()
        pairingJob = null
        activePairing?.cancel()
        activePairing = null
        pairingCandidate = null
        _uiState.update { it.copy(boxPairingStage = BoxPairingStage.Idle, boxCodeError = null) }
    }

    /**
     * Grava o MAC do Wi-Fi digitado (vazio apaga). Devolve false se o texto não for um MAC.
     */
    fun setBoxMac(text: String): Boolean {
        val box = _uiState.value.box ?: return false
        val trimmed = text.trim()
        val mac = if (trimmed.isEmpty()) null else MacAddress.normalize(trimmed) ?: return false
        saveBox(box.copy(mac = mac))
        return true
    }

    fun forgetBox() {
        cancelBoxPairing()
        tvBoxLink.forget()
        _uiState.update { it.copy(box = null) }
        viewModelScope.launch { settingsRepo.forgetTvBox() }
    }

    /** Muda a TV Box salva agora (não uma cópia antiga), se ainda for a mesma. */
    private fun updateBox(name: String, change: (AndroidTvBox) -> AndroidTvBox) {
        val current = _uiState.value.box ?: return
        if (current.name != name) return
        val updated = change(current)
        if (updated != current) {
            saveBox(updated)
        }
    }

    private fun saveBox(box: AndroidTvBox) {
        _uiState.update { it.copy(box = box) }
        viewModelScope.launch { settingsRepo.saveTvBox(box) }
    }

    override fun onCleared() {
        activePairing?.cancel()   // fecha a conexão com a TV Box que esperava o código
    }

    private fun describe(error: Exception): String =
        (error as? AndroidTvError)?.message ?: "Não consegui falar com a TV Box."
}
