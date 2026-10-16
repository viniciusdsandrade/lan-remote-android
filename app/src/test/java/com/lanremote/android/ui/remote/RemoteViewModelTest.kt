package com.lanremote.android.ui.remote

import com.lanremote.android.data.androidtv.AndroidTvError
import com.lanremote.android.data.androidtv.BoxPairing
import com.lanremote.android.data.androidtv.TvBoxLink
import com.lanremote.android.data.model.AndroidTvBox
import com.lanremote.android.data.model.ChannelInfo
import com.lanremote.android.data.model.TVConfig
import com.lanremote.android.data.model.TVKey
import com.lanremote.android.data.model.TVPowerState
import com.lanremote.android.data.model.VolumeInfo
import com.lanremote.android.data.network.DiscoveredTV
import com.lanremote.android.data.network.KtorTVClient
import com.lanremote.android.data.network.RoapResult
import com.lanremote.android.data.network.SsdpScanner
import com.lanremote.android.data.repository.SettingsRepository
import com.lanremote.android.system.HapticFeedbackHelper
import com.lanremote.android.ui.help.Help
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ConnectException

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val tvClient = mockk<KtorTVClient>()
    private val haptic = mockk<HapticFeedbackHelper>(relaxed = true)
    private val settingsRepo = mockk<SettingsRepository>(relaxed = true)
    private val ssdpScanner = mockk<SsdpScanner>(relaxed = true)
    private val tvBoxLink = mockk<TvBoxLink>(relaxed = true)
    private val boxFlow = MutableStateFlow<AndroidTvBox?>(null)

    // MAC fictício: o repositório é público.
    private val pairedBox = AndroidTvBox(name = "TV Box", host = "192.0.2.101", paired = true, bluetoothMac = "02:00:00:00:00:f9")

    private val defaultConfig = TVConfig(host = "192.0.2.100", port = 8080, pairingKey = "112233", sessionId = "sess_1")
    private val volume = VolumeInfo(level = 15, isMuted = false, minLevel = 0, maxLevel = 100)
    private val noAnswer = RoapResult.Unreachable(ConnectException("Connection refused"))

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { settingsRepo.tvConfigFlow } returns flowOf(defaultConfig)
        every { settingsRepo.tvBoxFlow } returns boxFlow
        every { tvBoxLink.isOnWifi() } returns true
        coEvery { tvBoxLink.discover(any()) } returns emptyList()
        coEvery { tvClient.getVolumeInfo(any()) } returns RoapResult.Ok(volume)
        coEvery { tvClient.getCurrentChannel(any()) } returns RoapResult.Ok(ChannelInfo(inputSource = "HDMI2", channel = null))
        coEvery { tvClient.sendCommand(any(), any()) } returns RoapResult.Ok(Unit)
        coEvery { tvClient.fetchModelName(any(), any()) } returns "42LN5700-SA_11"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel() =
        RemoteViewModel(tvClient, haptic, settingsRepo, ssdpScanner, tvBoxLink, clock = { currentTime })

    private fun TestScope.newViewModel(): RemoteViewModel {
        val viewModel = viewModel()
        advanceUntilIdle()
        return viewModel
    }

    /** TV em standby (sem resposta) com a TV Box pareada. */
    private fun TestScope.tvOffWithPairedBox(): RemoteViewModel {
        boxFlow.value = pairedBox
        coEvery { tvClient.getVolumeInfo(any()) } returns noAnswer
        val viewModel = newViewModel()
        assertEquals(TVPowerState.OFF, viewModel.uiState.value.powerState)
        return viewModel
    }

    @Test
    fun anAnswer_showsTheTvOnWithVolumeAndInput() = runTest(testDispatcher) {
        val viewModel = newViewModel()

        val state = viewModel.uiState.value
        assertEquals(TVPowerState.ON, state.powerState)
        assertEquals(15, state.status.volumeLevel)
        assertEquals("HDMI2", state.status.inputSource)
    }

    @Test
    fun aRefusal_alsoMeansTheTvIsOn() = runTest(testDispatcher) {
        // 401 sem chave salva: a TV respondeu, então a rede dela está de pé.
        coEvery { tvClient.getVolumeInfo(any()) } returns RoapResult.Refused(401)
        coEvery { tvClient.getCurrentChannel(any()) } returns RoapResult.Refused(401)

        val viewModel = newViewModel()

        assertEquals(TVPowerState.ON, viewModel.uiState.value.powerState)
    }

    @Test
    fun sendKey_sendsTheKeyWithTheSavedSession() = runTest(testDispatcher) {
        val viewModel = newViewModel()

        viewModel.sendKey(TVKey.VOLUME_UP)
        advanceUntilIdle()

        coVerify(exactly = 1) { tvClient.sendCommand(match { it.host == "192.0.2.100" && it.sessionId == "sess_1" }, TVKey.VOLUME_UP.code) }
    }

    @Test
    fun powerOff_asksFirstAndThenShowsTheTvOff() = runTest(testDispatcher) {
        val viewModel = newViewModel()

        viewModel.askToPowerOff()
        assertTrue(viewModel.uiState.value.isPowerOffDialogOpen)
        coVerify(exactly = 0) { tvClient.sendCommand(any(), TVKey.POWER.code) }

        viewModel.confirmPowerOff()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isPowerOffDialogOpen)
        coVerify(exactly = 1) { tvClient.sendCommand(match { it.host == "192.0.2.100" && it.sessionId == "sess_1" }, TVKey.POWER.code) }
        assertEquals("POWER aceito só desliga", TVPowerState.OFF, viewModel.uiState.value.powerState)
    }

    @Test
    fun oneMissedAnswer_isAskedAgainBeforeShowingTheTvOff() = runTest(testDispatcher) {
        coEvery { tvClient.getVolumeInfo(any()) } returnsMany listOf(noAnswer, RoapResult.Ok(volume))

        val viewModel = viewModel()
        runCurrent()
        advanceTimeBy(1_499)
        assertEquals(TVPowerState.UNKNOWN, viewModel.uiState.value.powerState)

        advanceUntilIdle()
        assertEquals(TVPowerState.ON, viewModel.uiState.value.powerState)
        coVerify(exactly = 2) { tvClient.getVolumeInfo(any()) }
    }

    @Test
    fun twoMissedAnswers_showTheTvOff() = runTest(testDispatcher) {
        coEvery { tvClient.getVolumeInfo(any()) } returns noAnswer

        val viewModel = newViewModel()

        assertEquals(TVPowerState.OFF, viewModel.uiState.value.powerState)
        coVerify(exactly = 2) { tvClient.getVolumeInfo(any()) }
        coVerify(exactly = 0) { tvClient.getCurrentChannel(any()) }
    }

    @Test
    fun withTheTvAlreadyOff_aMissIsNotAskedAgain() = runTest(testDispatcher) {
        coEvery { tvClient.getVolumeInfo(any()) } returns noAnswer
        val viewModel = newViewModel()

        viewModel.refreshStatus()
        advanceUntilIdle()

        coVerify(exactly = 3) { tvClient.getVolumeInfo(any()) }
        assertEquals(TVPowerState.OFF, viewModel.uiState.value.powerState)
    }

    @Test
    fun keysAreHeldBackWhileTheTvIsOff() = runTest(testDispatcher) {
        coEvery { tvClient.getVolumeInfo(any()) } returns noAnswer
        val viewModel = newViewModel()
        assertEquals(0, viewModel.uiState.value.attentionPulse)

        viewModel.sendKey(TVKey.VOLUME_UP)
        viewModel.sendKey(TVKey.OK)
        advanceUntilIdle()

        assertEquals("cada tecla com a TV desligada pulsa o caminho para ligá-la", 2, viewModel.uiState.value.attentionPulse)
        assertFalse(viewModel.uiState.value.canSendKeys)
        coVerify(exactly = 0) { tvClient.sendCommand(any(), any()) }
        verify(exactly = 2) { haptic.performWarning() }
    }

    @Test
    fun aKeyWithoutAnswer_checksTheTvInsteadOfSendingAgain() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        coEvery { tvClient.sendCommand(any(), any()) } returns noAnswer
        coEvery { tvClient.getVolumeInfo(any()) } returns noAnswer

        viewModel.sendKey(TVKey.OK)
        advanceUntilIdle()

        coVerify(exactly = 1) { tvClient.sendCommand(any(), TVKey.OK.code) }
        assertEquals(TVPowerState.OFF, viewModel.uiState.value.powerState)
    }

    @Test
    fun aRenewedSession_isSaved() = runTest(testDispatcher) {
        coEvery { tvClient.getVolumeInfo(any()) } returns RoapResult.Ok(volume, newSession = "sess_2")

        val viewModel = newViewModel()

        coVerify(exactly = 1) { settingsRepo.updateSession("sess_2") }
        assertEquals("sess_2", viewModel.uiState.value.config.sessionId)
    }

    @Test
    fun aHeldPhysicalButton_doesNotPileUpKeys() = runTest(testDispatcher) {
        val viewModel = newViewModel()

        viewModel.sendKey(TVKey.VOLUME_UP)
        viewModel.sendRepeatedKey(TVKey.VOLUME_UP)   // a primeira ainda está na fila
        viewModel.sendRepeatedKey(TVKey.VOLUME_UP)
        advanceUntilIdle()
        coVerify(exactly = 1) { tvClient.sendCommand(any(), TVKey.VOLUME_UP.code) }

        viewModel.sendRepeatedKey(TVKey.VOLUME_UP)   // fila vazia: passa
        advanceUntilIdle()
        coVerify(exactly = 2) { tvClient.sendCommand(any(), TVKey.VOLUME_UP.code) }
    }

    @Test
    fun polling_runsOnlyWhileStarted() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        coVerify(exactly = 1) { tvClient.getVolumeInfo(any()) }

        viewModel.startPollingStatus()
        runCurrent()
        advanceTimeBy(5_001)
        coVerify(exactly = 3) { tvClient.getVolumeInfo(any()) }

        viewModel.stopPollingStatus()
        advanceTimeBy(20_000)
        coVerify(exactly = 3) { tvClient.getVolumeInfo(any()) }
    }

    @Test
    fun toggleKeypad_flipsVisibility() = runTest(testDispatcher) {
        val viewModel = newViewModel()

        assertFalse(viewModel.uiState.value.isKeypadVisible)
        viewModel.toggleKeypad()
        assertTrue(viewModel.uiState.value.isKeypadVisible)
        viewModel.toggleKeypad()
        assertFalse(viewModel.uiState.value.isKeypadVisible)
    }

    @Test
    fun openAndCloseSettings_managesDialogState() = runTest(testDispatcher) {
        val viewModel = newViewModel()

        assertFalse(viewModel.uiState.value.isSettingsOpen)
        viewModel.openSettings()
        assertTrue(viewModel.uiState.value.isSettingsOpen)
        viewModel.closeSettings()
        assertFalse(viewModel.uiState.value.isSettingsOpen)
    }

    @Test
    fun scanForTVs_populatesDiscoveredList() = runTest(testDispatcher) {
        val mockTV = DiscoveredTV(ip = "192.0.2.50", port = 8080, name = "LG Living Room")
        coEvery { ssdpScanner.discoverTVs() } returns listOf(mockTV)

        val viewModel = newViewModel()

        viewModel.scanForTVs()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.discoveredTVs.size)
        assertEquals("192.0.2.50", viewModel.uiState.value.discoveredTVs.first().ip)
    }

    // Ligar a TV pela TV Box

    @Test
    fun turningOn_needsAPairedBox() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        assertFalse(viewModel.uiState.value.canTurnOnTv)

        boxFlow.value = pairedBox.copy(paired = false)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.canTurnOnTv)
        viewModel.turnOn()
        advanceUntilIdle()
        coVerify(exactly = 0) { tvBoxLink.switchTvOn(any()) }

        boxFlow.value = pairedBox
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canTurnOnTv)
    }

    @Test
    fun turnOn_wakesTheBoxAndWaitsForTheTvToAnswer() = runTest(testDispatcher) {
        val viewModel = tvOffWithPairedBox()
        coEvery { tvBoxLink.switchTvOn(pairedBox) } returns pairedBox.host
        // A rede da TV volta uns segundos depois do POWER na TV Box.
        coEvery { tvClient.getVolumeInfo(any()) } returnsMany listOf(noAnswer, noAnswer, RoapResult.Ok(volume))
        val start = currentTime

        viewModel.turnOn()
        runCurrent()
        assertEquals(TVPowerState.TURNING_ON, viewModel.uiState.value.powerState)
        assertEquals("A TV está acendendo. O controle volta a responder em até 1 minuto…", viewModel.uiState.value.powerOnStep)

        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(TVPowerState.ON, state.powerState)
        assertEquals("", state.powerOnStep)
        assertNull(state.powerOnError)
        assertEquals("três consultas, uma a cada 1,5 s", 4_500, currentTime - start)
        verify(exactly = 1) { haptic.performSuccess() }
    }

    @Test
    fun keysWhileTurningOn_pointAtTheBannerInsteadOfGoingOut() = runTest(testDispatcher) {
        val viewModel = tvOffWithPairedBox()
        coEvery { tvBoxLink.switchTvOn(any()) } coAnswers {
            delay(5_000)
            pairedBox.host
        }

        viewModel.turnOn()
        runCurrent()
        viewModel.sendKey(TVKey.VOLUME_UP)
        runCurrent()

        assertEquals(TVPowerState.TURNING_ON, viewModel.uiState.value.powerState)
        assertEquals(1, viewModel.uiState.value.attentionPulse)
        coVerify(exactly = 0) { tvClient.sendCommand(any(), any()) }
    }

    @Test
    fun turnOn_tvThatNeverAnswers_givesUpAfter90Seconds() = runTest(testDispatcher) {
        val viewModel = tvOffWithPairedBox()
        coEvery { tvBoxLink.switchTvOn(any()) } returns pairedBox.host
        val start = currentTime

        viewModel.turnOn()
        advanceUntilIdle()

        assertEquals(90_000, currentTime - start)
        assertEquals(TVPowerState.OFF, viewModel.uiState.value.powerState)
        assertEquals(
            "A TV Box recebeu o pedido, mas a TV não respondeu. Confira se o SIMPLINK está ligado na TV e o controle HDMI (CEC) na TV Box.",
            viewModel.uiState.value.powerOnError
        )
        verify(exactly = 1) { haptic.performError() }
    }

    @Test
    fun turnOn_boxThatForgotThePhone_marksItUnpaired() = runTest(testDispatcher) {
        val viewModel = tvOffWithPairedBox()
        coEvery { tvBoxLink.switchTvOn(any()) } throws AndroidTvError.NotPaired()

        viewModel.turnOn()
        advanceUntilIdle()

        assertEquals("A TV Box não reconhece mais este celular. Pareie de novo em Ajustes.", viewModel.uiState.value.powerOnError)
        assertFalse(viewModel.uiState.value.canTurnOnTv)
        coVerify { settingsRepo.saveTvBox(pairedBox.copy(paired = false)) }
    }

    @Test
    fun turnOn_boxThatDidNotWake_explainsWhatToDo() = runTest(testDispatcher) {
        val viewModel = tvOffWithPairedBox()
        coEvery { tvBoxLink.switchTvOn(any()) } throws AndroidTvError.Asleep()

        viewModel.turnOn()
        advanceUntilIdle()
        assertEquals(
            "A TV Box não acordou pelo Wi-Fi. Se o IP dela mudou, ligue a TV uma vez pelo controle e o app atualiza o endereço.",
            viewModel.uiState.value.powerOnError
        )

        boxFlow.value = pairedBox.copy(bluetoothMac = null)   // sem MAC nenhum para acordá-la
        advanceUntilIdle()
        viewModel.turnOn()
        advanceUntilIdle()
        assertEquals(
            "A TV Box está em repouso e o app não sabe o MAC do Wi-Fi dela para acordá-la. Informe em Ajustes.",
            viewModel.uiState.value.powerOnError
        )
        assertEquals(TVPowerState.OFF, viewModel.uiState.value.powerState)
    }

    @Test
    fun turnOn_otherFailuresSayWhatHappened() = runTest(testDispatcher) {
        val viewModel = tvOffWithPairedBox()
        coEvery { tvBoxLink.switchTvOn(any()) } throws AndroidTvError.Timeout()

        viewModel.turnOn()
        advanceUntilIdle()

        assertEquals("A TV Box não respondeu a tempo. Ela está ligada na tomada e no Wi-Fi?", viewModel.uiState.value.powerOnError)
    }

    @Test
    fun turnOn_withoutWifi_doesNotTry() = runTest(testDispatcher) {
        val viewModel = tvOffWithPairedBox()
        every { tvBoxLink.isOnWifi() } returns false

        viewModel.turnOn()
        advanceUntilIdle()

        assertEquals("Conecte o celular ao Wi-Fi de casa para ligar a TV.", viewModel.uiState.value.powerOnError)
        assertEquals(TVPowerState.OFF, viewModel.uiState.value.powerState)
        coVerify(exactly = 0) { tvBoxLink.switchTvOn(any()) }
    }

    @Test
    fun turnOn_boxFoundAtANewAddress_isSaved() = runTest(testDispatcher) {
        val viewModel = tvOffWithPairedBox()
        coEvery { tvBoxLink.switchTvOn(any()) } returns "192.0.2.23"

        viewModel.turnOn()
        runCurrent()

        coVerify { settingsRepo.saveTvBox(pairedBox.copy(host = "192.0.2.23")) }
        assertEquals("192.0.2.23", viewModel.uiState.value.box?.host)
    }

    // Pareamento com a TV Box

    @Test
    fun pairing_typoKeepsTheBoxWaitingAndTheRightCodePairs() = runTest(testDispatcher) {
        val pairing = mockk<BoxPairing>(relaxed = true)
        val candidate = pairedBox.copy(paired = false)
        coEvery { tvBoxLink.startPairing(candidate.host) } returns pairing
        coEvery { pairing.finish("4D1B0A") } throws AndroidTvError.WrongCode()
        val viewModel = newViewModel()

        viewModel.requestBoxCode(candidate)
        advanceUntilIdle()
        assertEquals(BoxPairingStage.WaitingForCode, viewModel.uiState.value.boxPairingStage)

        viewModel.confirmBoxCode("4D1B0A")
        advanceUntilIdle()
        assertEquals(BoxPairingStage.WaitingForCode, viewModel.uiState.value.boxPairingStage)
        assertEquals("Código incorreto. Confira os 6 caracteres na tela da TV.", viewModel.uiState.value.boxCodeError)
        coVerify(exactly = 0) { settingsRepo.saveTvBox(any()) }

        viewModel.confirmBoxCode("4D1B09")
        advanceUntilIdle()
        assertEquals(BoxPairingStage.Idle, viewModel.uiState.value.boxPairingStage)
        coVerify { tvBoxLink.checkConnection(candidate.host) }
        coVerify { settingsRepo.saveTvBox(candidate.copy(paired = true)) }
        assertTrue(viewModel.uiState.value.canTurnOnTv)
    }

    @Test
    fun pairing_boxThatRejectsTheAppAfterwards_fails() = runTest(testDispatcher) {
        val candidate = pairedBox.copy(paired = false)
        coEvery { tvBoxLink.startPairing(any()) } returns mockk(relaxed = true)
        coEvery { tvBoxLink.checkConnection(any()) } throws AndroidTvError.NotPaired()
        val viewModel = newViewModel()

        viewModel.requestBoxCode(candidate)
        advanceUntilIdle()
        viewModel.confirmBoxCode("4D1B09")
        advanceUntilIdle()

        assertEquals(
            BoxPairingStage.Failed("A TV Box não aceitou o certificado do app. Tente parear de novo."),
            viewModel.uiState.value.boxPairingStage
        )
        coVerify(exactly = 0) { settingsRepo.saveTvBox(any()) }
    }

    @Test
    fun pairing_byTypedAddress() = runTest(testDispatcher) {
        coEvery { tvBoxLink.startPairing(any()) } returns mockk(relaxed = true)
        val viewModel = newViewModel()

        viewModel.requestBoxCode(" 10.0.2.2 ")
        advanceUntilIdle()

        coVerify { tvBoxLink.startPairing("10.0.2.2") }
        assertEquals(BoxPairingStage.WaitingForCode, viewModel.uiState.value.boxPairingStage)
    }

    @Test
    fun boxMac_isValidatedAndNormalized() = runTest(testDispatcher) {
        boxFlow.value = pairedBox
        val viewModel = newViewModel()

        assertFalse(viewModel.setBoxMac("não é MAC"))
        assertTrue(viewModel.setBoxMac("02-00-00-00-00-AB"))
        advanceUntilIdle()
        coVerify { settingsRepo.saveTvBox(pairedBox.copy(mac = "02:00:00:00:00:ab")) }
        assertTrue(viewModel.setBoxMac(" "))
        advanceUntilIdle()
        coVerify { settingsRepo.saveTvBox(pairedBox.copy(mac = null)) }
    }

    @Test
    fun forgetBox_dropsTheKeyAndTheBox() = runTest(testDispatcher) {
        boxFlow.value = pairedBox
        val viewModel = newViewModel()

        viewModel.forgetBox()
        advanceUntilIdle()

        verify { tvBoxLink.forget() }
        coVerify { settingsRepo.forgetTvBox() }
        assertNull(viewModel.uiState.value.box)
    }

    @Test
    fun boxAddress_isRefreshedEveryFiveMinutesWhileTheTvIsOn() = runTest(testDispatcher) {
        boxFlow.value = pairedBox
        coEvery { tvBoxLink.discover(any()) } returns listOf(pairedBox.copy(host = "192.0.2.23", paired = false))

        val viewModel = newViewModel()
        coVerify(exactly = 1) { tvBoxLink.discover(any()) }
        coVerify { settingsRepo.saveTvBox(pairedBox.copy(host = "192.0.2.23")) }

        viewModel.refreshStatus()
        advanceUntilIdle()
        coVerify(exactly = 1) { tvBoxLink.discover(any()) }

        advanceTimeBy(5 * 60_000L)
        viewModel.refreshStatus()
        advanceUntilIdle()
        coVerify(exactly = 2) { tvBoxLink.discover(any()) }
    }

    // Controle didático

    @Test
    fun explain_showsTheHelpWithoutTouchingTheTv() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        val queriesBefore = 1   // a consulta inicial

        viewModel.explain(Help.input)
        runCurrent()

        assertEquals(Help.input, viewModel.uiState.value.explaining)
        coVerify(exactly = 0) { tvClient.sendCommand(any(), any()) }
        coVerify(exactly = queriesBefore) { tvClient.getVolumeInfo(any()) }
    }

    @Test
    fun explanation_goesAwayAfterSevenSeconds() = runTest(testDispatcher) {
        val viewModel = newViewModel()

        viewModel.explain(Help.mute)
        advanceTimeBy(6_999)
        assertEquals(Help.mute, viewModel.uiState.value.explaining)

        viewModel.explain(Help.home)   // outra explicação recomeça a contagem
        advanceTimeBy(6_999)
        assertEquals(Help.home, viewModel.uiState.value.explaining)
        advanceTimeBy(2)
        assertNull(viewModel.uiState.value.explaining)
    }

    @Test
    fun guide_opensAndClosesTheExplanation() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        viewModel.explain(Help.ok)

        viewModel.openGuide()
        assertTrue(viewModel.uiState.value.isGuideOpen)
        assertNull(viewModel.uiState.value.explaining)

        viewModel.closeGuide()
        assertFalse(viewModel.uiState.value.isGuideOpen)
    }

    @Test
    fun tvModel_isReadOnceAndSaved() = runTest(testDispatcher) {
        val viewModel = newViewModel()

        viewModel.refreshStatus()
        advanceUntilIdle()

        assertEquals("42LN5700-SA_11", viewModel.uiState.value.config.modelName)
        coVerify(exactly = 1) { tvClient.fetchModelName("192.0.2.100", 8080) }
        coVerify(exactly = 1) { settingsRepo.updateModelName("42LN5700-SA_11") }
    }
}
