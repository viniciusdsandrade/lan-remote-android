package com.lanremote.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lanremote.android.data.model.TVConfig
import com.lanremote.android.data.network.DiscoveredTV
import com.lanremote.android.ui.components.RemoteButton
import com.lanremote.android.ui.theme.ButtonBackground
import com.lanremote.android.ui.theme.OkBlue
import com.lanremote.android.ui.theme.OnlineGreen
import com.lanremote.android.ui.theme.SurfaceBorder
import com.lanremote.android.ui.theme.SurfaceDark
import com.lanremote.android.ui.theme.TextPrimary
import com.lanremote.android.ui.theme.TextSecondary

@Composable
fun SettingsDialog(
    config: TVConfig,
    isScanning: Boolean,
    discoveredTVs: List<DiscoveredTV>,
    pairingMessage: String?,
    onSave: (host: String, port: Int, pairingKey: String) -> Unit,
    onRequestPairingCode: (host: String, port: Int) -> Unit,
    onPair: (host: String, port: Int, pairingKey: String) -> Unit,
    onScan: () -> Unit,
    onSelectDiscoveredTV: (DiscoveredTV) -> Unit,
    onDismiss: () -> Unit
) {
    var host by remember(config.host) { mutableStateOf(config.host) }
    var portText by remember(config.port) { mutableStateOf(config.port.toString()) }
    var pairingKey by remember(config.pairingKey) { mutableStateOf(config.pairingKey) }

    val shape = RoundedCornerShape(24.dp)
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(SurfaceDark)
                .border(1.dp, SurfaceBorder, shape)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Configurar TV NetCast",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = OkBlue
                        )
                    } else {
                        RemoteButton(
                            onClick = onScan,
                            icon = Icons.Default.Refresh,
                            text = "Buscar",
                            shapeRadius = 12.dp,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Discovered TVs List (if any)
                if (discoveredTVs.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ButtonBackground)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "TVs Encontradas na Rede:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OnlineGreen
                        )
                        discoveredTVs.forEach { tv ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceDark)
                                    .clickable {
                                        host = tv.ip
                                        portText = tv.port.toString()
                                        onSelectDiscoveredTV(tv)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = null,
                                    tint = TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(text = tv.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                    Text(text = "${tv.ip}:${tv.port}", fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                }

                // IP Address Field
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Endereço IP da TV") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = OkBlue,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedLabelColor = OkBlue,
                        unfocusedLabelColor = TextSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Port Field
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { Text("Porta ROAP") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = OkBlue,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedLabelColor = OkBlue,
                        unfocusedLabelColor = TextSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Pairing Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ButtonBackground)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Pareamento (Chave de 6 dígitos)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )

                    RemoteButton(
                        onClick = {
                            val port = portText.toIntOrNull() ?: 8080
                            onRequestPairingCode(host, port)
                        },
                        text = "1. Exibir Código na TV",
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    )

                    OutlinedTextField(
                        value = pairingKey,
                        onValueChange = { pairingKey = it },
                        label = { Text("Código exibido na tela") },
                        placeholder = { Text("Ex: 123456") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = OkBlue,
                            unfocusedBorderColor = SurfaceBorder,
                            focusedLabelColor = OkBlue,
                            unfocusedLabelColor = TextSecondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    RemoteButton(
                        onClick = {
                            val port = portText.toIntOrNull() ?: 8080
                            onPair(host, port, pairingKey)
                        },
                        text = "2. Confirmar Pareamento",
                        backgroundColor = OkBlue,
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    )

                    if (!pairingMessage.isNullOrBlank()) {
                        Text(
                            text = pairingMessage,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (pairingMessage.contains("sucesso", ignoreCase = true)) OnlineGreen else TextSecondary
                        )
                    }
                }

                // Footer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = "Fechar", color = TextSecondary)
                    }

                    RemoteButton(
                        onClick = {
                            val port = portText.toIntOrNull() ?: 8080
                            onSave(host, port, pairingKey)
                        },
                        text = "Salvar",
                        backgroundColor = OkBlue,
                        shapeRadius = 12.dp,
                        modifier = Modifier.height(40.dp)
                    )
                }
            }
        }
    }
}
