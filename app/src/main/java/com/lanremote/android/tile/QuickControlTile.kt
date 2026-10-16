package com.lanremote.android.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.lanremote.android.LanRemoteApp
import com.lanremote.android.MainActivity
import com.lanremote.android.data.model.TVKey
import com.lanremote.android.data.network.RoapResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Bloco "TV Mudo" das Configurações rápidas: alterna o mudo da TV sem abrir o app. Com a TV
 * desligada (ou fora da rede) ele abre o app, que mostra como ligá-la.
 */
class QuickControlTile : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val app: LanRemoteApp get() = application as LanRemoteApp

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        setSubtitle(tile, null)
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        serviceScope.launch {
            val repository = app.settingsRepository
            val config = repository.tvConfigFlow.first()
            val result = app.ktorTvClient.sendCommand(config, TVKey.MUTE.code)
            result.newSession?.let { repository.updateSession(it) }
            val tile = qsTile ?: return@launch

            if (result is RoapResult.Ok) {
                // Mostra o estado real: o mudo pode ter sido mexido pelo controle da TV.
                delay(350)
                val volume = app.ktorTvClient.getVolumeInfo(config.copy(sessionId = result.newSession ?: config.sessionId))
                val muted = (volume as? RoapResult.Ok)?.value?.isMuted ?: (tile.state != Tile.STATE_ACTIVE)
                tile.state = if (muted) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                setSubtitle(tile, if (muted) "Sem som" else "Com som")
                tile.updateTile()
            } else {
                // Sem resposta é a TV em standby; recusa é a TV ligada sem aceitar o app (parear de novo).
                setSubtitle(tile, if (result is RoapResult.Refused) "Pareie no app" else "TV desligada")
                tile.updateTile()
                openApp()
            }
        }
    }

    // A versão com PendingIntent só existe a partir do Android 14; abaixo dele a com Intent é a única.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // No Android 14+ a versão com Intent lança UnsupportedOperationException.
            val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    /** Tile.subtitle só existe a partir do Android 10 (API 29); antes disso o bloco fica só com o rótulo. */
    private fun setSubtitle(tile: Tile, text: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = text
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
