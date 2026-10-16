package com.lanremote.android.ui.remote

import com.lanremote.android.data.model.TVConfig
import com.lanremote.android.data.model.TVStatus
import com.lanremote.android.data.network.DiscoveredTV

data class RemoteUiState(
    val config: TVConfig = TVConfig(),
    val status: TVStatus = TVStatus(),
    val isScanning: Boolean = false,
    val discoveredTVs: List<DiscoveredTV> = emptyList(),
    val isKeypadVisible: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val isSendingCommand: Boolean = false,
    val lastMessage: String? = null,
    val pairingMessage: String? = null
)
