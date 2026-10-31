package com.lanremote.android.ui.remote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lanremote.android.data.model.TVKey
import com.lanremote.android.data.model.TVPowerState
import com.lanremote.android.ui.components.CircularDPad
import com.lanremote.android.ui.components.ColorButtonsRow
import com.lanremote.android.ui.components.MediaControlsRow
import com.lanremote.android.ui.components.NumericKeypad
import com.lanremote.android.ui.components.PowerBanner
import com.lanremote.android.ui.components.RemoteButton
import com.lanremote.android.ui.components.RemoteSection
import com.lanremote.android.ui.components.RockerSwitch
import com.lanremote.android.ui.components.StatusHeader
import com.lanremote.android.ui.components.TurnOnGreen
import com.lanremote.android.ui.components.tvDisplayName
import com.lanremote.android.ui.help.ButtonGuideSheet
import com.lanremote.android.ui.help.ControlHelp
import com.lanremote.android.ui.help.Help
import com.lanremote.android.ui.help.HelpPanel
import com.lanremote.android.ui.help.RemoteKeys
import com.lanremote.android.ui.settings.BoxSettingsActions
import com.lanremote.android.ui.settings.SettingsScreen
import com.lanremote.android.ui.settings.TvSettingsActions
import com.lanremote.android.ui.theme.BlackOled
import com.lanremote.android.ui.theme.MutedOrange
import com.lanremote.android.ui.theme.PowerRed
import com.lanremote.android.ui.theme.PowerRedLight
import com.lanremote.android.ui.theme.SurfaceDark
import com.lanremote.android.ui.theme.TextPrimary
import com.lanremote.android.ui.theme.TextSecondary

private val HomeBlue = Color(0xFF245C99)
private val KeypadOpenBlue = Color(0xFF26598F)
private val MutedBrown = Color(0xFF80401A)

@Composable
fun RemoteScreen(
    viewModel: RemoteViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    // Ajustes ocupam a tela inteira no lugar do controle.
    if (uiState.isSettingsOpen) {
        SettingsScreen(
            state = uiState,
            tv = TvSettingsActions(
                onSave = { host, port, key -> viewModel.saveSettings(host, port, key) },
                onRequestPairingCode = { host, port -> viewModel.requestPairingKey(host, port) },
                onPair = { host, port, key -> viewModel.pairTV(host, port, key) },
                onScan = { viewModel.scanForTVs() },
                onSelectDiscoveredTV = { tv -> viewModel.selectDiscoveredTV(tv) }
            ),
            box = BoxSettingsActions(
                onSearch = { viewModel.searchBoxes() },
                onPair = { candidate -> viewModel.requestBoxCode(candidate) },
                onPairByIp = { host -> viewModel.requestBoxCode(host) },
                onConfirmCode = { code -> viewModel.confirmBoxCode(code) },
                onCancelPairing = { viewModel.cancelBoxPairing() },
                onSetMac = { text -> viewModel.setBoxMac(text) },
                onForget = { viewModel.forgetBox() },
                onTurnOn = { viewModel.turnOn() }
            ),
            onDismiss = { viewModel.closeSettings() }
        )
        return
    }

    val sendKey: (TVKey) -> Unit = { viewModel.sendKey(it) }
    val explain: (ControlHelp) -> Unit = { viewModel.explain(it) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BlackOled)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        StatusHeader(
            title = tvDisplayName(uiState.config.modelName),
            powerState = uiState.powerState,
            notAnswering = uiState.notAnswering,
            ipConflict = uiState.ipConflict,
            volumeLevel = uiState.status.volumeLevel,
            isMuted = uiState.status.isMuted,
            inputSource = uiState.status.inputSource,
            onOpenGuide = { viewModel.openGuide() },
            onOpenSettings = { viewModel.openSettings() },
            onExplain = explain,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Com a TV desligada: por que o controle está esmaecido e como ligá-la.
            AnimatedVisibility(visible = uiState.isTvAsleep, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                PowerBanner(
                    turningOn = uiState.powerState == TVPowerState.TURNING_ON,
                    box = uiState.box,
                    powerOnStep = uiState.powerOnStep,
                    powerOnError = uiState.powerOnError,
                    attentionPulse = uiState.attentionPulse,
                    onTurnOn = { viewModel.turnOn() },
                    onOpenSettings = { viewModel.openSettings() }
                )
            }

            MainActions(uiState, viewModel, explain)

            NumericKeypad(isVisible = uiState.isKeypadVisible, onKey = sendKey, onExplain = explain)

            Column(
                modifier = Modifier.alpha(if (uiState.isTvAsleep) 0.45f else 1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                RemoteSection(title = "Navegação") {
                    KeyRow(RemoteKeys.navigationTop, sendKey, explain)
                    CircularDPad(
                        onKey = sendKey,
                        onExplain = explain,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 4.dp)
                    )
                    KeyRow(RemoteKeys.navigationBottom, sendKey, explain)
                }

                RemoteSection(title = "Volume e canal") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RockerSwitch(
                            title = "VOLUME", upIcon = Icons.Filled.Add, downIcon = Icons.Filled.Remove,
                            upHelp = Help.volumeUp, downHelp = Help.volumeDown,
                            onUp = { sendKey(TVKey.VOLUME_UP) }, onDown = { sendKey(TVKey.VOLUME_DOWN) },
                            onExplain = explain
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val muted = uiState.status.isMuted && uiState.powerState == TVPowerState.ON
                            RemoteButton(
                                help = Help.mute,
                                onClick = { sendKey(TVKey.MUTE) },
                                onExplain = explain,
                                label = if (muted) "Sem som" else Help.mute.label,
                                icon = if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentColor = if (muted) MutedOrange else TextPrimary,
                                backgroundColor = if (muted) MutedBrown else SurfaceDark,
                                state = if (muted) "Sem som" else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(58.dp)
                            )
                            KeyButton(
                                TVKey.LIVE_TV, sendKey, explain,
                                Modifier
                                    .fillMaxWidth()
                                    .height(58.dp)
                            )
                        }
                        RockerSwitch(
                            title = "CANAL", upIcon = Icons.Filled.KeyboardArrowUp, downIcon = Icons.Filled.KeyboardArrowDown,
                            upHelp = Help.channelUp, downHelp = Help.channelDown,
                            onUp = { sendKey(TVKey.CHANNEL_UP) }, onDown = { sendKey(TVKey.CHANNEL_DOWN) },
                            onExplain = explain
                        )
                    }
                }

                RemoteSection(title = "Recursos da TV") {
                    KeyRow(RemoteKeys.features, sendKey, explain)
                }

                RemoteSection(title = "Reprodução") {
                    MediaControlsRow(onKey = sendKey, onExplain = explain)
                }

                RemoteSection(title = "Botões coloridos") {
                    ColorButtonsRow(onKey = sendKey, onExplain = explain)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        HelpPanel(
            explaining = uiState.explaining,
            onClose = { viewModel.closeExplanation() },
            onOpenGuide = { viewModel.openGuide() }
        )
    }

    if (uiState.isPowerOffDialogOpen) {
        PowerOffDialog(
            canTurnOnTv = uiState.canTurnOnTv,
            onConfirm = { viewModel.confirmPowerOff() },
            onDismiss = { viewModel.dismissPowerOff() }
        )
    }

    if (uiState.isGuideOpen) {
        ButtonGuideSheet(onDismiss = { viewModel.closeGuide() })
    }
}

/** Desligar (com confirmação) ou Ligar (pela TV Box), Entrada e o teclado numérico. */
@Composable
private fun MainActions(uiState: RemoteUiState, viewModel: RemoteViewModel, explain: (ControlHelp) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val actionModifier = Modifier
            .weight(1f)
            .height(60.dp)
        if (uiState.isTvAsleep) {
            RemoteButton(
                help = Help.powerOn,
                onClick = { if (uiState.canTurnOnTv) viewModel.turnOn() else viewModel.openSettings() },
                onExplain = explain,
                backgroundColor = TurnOnGreen,
                contentColor = Color.White,
                enabled = uiState.powerState != TVPowerState.TURNING_ON,
                modifier = actionModifier
            )
        } else {
            RemoteButton(
                help = Help.powerOff,
                onClick = { viewModel.askToPowerOff() },
                onExplain = explain,
                backgroundColor = PowerRed,
                contentColor = Color.White,
                modifier = actionModifier
            )
        }
        KeyButton(TVKey.INPUT, { viewModel.sendKey(it) }, explain, actionModifier)
        RemoteButton(
            help = Help.keypad,
            onClick = { viewModel.toggleKeypad() },
            onExplain = explain,
            backgroundColor = if (uiState.isKeypadVisible) KeypadOpenBlue else SurfaceDark,
            state = if (uiState.isKeypadVisible) "Aberto" else "Fechado",
            modifier = actionModifier
        )
    }
}

/** Uma linha de teclas com ícone e legenda, todas do mesmo tamanho. */
@Composable
private fun KeyRow(keys: List<TVKey>, onKey: (TVKey) -> Unit, onExplain: (ControlHelp) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { key ->
            KeyButton(
                key = key,
                onKey = onKey,
                onExplain = onExplain,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                backgroundColor = if (key == TVKey.HOME) HomeBlue else SurfaceDark
            )
        }
    }
}

@Composable
private fun KeyButton(
    key: TVKey,
    onKey: (TVKey) -> Unit,
    onExplain: (ControlHelp) -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = SurfaceDark
) {
    val help = RemoteKeys.helpFor(key) ?: return
    RemoteButton(
        help = help,
        onClick = { onKey(key) },
        onExplain = onExplain,
        backgroundColor = backgroundColor,
        modifier = modifier
    )
}

/**
 * Prevenção de erro: desligar é fácil, mas esta TV não liga pela rede sozinha.
 */
@Composable
private fun PowerOffDialog(canTurnOnTv: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Desligar a TV?") },
        text = {
            Text(
                if (canTurnOnTv) {
                    "Para ligar de novo, use o botão Ligar do app: a TV Box liga a TV pelo HDMI."
                } else {
                    "Esta TV não liga pela rede sozinha. Para ligar pelo app depois, configure a TV Box em Ajustes."
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Desligar", color = PowerRedLight, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextSecondary)
            }
        },
        containerColor = SurfaceDark,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary
    )
}
