package com.lanremote.android.ui.components

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
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanremote.android.data.model.AndroidTvBox
import com.lanremote.android.ui.theme.TextPrimary
import kotlinx.coroutines.delay

private val BannerBackground = Color(0xFF1C2630)
private val BannerBorder = Color(0xFF3D5C7A)
private val BannerAccent = Color(0xFF8CC7FF)
private val BannerIconBackground = Color(0xFF2B4054)
private val BannerText = Color(0xFFB3B3B8)
private val PulseOrange = Color(0xFFF59E0B)
val TurnOnGreen = Color(0xFF218C45)
private val SetupBlue = Color(0xFF2666A6)

/**
 * Aparece com a TV desligada: diz por que o controle está esmaecido e oferece o caminho para
 * ligá-la. Pulsa quando uma tecla é apertada com a TV desligada ([attentionPulse] muda).
 */
@Composable
fun PowerBanner(
    turningOn: Boolean,
    box: AndroidTvBox?,
    powerOnStep: String,
    powerOnError: String?,
    attentionPulse: Int,
    onTurnOn: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var nudge by remember { mutableStateOf(false) }
    // Só pulsa para teclas apertadas com o cartão na tela: ao reaparecer, o contador antigo não conta.
    val pulseWhenShown = remember { attentionPulse }
    LaunchedEffect(attentionPulse) {
        if (attentionPulse != pulseWhenShown) {
            nudge = true
            delay(400)
            nudge = false
        }
    }
    val scale by animateFloatAsState(if (nudge) 1.02f else 1f, label = "banner_scale")
    val borderColor by animateColorAsState(if (nudge) PulseOrange else BannerBorder, label = "banner_border")
    val borderWidth by animateDpAsState(if (nudge) 2.dp else 1.dp, label = "banner_border_width")
    val shape = RoundedCornerShape(16.dp)
    val canTurnOn = box?.paired == true

    val message = when {
        turningOn -> powerOnStep
        powerOnError != null -> powerOnError
        canTurnOn -> "Toque em Ligar: a ${box!!.name} liga a TV pelo HDMI. A imagem volta em segundos; o controle, em até 1 minuto."
        else -> "Em standby esta TV desliga a rede. Dá para ligá-la pela TV Box ligada no HDMI: configure uma vez."
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(shape)
            .background(BannerBackground)
            .border(borderWidth, borderColor, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(BannerIconBackground),
                contentAlignment = Alignment.Center
            ) {
                if (turningOn) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = BannerAccent)
                } else {
                    Icon(Icons.Default.Tv, contentDescription = null, tint = BannerAccent, modifier = Modifier.size(18.dp))
                }
            }
            Column(
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (turningOn) "Ligando a TV…" else "A TV está desligada",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = message,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = if (powerOnError != null && !turningOn) PulseOrange else BannerText
                )
            }
        }

        if (!turningOn) {
            Button(
                onClick = if (canTurnOn) onTurnOn else onOpenSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canTurnOn) TurnOnGreen else SetupBlue,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(11.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Icon(
                    imageVector = if (canTurnOn) Icons.Default.PowerSettingsNew else Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = when {
                        !canTurnOn -> "Configurar para ligar pelo app"
                        powerOnError == null -> "Ligar a TV"
                        else -> "Tentar de novo"
                    },
                    modifier = Modifier.padding(start = 8.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
