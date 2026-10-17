package com.lanremote.android.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanremote.android.data.model.AndroidTvBox
import com.lanremote.android.data.model.TVPowerState
import com.lanremote.android.data.network.DiscoveredTV
import com.lanremote.android.ui.components.TurnOnGreen
import com.lanremote.android.ui.components.color
import com.lanremote.android.ui.components.label
import com.lanremote.android.ui.remote.BoxPairingStage
import com.lanremote.android.ui.remote.RemoteUiState
import com.lanremote.android.ui.theme.BlackOled
import com.lanremote.android.ui.theme.ButtonBackground
import com.lanremote.android.ui.theme.MutedOrange
import com.lanremote.android.ui.theme.OkBlue
import com.lanremote.android.ui.theme.OnlineGreen
import com.lanremote.android.ui.theme.PowerRedLight
import com.lanremote.android.ui.theme.SurfaceBorder
import com.lanremote.android.ui.theme.SurfaceDark
import com.lanremote.android.ui.theme.TextPrimary
import com.lanremote.android.ui.theme.TextSecondary

/** O que os Ajustes fazem com a TV LG (ROAP). */
class TvSettingsActions(
    val onSave: (host: String, port: Int, pairingKey: String) -> Unit,
    val onRequestPairingCode: (host: String, port: Int) -> Unit,
    val onPair: (host: String, port: Int, pairingKey: String) -> Unit,
    val onScan: () -> Unit,
    val onSelectDiscoveredTV: (DiscoveredTV) -> Unit
)

/** O que os Ajustes fazem com a TV Box que liga a TV. */
class BoxSettingsActions(
    val onSearch: () -> Unit,
    val onPair: (AndroidTvBox) -> Unit,
    val onPairByIp: (String) -> Unit,
    val onConfirmCode: (String) -> Unit,
    val onCancelPairing: () -> Unit,
    /** Devolve false se o texto não for um MAC. */
    val onSetMac: (String) -> Boolean,
    val onForget: () -> Unit,
    val onTurnOn: () -> Unit
)

private val ErrorOrange = MutedOrange
private val StepBlue = Color(0xFF3366A6)
private val LinkBlue = Color(0xFF73B8FF)

/**
 * Ajustes em tela cheia: ligar a TV pelo app (TV Box) e a conexão com a TV LG. Fica no lugar do
 * controle (não numa janela de diálogo, que cortava o fim da rolagem); Voltar e Concluir salvam e fecham.
 */
@Composable
fun SettingsScreen(
    state: RemoteUiState,
    tv: TvSettingsActions,
    box: BoxSettingsActions,
    onDismiss: () -> Unit
) {
    val config = state.config
    var host by remember(config.host) { mutableStateOf(config.host) }
    var portText by remember(config.port) { mutableStateOf(config.port.toString()) }
    var pairingKey by remember(config.pairingKey) { mutableStateOf(config.pairingKey) }
    var macInput by remember(state.box?.mac) { mutableStateOf(state.box?.mac.orEmpty()) }
    var macInvalid by remember { mutableStateOf(false) }

    fun finish() {
        val port = portText.toIntOrNull() ?: 8080
        val cleanHost = host.trim()
        if (cleanHost.isNotEmpty() && (cleanHost != config.host || port != config.port || pairingKey.trim() != config.pairingKey)) {
            tv.onSave(cleanHost, port, pairingKey)
        }
        if (state.box?.paired == true && macInput.trim() != state.box.mac.orEmpty()) {
            box.onSetMac(macInput)
        }
        onDismiss()
    }

    BackHandler(onBack = ::finish)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BlackOled)
            .safeDrawingPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Ajustes",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            TextButton(onClick = ::finish, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Concluir", color = LinkBlue, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BoxSection(
                state = state,
                actions = box,
                macInput = macInput,
                macInvalid = macInvalid,
                onMacChange = {
                    macInput = it
                    macInvalid = false
                },
                onSaveMac = { macInvalid = !box.onSetMac(macInput) }
            )

            TvSection(
                state = state,
                host = host,
                onHostChange = { host = it },
                portText = portText,
                onPortChange = { portText = it.filter(Char::isDigit).take(5) },
                pairingKey = pairingKey,
                onPairingKeyChange = { pairingKey = it.filter(Char::isDigit).take(6) },
                actions = tv
            )
        }
    }
}

// Ligar a TV pelo app

@Composable
private fun BoxSection(
    state: RemoteUiState,
    actions: BoxSettingsActions,
    macInput: String,
    macInvalid: Boolean,
    onMacChange: (String) -> Unit,
    onSaveMac: () -> Unit
) {
    SettingsSection(title = "Ligar a TV pelo app") {
        Footnote(
            "Em standby esta TV desliga a rede, então nenhum comando chega até ela. O app acorda a TV Box " +
                "ligada no HDMI pelo Wi-Fi e ela liga a TV pelo SIMPLINK (HDMI-CEC). Basta parear uma vez."
        )

        val box = state.box
        if (box != null && box.paired) {
            PairedBox(box, state.powerState, actions, macInput, macInvalid, onMacChange, onSaveMac)
        } else {
            PairingSteps(state, actions)
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Se a TV não ligar, confira:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Bullet("Na TV LG: SIMPLINK ligado (botão SIMPLINK do app, ou Configurações › Opções › SIMPLINK).")
            Bullet("Na TV Box (HDMI-CEC): Configurações › Tela e som (ou Sistema › Entradas) › HDMI-CEC ativado, e 'Desligar automaticamente' desativado.")
            Bullet("Na TV Box (Energia): Configurações › Sistema › Energia e alimentação › Modo de energia em 'Consumo elevado (Sempre conectado)' e timer de desligamento em 'Nunca', para ela não sair do Wi-Fi após horas em espera.")
            Bullet(
                "O celular não enxerga o MAC do Wi-Fi da TV Box; o app tenta os endereços vizinhos ao do Bluetooth " +
                    "dela. Se não acordar, digite o MAC (Configurações › Sistema › Sobre › Status na TV Box)."
            )
            Bullet("Reserve o IP da TV Box no roteador para o endereço não mudar.")
        }
    }
}

@Composable
private fun PairedBox(
    box: AndroidTvBox,
    powerState: TVPowerState,
    actions: BoxSettingsActions,
    macInput: String,
    macInvalid: Boolean,
    onMacChange: (String) -> Unit,
    onSaveMac: () -> Unit
) {
    var confirmForget by remember { mutableStateOf(false) }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = OnlineGreen, modifier = Modifier.size(26.dp))
        Column {
            Text(box.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text("Pareada · ${box.host}", fontSize = 12.sp, color = TextSecondary)
        }
    }

    AppTextField(
        value = macInput,
        onValueChange = onMacChange,
        label = "MAC do Wi-Fi (opcional)",
        modifier = Modifier.fillMaxWidth(),
        placeholder = box.wakeMacs.firstOrNull()?.let { "provável: $it" } ?: "ex.: aa:bb:cc:dd:ee:ff",
        monospace = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSaveMac() })
    )
    if (macInvalid) {
        Text("Isso não parece um endereço MAC (ex.: aa:bb:cc:dd:ee:ff).", fontSize = 12.sp, color = ErrorOrange)
    }

    if (powerState == TVPowerState.OFF) {
        Button(
            onClick = actions.onTurnOn,
            colors = ButtonDefaults.buttonColors(containerColor = TurnOnGreen, contentColor = Color.White),
            shape = RoundedCornerShape(11.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Ligar a TV agora", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
        }
    }

    TextButton(onClick = { confirmForget = true }, modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(Icons.Default.Delete, contentDescription = null, tint = PowerRedLight, modifier = Modifier.size(18.dp))
        Text("Esquecer TV Box", color = PowerRedLight, modifier = Modifier.padding(start = 6.dp))
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Esquecer a TV Box?") },
            text = { Text("Para ligar a TV pelo app de novo, será preciso parear outra vez, com o código na tela da TV.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmForget = false
                    actions.onForget()
                }) { Text("Esquecer", color = PowerRedLight, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) { Text("Cancelar", color = TextSecondary) }
            },
            containerColor = SurfaceDark,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }
}

@Composable
private fun PairingSteps(state: RemoteUiState, actions: BoxSettingsActions) {
    when (val stage = state.boxPairingStage) {
        BoxPairingStage.Searching -> ProgressRow("Procurando TV Box na rede Wi-Fi…")
        BoxPairingStage.RequestingCode -> ProgressRow("Pedindo o código para a TV Box…")
        BoxPairingStage.WaitingForCode, BoxPairingStage.Confirming ->
            CodeEntry(confirming = stage == BoxPairingStage.Confirming, error = state.boxCodeError, actions = actions)
        is BoxPairingStage.Failed -> {
            Text(stage.message, fontSize = 13.sp, color = ErrorOrange)
            Candidates(state.discoveredBoxes, actions)
        }
        BoxPairingStage.Idle -> Candidates(state.discoveredBoxes, actions)
    }
}

@Composable
private fun Candidates(boxes: List<AndroidTvBox>, actions: BoxSettingsActions) {
    var manualIp by remember { mutableStateOf("") }

    Step(1, "Ligue a TV (só desta vez) e coloque na entrada HDMI da TV Box.")
    Step(2, "Toque em Parear: um código de 6 caracteres aparece na tela da TV.")

    if (boxes.isEmpty()) {
        Footnote("Nenhuma TV Box encontrada. Ela precisa estar na tomada e no mesmo Wi-Fi do celular.")
        OutlinedButton(onClick = actions.onSearch, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Default.Search, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
            Text("Procurar de novo", color = TextPrimary, modifier = Modifier.padding(start = 8.dp))
        }
    } else {
        boxes.forEach { candidate ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ButtonBackground)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Tv, contentDescription = null, tint = LinkBlue, modifier = Modifier.size(22.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(candidate.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(candidate.host, fontSize = 12.sp, color = TextSecondary)
                }
                Button(
                    onClick = { actions.onPair(candidate) },
                    colors = ButtonDefaults.buttonColors(containerColor = OkBlue, contentColor = Color.White),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("Parear") }
            }
        }
    }

    Footnote("Não apareceu (ou está no emulador)? Digite o IP da TV Box:")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AppTextField(
            value = manualIp,
            onValueChange = { manualIp = it.filter { c -> c.isDigit() || c == '.' }.take(15) },
            label = "IP da TV Box",
            placeholder = "192.0.2.101",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { actions.onPairByIp(manualIp) }),
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = { actions.onPairByIp(manualIp) },
            enabled = manualIp.count { it == '.' } == 3,
            colors = ButtonDefaults.buttonColors(containerColor = OkBlue, contentColor = Color.White),
            modifier = Modifier.heightIn(min = 48.dp)
        ) { Text("Parear") }
    }
}

@Composable
private fun CodeEntry(confirming: Boolean, error: String?, actions: BoxSettingsActions) {
    var code by remember { mutableStateOf("") }
    val canConfirm = code.length == 6 && !confirming

    Step(3, "Digite o código que apareceu na tela da TV:")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AppTextField(
            value = code,
            onValueChange = { typed ->
                code = typed.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(6)
            },
            label = "Código",
            placeholder = "Ex.: 4F2A9C",
            monospace = true,
            large = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { if (canConfirm) actions.onConfirmCode(code) }),
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = { actions.onConfirmCode(code) },
            enabled = canConfirm,
            colors = ButtonDefaults.buttonColors(containerColor = TurnOnGreen, contentColor = Color.White),
            modifier = Modifier.heightIn(min = 48.dp)
        ) { Text(if (confirming) "Confirmando…" else "Confirmar") }
    }
    if (error != null) {
        Text(error, fontSize = 13.sp, color = ErrorOrange)
    }
    TextButton(
        onClick = {
            code = ""
            actions.onCancelPairing()
        },
        enabled = !confirming,   // no meio da confirmação, cancelar só derrubaria a conexão
        modifier = Modifier.heightIn(min = 48.dp)
    ) { Text("Cancelar pareamento", color = TextSecondary) }
}

// TV LG

@Composable
private fun TvSection(
    state: RemoteUiState,
    host: String,
    onHostChange: (String) -> Unit,
    portText: String,
    onPortChange: (String) -> Unit,
    pairingKey: String,
    onPairingKeyChange: (String) -> Unit,
    actions: TvSettingsActions
) {
    SettingsSection(title = "TV LG") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Estado", fontSize = 14.sp, color = TextSecondary, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(state.powerState.color())
            )
            Text(state.powerState.label(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = state.powerState.color())
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTextField(
                value = host,
                onValueChange = onHostChange,
                label = "IP da TV",
                placeholder = "192.0.2.100",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            AppTextField(
                value = portText,
                onValueChange = onPortChange,
                label = "Porta",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(96.dp)
            )
        }

        OutlinedButton(onClick = actions.onScan, enabled = !state.isScanning, modifier = Modifier.heightIn(min = 48.dp)) {
            if (state.isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = TextPrimary)
                Text("Procurando TVs…", color = TextPrimary, modifier = Modifier.padding(start = 8.dp))
            } else {
                Icon(Icons.Default.Search, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                Text("Buscar TVs na rede Wi-Fi", color = TextPrimary, modifier = Modifier.padding(start = 8.dp))
            }
        }

        state.discoveredTVs.forEach { found ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ButtonBackground)
                    .clickable(onClickLabel = "Usar esta TV") {
                        onHostChange(found.ip)
                        onPortChange(found.port.toString())
                        actions.onSelectDiscoveredTV(found)
                    }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Tv, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                Column {
                    Text(found.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    Text("${found.ip}:${found.port}", fontSize = 11.sp, color = TextSecondary)
                }
            }
        }

        Text(
            "Parear com a TV (código de 6 dígitos)",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            modifier = Modifier.semantics { heading() }
        )
        Footnote("Toque em Pedir código: os 6 dígitos aparecem na tela da TV. É uma vez só.")
        OutlinedButton(
            onClick = { actions.onRequestPairingCode(host.trim(), portText.toIntOrNull() ?: 8080) },
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Icon(Icons.Default.Tv, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
            Text("Pedir código na TV", color = TextPrimary, modifier = Modifier.padding(start = 8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppTextField(
                value = pairingKey,
                onValueChange = onPairingKeyChange,
                label = "Código da TV",
                placeholder = "6 dígitos",
                monospace = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { actions.onPair(host.trim(), portText.toIntOrNull() ?: 8080, pairingKey) },
                enabled = pairingKey.length == 6,
                colors = ButtonDefaults.buttonColors(containerColor = TurnOnGreen, contentColor = Color.White),
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text("Confirmar") }
        }
        state.pairingMessage?.let { message ->
            Text(
                message,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (message.contains("sucesso", ignoreCase = true)) OnlineGreen else TextSecondary
            )
        }
    }
}

// Peças

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SurfaceDark)
            .border(1.dp, SurfaceBorder, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            color = LinkBlue,
            modifier = Modifier.semantics { heading() }
        )
        content()
    }
}

@Composable
private fun Footnote(text: String) {
    Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = TextSecondary)
}

@Composable
private fun Bullet(text: String) {
    Row {
        Text("•", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.width(14.dp))
        Text(text, fontSize = 12.sp, lineHeight = 16.sp, color = TextSecondary)
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(StepBlue),
            contentAlignment = Alignment.Center
        ) {
            Text("$number", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = TextPrimary, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun ProgressRow(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = LinkBlue)
        Footnote(text)
    }
}

@Composable
private fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    monospace: Boolean = false,
    large: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, fontFamily = if (monospace) FontFamily.Monospace else null) } },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            fontSize = if (large) 20.sp else 16.sp,
            fontWeight = if (large) FontWeight.SemiBold else FontWeight.Normal
        ),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = LinkBlue,
            unfocusedBorderColor = SurfaceBorder,
            focusedLabelColor = LinkBlue,
            unfocusedLabelColor = TextSecondary,
            cursorColor = LinkBlue
        ),
        modifier = modifier
    )
}
