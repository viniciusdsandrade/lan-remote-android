package com.lanremote.android.data.model

data class TVStatus(
    val isOnline: Boolean = false,
    val volumeLevel: Int = 0,
    val isMuted: Boolean = false,
    val minVolume: Int = 0,
    val maxVolume: Int = 100,
    val currentChannel: String? = null,
    val inputSource: String? = null
)
