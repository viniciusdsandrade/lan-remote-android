package com.lanremote.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanremote.android.data.model.TVPowerState
import com.lanremote.android.ui.theme.ButtonBackground
import com.lanremote.android.ui.theme.MutedOrange
import com.lanremote.android.ui.theme.OfflineRed
import com.lanremote.android.ui.theme.OnlineGreen
import com.lanremote.android.ui.theme.PowerRed
import com.lanremote.android.ui.theme.SurfaceBorder
import com.lanremote.android.ui.theme.SurfaceDark
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

@Composable
fun StatusHeader(
    powerState: TVPowerState,
    deviceTitle: String,
    volumeLevel: Int,
    isMuted: Boolean,
    inputSource: String?,
    powerTurnsOn: Boolean,
    powerEnabled: Boolean,
    onPowerClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SurfaceDark)
            .border(1.dp, SurfaceBorder, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(powerState.color())
            )

            Column {
                Text(
                    text = deviceTitle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = powerState.label(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = powerState.color()
                    )

                    if (powerState == TVPowerState.ON) {
                        Text(text = "•", fontSize = 10.sp, color = TextSecondary)
                        Text(
                            text = if (isMuted) "Mudo" else "Vol $volumeLevel",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isMuted) MutedOrange else VolumeCyan
                        )
                        Text(text = "•", fontSize = 10.sp, color = TextSecondary)
                        Text(
                            text = inputSource ?: "TV",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(ButtonBackground)
                    .border(1.dp, SurfaceBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Ajustes",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            RemoteButton(
                onClick = { if (powerEnabled) onPowerClick() },
                icon = Icons.Default.PowerSettingsNew,
                text = if (powerTurnsOn) "Ligar" else "Desligar",
                backgroundColor = if (powerTurnsOn) TurnOnGreen else PowerRed,
                contentColor = Color.White,
                shapeRadius = 24.dp,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}
