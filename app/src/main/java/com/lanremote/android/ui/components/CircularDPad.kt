package com.lanremote.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanremote.android.data.model.TVKey
import com.lanremote.android.ui.help.ControlHelp
import com.lanremote.android.ui.help.Help
import com.lanremote.android.ui.theme.OkBlue
import com.lanremote.android.ui.theme.SurfaceBorder
import com.lanremote.android.ui.theme.SurfaceDark
import com.lanremote.android.ui.theme.TextPrimary

/** Setas em volta do OK. Segurar qualquer seta explica as setas; segurar o OK explica o OK. */
@Composable
fun CircularDPad(
    onKey: (TVKey) -> Unit,
    onExplain: (ControlHelp) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(204.dp)
            .clip(CircleShape)
            .background(SurfaceDark)
            .border(1.5.dp, SurfaceBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Posição absoluta: as setas da TV não espelham em idiomas da direita para a esquerda.
        Arrow(TVKey.UP, rotation = 0f, "cima", onKey, onExplain, x = 0.dp, y = (-62).dp)
        Arrow(TVKey.DOWN, rotation = 180f, "baixo", onKey, onExplain, x = 0.dp, y = 62.dp)
        Arrow(TVKey.LEFT, rotation = 270f, "a esquerda", onKey, onExplain, x = (-62).dp, y = 0.dp)
        Arrow(TVKey.RIGHT, rotation = 90f, "a direita", onKey, onExplain, x = 62.dp, y = 0.dp)

        RemoteButton(
            help = Help.ok,
            onClick = { onKey(TVKey.OK) },
            onExplain = onExplain,
            backgroundColor = OkBlue,
            borderColor = Color.White.copy(alpha = 0.3f),
            shape = CircleShape,
            modifier = Modifier.size(72.dp)
        ) {
            Text("OK", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun Arrow(
    key: TVKey,
    rotation: Float,
    direction: String,
    onKey: (TVKey) -> Unit,
    onExplain: (ControlHelp) -> Unit,
    x: Dp,
    y: Dp
) {
    RemoteButton(
        // O TalkBack diz a direção ("Seta para cima"); a explicação é a mesma para as quatro.
        help = Help.arrows.copy(title = "Seta para $direction"),
        onClick = { onKey(key) },
        onExplain = { onExplain(Help.arrows) },
        backgroundColor = Color.Transparent,
        borderColor = Color.Transparent,
        shape = CircleShape,
        modifier = Modifier
            .absoluteOffset(x = x, y = y)
            .size(56.dp)
    ) {
        Icon(
            Icons.Filled.KeyboardArrowUp,
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier
                .size(32.dp)
                .rotate(rotation)
        )
    }
}
