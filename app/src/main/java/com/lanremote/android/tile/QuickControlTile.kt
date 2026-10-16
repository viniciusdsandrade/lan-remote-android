package com.lanremote.android.tile

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.lanremote.android.MainActivity
import com.lanremote.android.data.model.TVKey
import com.lanremote.android.data.network.KtorTVClient
import com.lanremote.android.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class QuickControlTile : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tvClient by lazy { KtorTVClient() }
    private val settingsRepo by lazy { SettingsRepository(applicationContext) }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        serviceScope.launch {
            val config = settingsRepo.tvConfigFlow.firstOrNull() ?: return@launch
            // Dispara comando de Mudo na TV
            val result = tvClient.sendCommand(config, TVKey.MUTE.code)

            val tile = qsTile ?: return@launch
            if (result.isSuccess) {
                tile.state = if (tile.state == Tile.STATE_ACTIVE) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
                tile.subtitle = "Mudo alternado"
            } else {
                tile.subtitle = "TV Offline"
                // Se falhar ou TV offline, abre o app para permitir controle e configuração
                val appIntent = Intent(this@QuickControlTile, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                @Suppress("DEPRECATION")
                startActivityAndCollapse(appIntent)
            }
            tile.updateTile()
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        serviceScope.launch {
            val config = settingsRepo.tvConfigFlow.firstOrNull()
            if (config != null) {
                tile.label = "TV Mudo"
                tile.subtitle = config.host
                tile.state = Tile.STATE_INACTIVE
            } else {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.subtitle = "Não configurado"
            }
            tile.updateTile()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
