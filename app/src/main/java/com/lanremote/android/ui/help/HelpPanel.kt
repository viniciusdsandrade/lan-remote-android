package com.lanremote.android.ui.help

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanremote.android.ui.theme.TextPrimary

private val PanelBackground = Color(0xFF1C1C1F)
private val PanelDivider = Color(0xFF38383D)
private val DetailText = Color(0xFFBFBFC4)
private val HintText = Color(0xFFA6A6AB)
private val HintAmber = Color(0xFFFFD159)
private val LinkBlue = Color(0xFF73B8FF)
private val IconBackground = Color(0xFF38383D)

/** O ícone do controle num quadrado arredondado (ou o círculo da cor, nas teclas coloridas). */
@Composable
fun HelpIcon(help: ControlHelp, size: Dp = 34.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.27f))
            .background(IconBackground),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = help.icon,
            contentDescription = null,
            tint = help.tint ?: Color(0xFFE6E6EB),
            modifier = Modifier.size(size * (if (help.tint == null) 0.55f else 0.6f))
        )
    }
}

/** Selo pequeno, ex.: "Botões laterais". */
@Composable
fun TipBadge(text: String) {
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFFCCCCCC),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF3D3D42))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/**
 * Faixa fixa embaixo do controle: explica o botão que foi segurado (some sozinha em 7 s, ou com
 * um toque) ou diz como pedir a explicação. O TalkBack anuncia a explicação quando ela aparece.
 */
@Composable
fun HelpPanel(
    explaining: ControlHelp?,
    onClose: () -> Unit,
    onOpenGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().background(PanelBackground)) {
        HorizontalDivider(color = PanelDivider, thickness = 1.dp)
        AnimatedContent(
            targetState = explaining,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "help_panel",
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        ) { help ->
            if (help != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = "Fechar explicação", onClick = onClose)
                        .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    HelpIcon(help)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(help.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false))
                            help.tip?.let { TipBadge(it) }
                        }
                        Text(help.detail, fontSize = 12.sp, lineHeight = 16.sp, color = DetailText)
                        TextButton(onClick = onOpenGuide, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Ver todos os botões", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LinkBlue)
                        }
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Cancel, contentDescription = "Fechar explicação", tint = Color(0xFF737378))
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.TouchApp, contentDescription = null, tint = HintAmber, modifier = Modifier.size(18.dp))
                    Text(
                        "Segure um botão para saber o que ele faz.",
                        fontSize = 12.sp,
                        color = HintText,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onOpenGuide, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Ver guia", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LinkBlue)
                    }
                }
            }
        }
    }
}
