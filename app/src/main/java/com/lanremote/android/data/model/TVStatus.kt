package com.lanremote.android.data.model

data class TVStatus(
    val volumeLevel: Int = 0,
    val isMuted: Boolean = false,
    val minVolume: Int = 0,
    val maxVolume: Int = 100,
    val currentChannel: String? = null,
    val inputSource: String? = null
)

/** Resposta de `volume_info`. */
data class VolumeInfo(
    val level: Int,
    val isMuted: Boolean,
    val minLevel: Int,
    val maxLevel: Int
)

/** Resposta de `cur_channel`: a entrada (ex.: `HDMI2`) e o canal, quando há um sintonizado. */
data class ChannelInfo(
    val inputSource: String?,
    val channel: String?
)
