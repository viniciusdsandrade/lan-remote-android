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

    /**
     * Interceptação dos botões físicos de volume do smartphone para controlar o volume da TV diretamente.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                viewModel.sendKey(TVKey.VOLUME_UP)
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                viewModel.sendKey(TVKey.VOLUME_DOWN)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}
