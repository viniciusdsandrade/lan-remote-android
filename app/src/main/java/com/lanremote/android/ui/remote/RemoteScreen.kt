package com.lanremote.android.ui.remote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanremote.android.data.model.TVKey
import com.lanremote.android.ui.components.CircularDPad
import com.lanremote.android.ui.components.ColorButtonsRow
import com.lanremote.android.ui.components.MediaControlsRow
import com.lanremote.android.ui.components.NumericKeypad
import com.lanremote.android.ui.components.RemoteButton
import com.lanremote.android.ui.components.RockerSwitch
import com.lanremote.android.ui.components.StatusHeader
import com.lanremote.android.ui.settings.SettingsDialog
import com.lanremote.android.ui.theme.BlackOled
import com.lanremote.android.ui.theme.ButtonBackground
import com.lanremote.android.ui.theme.OkBlue
import com.lanremote.android.ui.theme.SurfaceDark
import com.lanremote.android.ui.theme.TextPrimary
import com.lanremote.android.ui.theme.TextSecondary

@Composable
fun RemoteScreen(
    viewModel: RemoteViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BlackOled)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Status Header (LED, Device Info, Settings & Power)
            StatusHeader(
                isOnline = uiState.status.isOnline,
                deviceTitle = uiState.config.modelName,
                deviceSubTitle = "${uiState.config.host}:${uiState.config.port}",
                volumeLevel = uiState.status.volumeLevel,
                isMuted = uiState.status.isMuted,
                onPowerClick = { viewModel.sendKey(TVKey.POWER) },
                onSettingsClick = { viewModel.openSettings() }
            )

            // 2. Utility Actions: Input, Mute, 123 (Keypad Toggle)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RemoteButton(
                    onClick = { viewModel.sendKey(TVKey.INPUT) },
                    icon = Icons.AutoMirrored.Filled.Input,
                    text = "Entrada",
                    modifier = Modifier.weight(1f).height(42.dp)
                )
                RemoteButton(
                    onClick = { viewModel.sendKey(TVKey.MUTE) },
                    icon = Icons.AutoMirrored.Filled.VolumeMute,
                    text = "Mudo",
                    modifier = Modifier.weight(1f).height(42.dp)
                )
                RemoteButton(
                    onClick = { viewModel.toggleKeypad() },
                    icon = Icons.Default.Dialpad,
                    text = "123",
                    backgroundColor = if (uiState.isKeypadVisible) OkBlue else ButtonBackground,
                    modifier = Modifier.weight(1f).height(42.dp)
                )
            }

            // 3. Numeric Keypad (Collapsible)
            NumericKeypad(
                isVisible = uiState.isKeypadVisible,
                onKeyClick = { key -> viewModel.sendKey(key) }
            )

            // 4. Navigation Bar: Back, Home, Menu, Exit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RemoteButton(
                    onClick = { viewModel.sendKey(TVKey.BACK) },
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    text = "Voltar",
                    modifier = Modifier.weight(1f).height(44.dp)
                )
                RemoteButton(
                    onClick = { viewModel.sendKey(TVKey.HOME) },
                    icon = Icons.Default.Home,
                    text = "Home",
                    backgroundColor = OkBlue,
                    modifier = Modifier.weight(1f).height(44.dp)
                )
                RemoteButton(
                    onClick = { viewModel.sendKey(TVKey.MENU) },
                    icon = Icons.Default.Menu,
                    text = "Menu",
                    modifier = Modifier.weight(1f).height(44.dp)
                )
                RemoteButton(
                    onClick = { viewModel.sendKey(TVKey.EXIT) },
                    icon = Icons.Default.Close,
                    text = "Sair",
                    modifier = Modifier.weight(1f).height(44.dp)
                )
            }

            // 5. Circular D-Pad (Up, Down, Left, Right, OK)
            CircularDPad(
                onUp = { viewModel.sendKey(TVKey.UP) },
                onDown = { viewModel.sendKey(TVKey.DOWN) },
                onLeft = { viewModel.sendKey(TVKey.LEFT) },
                onRight = { viewModel.sendKey(TVKey.RIGHT) },
                onOk = { viewModel.sendKey(TVKey.OK) },
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // 6. Volume & Channel Rockers + Center Aux Buttons (Info / EPG)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Rocker: Volume
                RockerSwitch(
                    title = "VOL",
                    onPlus = { viewModel.sendKey(TVKey.VOLUME_UP) },
                    onMinus = { viewModel.sendKey(TVKey.VOLUME_DOWN) }
                )

                // Center Aux Buttons: Info, Guia (EPG), Live TV
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RemoteButton(
                        onClick = { viewModel.sendKey(TVKey.INFO) },
                        text = "INFO",
                        shapeRadius = 12.dp,
                        modifier = Modifier.height(38.dp)
                    )
                    RemoteButton(
                        onClick = { viewModel.sendKey(TVKey.EPG) },
                        text = "GUIA",
                        shapeRadius = 12.dp,
                        modifier = Modifier.height(38.dp)
                    )
                    RemoteButton(
                        onClick = { viewModel.sendKey(TVKey.LIVE_TV) },
                        text = "TV",
                        shapeRadius = 12.dp,
                        modifier = Modifier.height(38.dp)
                    )
                }

                // Right Rocker: Channel
                RockerSwitch(
                    title = "CH",
                    onPlus = { viewModel.sendKey(TVKey.CHANNEL_UP) },
                    onMinus = { viewModel.sendKey(TVKey.CHANNEL_DOWN) }
                )
            }

            // 7. NetCast 4 Color Buttons (Red, Green, Yellow, Blue)
            ColorButtonsRow(
                onKeyClick = { key -> viewModel.sendKey(key) }
            )

            // 8. Media Playback Controls
            MediaControlsRow(
                onKeyClick = { key -> viewModel.sendKey(key) }
            )

            // 9. Feedback Toast bar
            AnimatedVisibility(
                visible = !uiState.lastMessage.isNullOrBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceDark)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = uiState.lastMessage ?: "",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Settings Dialog Modal
        if (uiState.isSettingsOpen) {
            SettingsDialog(
                config = uiState.config,
                isScanning = uiState.isScanning,
                discoveredTVs = uiState.discoveredTVs,
                pairingMessage = uiState.pairingMessage,
                onSave = { host, port, key -> viewModel.saveSettings(host, port, key) },
                onRequestPairingCode = { host, port -> viewModel.requestPairingKey(host, port) },
                onPair = { host, port, key -> viewModel.pairTV(host, port, key) },
                onScan = { viewModel.scanForTVs() },
                onSelectDiscoveredTV = { tv -> viewModel.selectDiscoveredTV(tv) },
                onDismiss = { viewModel.closeSettings() }
            )
        }
    }
}
