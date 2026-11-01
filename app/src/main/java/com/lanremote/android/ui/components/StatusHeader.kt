package com.lanremote.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanremote.android.data.model.TVPowerState
import com.lanremote.android.ui.help.ControlHelp
import com.lanremote.android.ui.help.Help
import com.lanremote.android.ui.theme.MutedOrange
import com.lanremote.android.ui.theme.OfflineRed
import com.lanremote.android.ui.theme.OnlineGreen
import com.lanremote.android.ui.theme.TextPrimary
import com.lanremote.android.ui.theme.TextSecondary
import com.lanremote.android.ui.theme.TurningOnBlue
import com.lanremote.android.ui.theme.UnknownGray
import com.lanremote.android.ui.theme.VolumeCyan

/** Como a tela chama cada estado da TV. */
fun TVPowerState.label(): String = when (this) {
    TVPowerState.ON -> "Ligada"
    TVPowerState.OFF -> "Desligada"
    TVPowerState.TURNING_ON -> "Ligando…"
    TVPowerState.UNKNOWN -> "Verificando…"
}

fun TVPowerState.color(): Color = when (this) {
    TVPowerState.ON -> OnlineGreen
    TVPowerState.OFF -> OfflineRed
    TVPowerState.TURNING_ON -> TurningOnBlue
    TVPowerState.UNKNOWN -> UnknownGray
}

/** "TV LG 42LN5700" a partir do modelName da TV ("42LN5700-SA_11"); só "TV LG" se não souber. */
fun tvDisplayName(modelName: String): String {
    val model = modelName.substringBefore('-').trim()
    return if (Regex("^\\d{2}[A-Z]{2,3}\\d{3,4}[A-Z0-9]*$").matches(model)) "TV LG $model" else "TV LG"
}

private val HeaderButtonBackground = Color(0xFF333338)

/**
 * Cabeçalho: LED, nome da TV e o estado dela (Ligada, Ligada · sem resposta, Ligada · conflito de
 * IP, Ligada · falta parear, Desligada, Ligando…, Verificando…, ou Celular fora do Wi-Fi), com volume
 * e entrada quando ligada e respondendo; botões do Guia dos botões e dos Ajustes.
 */
@Composable
fun StatusHeader(
    title: String,
    powerState: TVPowerState,
    volumeLevel: Int,
    isMuted: Boolean,
    inputSource: String?,
    onOpenGuide: () -> Unit,
    onOpenSettings: () -> Unit,
    onExplain: (ControlHelp) -> Unit,
    modifier: Modifier = Modifier,
    notAnswering: Boolean = false,
    ipConflict: Boolean = false,
    offWifi: Boolean = false,
    needsPairing: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val explain: (ControlHelp) -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onExplain(it)
    }
    val statusLabel = when {
        offWifi -> "Celular fora do Wi-Fi"
        powerState == TVPowerState.ON && ipConflict -> "Ligada · conflito de IP"
        powerState == TVPowerState.ON && needsPairing -> "Ligada · falta parear"
        powerState == TVPowerState.ON && notAnswering -> "Ligada · sem resposta"
        else -> powerState.label()
    }
    val statusColor = when {
        offWifi -> MutedOrange
        powerState == TVPowerState.ON && (notAnswering || needsPairing) -> MutedOrange
        else -> powerState.color()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(statusColor)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val statusHelp = if (ipConflict) Help.ipConflictStatus else Help.notAnsweringStatus
                val statusModifier = if (!offWifi && powerState == TVPowerState.ON && notAnswering) {
                    Modifier.controlHelp(
                        statusHelp,
                        onClick = { explain(statusHelp) },
                        onExplain = explain,
                        state = statusLabel
                    )
                } else {
                    Modifier.semantics { contentDescription = "TV $statusLabel" }
                }
                Text(
                    text = statusLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = statusModifier
                )
                if (powerState == TVPowerState.ON && !notAnswering && !needsPairing && !offWifi) {
                    Separator()
                    Text(
                        text = if (isMuted) "Mudo" else "Vol $volumeLevel",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isMuted) MutedOrange else VolumeCyan,
                        modifier = Modifier.controlHelp(Help.volumeStatus, onClick = { explain(Help.volumeStatus) }, onExplain = explain,
                            state = if (isMuted) "Mudo" else "Volume $volumeLevel")
                    )
                    Separator()
                    Text(
                        text = inputSource ?: "TV",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.controlHelp(Help.inputStatus, onClick = { explain(Help.inputStatus) }, onExplain = explain,
                            state = inputSource ?: "TV")
                    )
                }
            }
        }

        RemoteButton(
            help = Help.guideSheet,
            onClick = onOpenGuide,
            onExplain = onExplain,
            label = "",
            backgroundColor = HeaderButtonBackground,
            borderColor = Color.Transparent,
            shape = CircleShape,
            modifier = Modifier.size(48.dp)
        )
        RemoteButton(
            help = Help.settings,
            onClick = onOpenSettings,
            onExplain = onExplain,
            label = "",
            backgroundColor = HeaderButtonBackground,
            borderColor = Color.Transparent,
            shape = CircleShape,
            modifier = Modifier.size(48.dp)
        )
    }
}

@Composable
private fun Separator() {
    Text("•", fontSize = 10.sp, color = TextSecondary, modifier = Modifier.clearAndSetSemantics {})
}
