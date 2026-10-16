package com.lanremote.android

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lanremote.android.data.model.TVKey
import com.lanremote.android.ui.remote.RemoteScreen
import com.lanremote.android.ui.remote.RemoteViewModel
import com.lanremote.android.ui.theme.LanRemoteTheme

class MainActivity : ComponentActivity() {

    private val app by lazy { application as LanRemoteApp }

    private val viewModel: RemoteViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return RemoteViewModel(
                    tvClient = app.ktorTvClient,
                    haptic = app.hapticHelper,
                    settingsRepo = app.settingsRepository,
                    ssdpScanner = app.ssdpScanner
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LanRemoteTheme {
                RemoteScreen(viewModel = viewModel)
            }
        }
    }

    // O ViewModel sobrevive à tela: a consulta periódica só roda com o app visível.
    override fun onStart() {
        super.onStart()
        viewModel.startPollingStatus()
    }

    override fun onStop() {
        viewModel.stopPollingStatus()
        super.onStop()
    }

    /**
     * Os botões físicos de volume do celular controlam o volume da TV. Com a TV desligada eles
     * voltam a mexer no volume do próprio celular.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val key = volumeKey(keyCode) ?: return super.onKeyDown(keyCode, event)
        if (!viewModel.uiState.value.canSendKeys) return super.onKeyDown(keyCode, event)
        if ((event?.repeatCount ?: 0) > 0) {
            viewModel.sendRepeatedKey(key)
        } else {
            viewModel.sendKey(key)
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (volumeKey(keyCode) != null && viewModel.uiState.value.canSendKeys) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun volumeKey(keyCode: Int): TVKey? = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> TVKey.VOLUME_UP
        KeyEvent.KEYCODE_VOLUME_DOWN -> TVKey.VOLUME_DOWN
        else -> null
    }
}
