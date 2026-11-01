package com.lanremote.android.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanremote.android.ui.theme.TextPrimary
import kotlinx.coroutines.delay

private val WarnBackground = Color(0xFF30241A)
private val WarnBorder = Color(0xFF7A5428)
private val WarnAccent = Color(0xFFF59E0B)
private val WarnIconBackground = Color(0xFF543A1A)
private val InfoBackground = Color(0xFF1C2630)
private val InfoBorder = Color(0xFF3D5C7A)
private val InfoAccent = Color(0xFF8CC7FF)
private val InfoIconBackground = Color(0xFF2B4054)
private val BannerText = Color(0xFFB3B3B8)
private val ActionBlue = Color(0xFF2666A6)

/**
 * O celular está fora do Wi-Fi: nada da rede de casa responde, então a TV não está necessariamente
 * desligada. Abre o painel de Wi-Fi do sistema. Pulsa quando uma tecla é apertada ([attentionPulse]).
 */
@Composable
fun WifiBanner(attentionPulse: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var nudge by remember { mutableStateOf(false) }
    val pulseWhenShown = remember { attentionPulse }
    LaunchedEffect(attentionPulse) {
        if (attentionPulse != pulseWhenShown) {
            nudge = true
            delay(400)
            nudge = false
        }
    }
    val scale by animateFloatAsState(if (nudge) 1.02f else 1f, label = "wifi_banner_scale")
    val borderColor by animateColorAsState(if (nudge) WarnAccent else WarnBorder, label = "wifi_banner_border")
    val borderWidth by animateDpAsState(if (nudge) 2.dp else 1.dp, label = "wifi_banner_border_width")

    Banner(
        icon = Icons.Default.WifiOff,
        accent = WarnAccent,
        iconBackground = WarnIconBackground,
        background = WarnBackground,
        border = borderColor,
        borderWidth = borderWidth,
        title = "O celular está fora do Wi-Fi",
        message = "O controle fala com a TV pela rede Wi-Fi de casa. Conecte o celular à mesma rede da TV.",
        actionIcon = Icons.Default.Wifi,
        actionLabel = "Abrir o Wi-Fi",
        onAction = {
            val panel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            } else {
                Intent(Settings.ACTION_WIFI_SETTINGS)
            }
            try {
                context.startActivity(panel)
            } catch (e: ActivityNotFoundException) {
                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
        },
        modifier = modifier.scale(scale)
    )
}

/** A TV respondeu, mas recusa o app (sem chave salva, ou uma que ela não aceita mais): leva a parear. */
@Composable
fun PairingBanner(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Banner(
        icon = Icons.Default.Key,
        accent = InfoAccent,
        iconBackground = InfoIconBackground,
        background = InfoBackground,
        border = InfoBorder,
        borderWidth = 1.dp,
        title = "Pareie o app com a TV",
        message = "A TV foi encontrada, mas ainda não aceita este celular. Em Ajustes, peça o código na TV e digite os 6 números que aparecem na tela.",
        actionIcon = Icons.Default.Key,
        actionLabel = "Parear",
        onAction = onOpenSettings,
        modifier = modifier
    )
}

@Composable
private fun Banner(
    icon: ImageVector,
    accent: Color,
    iconBackground: Color,
    background: Color,
    border: Color,
    borderWidth: Dp,
    title: String,
    message: String,
    actionIcon: ImageVector,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(borderWidth, border, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(iconBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Column(
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(text = message, fontSize = 12.sp, lineHeight = 16.sp, color = BannerText)
            }
        }
        Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColors(containerColor = ActionBlue, contentColor = Color.White),
            shape = RoundedCornerShape(11.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Icon(imageVector = actionIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = actionLabel, modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
        }
    }
}
