package com.lanremote.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanremote.android.ui.help.ControlHelp
import com.lanremote.android.ui.theme.ButtonBackground
import com.lanremote.android.ui.theme.SurfaceBorder

private val SeamLabelBackground = Color(0xFF2B2B30)
private val SeamLabelText = Color(0xFFC7C7CC)

/**
 * Gangorra de duas pontas (Volume + / −, Canal ˄ / ˅). O nome fica na emenda, para ler como um
 * controle só; cada ponta tem a própria explicação.
 */
@Composable
fun RockerSwitch(
    title: String,
    upIcon: ImageVector,
    downIcon: ImageVector,
    upHelp: ControlHelp,
    downHelp: ControlHelp,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onExplain: (ControlHelp) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)

    Box(modifier = modifier.width(76.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, SurfaceBorder, shape)
        ) {
            RemoteButton(
                help = upHelp,
                onClick = onUp,
                onExplain = onExplain,
                label = "",
                icon = upIcon,
                iconSize = 24.dp,
                backgroundColor = ButtonBackground,
                borderColor = Color.Transparent,
                shape = RectangleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            )
            HorizontalDivider(color = SurfaceBorder, thickness = 1.dp)
            RemoteButton(
                help = downHelp,
                onClick = onDown,
                onExplain = onExplain,
                label = "",
                icon = downIcon,
                iconSize = 24.dp,
                backgroundColor = ButtonBackground,
                borderColor = Color.Transparent,
                shape = RectangleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            )
        }
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            color = SeamLabelText,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(SeamLabelBackground)
                .border(1.dp, SurfaceBorder, RoundedCornerShape(50))
                .padding(horizontal = 7.dp, vertical = 2.dp)
                .clearAndSetSemantics {}
        )
    }
}
