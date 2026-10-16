package com.lanremote.android.ui.remote

import android.content.Context
import com.lanremote.android.data.model.TVConfig
import com.lanremote.android.data.model.TVKey
import com.lanremote.android.data.network.DiscoveredTV
import com.lanremote.android.data.network.KtorTVClient
import com.lanremote.android.data.network.SsdpScanner
import com.lanremote.android.data.repository.SettingsRepository
import com.lanremote.android.system.HapticFeedbackHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val tvClient = mockk<KtorTVClient>(relaxed = true)
    private val haptic = mockk<HapticFeedbackHelper>(relaxed = true)
    private val settingsRepo = mockk<SettingsRepository>(relaxed = true)
    private val ssdpScanner = mockk<SsdpScanner>(relaxed = true)

    private val defaultConfig = TVConfig(host = "192.0.2.100", port = 8080)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { settingsRepo.tvConfigFlow } returns flowOf(defaultConfig)
        coEvery { tvClient.getVolumeInfo(any()) } returns Result.success(com.lanremote.android.data.model.TVStatus(isOnline = true, volumeLevel = 15))
        coEvery { tvClient.checkOnline(any(), any()) } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun sendKey_invokesTvClientWithDebounce() = runTest(testDispatcher) {
        coEvery { tvClient.sendCommand(any(), any()) } returns Result.success(Unit)

        val viewModel = RemoteViewModel(tvClient, haptic, settingsRepo, ssdpScanner, autoStartPolling = false)
        advanceUntilIdle()

        viewModel.sendKey(TVKey.POWER)
        advanceUntilIdle()

        coVerify(exactly = 1) { tvClient.sendCommand(defaultConfig, TVKey.POWER.code) }
        assertEquals("Power enviado", viewModel.uiState.value.lastMessage)
    }

    @Test
    fun toggleKeypad_flipsVisibility() = runTest(testDispatcher) {
        val viewModel = RemoteViewModel(tvClient, haptic, settingsRepo, ssdpScanner, autoStartPolling = false)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isKeypadVisible)
        viewModel.toggleKeypad()
        assertTrue(viewModel.uiState.value.isKeypadVisible)
        viewModel.toggleKeypad()
        assertFalse(viewModel.uiState.value.isKeypadVisible)
    }

    @Test
    fun openAndCloseSettings_managesDialogState() = runTest(testDispatcher) {
        val viewModel = RemoteViewModel(tvClient, haptic, settingsRepo, ssdpScanner, autoStartPolling = false)
        advanceUntilIdle()

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

        val viewModel = RemoteViewModel(tvClient, haptic, settingsRepo, ssdpScanner, autoStartPolling = false)
        advanceUntilIdle()

        viewModel.scanForTVs()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.discoveredTVs.size)
        assertEquals("192.0.2.50", viewModel.uiState.value.discoveredTVs.first().ip)
    }
}
