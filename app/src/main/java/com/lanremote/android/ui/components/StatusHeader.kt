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
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import com.lanremote.android.ui.theme.ButtonBackground
import com.lanremote.android.ui.theme.OfflineRed
import com.lanremote.android.ui.theme.OnlineGreen
import com.lanremote.android.ui.theme.PowerRed
import com.lanremote.android.ui.theme.SurfaceBorder
import com.lanremote.android.ui.theme.SurfaceDark
import com.lanremote.android.ui.theme.TextPrimary
import com.lanremote.android.ui.theme.TextSecondary

@Composable
fun StatusHeader(
    isOnline: Boolean,
    deviceTitle: String,
    deviceSubTitle: String,
    volumeLevel: Int,
    isMuted: Boolean,
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
        // Status Info & Settings Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Online LED with glow
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) OnlineGreen else OfflineRed)
            )

            Column {
                Text(
                    text = deviceTitle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = deviceSubTitle,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    if (isOnline) {
                        Text(text = "•", fontSize = 10.sp, color = TextSecondary)
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = if (isMuted) PowerRed else TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isMuted) "Mudo" else "Vol $volumeLevel",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isMuted) PowerRed else TextSecondary
                        )
                    }
                }
            }
        }

        // Actions: Settings + Power Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(ButtonBackground)
                    .border(1.dp, SurfaceBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Configurações",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            RemoteButton(
                onClick = onPowerClick,
                icon = Icons.Default.PowerSettingsNew,
                text = "Power",
                backgroundColor = PowerRed,
                contentColor = Color.White,
                shapeRadius = 21.dp,
                modifier = Modifier.size(42.dp)
            )
        }
    }
}
