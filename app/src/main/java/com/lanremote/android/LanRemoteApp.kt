package com.lanremote.android

import android.app.Application
import com.lanremote.android.data.androidtv.NetworkTvBoxLink
import com.lanremote.android.data.network.KtorTVClient
import com.lanremote.android.data.network.SsdpScanner
import com.lanremote.android.data.repository.SettingsRepository
import com.lanremote.android.system.HapticFeedbackHelper

class LanRemoteApp : Application() {

    val ktorTvClient by lazy { KtorTVClient() }
    val settingsRepository by lazy { SettingsRepository(this) }
    val hapticHelper by lazy { HapticFeedbackHelper(this) }
    val ssdpScanner by lazy { SsdpScanner(this) }
    val tvBoxLink by lazy { NetworkTvBoxLink(this) }

    override fun onCreate() {
        super.onCreate()
    }
}
