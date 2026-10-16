package com.lanremote.android.ui.remote

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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private val defaultConfig = TVConfig(host = "192.168.1.100", port = 8080, pairingKey = "112233", sessionId = "sess_1")
    private val volume = VolumeInfo(level = 15, isMuted = false, minLevel = 0, maxLevel = 100)
    private val noAnswer = RoapResult.Unreachable(ConnectException("Connection refused"))

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { settingsRepo.tvConfigFlow } returns flowOf(defaultConfig)
        coEvery { tvClient.getVolumeInfo(any()) } returns RoapResult.Ok(volume)
        coEvery { tvClient.getCurrentChannel(any()) } returns RoapResult.Ok(ChannelInfo(inputSource = "HDMI2", channel = null))
        coEvery { tvClient.sendCommand(any(), any()) } returns RoapResult.Ok(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.newViewModel(): RemoteViewModel {
        val viewModel = RemoteViewModel(tvClient, haptic, settingsRepo, ssdpScanner)
        advanceUntilIdle()
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

        coVerify(exactly = 1) { tvClient.sendCommand(defaultConfig, TVKey.VOLUME_UP.code) }
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
        coVerify(exactly = 1) { tvClient.sendCommand(defaultConfig, TVKey.POWER.code) }
        assertEquals("POWER aceito só desliga", TVPowerState.OFF, viewModel.uiState.value.powerState)
    }

    @Test
    fun oneMissedAnswer_isAskedAgainBeforeShowingTheTvOff() = runTest(testDispatcher) {
        coEvery { tvClient.getVolumeInfo(any()) } returnsMany listOf(noAnswer, RoapResult.Ok(volume))

        val viewModel = RemoteViewModel(tvClient, haptic, settingsRepo, ssdpScanner)
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
        val mockTV = DiscoveredTV(ip = "192.168.1.50", port = 8080, name = "LG Living Room")
        coEvery { ssdpScanner.discoverTVs() } returns listOf(mockTV)

        val viewModel = newViewModel()

        viewModel.scanForTVs()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.discoveredTVs.size)
        assertEquals("192.168.1.50", viewModel.uiState.value.discoveredTVs.first().ip)
    }
}
